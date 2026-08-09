package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/** Displays the config-defined clan quest catalogue. Progress tracking is added by ClanQuestService. */
class ClanQuestsUX(clanService: ClanService) : BaseGui(clanService) {
    init {
        val config = clanService.plugin.configService.quests
        title(config.title)
        rows(config.rows)
        config.quests.forEach { (id, quest) ->
            if (quest.slot !in 0 until config.rows.coerceIn(1, 6) * 9) return@forEach
            slot(quest.slot) {
                dynamicItem(runCatching { Material.valueOf(quest.material.uppercase()) }.getOrDefault(Material.WRITABLE_BOOK)) { player ->
                    val placeholders = mapOf("quest" to id, "target" to quest.target.toString())
                    name(clanService.plugin.configService.formatMessage(player, quest.name, placeholders))
                    lore(quest.lore.map { clanService.plugin.configService.formatMessage(player, it, placeholders) })
                    null
                }
            }
        }
    }
}
