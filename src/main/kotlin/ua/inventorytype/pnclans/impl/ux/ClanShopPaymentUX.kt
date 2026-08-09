package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.shop.ClanShopPurchaseRejection
import ua.inventorytype.pnclans.impl.shop.ClanShopPurchaseResult
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/** Currency selection and purchase confirmation for one configured product. */
class ClanShopPaymentUX(
    clanService: ClanService,
    private val productId: String,
    private val returnCategory: String
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
                        "product_name" to this@ClanShopPaymentUX.stripColours(configuredProduct.name),
                        "quantity" to configuredProduct.itemAmount.coerceAtLeast(1).toString()
                    )
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
                        val available = shop.isCurrencyAvailable(payment.currency)
                        val balance = shop.balance(payment.currency, player, clan)
                        val enough = available && balance != null && balance >= payment.amount
                        name(this@ClanShopPaymentUX.format(player, "${if (enough) "&#5EFD7D✔" else "&#FC3737✘"} ${this@ClanShopPaymentUX.currencyName(payment.currency)}"))
                        lore(listOf(
                            this@ClanShopPaymentUX.format(player, ""),
                            this@ClanShopPaymentUX.format(player, "&#9EFC65 «Оплата»"),
                            this@ClanShopPaymentUX.format(player, " &7- &fСтоимость: &#FFD700${payment.amount}"),
                            this@ClanShopPaymentUX.format(player, " &7- &fВаш баланс: ${if (balance == null) this@ClanShopPaymentUX.config.display.unavailableText else "&#5EA9FD${this@ClanShopPaymentUX.formatNumber(balance)}"}"),
                            this@ClanShopPaymentUX.format(player, " &7- &fСтатус: ${if (enough) this@ClanShopPaymentUX.config.display.availableText else this@ClanShopPaymentUX.config.display.unavailableText}"),
                            this@ClanShopPaymentUX.format(player, ""),
                            this@ClanShopPaymentUX.format(player, if (enough) "&#FF8702➥ &fНажмите, чтобы подтвердить покупку" else "&#FC3737✘ &fОплата недоступна")
                        ))
                        glow(enough)
                        null
                    }
                    onClick { player, _ -> this@ClanShopPaymentUX.purchase(player, payment.currency, payment.amount) }
                }
            }
        }

        slot(49) {
            dynamicItem(Material.RED_CANDLE) { player ->
                name(this@ClanShopPaymentUX.format(player, "&#FC3737← Назад к товарам"))
                lore(listOf(this@ClanShopPaymentUX.format(player, "&7Вернуться к выбранной категории.")))
                null
            }
            onClick { player, _ -> ClanShopUX(this@ClanShopPaymentUX.clanService, this@ClanShopPaymentUX.returnCategory).open(player) }
        }
    }

    private fun purchase(player: Player, currency: ClanShopCurrency, configuredPrice: Long) {
        val clan = clanService.getClanUser(player) ?: return
        val placeholders = mapOf(
            "product_name" to stripColours(product?.name.orEmpty()),
            "price" to configuredPrice.toString(),
            "currency" to currencyName(currency)
        )
        when (val result = clanService.plugin.clanShopService.purchase(player, clan, productId, currency)) {
            ClanShopPurchaseResult.Success -> {
                player.sendMessage(format(player, config.messages.success, placeholders))
                ClanShopUX(clanService, returnCategory).open(player)
            }
            is ClanShopPurchaseResult.Rejected -> {
                val message = when (result.reason) {
                    ClanShopPurchaseRejection.CURRENCY_UNAVAILABLE -> config.messages.currencyUnavailable
                    ClanShopPurchaseRejection.INSUFFICIENT_FUNDS -> config.messages.insufficientFunds
                    ClanShopPurchaseRejection.REQUIREMENTS_NOT_MET -> config.messages.requirementsNotMet
                    ClanShopPurchaseRejection.CLAN_LIMIT_REACHED -> config.messages.clanLimitReached
                    ClanShopPurchaseRejection.GLOBAL_LIMIT_REACHED -> config.messages.globalLimitReached
                    ClanShopPurchaseRejection.CANCELLED_BY_EVENT -> config.messages.cancelled
                    else -> config.messages.requirementsNotMet
                }
                player.sendMessage(format(player, message, placeholders))
            }
        }
    }

    private fun currencyMaterial(currency: ClanShopCurrency): Material = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> Material.NETHER_STAR
        ClanShopCurrency.VAULT -> Material.GOLD_INGOT
        ClanShopCurrency.PLAYER_POINTS -> Material.SUNFLOWER
    }

    private fun currencyName(currency: ClanShopCurrency): String = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> "Очки клана"
        ClanShopCurrency.VAULT -> "Vault"
        ClanShopCurrency.PLAYER_POINTS -> "PlayerPoints"
    }

    private fun material(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun format(player: Player, text: String, placeholders: Map<String, String> = emptyMap()): String =
        clanService.plugin.configService.formatMessage(player, text, placeholders)

    private fun stripColours(value: String): String = value.replace(Regex("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]"), "")
    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
}
