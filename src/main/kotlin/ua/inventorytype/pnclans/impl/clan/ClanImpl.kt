package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.api.permission.Permission.Flag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ClanImpl(
    val id: String,
    val name: String,
    initialUsers: Set<Pair<User, ClanRole>> = emptySet()
) : Clan {

    // Хранилище настроек: если значения нет в карте, берётся defaultValue из Enum
    private val _settings = ConcurrentHashMap<ClanSetting, Boolean>()

    // Внутренние изменяемые карты для хранения прав.
    // ConcurrentHashMap обеспечивает потокобезопасность.
    private val _rolePermissions = ConcurrentHashMap<ClanRole, MutableSet<Pair<Permission, Permission.Flag>>>()
    private val _userPermissions = ConcurrentHashMap<UUID, MutableSet<Pair<Permission, Permission.Flag>>>()

    // 2. Внутренний изменяемый сет для хранения участников в памяти.
    // Используем ConcurrentHashMap.newKeySet() для потокобезопасности.
    private val _users: MutableSet<Pair<User, ClanRole>> = ConcurrentHashMap.newKeySet<Pair<User, ClanRole>>().apply {
        addAll(initialUsers)
    }

    override val settings: Map<ClanSetting, Boolean>
        get() = ClanSetting.entries.associateWith { isSettingEnabled(it) }

    // Публичные неизменяемые представления для API
    override val rolePermissions: Map<ClanRole, Set<Pair<Permission, Permission.Flag>>>
        get() = _rolePermissions.mapValues { it.value.toSet() }

    override val userPermissions: Map<User, Set<Pair<Permission, Permission.Flag>>>
        get() = _userPermissions.mapNotNull { (uuid, perms) ->
            val user = users.find { it.uuid == uuid } ?: return@mapNotNull null
            user to perms.toSet()
        }.toMap()

    // 3. Публичное неизменяемое представление для API (Read-Only)
    override val users: Set<User>
        get() = _users.map { it.first }.toSet()


    override fun isSettingEnabled(setting: ClanSetting): Boolean {
        // Если игроки еще не меняли настройку, отдаём дефолтное значение
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

    // ==========================================
    // ROLE PERMISSIONS
    // ==========================================

    override fun grantRolePermission(role: ClanRole, vararg permissions: Pair<Permission, Permission.Flag>) {
        if (permissions.any { it.second == Permission.Flag.FALSE }) {
            error("Ошибка! Флаг FALSE не может быть использован при выдаче прав. Используйте TRUE или DENY.")
        }

        val set = _rolePermissions.computeIfAbsent(role) { ConcurrentHashMap.newKeySet() }
        for (newPair in permissions) {
            set.removeIf { it.first == newPair.first }
            set.add(newPair)
        }
    }

    override fun revokeRolePermission(role: ClanRole, vararg permissions: Permission) {
        val roleSet = _rolePermissions[role] ?: return
        val targetNodes = permissions.map { it.node }.toSet()

        roleSet.removeIf { it.first.node in targetNodes }
    }

    override fun hasRolePermission(role: ClanRole, permission: Permission): Permission.Flag {
        val roleSet = _rolePermissions[role] ?: return Permission.Flag.FALSE
        val foundPair = roleSet.find { it.first.node == permission.node }

        return foundPair?.second ?: return Permission.Flag.FALSE
    }

    // ==========================================
    // USER PERMISSIONS (Personal Overrides)
    // ==========================================

    override fun grantUserPermission(user: User, vararg permissions: Pair<Permission, Permission.Flag>) {
        if (permissions.any { it.second == Permission.Flag.FALSE }) {
            error("Ошибка! Флаг FALSE не может быть использован при выдаче прав. Используйте TRUE или DENY.")
        }

        val set = _userPermissions.computeIfAbsent(user.uuid) { ConcurrentHashMap.newKeySet() }

        // Перебираем новые права: если для разрешения уже стоял другой флаг, обновляем его
        for (newPair in permissions) {
            set.removeIf { it.first == newPair.first }
            set.add(newPair)
        }
    }

    override fun revokeUserPermission(user: User, vararg permissions: Permission) {
        val userSet = _userPermissions[user.uuid] ?: return
        val targetNodes = permissions.map { it.node }.toSet()

        // Удаляем любые персональные записи (и TRUE, и FALSE) для указанных прав
        userSet.removeIf { it.first.node in targetNodes }
    }

    override fun hasUserPermission(user: User, permission: Permission): Permission.Flag {
        val userSet = _userPermissions[user.uuid] ?: return Permission.Flag.FALSE
        val foundPair = userSet.find { it.first.node == permission.node }

        return foundPair?.second ?: Permission.Flag.FALSE
    }

    // ==========================================
    // COMBINED EVALUATION (Главная логика)
    // ==========================================

    override fun hasPermission(user: User, permission: Permission): Permission.Flag {
        // 1. Проверяем персональные права игрока (Override)
        val userSet = _userPermissions[user.uuid]
        val personalOverride = userSet?.find { it.first.node == permission.node }

        // Если у пользователя жестко прописан персональный флаг (TRUE или FALSE), возвращаем его
        if (personalOverride != null) {
            return personalOverride.second
        }

        // 2. Если персонального переопределения нет — берем роль юзера и проверяем права роли
        // (Здесь нужно подставить способ получения роли игрока из вашего юзера/клана)
        val userRole = getUserRole(user)

        // Лидер по умолчанию имеет доступ ко всему
        if (userRole == ClanRole.LEADER) {
            return Permission.Flag.TRUE
        }

        return hasRolePermission(userRole, permission)
    }

    override fun getUserRole(user: User): ClanRole {
        return _users.find { it.first.uuid == user.uuid }?.second
            ?: error("Игрок ${user.uuid} не является участником клана $name!")
    }
}