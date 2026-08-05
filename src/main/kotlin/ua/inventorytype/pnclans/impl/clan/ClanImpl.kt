package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Location
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.Permission
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction

/**
 * High-performance, thread-safe implementation of the [Clan] contract.
 * Utilizes concurrent data structures ([ConcurrentHashMap]) to ensure safe asynchronous operations across thread pools.
 *
 * @param id The unique lowercase identifier string of the clan.
 * @param name The display name of the clan.
 * @param initialUsers Initial set of member-role pairs to populate upon creation.
 */
class ClanImpl(
    override val id: String,
    override val name: String,
    initialUsers: Set<Pair<User, ClanRole>> = emptySet()
) : Clan {

    override var level: Int = 1
    override var mmr: Int = 1000
    override var kills: Int = 0
    override var deaths: Int = 0
    override var bankBalance: Double = 0.0

    private val _treasuryLogs = java.util.Collections.synchronizedList(mutableListOf<TreasuryTransaction>())
    override val treasuryLogs: List<TreasuryTransaction>
        get() = synchronized(_treasuryLogs) { _treasuryLogs.toList() }

    override fun addTreasuryLog(log: TreasuryTransaction) {
        synchronized(_treasuryLogs) {
            _treasuryLogs.add(log)
            if (_treasuryLogs.size > 500) _treasuryLogs.removeAt(0)
        }
    }

    private val _homes = ConcurrentHashMap<String, Location>()
    override val homes: Map<String, Location>
        get() = _homes.toMap()

    private val _settings = ConcurrentHashMap<ClanSetting, Boolean>()

    private val _rolePermissions = ConcurrentHashMap<ClanRole, MutableSet<Pair<Permission, Boolean>>>()
    private val _userPermissions = ConcurrentHashMap<UUID, MutableSet<Pair<Permission, Boolean>>>()

    private val _users: MutableSet<Pair<User, ClanRole>> = ConcurrentHashMap.newKeySet<Pair<User, ClanRole>>().apply {
        addAll(initialUsers)
    }

    override val settings: Map<ClanSetting, Boolean>
        get() = ClanSetting.entries.associateWith { isSettingEnabled(it) }

    val rolePermissions: Map<ClanRole, Set<Pair<Permission, Boolean>>>
        get() = _rolePermissions.toMap()

    val userPermissions: Map<UUID, Set<Pair<Permission, Boolean>>>
        get() = _userPermissions.toMap()

    override val users: Set<User>
        get() = _users.map { it.first }.toSet()

    override fun addUser(user: User, role: ClanRole): Boolean {
        if (_users.any { it.first.uuid == user.uuid }) return false
        return _users.add(user to role)
    }

    override fun removeUser(uuid: UUID): Boolean {
        return _users.removeIf { it.first.uuid == uuid }
    }

    override fun setUserRole(user: User, role: ClanRole): Boolean {
        val existing = _users.find { it.first.uuid == user.uuid } ?: return false
        _users.remove(existing)
        return _users.add(user to role)
    }

    override fun depositBank(amount: Double) {
        if (amount > 0) bankBalance += amount
    }

    override fun withdrawBank(amount: Double): Boolean {
        if (amount <= 0 || bankBalance < amount) return false
        bankBalance -= amount
        return true
    }

    override fun setHome(name: String, location: Location) {
        _homes[name.lowercase()] = location
    }

    override fun deleteHome(name: String): Boolean {
        return _homes.remove(name.lowercase()) != null
    }

    override fun isSettingEnabled(setting: ClanSetting): Boolean {
        return _settings.getOrDefault(setting, setting.defaultValue)
    }

    override fun setSetting(setting: ClanSetting, enabled: Boolean) {
        _settings[setting] = enabled
    }

    override fun toggleSetting(setting: ClanSetting): Boolean {
        val newValue = !isSettingEnabled(setting)
        _settings[setting] = newValue
        return newValue
    }

    override fun grantRolePermission(role: ClanRole, vararg permissions: Pair<Permission, Boolean>) {
        val set = _rolePermissions.computeIfAbsent(role) { ConcurrentHashMap.newKeySet() }
        for (newPair in permissions) {
            set.removeIf { it.first.node == newPair.first.node }
            set.add(newPair)
        }
    }

    override fun revokeRolePermission(role: ClanRole, vararg permissions: Permission) {
        val set = _rolePermissions.computeIfAbsent(role) { ConcurrentHashMap.newKeySet() }
        for (perm in permissions) {
            set.removeIf { it.first.node == perm.node }
            set.add(perm to false)
        }
    }

    override fun hasRolePermission(role: ClanRole, permission: Permission): Boolean {
        val roleSet = _rolePermissions[role]
        val foundPair = roleSet?.find { it.first.node == permission.node }
        if (foundPair != null) return foundPair.second
        return permission in role.defaultPermissions
    }

    override fun grantUserPermission(user: User, vararg permissions: Pair<Permission, Boolean>) {
        val set = _userPermissions.computeIfAbsent(user.uuid) { ConcurrentHashMap.newKeySet() }
        for (newPair in permissions) {
            set.removeIf { it.first.node == newPair.first.node }
            set.add(newPair)
        }
    }

    override fun revokeUserPermission(user: User, vararg permissions: Permission) {
        val userSet = _userPermissions[user.uuid] ?: return
        val targetNodes = permissions.map { it.node }.toSet()
        userSet.removeIf { it.first.node in targetNodes }
    }

    override fun hasUserPermission(user: User, permission: Permission): Boolean {
        val userRole = getUserRole(user)
        if (userRole == ClanRole.LEADER) return true
        val userSet = _userPermissions[user.uuid] ?: return hasPermission(user, permission)
        val foundPair = userSet.find { it.first.node == permission.node }
        return foundPair?.second ?: hasPermission(user, permission)
    }

    override fun hasPermission(user: User, permission: Permission): Boolean {
        val userRole = getUserRole(user)
        if (userRole == ClanRole.LEADER) {
            return true
        }

        val userSet = _userPermissions[user.uuid]
        val personalOverride = userSet?.find { it.first.node == permission.node }
        if (personalOverride != null) {
            return personalOverride.second
        }

        return hasRolePermission(userRole, permission)
    }

    override fun getUserRole(user: User): ClanRole {
        return _users.find { it.first.uuid == user.uuid }?.second ?: ClanRole.MEMBER
    }
}
