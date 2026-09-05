package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ClanCreationConfig
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt

/** Welcome GUI presented to players who are not currently members of a clan. */
class NoClanUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.noClanMenu

        title(menuCfg.title)
        rows(menuCfg.rows.coerceIn(1, 6))
        background(clanService.plugin.configService.menus.background)

        addItem("info")
        addItem("create", mapOf("cost" to cfg.settings.createClanCost.toBigDecimal().stripTrailingZeros().toPlainString())) { player ->
            startCreatePrompt(player)
        }
        addItem("top") { player -> TopClansUX(this@NoClanUX.clanService).open(player) }
        addItem("help") { player -> HelpUX(this@NoClanUX.clanService).open(player) }
    }

    private fun addItem(
        key: String,
        placeholders: Map<String, String> = emptyMap(),
        click: ((Player) -> Unit)? = null
    ) {
        val itemCfg = clanService.plugin.configService.menus.noClanMenu.items[key] ?: return
        slot(itemCfg.slot) {
            dynamicItem(this@NoClanUX.parseMaterial(itemCfg.material, Material.STONE)) { player ->
                this@NoClanUX.render(this, player, itemCfg, placeholders)
                null
            }
            if (click != null) onClick { player, _ -> click(player) }
        }
    }

    private fun startCreatePrompt(player: Player) {
        val cfg = clanService.plugin.configService
        val promptCfg = cfg.settings.clanCreation
        val timeoutSeconds = promptCfg.promptTimeoutSeconds.coerceAtLeast(1)
        val cancelInputs = promptCfg.cancelInputs
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { ClanCreationConfig().cancelInputs }
        val placeholders = mapOf(
            "seconds" to timeoutSeconds.toString(),
            "cancel" to cancelInputs.first()
        )

        player.closeInventory()
        cfg.send(player, cfg.messages.clan.creationPromptStarted, placeholders)

        ChatInputPrompt.prompt(
            plugin = clanService.plugin,
            player = player,
            timeoutTicks = timeoutSeconds.toLong() * 20L,
            onInput = { rawInput ->
                val input = rawInput.trim()
                if (cancelInputs.any { it.equals(input, ignoreCase = true) }) {
                    cfg.send(player, cfg.messages.clan.creationPromptCancelled)
                    NoClanUX(clanService).open(player)
                    return@prompt
                }
                if (clanService.createClan(input, player) != null) MainUX(clanService).open(player)
                else NoClanUX(clanService).open(player)
            },
            onTimeout = {
                cfg.send(player, cfg.messages.clan.creationPromptTimedOut)
                NoClanUX(clanService).open(player)
            }
        )
    }

    private fun render(builder: ItemBuilder, player: Player, itemCfg: GuiItemConfig, placeholders: Map<String, String>) {
        val cfg = clanService.plugin.configService
        builder.name(cfg.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { cfg.formatMessage(player, it, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)
}
