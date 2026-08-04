package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.util.ColorUtil

/**
 * Clan homes management GUI — 100% config-driven layout.
 *
 * All text (item names, lore lines, title), materials, slot indices, and visual settings
 * are read from [ua.inventorytype.pnclans.impl.config.HomesMenuConfig] via `menus.yml`.
 * No strings or item designs are hardcoded in this class.
 *
 * **Visual design (default config):**
 * - HotWorld-style black + orange glass border on all 6 rows (54 slots).
 * - Three home slots in the content zone — unset shows coloured glass (locked look),
 *   set shows glowing coloured bed with world coordinates.
 * - Informational compass panel in the center.
 * - Back button at the bottom center.
 *
 * **Click interactions per home slot:**
 * - **LMB**: Teleport to the home (if set).
 * - **RMB**: Set home at current player location (requires [ClanPerms.Homes.SET]).
 * - **Shift+RMB**: Delete the home point (requires [ClanPerms.Homes.SET]).
 *
 * All feedback messages are dispatched via the [ua.inventorytype.pnclans.api.Action] system
 * from `messages.yml`.
 *
 * @param clanService The clan service providing home data, persistence, and config access.
 */
class HomesUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val homeCfg = cfg.menus.homesMenu

        title(homeCfg.title)
        rows(homeCfg.rows)
        hotWorldDecor(true)

        // ── Home entry slots — driven by homesMenu.homes list ─────────────────
        homeCfg.homes.forEach { entry ->
            val lockedMat   = parseMaterial(entry.lockedMaterial,   Material.RED_STAINED_GLASS_PANE)
            val unlockedMat = parseMaterial(entry.unlockedMaterial, Material.RED_BED)

            slot(entry.slot) {

                dynamicItem(lockedMat) { player ->
                    val clan = this@HomesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val loc  = clan.homes[entry.key]

                    if (loc != null) {
                        // ── SET: glowing coloured bed, coordinates injected into lore ──
                        this.type = unlockedMat
                        name("${entry.colorCode}${entry.label}")
                        glow(true)

                        val world = loc.world?.name ?: "world"
                        val resolvedLore = homeCfg.setLore.map { line ->
                            line.replace("{world}", world)
                                .replace("{x}", loc.blockX.toString())
                                .replace("{y}", loc.blockY.toString())
                                .replace("{z}", loc.blockZ.toString())
                                .replace("{home}", entry.key)
                        }
                        lore(resolvedLore)
                    } else {
                        // ── UNSET: coloured glass pane, locked look ───────────────────
                        this.type = lockedMat
                        name("&#FC3737${entry.emoji} ${entry.label} &7(Не установлена)")

                        val resolvedLore = homeCfg.unsetLore.map { line ->
                            line.replace("{home}", entry.key)
                        }
                        lore(resolvedLore)
                    }
                    null
                }

                onClick { player, event ->
                    val msgCfg  = cfg.messages
                    val clan    = this@HomesUX.clanService.getClanUser(player) ?: return@onClick
                    val user    = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick
                    val loc     = clan.homes[entry.key]

                    when {
                        // LMB → teleport (if set)
                        event.isLeftClick && !event.isShiftClick -> {
                            if (loc != null) {
                                player.teleport(loc)
                                cfg.send(player, msgCfg.homes.teleported, mapOf("home" to entry.key))
                                player.closeInventory()
                            } else {
                                cfg.send(player, msgCfg.homes.notSet, mapOf("home" to entry.key))
                            }
                        }

                        // RMB (no shift) → set home
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

                        // Shift+RMB → delete home
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

        // ── Info compass panel — slot and text from config ────────────────────
        slot(homeCfg.infoSlot) {
            dynamicItem(Material.COMPASS) { player ->
                val clan     = this@HomesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val setCount = homeCfg.homes.count { clan.homes.containsKey(it.key) }
                val maxCount = homeCfg.homes.size

                name(homeCfg.infoName)
                val resolvedLore = homeCfg.infoLore.map { line ->
                    line.replace("{set}", setCount.toString())
                        .replace("{max}", maxCount.toString())
                }
                lore(resolvedLore)
                null
            }
        }

        // ── Back button — slot and text from config ───────────────────────────
        slot(homeCfg.backSlot) {
            item(Material.OAK_DOOR) {
                name(homeCfg.backName)
                lore(homeCfg.backLore)
            }
            onClick { player, _ ->
                MainUX(this@HomesUX.clanService).open(player)
            }
        }
    }

    companion object {
        /**
         * Safely parses a [Material] by name, returning [fallback] if the name is invalid.
         *
         * @param name The uppercase material name string (e.g. `"RED_BED"`).
         * @param fallback The [Material] to use when parsing fails.
         * @return The parsed [Material], or [fallback] on error.
         */
        fun parseMaterial(name: String, fallback: Material): Material =
            runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
    }
}
