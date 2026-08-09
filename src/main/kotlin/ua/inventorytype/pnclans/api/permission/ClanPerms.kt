package ua.inventorytype.pnclans.api.permission

import org.bukkit.Material

/**
 * Centralized registry containing all available clan permissions grouped by functional modules.
 * Includes permissions for Bank/Economy, Member Management, Clan Homes, Clan Actions, and Global Settings.
 */
object ClanPerms {

    /**
     * Immutable set containing every permission registered in the plugin.
     */
    val ALL_PERMISSIONS: Set<Permission> by lazy {
        buildSet {
            addAll(Bank.entries)
            addAll(Members.entries)
            addAll(Homes.entries)
            addAll(Action.entries)
            addAll(Settings.entries)
        }
    }

    /**
     * Permissions related to clan banking and economy operations.
     */
    enum class Bank(
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows depositing money into the clan bank. */
        DEPOSIT(
            "Пополнение казны",
            "Позволяет вносить игровые средства в банк клана.",
            Material.GOLD_INGOT
        ),

        /** Allows withdrawing funds from the clan bank. */
        WITHDRAW(
            "Снятие с казны",
            "Позволяет снимать накопленные средства из банка клана.",
            Material.GOLD_NUGGET
        ),

        /** Allows viewing the clan bank balance. */
        SEE(
            "Просмотр баланса",
            "Позволяет просматривать текущий баланс казны клана.",
            Material.EMERALD
        );
    }

    /**
     * Permissions related to managing clan member ranks and recruitment.
     */
    enum class Members(
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows inviting new players to join the clan. */
        INVITE(
            "Приглашение игроков",
            "Позволяет отправлять игрокам приглашения на вступление.",
            Material.OAK_SIGN
        ),

        /** Allows kicking subordinate members from the clan. */
        KICK(
            "Исключение участников",
            "Позволяет исключать участников из состава клана.",
            Material.LEATHER_BOOTS
        );
    }

    /**
     * Permissions related to clan home locations and teleportation.
     */
    enum class Homes(
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows creating or relocating clan home waypoints. */
        SET(
            "Установка точки дома",
            "Позволяет устанавливать или переносить точку дома клана.",
            Material.RED_BED
        ),

        /** Allows deleting only personal home waypoints created by the user. */
        DELETE_OWN(
            "Удаление своих точек",
            "Позволяет удалять только личные созданные точки дома.",
            Material.WOODEN_PICKAXE
        ),

        /** Allows deleting any clan home waypoint regardless of creator. */
        DELETE_ANY(
            "Удаление любых точек",
            "Позволяет удалять любые точки дома, созданные другими.",
            Material.DIAMOND_PICKAXE
        );
    }

    /**
     * Permissions for general clan interactions and upgrades.
     */
    enum class Action(
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows opening and interacting with the clan's virtual chest storage. */
        OPEN_CHEST(
            "Доступ к хранилищу",
            "Позволяет открывать и взаимодействовать с сундуком клана.",
            Material.CHEST
        ),

        /** Allows upgrading the overall progression level of the clan. */
        UPGRADE_LEVEL(
            "Улучшение уровня",
            "Позволяет повышать общий уровень развития клана.",
            Material.NETHER_STAR
        );
    }

    /**
     * Permissions for toggling core clan operational settings.
     */
    enum class Settings(
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows toggling friendly fire (PvP) between clan members. */
        TOGGLE_PVP(
            "Переключение PvP",
            "Позволяет менять режим уронa между участниками клана.",
            Material.DIAMOND_SWORD
        ),

        /** Allows enabling or disabling the private clan chat channel. */
        TOGGLE_CHAT(
            "Управление чатом",
            "Позволяет включать или отключать внутренний чат клана.",
            Material.WRITABLE_BOOK
        ),

        /** Allows toggling member join and quit broadcast notifications. */
        TOGGLE_JOIN(
            "Уведомления о входе",
            "Позволяет настраивать оповещения о входе и выходе участников.",
            Material.BELL
        ),

        /** Allows changing clan teammate highlight color. */
        TOGGLE_COLOR(
            "Цвет соклановцев",
            "Позволяет выбрать цвет подсветки клана.",
            Material.LIME_DYE
        ),

        /** Allows changing when clan teammate highlight is active. */
        TOGGLE_HIGHLIGHT_MODE(
            "Режим подсветки",
            "Позволяет выбрать, когда работает подсветка соклановцев.",
            Material.BEACON
        );
    }
}
