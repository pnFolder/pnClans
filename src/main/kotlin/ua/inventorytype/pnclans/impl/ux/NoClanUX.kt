package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.impl.clan.ClanService
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
        hotWorldDecor(true)

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
        player.closeInventory()
        player.sendMessage("§a[pnClans] §fВведите желаемое название для вашего нового клана в чат (или §c'cancel'§f для отмены):")

        ChatInputPrompt.prompt(
            plugin = clanService.plugin,
            player = player,
            timeoutTicks = 600L,
            onInput = { input ->
                if (input.equals("cancel", ignoreCase = true)) {
                    player.sendMessage("§c[pnClans] Создание клана отменено.")
                    NoClanUX(clanService).open(player)
                    return@prompt
                }
                if (clanService.createClan(input, player) != null) MainUX(clanService).open(player)
                else NoClanUX(clanService).open(player)
            },
            onTimeout = {
                player.sendMessage("§c[pnClans] Время на ввод названия клана истекло.")
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
