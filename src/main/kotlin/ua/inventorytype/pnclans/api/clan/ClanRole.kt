package ua.inventorytype.pnclans.api.clan

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission

/**
 * Represents the hierarchical roles within a clan.
 *
 * @property weight The hierarchical weight of the role. Higher value means higher authority.
 */
enum class ClanRole(
    val weight: Int,
    val icon: Material,
    val defaultPermissions: Set<Permission>
) {

    /** Regular clan member with basic access. */
    MEMBER(
        weight = 1,
        icon = Material.IRON_INGOT,
        defaultPermissions = setOf(
            ClanPerms.Bank.DEPOSIT,
            ClanPerms.Bank.SEE,
            ClanPerms.Action.OPEN_CHEST,
            ClanPerms.Homes.DELETE_OWN
        )
    ),

    /** Respected member with slightly elevated privileges. */
    ELDER(
        weight = 2,
        icon = Material.GOLD_INGOT,
        defaultPermissions = setOf(
            ClanPerms.Bank.DEPOSIT,
            ClanPerms.Bank.SEE,
            ClanPerms.Action.OPEN_CHEST,
            ClanPerms.Homes.DELETE_OWN,
            ClanPerms.Members.INVITE
        )
    ),

    /** The deputy/co-leader who manages the clan when the leader is absent. */
    DEPUTY(
        weight = 3,
        icon = Material.DIAMOND,
        defaultPermissions = setOf(
            // Зам может почти всё, кроме удаления клана
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
            ClanPerms.Settings.TOGGLE_CHAT
        )
    ),

    // У Лидера нет списка по умолчанию, потому что в коде
    // мы сделаем так, чтобы Лидеру разрешалось ВСЁ автоматически.
    /** The absolute owner and creator of the clan. */
    LEADER(
        weight = 4,
        icon = Material.NETHER_STAR,
        defaultPermissions = emptySet()
    );
}