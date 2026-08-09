package ua.inventorytype.pnclans.impl.shop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.ActionContext
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.event.ClanShopPurchaseEvent
import ua.inventorytype.pnclans.api.event.ClanShopPurchasePreEvent
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import ua.inventorytype.pnclans.impl.config.ClanShopProductConfig
import ua.inventorytype.pnclans.impl.storage.ItemStackSerializer
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

internal enum class ClanShopPurchaseRejection {
    PRODUCT_NOT_FOUND,
    PAYMENT_NOT_CONFIGURED,
    REQUIREMENTS_NOT_MET,
    CLAN_LIMIT_REACHED,
    GLOBAL_LIMIT_REACHED,
    CURRENCY_UNAVAILABLE,
    INSUFFICIENT_FUNDS,
    CANCELLED_BY_EVENT,
    REWARD_FAILED
}

internal sealed interface ClanShopPurchaseResult {
    data object Success : ClanShopPurchaseResult
    data class Rejected(val reason: ClanShopPurchaseRejection) : ClanShopPurchaseResult
}

@Serializable
private data class ClanShopPurchaseLedger(
    var date: String = LocalDate.now(ZoneOffset.UTC).toString(),
    val clanPurchases: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    val globalPurchases: MutableMap<String, Int> = mutableMapOf()
)

/** Validates, charges, rewards, and persists a clan shop purchase. */
internal class ClanShopService(private val plugin: BukkitPlugin) {
    private val currencies = ClanShopCurrencyService(plugin)
    private val ledgerFile = File(plugin.dataFolder, "shop-purchases.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var ledger = loadLedger()

    fun isCurrencyAvailable(currency: ClanShopCurrency): Boolean = currencies.isAvailable(currency)
    fun balance(currency: ClanShopCurrency, player: Player, clan: Clan): Double? = currencies.balance(currency, player, clan)

    fun clanPurchasesToday(clan: Clan, productId: String): Int {
        resetDayIfNeeded()
        return ledger.clanPurchases[clan.id]?.get(productId) ?: 0
    }

    fun globalPurchasesToday(productId: String): Int {
        resetDayIfNeeded()
        return ledger.globalPurchases[productId] ?: 0
    }

    fun purchase(player: Player, clan: Clan, productId: String, currency: ClanShopCurrency): ClanShopPurchaseResult {
        val product = plugin.configService.shop.products[productId]
            ?: return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.PRODUCT_NOT_FOUND)
        val payment = product.payments.firstOrNull { it.currency == currency }
            ?: return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.PAYMENT_NOT_CONFIGURED)
        if (!meetsRequirements(clan, product)) return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REQUIREMENTS_NOT_MET)

        resetDayIfNeeded()
        if (product.conditions.dailyClanLimit > 0 && clanPurchasesToday(clan, productId) >= product.conditions.dailyClanLimit) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.CLAN_LIMIT_REACHED)
        }
        if (product.conditions.dailyGlobalLimit > 0 && globalPurchasesToday(productId) >= product.conditions.dailyGlobalLimit) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.GLOBAL_LIMIT_REACHED)
        }
        if (!currencies.isAvailable(currency)) return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.CURRENCY_UNAVAILABLE)

        val event = ClanShopPurchasePreEvent(clan, player, productId, currency, payment.amount)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled || event.price <= 0L) return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.CANCELLED_BY_EVENT)
        val balance = currencies.balance(currency, player, clan)
        if (balance == null || balance < event.price) return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.INSUFFICIENT_FUNDS)
        if (!currencies.withdraw(currency, player, clan, event.price)) return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.INSUFFICIENT_FUNDS)

        val rewarded = runCatching { grantRewards(player, clan, productId, product) }.isSuccess
        if (!rewarded) {
            currencies.refund(currency, player, clan, event.price)
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REWARD_FAILED)
        }

        ledger.clanPurchases.computeIfAbsent(clan.id) { mutableMapOf() }.merge(productId, 1, Int::plus)
        ledger.globalPurchases.merge(productId, 1, Int::plus)
        saveLedger()
        Bukkit.getPluginManager().callEvent(ClanShopPurchaseEvent(clan, player, productId, currency, event.price))
        return ClanShopPurchaseResult.Success
    }

    private fun meetsRequirements(clan: Clan, product: ClanShopProductConfig): Boolean =
        clan.level >= product.conditions.minimumClanLevel &&
            clan.users.size >= product.conditions.minimumMembers &&
            product.conditions.requiredQuests.isEmpty()

    private fun grantRewards(player: Player, clan: Clan, productId: String, product: ClanShopProductConfig) {
        product.itemStack?.takeIf { it.isNotBlank() }?.let { encoded ->
            ItemStackSerializer.fromBase64(encoded).firstOrNull()?.clone()?.let { item ->
                item.amount = product.itemAmount.coerceIn(1, item.maxStackSize)
                player.inventory.addItem(item)
            }
        }
        val placeholders = mapOf("product" to productId, "clan" to clan.name, "quantity" to product.itemAmount.toString())
        val context = ActionContext(player, plugin.placeholderRegistry, placeholders, plugin)
        product.rewards.forEach { it.execute(context) }
    }

    private fun resetDayIfNeeded() {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        if (ledger.date == today) return
        ledger = ClanShopPurchaseLedger(today)
        saveLedger()
    }

    private fun loadLedger(): ClanShopPurchaseLedger {
        if (!ledgerFile.exists()) return ClanShopPurchaseLedger()
        return runCatching { json.decodeFromString<ClanShopPurchaseLedger>(ledgerFile.readText()) }
            .getOrDefault(ClanShopPurchaseLedger())
    }

    private fun saveLedger() {
        ledgerFile.writeText(json.encodeToString(ledger))
    }
}
