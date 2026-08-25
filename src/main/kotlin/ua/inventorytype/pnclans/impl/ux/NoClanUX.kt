package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt

/** Welcome GUI for players who are not currently members of a clan. */
class NoClanUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val menuCfg = clanService.plugin.supplementalMenus.noClanMenu

        title(menuCfg.title)
        rows(menuCfg.rows.coerceIn(1, 6))
        hotWorldDecor(true)

        menuCfg.items["info"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@NoClanUX.parseMaterial(itemCfg.material, Material.BEACON)) { player ->
                    this@NoClanUX.renderConfigItem(this, player, itemCfg)
                    null
                }
            }
        }

        menuCfg.items["create"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@NoClanUX.parseMaterial(itemCfg.material, Material.EMERALD)) { player ->
                    val cost = this@NoClanUX.clanService.plugin.configService.settings.createClanCost
                        .toBigDecimal().stripTrailingZeros().toPlainString()
                    this@NoClanUX.renderConfigItem(this, player, itemCfg, mapOf("cost" to cost))
                    null
                }
                onClick { player, _ -> this@NoClanUX.openCreationPrompt(player) }
            }
        }

        menuCfg.items["top"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@NoClanUX.parseMaterial(itemCfg.material, Material.GOLDEN_HELMET)) { player ->
                    this@NoClanUX.renderConfigItem(this, player, itemCfg)
                    null
                }
                onClick { player, _ -> TopClansUX(this@NoClanUX.clanService).open(player) }
            }
        }

        menuCfg.items["help"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@NoClanUX.parseMaterial(itemCfg.material, Material.BOOK)) { player ->
                    this@NoClanUX.renderConfigItem(this, player, itemCfg)
                    null
                }
                onClick { player, _ -> HelpUX(this@NoClanUX.clanService).open(player) }
            }
        }
    }

    private fun openCreationPrompt(player: Player) {
        val plugin = clanService.plugin
        val cfg = plugin.configService
        val promptCfg = plugin.supplementalSettings.clanCreation
        val messages = plugin.supplementalMessages.clanCreation
        val timeoutSeconds = promptCfg.promptTimeoutSeconds.coerceAtLeast(1)
        val cancelInputs = promptCfg.cancelInputs
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf("cancel") }
        val placeholders = mapOf(
            "seconds" to timeoutSeconds.toString(),
            "cancel" to cancelInputs.first()
        )

        player.closeInventory()
        cfg.send(player, messages.promptStarted, placeholders)

        ChatInputPrompt.prompt(
            plugin = plugin,
            player = player,
            timeoutTicks = timeoutSeconds.toLong() * 20L,
            onInput = { rawInput ->
                val input = rawInput.trim()
                if (cancelInputs.any { it.equals(input, ignoreCase = true) }) {
                    cfg.send(player, messages.cancelled)
                    NoClanUX(clanService).open(player)
                    return@prompt
                }

                val createdClan = clanService.createClan(input, player)
                if (createdClan != null) {
                    MainUX(clanService).open(player)
                } else {
                    NoClanUX(clanService).open(player)
                }
            },
            onTimeout = {
                cfg.send(player, messages.timedOut)
                NoClanUX(clanService).open(player)
            }
        )
    }

    private fun renderConfigItem(
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String> = emptyMap()
    ) {
        val cfg = clanService.plugin.configService
        builder.name(cfg.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { line -> cfg.formatMessage(player, line, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
}
