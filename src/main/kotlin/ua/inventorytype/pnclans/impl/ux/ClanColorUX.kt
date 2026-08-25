package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanHighlightColor
import ua.inventorytype.pnclans.api.clan.ClanHighlightType
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/** Config-driven editor for the visual marker shown to clan members. */
class ClanColorUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.clanColorMenu

        title(menuCfg.title)
        rows(menuCfg.rows.coerceIn(1, 6))
        hotWorldDecor(true)

        addItem("overview", placeholders = { clan -> commonPlaceholders(clan) })
        addItem("typeInfo")
        addItem(
            "armor",
            placeholders = { clan -> commonPlaceholders(clan) + ("selected" to selectedText(clan.highlightType == ClanHighlightType.ARMOR)) },
            glow = { clan -> clan.highlightType == ClanHighlightType.ARMOR }
        ) { player -> applyType(player, ClanHighlightType.ARMOR) }
        addItem(
            "glow",
            placeholders = { clan -> commonPlaceholders(clan) + ("selected" to selectedText(clan.highlightType == ClanHighlightType.GLOW)) },
            glow = { clan -> clan.highlightType == ClanHighlightType.GLOW }
        ) { player -> applyType(player, ClanHighlightType.GLOW) }

        addItem("statusInfo")
        addItem("enabled", placeholders = { clan -> commonPlaceholders(clan) }, glow = { clan -> clan.highlightEnabled }) { player -> applyStatus(player, true) }
        addItem("disabled", placeholders = { clan -> commonPlaceholders(clan) }, glow = { clan -> !clan.highlightEnabled }) { player -> applyStatus(player, false) }

        cfg.settings.clanHighlightColors
            .mapNotNull(ClanHighlightColor::fromKey)
            .distinct()
            .forEach { color ->
                val id = "color_${color.name.lowercase()}"
                addItem(
                    id,
                    placeholders = { clan -> commonPlaceholders(clan) + mapOf("color_code" to color.chatColor.toString(), "color_name" to color.displayName) },
                    glow = { clan -> clan.highlightColor == color }
                ) { player -> applyColor(player, color) }
            }

        addItem("reset") { player -> applyReset(player) }
        addItem("back") { player -> SettingsUX(this@ClanColorUX.clanService).open(player) }
    }

    private fun addItem(
        key: String,
        placeholders: (Clan) -> Map<String, String> = { emptyMap() },
        glow: (Clan) -> Boolean = { false },
        click: ((Player) -> Unit)? = null
    ) {
        val itemCfg = clanService.plugin.configService.menus.clanColorMenu.items[key] ?: return
        val maxSlot = clanService.plugin.configService.menus.clanColorMenu.rows.coerceIn(1, 6) * 9 - 1
        if (itemCfg.slot !in 0..maxSlot) {
            clanService.plugin.logger.warning("[pnClans] clanColorMenu item '$key' has invalid slot ${itemCfg.slot}; item skipped.")
            return
        }

        slot(itemCfg.slot) {
            dynamicItem(this@ClanColorUX.parseMaterial(itemCfg.material, Material.PAPER)) { player ->
                val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null
                this@ClanColorUX.render(this, player, itemCfg, placeholders(clan), itemCfg.glow || glow(clan))
                null
            }
            if (click != null) onClick { player, _ -> click(player) }
        }
    }

    private fun commonPlaceholders(clan: Clan): Map<String, String> {
        val display = clanService.plugin.configService.settings.clanHighlightDisplay
        return mapOf(
            "type" to clan.highlightType.displayName,
            "status" to if (clan.highlightEnabled) display.enabledText else display.disabledText,
            "color" to clan.highlightColor.displayName
        )
    }

    private fun selectedText(selected: Boolean): String {
        val display = clanService.plugin.configService.settings.clanHighlightDisplay
        return if (selected) display.selectedYes else display.selectedNo
    }

    private fun render(
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>,
        glow: Boolean
    ) {
        val cfg = clanService.plugin.configService
        builder.name(cfg.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { cfg.formatMessage(player, it, placeholders) })
        builder.glow(glow)
    }

    private fun applyColor(player: Player, color: ClanHighlightColor) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_COLOR)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }
        clan.highlightColor = color
        if (!saveHighlight(clan, player)) return
        cfg.send(player, cfg.messages.settings.highlightColorChanged, mapOf("color" to color.displayName))
        update(player)
    }

    private fun applyType(player: Player, type: ClanHighlightType) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_HIGHLIGHT_MODE)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }
        clan.highlightType = type
        if (!saveHighlight(clan, player)) return
        cfg.send(player, cfg.messages.settings.highlightTypeChanged, mapOf("type" to type.displayName))
        update(player)
    }

    private fun applyStatus(player: Player, enabled: Boolean) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_HIGHLIGHT_MODE)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }
        clan.highlightEnabled = enabled
        if (!saveHighlight(clan, player)) return
        cfg.send(player, if (enabled) cfg.messages.settings.highlightEnabled else cfg.messages.settings.highlightDisabled)
        update(player)
    }

    private fun applyReset(player: Player) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_HIGHLIGHT_MODE)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }

        val display = cfg.settings.clanHighlightDisplay
        clan.highlightEnabled = display.resetEnabled
        clan.highlightType = runCatching { ClanHighlightType.valueOf(display.resetType.uppercase()) }.getOrDefault(ClanHighlightType.ARMOR)
        clan.highlightColor = ClanHighlightColor.fromKey(display.resetColor) ?: ClanHighlightColor.AQUA
        if (!saveHighlight(clan, player)) return
        cfg.send(player, cfg.messages.settings.highlightReset)
        update(player)
    }

    private fun saveHighlight(clan: Clan, player: Player): Boolean {
        if (!clanService.saveClan(clan)) {
            clanService.plugin.configService.send(player, clanService.plugin.configService.messages.settings.highlightSaveFailed)
            return false
        }
        clanService.plugin.clanHighlightService.syncClan(clan)
        clanService.plugin.clanHighlightService.resyncClanLater(clan)
        return true
    }

    private fun parseMaterial(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)
}
