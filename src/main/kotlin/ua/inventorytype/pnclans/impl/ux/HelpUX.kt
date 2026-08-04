package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/**
 * Config-driven clan help GUI that explains progression, rewards, and earning rules.
 *
 * All visible text, materials, and slot placements come from [ua.inventorytype.pnclans.impl.config.MenusConfig.helpMenu]
 * in `menus.yml`. Placeholders such as `{next_level}` and `{level}` are filled with live clan data.
 *
 * @param clanService The clan service providing live clan and progression state.
 */
class HelpUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.helpMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        hotWorldDecor(true)

        val clan = clanService.getAllClans().firstOrNull()
        val level = clan?.level ?: 1
        val nextLevel = (level + 1).coerceAtMost(MAX_LEVEL)
        val placeholders = mapOf(
            "level" to level.toString(),
            "next_level" to nextLevel.toString()
        )

        menuCfg.items["evolution"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.BEACON)) { player ->
                    renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
            }
        }

        menuCfg.items["rewards"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.TOTEM_OF_UNDYING)) { player ->
                    renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
            }
        }

        menuCfg.items["earning"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.EXPERIENCE_BOTTLE)) { player ->
                    renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
                onClick { player, _ -> MainUX(clanService).open(player) }
            }
        }
    }

    private fun renderConfigItem(
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(clanService.plugin.configService.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { line -> clanService.plugin.configService.formatMessage(player, line, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)

    private companion object {
        const val MAX_LEVEL = 5
    }
}
