package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.config.HomesEntryConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/**
 * Clan homes management GUI with level-aware pages and fully config-driven visuals.
 *
 * All item texts, lore lines, materials, slot indices, page numbers, and clan level
 * requirements are read from [ua.inventorytype.pnclans.impl.config.HomesMenuConfig].
 *
 * @param clanService The clan service providing home data, persistence, and config access.
 * @param requestedPage The page number to open, clamped to the configured page range.
 */
class HomesUX(
    clanService: ClanService,
    requestedPage: Int = 1
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val homeCfg = cfg.menus.homesMenu
        val maxPage = (homeCfg.homes.maxOfOrNull { it.page } ?: 1).coerceAtLeast(1)
        val currentPage = requestedPage.coerceIn(1, maxPage)
        val pagePlaceholders = mapOf(
            "page" to currentPage.toString(),
            "pages" to maxPage.toString()
        )

        title(applyPlaceholders(homeCfg.title, pagePlaceholders))
        rows(homeCfg.rows)
        hotWorldDecor(true)

        homeCfg.homes.filter { it.page == currentPage }.forEach { entry ->
            val lockedMat = parseMaterial(entry.lockedMaterial, Material.BARRIER)
            val unsetMat = parseMaterial(entry.unsetMaterial, Material.ENDER_EYE)
            val setMat = parseMaterial(entry.unlockedMaterial.ifBlank { entry.setMaterial }, Material.RED_BED)

            slot(entry.slot) {
                dynamicItem(lockedMat) { player ->
                    val clan = this@HomesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val loc = clan.homes[entry.key]
                    val unlocked = clan.level >= entry.requiredLevel
                    val placeholders = basePlaceholders(entry, clan.level, maxPage)

                    when {
                        !unlocked -> {
                            type(lockedMat)
                            name(this@HomesUX.format(player, entry.lockedName, placeholders))
                            lore(this@HomesUX.format(player, entry.lockedLore, placeholders))
                            glow(false)
                        }

                        loc == null -> {
                            type(unsetMat)
                            name(this@HomesUX.format(player, entry.unsetName, placeholders))
                            lore(this@HomesUX.format(player, entry.unsetLore, placeholders))
                            glow(false)
                        }

                        else -> {
                            type(setMat)
                            val locationPlaceholders = placeholders + mapOf(
                                "world" to loc.world?.name.orEmpty(),
                                "x" to loc.blockX.toString(),
                                "y" to loc.blockY.toString(),
                                "z" to loc.blockZ.toString()
                            )
                            name(this@HomesUX.format(player, entry.setName, locationPlaceholders))
                            lore(this@HomesUX.format(player, entry.setLore, locationPlaceholders))
                            glow(true)
                        }
                    }
                    null
                }

                onClick { player, event ->
                    val msgCfg = cfg.messages
                    val clan = this@HomesUX.clanService.getClanUser(player) ?: return@onClick
                    val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick
                    val loc = clan.homes[entry.key]

                    if (clan.level < entry.requiredLevel) return@onClick

                    when {
                        event.isLeftClick && !event.isShiftClick -> {
                            if (loc != null) {
                                player.teleport(loc)
                                cfg.send(player, msgCfg.homes.teleported, mapOf("home" to entry.key))
                                player.closeInventory()
                            } else {
                                cfg.send(player, msgCfg.homes.notSet, mapOf("home" to entry.key))
                            }
                        }

                        event.isRightClick && !event.isShiftClick -> {
                            if (!clan.hasPermission(user, ClanPerms.Homes.SET)) {
                                cfg.send(player, msgCfg.homes.noPermissionSet)
                                return@onClick
                            }
                            clan.setHome(entry.key, player.location)
                            this@HomesUX.clanService.saveClan(clan)
                            cfg.send(player, msgCfg.homes.set, mapOf("home" to entry.key))
                            this@HomesUX.updateSlot(event.slot, player)
                        }

                        event.isRightClick && event.isShiftClick -> {
                            if (!clan.hasPermission(user, ClanPerms.Homes.SET)) {
                                cfg.send(player, msgCfg.homes.noPermissionDelete)
                                return@onClick
                            }
                            if (clan.deleteHome(entry.key)) {
                                this@HomesUX.clanService.saveClan(clan)
                                cfg.send(player, msgCfg.homes.deleted, mapOf("home" to entry.key))
                                this@HomesUX.updateSlot(event.slot, player)
                            }
                        }
                    }
                }
            }
        }

        slot(homeCfg.infoSlot) {
            dynamicItem(parseMaterial(homeCfg.infoMaterial, Material.COMPASS)) { player ->
                val clan = this@HomesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val unlockedHomes = homeCfg.homes.filter { clan.level >= it.requiredLevel }
                val setCount = unlockedHomes.count { clan.homes.containsKey(it.key) }
                val placeholders = pagePlaceholders + mapOf(
                    "set" to setCount.toString(),
                    "max" to unlockedHomes.size.toString(),
                    "total" to homeCfg.homes.size.toString(),
                    "current_level" to clan.level.toString()
                )

                name(this@HomesUX.format(player, homeCfg.infoName, placeholders))
                lore(this@HomesUX.format(player, homeCfg.infoLore, placeholders))
                glow(true)
                null
            }
        }

        val previousButton = if (currentPage > 1) homeCfg.previousPageButton else homeCfg.previousPageLockedButton
        slot(previousButton.slot) {
            dynamicItem(parseMaterial(previousButton.material, Material.ARROW)) { player ->
                this@HomesUX.renderConfigItem(this, player, previousButton, pagePlaceholders)
                null
            }
            onClick { player, _ ->
                if (currentPage > 1) HomesUX(this@HomesUX.clanService, currentPage - 1).open(player)
            }
        }

        val nextButton = if (currentPage < maxPage) homeCfg.nextPageButton else homeCfg.nextPageLockedButton
        slot(nextButton.slot) {
            dynamicItem(parseMaterial(nextButton.material, Material.SPECTRAL_ARROW)) { player ->
                this@HomesUX.renderConfigItem(this, player, nextButton, pagePlaceholders)
                null
            }
            onClick { player, _ ->
                if (currentPage < maxPage) HomesUX(this@HomesUX.clanService, currentPage + 1).open(player)
            }
        }

        slot(homeCfg.backButton.slot) {
            dynamicItem(parseMaterial(homeCfg.backButton.material, Material.RED_CANDLE)) { player ->
                this@HomesUX.renderConfigItem(this, player, homeCfg.backButton, pagePlaceholders)
                null
            }
            onClick { player, _ ->
                MainUX(this@HomesUX.clanService).open(player)
            }
        }
    }

    private fun renderConfigItem(
        builder: ItemBuilder,
        player: Player,
        item: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(format(player, item.name, placeholders))
        builder.lore(format(player, item.lore, placeholders))
        builder.glow(item.glow)
    }

    private fun format(player: Player, template: String, placeholders: Map<String, String>): String =
        clanService.plugin.configService.formatMessage(player, template, placeholders)

    private fun format(player: Player, templates: List<String>, placeholders: Map<String, String>): List<String> =
        templates.map { format(player, it, placeholders) }

    companion object {
        /**
         * Safely parses a [Material] by name, returning [fallback] if the name is invalid.
         *
         * @param name The material name string.
         * @param fallback The [Material] to use when parsing fails.
         * @return The parsed [Material], or [fallback] on error.
         */
        fun parseMaterial(name: String, fallback: Material): Material =
            runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)

        private fun basePlaceholders(entry: HomesEntryConfig, currentLevel: Int, maxPage: Int): Map<String, String> =
            mapOf(
                "home" to entry.key,
                "label" to entry.label,
                "emoji" to entry.emoji,
                "color" to entry.colorCode,
                "required_level" to entry.requiredLevel.toString(),
                "current_level" to currentLevel.toString(),
                "page" to entry.page.toString(),
                "pages" to maxPage.toString()
            )

        private fun applyPlaceholders(template: String, placeholders: Map<String, String>): String =
            placeholders.entries.fold(template) { result, (key, value) -> result.replace("{$key}", value) }
    }
}
