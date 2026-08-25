package ua.inventorytype.pnclans.impl.config

import org.bukkit.Material
import ua.inventorytype.pnclans.BukkitPlugin

/**
 * Verifies that every GUI template consumed by pnClans is physically present and usable.
 * Missing templates are reported with their exact menus.yml path instead of being replaced by
 * anonymous STONE items with empty names or lore.
 */
internal object MenuConfigValidator {

    fun validate(plugin: BukkitPlugin, config: ConfigService): Int {
        val issues = mutableListOf<String>()
        fun issue(path: String, message: String) {
            issues += "$path: $message"
        }

        val menus = config.menus

        validateMenu(
            "noClanMenu",
            menus.noClanMenu.title,
            menus.noClanMenu.rows,
            menus.noClanMenu.items,
            setOf("info", "create", "top", "help"),
            issue = ::issue
        )

        val configuredColorKeys = config.settings.clanHighlightColors
            .map { "color_${it.lowercase()}" }
            .toSet()
        validateMenu(
            "clanColorMenu",
            menus.clanColorMenu.title,
            menus.clanColorMenu.rows,
            menus.clanColorMenu.items,
            setOf("overview", "typeInfo", "armor", "glow", "statusInfo", "enabled", "disabled", "reset", "back") + configuredColorKeys,
            issue = ::issue
        )

        validateMenu(
            "mainMenu",
            menus.mainMenu.title,
            menus.mainMenu.rows,
            menus.mainMenu.items,
            setOf("stats", "members", "chest", "treasury", "homes", "invite", "top", "upgrade", "settings", "help", "leave", "shop", "quests", "battles"),
            issue = ::issue
        )
        validateMenu("helpMenu", menus.helpMenu.title, menus.helpMenu.rows, menus.helpMenu.items, setOf("evolution", "rewards", "earning", "back"), issue = ::issue)
        validateMenu("leaveConfirmMenu", menus.leaveConfirmMenu.title, menus.leaveConfirmMenu.rows, menus.leaveConfirmMenu.items, setOf("confirmDisband", "confirmLeave", "info", "cancel"), issue = ::issue)
        validateMenu("membersMenu", menus.membersMenu.title, menus.membersMenu.rows, menus.membersMenu.items, setOf("member", "member_self", "member_no_permission", "previous", "back", "next"), issue = ::issue)
        validateMenu("settingsMenu", menus.settingsMenu.title, menus.settingsMenu.rows, menus.settingsMenu.items, setOf("overview", "pvp", "chat", "chest", "join", "color", "roles", "hint", "back"), issue = ::issue)
        validateMenu("editorRolesMenu", menus.editorRolesMenu.title, menus.editorRolesMenu.rows, menus.editorRolesMenu.items, setOf("overview", "role", "permission", "back"), issue = ::issue)
        validateMenu("userPermissionsMenu", menus.userPermissionsMenu.title, menus.userPermissionsMenu.rows, menus.userPermissionsMenu.items, setOf("permission", "back"), issue = ::issue)
        validateMenu("treasuryMenu", menus.treasuryMenu.title, menus.treasuryMenu.rows, menus.treasuryMenu.items, setOf("center", "deposit", "withdraw", "depositPresets", "withdrawPresets", "history", "back"), issue = ::issue)
        validateMenu("upgradeMenu", menus.upgradeMenu.title, menus.upgradeMenu.rows, menus.upgradeMenu.items, setOf("overview", "level", "upgrade", "back"), issue = ::issue)
        validateMenu("topMenu", menus.topMenu.title, menus.topMenu.rows, menus.topMenu.items, setOf("overview", "entry", "empty", "previous", "previousLocked", "back", "next", "nextLocked"), issue = ::issue)
        validateMenu("chestMenu", menus.chestMenu.title, menus.chestMenu.rows, menus.chestMenu.items, setOf("lockedSlot", "stats", "back", "core", "upgrade", "close"), issue = ::issue)

        validateMenu(
            "treasuryHistoryMenu",
            menus.treasuryHistoryMenu.title,
            menus.treasuryHistoryMenu.rows,
            menus.treasuryHistoryMenu.items,
            setOf("depositEntry", "withdrawEntry", "upgradeEntry", "previous", "back", "next"),
            issue = ::issue
        )

        validateEntrySlots("topMenu.entrySlots", menus.topMenu.entrySlots, menus.topMenu.rows, ::issue)
        validateEntrySlots("treasuryHistoryMenu.entrySlots", menus.treasuryHistoryMenu.entrySlots, menus.treasuryHistoryMenu.rows, ::issue)

        if (issues.isNotEmpty()) {
            issues.forEach { plugin.logger.warning("[pnClans/MenuConfig] $it") }
            plugin.logger.warning(
                "[pnClans/MenuConfig] Found ${issues.size} menu configuration issue(s). " +
                    "Missing IDs are not replaced by blank fallback items; repair the paths above."
            )
        }
        return issues.size
    }

    private fun validateMenu(
        menuName: String,
        title: String,
        rows: Int,
        items: Map<String, GuiItemConfig>,
        requiredKeys: Set<String>,
        issue: (String, String) -> Unit
    ) {
        val path = "menus.yml.$menuName"
        if (title.isBlank()) issue("$path.title", "cannot be blank")
        if (rows !in 1..6) issue("$path.rows", "must be between 1 and 6")
        val maximumSlot = rows.coerceIn(1, 6) * 9 - 1

        requiredKeys.forEach { key ->
            val item = items[key]
            if (item == null) {
                issue("$path.items.$key", "required GUI item ID is missing")
                return@forEach
            }
            if (item.name.isBlank()) issue("$path.items.$key.name", "cannot be blank")
            if (item.lore.isEmpty()) issue("$path.items.$key.lore", "must contain a description")
        }

        items.forEach { (key, item) ->
            if (item.slot !in 0..maximumSlot) {
                issue("$path.items.$key.slot", "slot ${item.slot} is outside 0..$maximumSlot")
            }
            if (Material.matchMaterial(item.material) == null) {
                issue("$path.items.$key.material", "unknown Material '${item.material}'")
            }
        }
    }

    private fun validateEntrySlots(
        path: String,
        slots: List<Int>,
        rows: Int,
        issue: (String, String) -> Unit
    ) {
        if (slots.isEmpty()) {
            issue("menus.yml.$path", "must contain at least one slot")
            return
        }
        val maximumSlot = rows.coerceIn(1, 6) * 9 - 1
        slots.forEachIndexed { index, slot ->
            if (slot !in 0..maximumSlot) {
                issue("menus.yml.$path[$index]", "slot $slot is outside 0..$maximumSlot")
            }
        }
        slots.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { slot ->
            issue("menus.yml.$path", "slot $slot is used more than once")
        }
    }
}
