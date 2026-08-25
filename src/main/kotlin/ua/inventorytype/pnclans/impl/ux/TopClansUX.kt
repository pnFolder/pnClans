package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.config.TopMenuConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import java.util.Locale
import kotlin.math.ceil

/**
 * Paginated Hall of Fame GUI with config-driven rank cards and navigation.
 *
 * Empty ranking slots remain empty instead of rendering anonymous fallback items. The first three
 * ranks use separately configurable icons and colors to make their meaning explicit.
 *
 * @param clanService The clan service providing the live ranking data.
 * @param requestedPage The requested zero-based ranking page.
 */
class TopClansUX(
    clanService: ClanService,
    requestedPage: Int = 0
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.topMenu
        val totalClans = rankedClans().size
        val maxPages = pageCount(totalClans, menuCfg.entrySlots.size)
        val currentPage = requestedPage.coerceIn(0, maxPages - 1)
        val pagePlaceholders = mapOf(
            "page" to (currentPage + 1).toString(),
            "pages" to maxPages.toString(),
            "total" to totalClans.toString()
        )

        title(replace(menuCfg.title, pagePlaceholders))
        rows(menuCfg.rows)
        hotWorldDecor(true)

        menuCfg.items["overview"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TopClansUX.parseMaterial(itemCfg.material, Material.BEACON)) { player ->
                    this@TopClansUX.renderConfigItem(this, player, itemCfg, pagePlaceholders)
                    null
                }
            }
        }

        if (totalClans == 0) {
            menuCfg.items["empty"]?.let { itemCfg ->
                slot(itemCfg.slot) {
                    dynamicItem(this@TopClansUX.parseMaterial(itemCfg.material, Material.KNOWLEDGE_BOOK)) { player ->
                        this@TopClansUX.renderConfigItem(this, player, itemCfg, pagePlaceholders)
                        null
                    }
                }
            }
        } else {
            menuCfg.items["entry"]?.let { entryTemplate ->
                val cachedClans = rankedClans()
                menuCfg.entrySlots.forEachIndexed { slotIndex, entrySlot ->
                    slot(entrySlot) {
                        dynamicItemNullable(this@TopClansUX.parseMaterial(entryTemplate.material, Material.AMETHYST_SHARD)) { player ->
                            val index = currentPage * menuCfg.entrySlots.size + slotIndex
                            val clan = cachedClans.getOrNull(index) ?: return@dynamicItemNullable null
                            val rank = index + 1
                            val (material, color) = this@TopClansUX.rankVisual(menuCfg, rank)
                            val leader = clan.users.find { clan.getUserRole(it) == ClanRole.LEADER }
                            val placeholders = pagePlaceholders + mapOf(
                                "rank" to rank.toString(),
                                "rank_color" to color,
                                "clan" to clan.name,
                                "leader" to leader?.playerName.orEmpty(),
                                "level" to clan.level.toString(),
                                "mmr" to clan.mmr.toString(),
                                "kills" to clan.kills.toString(),
                                "deaths" to clan.deaths.toString(),
                                "kda" to this@TopClansUX.kda(clan),
                                "members" to clan.users.size.toString(),
                                "balance" to clan.bankBalance.toString()
                            )

                            type(material)
                            this@TopClansUX.renderConfigItem(this, player, entryTemplate, placeholders)
                            build()
                        }
                    }
                }
            }
        }

        val previousCfg = if (currentPage > 0) menuCfg.items["previous"] else menuCfg.items["previousLocked"]
        previousCfg?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TopClansUX.parseMaterial(itemCfg.material, Material.GRAY_DYE)) { player ->
                    this@TopClansUX.renderConfigItem(this, player, itemCfg, pagePlaceholders)
                    null
                }
                onClick { player, _ ->
                    if (currentPage > 0) TopClansUX(this@TopClansUX.clanService, currentPage - 1).open(player)
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TopClansUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    this@TopClansUX.renderConfigItem(this, player, itemCfg, pagePlaceholders)
                    null
                }
                onClick { player, _ -> MainUX(this@TopClansUX.clanService).open(player) }
            }
        }

        val nextCfg = if (currentPage + 1 < maxPages) menuCfg.items["next"] else menuCfg.items["nextLocked"]
        nextCfg?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TopClansUX.parseMaterial(itemCfg.material, Material.GRAY_DYE)) { player ->
                    this@TopClansUX.renderConfigItem(this, player, itemCfg, pagePlaceholders)
                    null
                }
                onClick { player, _ ->
                    if (currentPage + 1 < maxPages) TopClansUX(this@TopClansUX.clanService, currentPage + 1).open(player)
                }
            }
        }
    }

    private fun rankedClans(): List<Clan> =
        clanService.getAllClans().sortedWith(
            compareByDescending<Clan> { it.mmr }
                .thenByDescending { it.kills }
                .thenBy { it.name }
        )

    private fun rankVisual(menuCfg: TopMenuConfig, rank: Int): Pair<Material, String> =
        when (rank) {
            1 -> parseMaterial(menuCfg.firstMaterial, Material.DRAGON_EGG) to menuCfg.firstColor
            2 -> parseMaterial(menuCfg.secondMaterial, Material.NETHER_STAR) to menuCfg.secondColor
            3 -> parseMaterial(menuCfg.thirdMaterial, Material.DIAMOND) to menuCfg.thirdColor
            else -> parseMaterial(menuCfg.otherMaterial, Material.AMETHYST_SHARD) to menuCfg.otherColor
        }

    private fun kda(clan: Clan): String =
        if (clan.deaths == 0) ZERO_KDA else String.format(Locale.US, KDA_FORMAT, clan.kills.toDouble() / clan.deaths)

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

    private fun pageCount(total: Int, entriesPerPage: Int): Int =
        ceil(total / entriesPerPage.coerceAtLeast(1).toDouble()).toInt().coerceAtLeast(1)

    private fun replace(template: String, placeholders: Map<String, String>): String =
        placeholders.entries.fold(template) { result, (key, value) -> result.replace("{$key}", value) }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)

    private companion object {
        const val ZERO_KDA = "0.00"
        const val KDA_FORMAT = "%.2f"
    }
}
