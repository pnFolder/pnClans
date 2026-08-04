package ua.inventorytype.pnclans.api.clan

import org.bukkit.Location
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.permission.Permission
import java.util.UUID

/**
 * Primary interface representing a Clan entity.
 * Provides thread-safe methods for managing members, roles, permissions, bank balance, home waypoints, and settings.
 */
interface Clan {

    /** Unique lowercase string identifier of the clan. */
    val id: String

    /** Display name of the clan. */
    val name: String

    /** Set of all current members in the clan. */
    val users: Set<User>

    /** Map of global clan settings and their active states. */
    val settings: Map<ClanSetting, Boolean>

    /** Current progression level of the clan (ranging from 1 to 5). */
    var level: Int

    /** Competitive matchmaking rating (MMR) of the clan. */
    var mmr: Int

    /** Total kills accumulated by clan members. */
    var kills: Int

    /** Total deaths accumulated by clan members. */
    var deaths: Int

    /** Current monetary balance stored in the clan bank. */
    var bankBalance: Double

    /** Map of clan home waypoint names to their Bukkit locations. */
    val homes: Map<String, Location>

    /** Total count of clan members currently online. */
    val onlineCount: Int
        get() = users.count { it.isOnline }

    /** Maximum allowed member capacity based on current clan level. */
    val maxMembers: Int
        get() = 10 + (level * 5)

    /**
     * Adds a user to the clan with the specified rank.
     *
     * @param user The user to add.
     * @param role The rank to assign (defaults to MEMBER).
     * @return True if the user was successfully added, false if already in the clan.
     */
    fun addUser(user: User, role: ClanRole = ClanRole.MEMBER): Boolean

    /**
     * Removes a member from the clan by UUID.
     *
     * @param uuid The UUID of the member to remove.
     * @return True if the member was removed, false otherwise.
     */
    fun removeUser(uuid: UUID): Boolean

    /**
     * Changes the membership rank of an existing clan member.
     *
     * @param user The member whose rank to change.
     * @param role The new rank to assign.
     * @return True if rank was updated, false if user is not in clan.
     */
    fun setUserRole(user: User, role: ClanRole): Boolean

    /**
     * Deposits money into the clan bank.
     *
     * @param amount The monetary amount to deposit.
     */
    fun depositBank(amount: Double)

    /**
     * Withdraws money from the clan bank.
     *
     * @param amount The monetary amount to withdraw.
     * @return True if withdrawal succeeded, false if insufficient funds or invalid amount.
     */
    fun withdrawBank(amount: Double): Boolean

    /**
     * Sets or updates a clan home waypoint location.
     *
     * @param name The waypoint identifier name.
     * @param location The Bukkit location to set.
     */
    fun setHome(name: String, location: Location)

    /**
     * Deletes a clan home waypoint by name.
     *
     * @param name The waypoint identifier name.
     * @return True if home was removed, false if not found.
     */
    fun deleteHome(name: String): Boolean

    /**
     * Checks whether a global clan setting is currently enabled.
     *
     * @param setting The setting to check.
     * @return True if enabled, false otherwise.
     */
    fun isSettingEnabled(setting: ClanSetting): Boolean

    /**
     * Updates the state of a global clan setting.
     *
     * @param setting The setting to update.
     * @param enabled The new boolean state.
     */
    fun setSetting(setting: ClanSetting, enabled: Boolean)

    /**
     * Toggles the state of a global clan setting.
     *
     * @param setting The setting to toggle.
     * @return The new boolean state after toggling.
     */
    fun toggleSetting(setting: ClanSetting): Boolean

    /**
     * Grants one or more permission overrides to a specific role.
     *
     * @param role The role to modify.
     * @param permissions Permission and boolean state pairs to grant.
     */
    fun grantRolePermission(role: ClanRole, vararg permissions: Pair<Permission, Boolean>)

    /**
     * Revokes permissions from a specific role (setting them explicitly to false).
     *
     * @param role The role to modify.
     * @param permissions Permissions to revoke.
     */
    fun revokeRolePermission(role: ClanRole, vararg permissions: Permission)

    /**
     * Checks if a role has access to a specific permission.
     *
     * @param role The role to check.
     * @param permission The permission to evaluate.
     * @return True if permitted, false otherwise.
     */
    fun hasRolePermission(role: ClanRole, permission: Permission): Boolean

    /**
     * Grants a personal permission override directly to an individual member.
     *
     * @param user The member to modify.
     * @param permissions Permission and boolean state pairs to grant.
     */
    fun grantUserPermission(user: User, vararg permissions: Pair<Permission, Boolean>)

    /**
     * Revokes personal permission overrides from an individual member.
     *
     * @param user The member to modify.
     * @param permissions Permissions to revoke.
     */
    fun revokeUserPermission(user: User, vararg permissions: Permission)

    /**
     * Checks if a member has a personal permission override.
     *
     * @param user The member to check.
     * @param permission The permission to evaluate.
     * @return True if permitted, false otherwise.
     */
    fun hasUserPermission(user: User, permission: Permission): Boolean

    /**
     * Evaluates whether a user has access to perform a specific permission.
     * Leaders bypass all checks and always return true.
     * Personal user overrides take precedence over role permissions.
     *
     * @param user The user evaluating permission access.
     * @param permission The permission to evaluate.
     * @return True if access is permitted, false otherwise.
     */
    fun hasPermission(user: User, permission: Permission): Boolean

    operator fun get(role: ClanRole) = RoleAccess(this, role)
    operator fun get(user: User) = UserAccess(this, user)

    class RoleAccess(private val clan: Clan, private val role: ClanRole) {
        operator fun plusAssign(permission: Permission) = clan.grantRolePermission(role, permission to true)
        operator fun minusAssign(permission: Permission) = clan.revokeRolePermission(role, permission)
    }

    class UserAccess(private val clan: Clan, private val user: User) {
        operator fun plusAssign(permission: Pair<Permission, Boolean>) = clan.grantUserPermission(user, permission)
        operator fun minusAssign(permission: Permission) = clan.revokeUserPermission(user, permission)
    }

    /**
     * Retrieves the current role of a clan member.
     *
     * @param user The member to query.
     * @return The assigned [ClanRole], or MEMBER if not found.
     */
    fun getUserRole(user: User): ClanRole
}