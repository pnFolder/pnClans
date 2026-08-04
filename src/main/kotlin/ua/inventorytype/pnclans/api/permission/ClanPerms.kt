package ua.inventorytype.pnclans.api.permission

import org.bukkit.Material

/**
 * A centralized registry containing all available clan permissions,
 * grouped by their respective modules.
 */
object ClanPerms {


    /**
     * Список всех доступных прав в плагине.
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
     * Permissions related to clan economy and banking.
     */
    enum class Bank(
        override val flag: Permission.Flag,
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows depositing money into the clan bank. */
        DEPOSIT(
            Permission.Flag.TRUE,
            "Пополнение казны",
            "Позволяет вносить игровые средства в банк клана.",
            Material.GOLD_INGOT
        ),

        /** Allows withdrawing money from the clan bank. */
        WITHDRAW(
            Permission.Flag.FALSE,
            "Снятие с казны",
            "Позволяет снимать накопленные средства из банка клана.",
            Material.GOLD_NUGGET
        ),

        /** Allows viewing the clan bank balance. */
        SEE(
            Permission.Flag.TRUE,
            "Просмотр баланса",
            "Позволяет просматривать текущий баланс казны клана.",
            Material.EMERALD
        );
    }

    /**
     * Permissions related to managing clan members.
     */
    enum class Members(
        override val flag: Permission.Flag,
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows inviting new players to the clan. */
        INVITE(
            Permission.Flag.TRUE,
            "Приглашение игроков",
            "Позволяет отправлять игрокам приглашения на вступление.",
            Material.OAK_SIGN
        ),

        /** Allows kicking existing players from the clan. */
        KICK(
            Permission.Flag.FALSE,
            "Исключение участников",
            "Позволяет исключать участников из состава клана.",
            Material.LEATHER_BOOTS
        );
    }

    /**
     * Permissions related to clan homes and teleportation points.
     */
    enum class Homes(
        override val flag: Permission.Flag,
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows setting a new clan home location. */
        SET(
            Permission.Flag.FALSE,
            "Установка точки дома",
            "Позволяет устанавливать или переносить точку дома клана.",
            Material.RED_BED
        ),

        /** Allows deleting only the clan homes created by the user themselves. */
        DELETE_OWN(
            Permission.Flag.TRUE,
            "Удаление своих точек",
            "Позволяет удалять только личные созданные точки дома.",
            Material.WOODEN_PICKAXE
        ),

        /** Allows deleting ANY clan home, regardless of who created it. */
        DELETE_ANY(
            Permission.Flag.FALSE,
            "Удаление любых точек",
            "Позволяет удалять любые точки дома, созданные другими.",
            Material.DIAMOND_PICKAXE
        );
    }

    /**
     * Permissions for various clan activities and interactions.
     */
    enum class Action(
        override val flag: Permission.Flag,
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows opening and interacting with the clan chest. */
        OPEN_CHEST(
            Permission.Flag.TRUE,
            "Доступ к хранилищу",
            "Позволяет открывать и взаимодействовать с сундуком клана.",
            Material.CHEST
        ),

        /** Allows upgrading the clan's overall level. */
        UPGRADE_LEVEL(
            Permission.Flag.FALSE,
            "Улучшение уровня",
            "Позволяет повышать общий уровень развития клана.",
            Material.NETHER_STAR
        );
    }

    /**
     * Permissions related to toggling core clan settings.
     * Note: These permissions allow CHANGING the setting, not the state of the setting itself.
     */
    enum class Settings(
        override val flag: Permission.Flag,
        override val displayName: String,
        override val description: String,
        override val icon: Material
    ) : Permission {
        /** Allows toggling friendly fire (PvP) within the clan. */
        TOGGLE_PVP(
            Permission.Flag.FALSE,
            "Переключение PvP",
            "Позволяет менять режим уронa между участниками клана.",
            Material.DIAMOND_SWORD
        ),

        /** Allows toggling the availability of the clan chat. */
        TOGGLE_CHAT(
            Permission.Flag.FALSE,
            "Управление чатом",
            "Позволяет включать или отключать внутренний чат клана.",
            Material.WRITABLE_BOOK
        ),

        /** Allows toggling clan join/leave broadcast messages. */
        TOGGLE_JOIN(
            Permission.Flag.FALSE,
            "Уведомления о входе",
            "Позволяет настраивать оповещения о входе и выходе участников.",
            Material.BELL
        );
    }
}