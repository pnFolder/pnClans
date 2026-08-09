package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.ActionContext
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.impl.config.ClanShopCurrency
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/** Displays the config-defined clan shop catalogue. Purchase handling is added by ClanShopService. */
class ClanShopUX(clanService: ClanService) : BaseGui(clanService) {
    init {
        val config = clanService.plugin.configService.shop
        title(config.title)
        rows(config.rows)
        config.products.forEach { (id, product) ->
            if (product.slot !in 0 until config.rows.coerceIn(1, 6) * 9) return@forEach
            slot(product.slot) {
                dynamicItem(runCatching { Material.valueOf(product.material.uppercase()) }.getOrDefault(Material.CHEST)) { player ->
                    val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val payment = product.payments.minByOrNull { it.amount }
                    val placeholders = mapOf(
                        "product" to id,
                        "clan_points" to clan.points.toString(),
                        "price" to (payment?.amount?.toString() ?: "-"),
                        "required_level" to product.conditions.minimumClanLevel.coerceAtLeast(1).toString()
                    )
                    name(clanService.plugin.configService.formatMessage(player, product.name, placeholders))
                    lore(product.lore.map { clanService.plugin.configService.formatMessage(player, it, placeholders) })
                    null
                }
                onClick { player, _ ->
                    val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@onClick
                    if (clan.level < product.conditions.minimumClanLevel || clan.users.size < product.conditions.minimumMembers) {
                        player.sendMessage("§cВаш клан не выполняет условия этого товара.")
                        return@onClick
                    }
                    val payment = product.payments.firstOrNull { it.currency == ClanShopCurrency.CLAN_POINTS }
                    if (payment == null) {
                        player.sendMessage("§cДля этого товара пока нет доступной валюты.")
                        return@onClick
                    }
                    if (!this@ClanShopUX.clanService.plugin.clanPointsService.spend(clan, payment.amount, ClanPointsSource.SHOP)) {
                        player.sendMessage("§cУ клана недостаточно очков.")
                        return@onClick
                    }
                    val context = ActionContext(player, this@ClanShopUX.clanService.plugin.placeholderRegistry, mapOf("product" to id), this@ClanShopUX.clanService.plugin)
                    product.rewards.forEach { it.execute(context) }
                    player.sendMessage("§aКлан приобрёл товар §e$id§a.")
                    this@ClanShopUX.update(player)
                }
            }
        }
    }
}
