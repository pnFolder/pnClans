package ua.inventorytype.pnclans.api.clan

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission

/**
 * Enumerates the hierarchical membership ranks within a clan.
 *
 * @property weight The hierarchy level of the rank. Higher weight indicates greater authority over lower ranks.
 * @property icon The Bukkit Material item used to represent this role in role editor GUIs.
 * @property defaultPermissions The default set of permissions automatically granted to members of this role.
 */
enum class ClanRole(
    val weight: Int,
    val icon: Material,
    val defaultPermissions: Set<Permission>
) {

    /** Regular clan member with standard entry-level privileges. */
    MEMBER(
        weight = 1,
        icon = Material.NAME_TAG,
        defaultPermissions = setOf(
            ClanPerms.Bank.DEPOSIT,
            ClanPerms.Bank.SEE,
            ClanPerms.Action.OPEN_CHEST,
            ClanPerms.Homes.DELETE_OWN
        )
    ),

    /** Senior clan member empowered to recruit new players. */
    ELDER(
        weight = 2,
        icon = Material.ENCHANTED_BOOK,
        defaultPermissions = setOf(
            ClanPerms.Bank.DEPOSIT,
            ClanPerms.Bank.SEE,
            ClanPerms.Action.OPEN_CHEST,
            ClanPerms.Homes.DELETE_OWN,
            ClanPerms.Members.INVITE
        )
    ),

    /** Deputy co-leader possessing extensive management authority over lower ranks and clan treasury. */
    DEPUTY(
        weight = 3,
        icon = Material.TOTEM_OF_UNDYING,
        defaultPermissions = setOf(
            ClanPerms.Bank.DEPOSIT,
            ClanPerms.Bank.WITHDRAW,
            ClanPerms.Bank.SEE,
            ClanPerms.Members.INVITE,
            ClanPerms.Members.KICK,
            ClanPerms.Homes.SET,
            ClanPerms.Homes.DELETE_OWN,
            ClanPerms.Homes.DELETE_ANY,
            ClanPerms.Action.OPEN_CHEST,
            ClanPerms.Action.UPGRADE_LEVEL,
            ClanPerms.Action.START_BATTLE,
            ClanPerms.Settings.TOGGLE_CHAT
        )
    ),

    /** Absolute leader and owner of the clan. Automatically possesses unrestricted access to all permissions. */
    LEADER(
        weight = 4,
        icon = Material.NETHER_STAR,
        defaultPermissions = emptySet()
    );
}
