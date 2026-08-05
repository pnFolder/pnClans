package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/**
 * Explicit confirmation screen for leaving or disbanding a clan.
 *
 * The current role is rechecked when the confirmation button is clicked, preventing an outdated
 * screen from performing the wrong destructive action after a rank change.
 *
 * @param clanService The clan service used to verify and persist the action.
 */
class ClanLeaveConfirmUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.leaveConfirmMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        border(Material.BLACK_STAINED_GLASS_PANE)

        val confirmLeader = menuCfg.items["confirmDisband"]
        val confirmMember = menuCfg.items["confirmLeave"]

        if (confirmLeader != null && confirmMember != null) {
            slot(confirmLeader.slot) {
                dynamicItem(this@ClanLeaveConfirmUX.parseMaterial(confirmLeader.material, Material.RED_DYE)) { player ->
                    val clan = this@ClanLeaveConfirmUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem null
                    val itemCfg = if (clan.getUserRole(user) == ClanRole.LEADER) confirmLeader else confirmMember

                    type(this@ClanLeaveConfirmUX.parseMaterial(itemCfg.material, Material.RED_DYE))
                    this@ClanLeaveConfirmUX.renderConfigItem(this, player, itemCfg, mapOf("clan" to clan.name))
                    null
                }
                onClick { player, _ ->
                    val clan = this@ClanLeaveConfirmUX.clanService.getClanUser(player) ?: return@onClick
                    val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                    if (clan.getUserRole(user) == ClanRole.LEADER) {
                        val errorMsg = this@ClanLeaveConfirmUX.clanService.disbandClan(clan, player)
                        if (errorMsg != null) {
                            player.sendMessage(errorMsg)
                            return@onClick
                        }
                    } else if (this@ClanLeaveConfirmUX.clanService.removeUserFromClan(clan, player.uniqueId)) {
                        this@ClanLeaveConfirmUX.clanService.saveClan(clan)
                        this@ClanLeaveConfirmUX.clanService.notifyClanUpdated(player.uniqueId)
                        cfg.send(player, cfg.messages.clan.left, mapOf("clan" to clan.name))
                    }
                    player.closeInventory()
                }
            }
        }

        menuCfg.items["info"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanLeaveConfirmUX.parseMaterial(itemCfg.material, Material.ENCHANTED_BOOK)) { player ->
                    val clan = this@ClanLeaveConfirmUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    this@ClanLeaveConfirmUX.renderConfigItem(this, player, itemCfg, mapOf("clan" to clan.name))
                    null
                }
            }
        }

        menuCfg.items["cancel"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanLeaveConfirmUX.parseMaterial(itemCfg.material, Material.LIME_CANDLE)) { player ->
                    this@ClanLeaveConfirmUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> MainUX(this@ClanLeaveConfirmUX.clanService).open(player) }
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
}
