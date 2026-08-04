package ua.inventorytype.pnclans.api.clan

import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.permission.Permission
import java.util.UUID

/**
 * Represents a clan entity and handles its permission management for both roles and individual users.
 */
interface Clan {

    /** Публичное неизменяемое представление всех настроек клана */
    val settings: Map<ClanSetting, Boolean>

    /** Проверить, включена ли настройка */
    fun isSettingEnabled(setting: ClanSetting): Boolean

    /** Установить значение настройки */
    fun setSetting(setting: ClanSetting, enabled: Boolean)

    /** Переключить значение (было true -> стало false) */
    fun toggleSetting(setting: ClanSetting): Boolean



    /** Map of custom permissions assigned directly to specific clan roles. */
    val rolePermissions: Map<ClanRole, Set<Pair<Permission, Permission.Flag>>>

    /** Map of personal permission overrides assigned to specific users. */
    val userPermissions: Map<User, Set<Pair<Permission, Permission.Flag>>>

    val users: Set<User>



    /** Grants one or more permissions to a specific [ClanRole]. */
    fun grantRolePermission(role: ClanRole, vararg permissions: Pair<Permission, Permission.Flag>)

    /** Revokes one or more permissions from a specific [ClanRole]. */
    fun revokeRolePermission(role: ClanRole, vararg permissions: Permission)

    /** Checks whether a specific [ClanRole] has the specified [Permission]. */
    fun hasRolePermission(role: ClanRole, permission: Permission): Permission.Flag



    /** Grants one or more personal override permissions directly to a [User]. */
    fun grantUserPermission(user: User, vararg permissions: Pair<Permission, Permission.Flag>)

    /** Revokes one or more personal override permissions from a [User]. */
    fun revokeUserPermission(user: User, vararg permissions: Permission)

    /** Checks whether a [User] has a specific personal [Permission] override. */
    fun hasUserPermission(user: User, permission: Permission): Permission.Flag



    /**
     * Evaluates whether a [User] has access to a specific [Permission].
     * Checks personal overrides first, falling back to the user's [ClanRole] permissions.
     */
    fun hasPermission(user: User, permission: Permission): Permission.Flag


// ==========================================
    // OPERATORS (Операторы перенесены внутрь!)
    // ==========================================

    operator fun get(role: ClanRole) = RoleAccess(this, role)
    operator fun get(user: User) = UserAccess(this, user)

    class RoleAccess(private val clan: Clan, private val role: ClanRole) {
        operator fun plusAssign(permission: Permission) = clan.grantRolePermission(role, permission)
        operator fun minusAssign(permission: Permission) = clan.revokeRolePermission(role, permission)
    }

    class UserAccess(private val clan: Clan, private val user: User) {
        operator fun plusAssign(permission: Pair<Permission, Permission.Flag>) = clan.grantUserPermission(user, permission)
        operator fun minusAssign(permission: Permission) = clan.revokeUserPermission(user, permission)
    }

//    clan[ClanRole.MEMBER] += ClanPerms.Bank.DEPOSIT
//
//    // 2. Забрать право у роли Участник
//    clan[ClanRole.MEMBER] -= ClanPerms.Bank.WITHDRAW
//
//    // 3. Выдать персональное право игроку
//    clan[user] += ClanPerms.Bank.WITHDRAW
//
//    // 4. Забрать персональное право у игрока
//    clan[user] -= ClanPerms.Bank.DEPOSIT

    fun getUserRole(user: User): ClanRole
}