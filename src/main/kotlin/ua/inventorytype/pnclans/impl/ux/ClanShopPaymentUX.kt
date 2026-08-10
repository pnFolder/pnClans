package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ClanShopPaymentOption
import ua.inventorytype.pnclans.impl.shop.ClanShopPurchaseRejection
import ua.inventorytype.pnclans.impl.shop.ClanShopPurchaseResult
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/** Currency selection and purchase confirmation for one configured product. */
class ClanShopPaymentUX(
    clanService: ClanService,
    private val productId: String,
    private val returnCategory: String,
    private val returnPage: Int = 0,
    private val returnSort: String? = null
) : BaseGui(clanService) {
    private val config = clanService.plugin.configService.shop
    private val product = config.products[productId]

    init {
        title(config.display.paymentTitle)
        rows(6)
        hotWorldDecor(true)

        product?.let { configuredProduct ->
            slot(13) {
                dynamicItem(this@ClanShopPaymentUX.material(configuredProduct.material, Material.CHEST)) { player ->
                    val placeholders = mapOf(
                        "product_name" to this@ClanShopPaymentUX.productName(player),
                        "category" to this@ClanShopPaymentUX.categoryName(configuredProduct.category),
                        "rarity" to this@ClanShopPaymentUX.rarityName(configuredProduct.rarity),
                        "quantity" to configuredProduct.itemAmount.coerceAtLeast(1).toString()
                    )
                    amount(configuredProduct.itemAmount.coerceAtLeast(1))
                    name(this@ClanShopPaymentUX.format(player, this@ClanShopPaymentUX.config.display.paymentHeaderName, placeholders))
                    lore(this@ClanShopPaymentUX.config.display.paymentHeaderLore.map { this@ClanShopPaymentUX.format(player, it, placeholders) })
                    glow(true)
                    null
                }
            }

            val paymentSlots = listOf(29, 31, 33)
            configuredProduct.payments.take(paymentSlots.size).forEachIndexed { index, payment ->
                slot(paymentSlots[index]) {
                    dynamicItem(this@ClanShopPaymentUX.currencyMaterial(payment.currency)) { player ->
                        val clan = this@ClanShopPaymentUX.clanService.getClanUser(player) ?: return@dynamicItem null
                        val shop = this@ClanShopPaymentUX.clanService.plugin.clanShopService
                        val permitted = this@ClanShopPaymentUX.hasPaymentPermission(player, payment)
                        val available = permitted && shop.isCurrencyAvailable(payment.currency)
                        val balance = shop.balance(payment.currency, player, clan)
                        val enough = available && balance != null && balance >= payment.amount
                        val state = when {
                            !permitted -> this@ClanShopPaymentUX.config.display.noPermissionText
                            !available -> this@ClanShopPaymentUX.config.display.unavailableText
                            !enough -> this@ClanShopPaymentUX.config.display.insufficientText
                            else -> this@ClanShopPaymentUX.config.display.availableText
                        }
                        val placeholders = mapOf(
                            "state_icon" to if (enough) "&#5EFD7D✔" else if (available) "&#FFD700!" else "&#FC3737✘",
                            "currency" to this@ClanShopPaymentUX.currencyName(payment),
                            "source" to this@ClanShopPaymentUX.paymentSource(payment.currency),
                            "price" to payment.amount.toString(),
                            "balance" to (balance?.let(this@ClanShopPaymentUX::formatNumber) ?: this@ClanShopPaymentUX.config.display.unavailableText),
                            "remaining" to (balance?.let { this@ClanShopPaymentUX.formatNumber((it - payment.amount).coerceAtLeast(0.0)) } ?: this@ClanShopPaymentUX.config.display.unavailableText),
                            "state" to state,
                            "action" to if (enough) "&#FF8702➥ &fНажмите &eЛКМ &fчтобы подтвердить оплату" else "&8Оплата с этого счёта сейчас недоступна"
                        )
                        name(this@ClanShopPaymentUX.format(player, this@ClanShopPaymentUX.config.display.paymentOptionName, placeholders))
                        lore(this@ClanShopPaymentUX.config.display.paymentOptionLore.map { this@ClanShopPaymentUX.format(player, it, placeholders) })
                        glow(enough)
                        null
                    }
                    onClick { player, _ -> this@ClanShopPaymentUX.purchase(player, index, payment) }
                }
            }
        }

        slot(49) {
            dynamicItem(Material.OAK_DOOR) { player ->
                name(this@ClanShopPaymentUX.format(player, this@ClanShopPaymentUX.config.display.paymentBackName))
                lore(this@ClanShopPaymentUX.config.display.paymentBackLore.map { this@ClanShopPaymentUX.format(player, it) })
                glow(true)
                null
            }
            onClick { player, _ -> this@ClanShopPaymentUX.returnToShop(player) }
        }
    }

    private fun purchase(player: Player, paymentIndex: Int, displayedPayment: ClanShopPaymentOption) {
        val clan = clanService.getClanUser(player) ?: return
        val displayedProduct = product ?: return
        val placeholders = mapOf(
            "product_name" to productName(player),
            "currency" to currencyName(displayedPayment)
        )
        when (val result = clanService.plugin.clanShopService.purchase(
            player,
            clan,
            productId,
            paymentIndex,
            displayedProduct,
            displayedPayment
        )) {
            is ClanShopPurchaseResult.Success -> {
                player.sendMessage(format(player, config.messages.success, placeholders + ("price" to result.chargedPrice.toString())))
                returnToShop(player)
            }
            is ClanShopPurchaseResult.Rejected -> {
                val message = when (result.reason) {
                    ClanShopPurchaseRejection.CURRENCY_UNAVAILABLE -> config.messages.currencyUnavailable
                    ClanShopPurchaseRejection.NO_PERMISSION -> config.messages.noPermission
                    ClanShopPurchaseRejection.SHOP_CHANGED -> config.messages.shopChanged
                    ClanShopPurchaseRejection.INSUFFICIENT_FUNDS -> config.messages.insufficientFunds
                    ClanShopPurchaseRejection.REQUIREMENTS_NOT_MET -> config.messages.requirementsNotMet
                    ClanShopPurchaseRejection.CLAN_LIMIT_REACHED -> config.messages.clanLimitReached
                    ClanShopPurchaseRejection.GLOBAL_LIMIT_REACHED -> config.messages.globalLimitReached
                    ClanShopPurchaseRejection.CANCELLED_BY_EVENT -> config.messages.cancelled
                    else -> config.messages.requirementsNotMet
                }
                val requiredPrice = result.requiredPrice ?: displayedPayment.amount
                player.sendMessage(format(player, message, placeholders + ("price" to requiredPrice.toString())))
            }
        }
    }

    private fun currencyMaterial(currency: ClanShopCurrency): Material = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> Material.NETHER_STAR
        ClanShopCurrency.VAULT -> Material.GOLD_INGOT
        ClanShopCurrency.PLAYER_POINTS -> Material.SUNFLOWER
    }

    private fun currencyName(payment: ClanShopPaymentOption): String = payment.displayName?.takeIf(String::isNotBlank) ?: when (payment.currency) {
        ClanShopCurrency.CLAN_POINTS -> "Клановые очки"
        ClanShopCurrency.VAULT -> "Монеты"
        ClanShopCurrency.PLAYER_POINTS -> "Бонусные очки"
    }

    private fun paymentSource(currency: ClanShopCurrency): String = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> "Казна клана (клановые очки)"
        ClanShopCurrency.VAULT -> "Личный счёт игрока (монеты)"
        ClanShopCurrency.PLAYER_POINTS -> "Личный счёт игрока (бонусные очки)"
    }

    private fun hasPaymentPermission(player: Player, payment: ClanShopPaymentOption): Boolean =
        payment.permission.isNullOrBlank() || player.hasPermission(payment.permission)

    private fun material(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun returnToShop(player: Player) {
        ClanShopUX(clanService, returnCategory, returnPage, returnSort).open(player)
    }

    private fun categoryName(categoryId: String): String =
        config.categories[categoryId]?.name ?: categoryId

    private fun rarityName(rarityId: String): String =
        config.rarities[rarityId]?.name ?: rarityId

    private fun productName(player: Player): String {
        val template = product?.name?.takeIf(String::isNotBlank) ?: config.display.fallbackProductName
        return stripColours(format(player, template, mapOf("product" to productId)))
    }

    private fun format(player: Player, text: String, placeholders: Map<String, String> = emptyMap()): String =
        clanService.plugin.configService.formatMessage(player, text, placeholders)

    private fun stripColours(value: String): String = value.replace(Regex("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]"), "")
    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
}
