package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ClanShopProductConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/** Config-driven clan shop catalogue with compact category navigation. */
class ClanShopUX(clanService: ClanService, selectedCategory: String? = null) : BaseGui(clanService) {
    private val config = clanService.plugin.configService.shop
    private val activeCategory = selectedCategory?.takeIf(config.categories::containsKey) ?: "all"

    init {
        title(config.title)
        rows(config.rows)
        hotWorldDecor(true)

        slot(4) {
            dynamicItem(Material.EMERALD) { player ->
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.headerName))
                lore(this@ClanShopUX.config.display.headerLore.map { this@ClanShopUX.format(player, it) })
                glow(true)
                null
            }
        }

        config.categories.forEach { (id, category) ->
            if (category.slot !in 0 until config.rows.coerceIn(1, 6) * 9) return@forEach
            slot(category.slot) {
                dynamicItem(this@ClanShopUX.material(category.material, Material.CHEST)) { player ->
                    name(this@ClanShopUX.format(player, category.name))
                    val lines = category.lore.toMutableList()
                    if (id == this@ClanShopUX.activeCategory) lines += listOf("", "&#5EFD7D✔ &fКатегория выбрана")
                    lore(lines.map { this@ClanShopUX.format(player, it) })
                    glow(id == this@ClanShopUX.activeCategory)
                    null
                }
                onClick { player, _ -> ClanShopUX(this@ClanShopUX.clanService, id).open(player) }
            }
        }

        config.products
            .filter { activeCategory == "all" || it.value.category == activeCategory }
            .forEach { (id, product) -> addProduct(id, product) }

        slot(45) {
            dynamicItem(Material.RED_CANDLE) { player ->
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.backName))
                lore(this@ClanShopUX.config.display.backLore.map { this@ClanShopUX.format(player, it) })
                null
            }
            onClick { player, _ -> MainUX(this@ClanShopUX.clanService).open(player) }
        }

        slot(49) {
            dynamicItem(Material.SUNFLOWER) { player ->
                val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val placeholders = this@ClanShopUX.balancePlaceholders(player, clan)
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.balanceName, placeholders))
                lore(this@ClanShopUX.config.display.balanceLore.map { this@ClanShopUX.format(player, it, placeholders) })
                null
            }
        }
    }

    private fun addProduct(id: String, product: ClanShopProductConfig) {
        if (product.slot !in 0 until config.rows.coerceIn(1, 6) * 9) return
        slot(product.slot) {
            dynamicItem(this@ClanShopUX.material(product.material, Material.CHEST)) { player ->
                val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val placeholders = this@ClanShopUX.productPlaceholders(player, clan, id, product)
                name(this@ClanShopUX.format(player, product.name, placeholders))
                lore(product.lore.flatMap { line ->
                    if (line == "{payment_lines}") this@ClanShopUX.paymentLines(player, clan, product) else listOf(this@ClanShopUX.format(player, line, placeholders))
                })
                glow(this@ClanShopUX.meetsBasicRequirements(clan, product))
                null
            }
            onClick { player, _ ->
                val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@onClick
                if (!this@ClanShopUX.meetsBasicRequirements(clan, product)) {
                    this@ClanShopUX.send(player, this@ClanShopUX.config.messages.requirementsNotMet)
                    return@onClick
                }
                ClanShopPaymentUX(this@ClanShopUX.clanService, id, this@ClanShopUX.activeCategory).open(player)
            }
        }
    }

    private fun productPlaceholders(player: Player, clan: Clan, id: String, product: ClanShopProductConfig): Map<String, String> {
        val shop = clanService.plugin.clanShopService
        val clanLimit = product.conditions.dailyClanLimit
        val globalLimit = product.conditions.dailyGlobalLimit
        return balancePlaceholders(player, clan) + mapOf(
            "product" to id,
            "quantity" to product.itemAmount.coerceAtLeast(1).toString(),
            "required_level" to product.conditions.minimumClanLevel.coerceAtLeast(1).toString(),
            "required_members" to product.conditions.minimumMembers.toString(),
            "clan_limit" to if (clanLimit <= 0) config.display.unlimitedText else "${shop.clanPurchasesToday(clan, id)}/$clanLimit",
            "global_limit" to if (globalLimit <= 0) config.display.unlimitedText else "${shop.globalPurchasesToday(id)}/$globalLimit"
        )
    }

    private fun paymentLines(player: Player, clan: Clan, product: ClanShopProductConfig): List<String> =
        product.payments.map { payment ->
            val available = clanService.plugin.clanShopService.isCurrencyAvailable(payment.currency)
            val state = if (available) "&#5EFD7D✔" else "&#FC3737✘"
            format(player, " $state &f${currencyName(payment.currency)}: &#FFD700${payment.amount}")
        }

    private fun balancePlaceholders(player: Player, clan: Clan): Map<String, String> {
        val shop = clanService.plugin.clanShopService
        return mapOf(
            "clan_points" to formatNumber(shop.balance(ClanShopCurrency.CLAN_POINTS, player, clan)),
            "vault_balance" to formatNumber(shop.balance(ClanShopCurrency.VAULT, player, clan)),
            "player_points_balance" to formatNumber(shop.balance(ClanShopCurrency.PLAYER_POINTS, player, clan))
        )
    }

    private fun meetsBasicRequirements(clan: Clan, product: ClanShopProductConfig): Boolean =
        clan.level >= product.conditions.minimumClanLevel && clan.users.size >= product.conditions.minimumMembers

    private fun material(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun format(player: Player, text: String, placeholders: Map<String, String> = emptyMap()): String =
        clanService.plugin.configService.formatMessage(player, text, placeholders)

    private fun send(player: Player, text: String, placeholders: Map<String, String> = emptyMap()) {
        player.sendMessage(format(player, text, placeholders))
    }

    private fun currencyName(currency: ClanShopCurrency): String = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> "Очки клана"
        ClanShopCurrency.VAULT -> "Vault"
        ClanShopCurrency.PLAYER_POINTS -> "PlayerPoints"
    }

    private fun formatNumber(value: Double?): String = value?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it)
    } ?: config.display.unavailableText
}
