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
import ua.inventorytype.pnclans.impl.config.ClanShopPaymentOption
import ua.inventorytype.pnclans.impl.storage.ItemStackSerializer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.logging.Level

internal enum class ClanShopPurchaseRejection {
    SHOP_DISABLED,
    PRODUCT_NOT_FOUND,
    PAYMENT_NOT_CONFIGURED,
    SHOP_CHANGED,
    REQUIREMENTS_NOT_MET,
    CLAN_LIMIT_REACHED,
    GLOBAL_LIMIT_REACHED,
    CURRENCY_UNAVAILABLE,
    NO_PERMISSION,
    INSUFFICIENT_FUNDS,
    CANCELLED_BY_EVENT,
    LEDGER_FAILED,
    REFUND_FAILED,
    REWARD_FAILED,
    REWARD_PARTIAL
}

internal sealed interface ClanShopPurchaseResult {
    data class Success(val chargedPrice: Long) : ClanShopPurchaseResult
    data class Rejected(
        val reason: ClanShopPurchaseRejection,
        val requiredPrice: Long? = null
    ) : ClanShopPurchaseResult
}

@Serializable
private data class ClanShopPurchaseLedger(
    var date: String = LocalDate.now(ZoneOffset.UTC).toString(),
    val clanPurchases: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    val globalPurchases: MutableMap<String, Int> = mutableMapOf()
)

private sealed interface RewardDeliveryResult {
    data object Success : RewardDeliveryResult
    data class Failed(val deliveryStarted: Boolean, val error: Throwable) : RewardDeliveryResult
}

/** Validates, charges, rewards, and persists a clan shop purchase. */
internal class ClanShopService(private val plugin: BukkitPlugin) {
    private val currencies = ClanShopCurrencyService(plugin)
    private val ledgerFile = File(plugin.dataFolder, "shop-purchases.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var ledgerHealthy = true
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

    fun purchase(
        player: Player,
        clan: Clan,
        productId: String,
        paymentIndex: Int,
        expectedProduct: ClanShopProductConfig,
        expectedPayment: ClanShopPaymentOption
    ): ClanShopPurchaseResult {
        if (!plugin.configService.shop.enabled) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.SHOP_DISABLED)
        }
        if (!ledgerHealthy || !resetDayIfNeeded()) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.LEDGER_FAILED)
        }

        val product = plugin.configService.shop.products[productId]
            ?: return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.PRODUCT_NOT_FOUND)
        if (product != expectedProduct) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.SHOP_CHANGED)
        }
        val payment = product.payments.getOrNull(paymentIndex)
            ?: return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.PAYMENT_NOT_CONFIGURED)
        if (payment != expectedPayment) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.SHOP_CHANGED)
        }
        val currency = payment.currency
        if (!payment.permission.isNullOrBlank() && !player.hasPermission(payment.permission)) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.NO_PERMISSION)
        }
        if (!meetsRequirements(clan, product)) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REQUIREMENTS_NOT_MET)
        }

        if (product.conditions.dailyClanLimit > 0 && currentClanPurchases(clan, productId) >= product.conditions.dailyClanLimit) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.CLAN_LIMIT_REACHED)
        }
        if (product.conditions.dailyGlobalLimit > 0 && currentGlobalPurchases(productId) >= product.conditions.dailyGlobalLimit) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.GLOBAL_LIMIT_REACHED)
        }
        if (!currencies.isAvailable(currency)) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.CURRENCY_UNAVAILABLE)
        }

        val preparedItem = runCatching { prepareItemReward(productId, product) }
            .getOrElse { error ->
                plugin.logger.log(Level.SEVERE, "Invalid clan shop reward configuration for product '$productId'.", error)
                return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REWARD_FAILED)
            }

        val event = ClanShopPurchasePreEvent(clan, player, productId, currency, payment.amount)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled || event.price <= 0L) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.CANCELLED_BY_EVENT)
        }
        val balance = currencies.balance(currency, player, clan)
        if (balance == null || balance < event.price) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.INSUFFICIENT_FUNDS, event.price)
        }
        if (!currencies.withdraw(currency, player, clan, event.price)) {
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.INSUFFICIENT_FUNDS, event.price)
        }

        if (!reservePurchase(clan, productId)) {
            val refunded = currencies.refund(currency, player, clan, event.price)
            if (!refunded) {
                plugin.logger.severe("Clan shop ledger failed after charging ${player.name} for '$productId', and the refund also failed.")
                return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REFUND_FAILED, event.price)
            }
            return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.LEDGER_FAILED, event.price)
        }

        when (val delivery = deliverRewards(player, clan, productId, product, preparedItem)) {
            RewardDeliveryResult.Success -> Unit
            is RewardDeliveryResult.Failed -> {
                plugin.logger.log(
                    Level.SEVERE,
                    "Clan shop reward delivery failed for player=${player.name}, clan=${clan.id}, product=$productId. deliveryStarted=${delivery.deliveryStarted}",
                    delivery.error
                )

                if (delivery.deliveryStarted) {
                    // Some reward code may already have produced an irreversible side effect. Refunding here can create a dupe.
                    return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REWARD_PARTIAL, event.price)
                }

                val refunded = currencies.refund(currency, player, clan, event.price)
                if (!refunded) {
                    return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REFUND_FAILED, event.price)
                }
                if (!releasePurchase(clan, productId)) {
                    return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.LEDGER_FAILED, event.price)
                }
                return ClanShopPurchaseResult.Rejected(ClanShopPurchaseRejection.REWARD_FAILED, event.price)
            }
        }

        Bukkit.getPluginManager().callEvent(ClanShopPurchaseEvent(clan, player, productId, currency, event.price))
        plugin.clanQuestService.recordShopPurchase(clan, player)
        return ClanShopPurchaseResult.Success(event.price)
    }

    private fun meetsRequirements(clan: Clan, product: ClanShopProductConfig): Boolean =
        clan.level >= product.conditions.minimumClanLevel &&
            clan.users.size >= product.conditions.minimumMembers &&
            plugin.clanQuestService.requiredQuestsMet(clan, product.conditions.requiredQuests)

    /** Decode static item data before charging so a broken item config cannot take a player's currency. */
    private fun prepareItemReward(productId: String, product: ClanShopProductConfig): org.bukkit.inventory.ItemStack? =
        product.itemStack?.takeIf { it.isNotBlank() }?.let { encoded ->
            ItemStackSerializer.fromBase64(encoded).firstOrNull()?.clone()
                ?: throw IllegalArgumentException("Invalid serialized shop item for $productId")
        }

    private fun deliverRewards(
        player: Player,
        clan: Clan,
        productId: String,
        product: ClanShopProductConfig,
        preparedItem: org.bukkit.inventory.ItemStack?
    ): RewardDeliveryResult {
        var deliveryStarted = false
        return try {
            val placeholders = mapOf(
                "player" to player.name,
                "player_name" to player.name,
                "product" to productId,
                "clan" to clan.name,
                "quantity" to product.itemAmount.toString()
            )
            val context = ActionContext(player, plugin.placeholderRegistry, placeholders, plugin)

            // Execute configurable actions before the static item. The item is already decoded and therefore cannot fail because of malformed Base64.
            product.rewards.forEach { action ->
                deliveryStarted = true
                action.execute(context)
            }
            preparedItem?.let { item ->
                deliveryStarted = true
                giveItem(player, item, product.itemAmount)
            }
            RewardDeliveryResult.Success
        } catch (error: Throwable) {
            RewardDeliveryResult.Failed(deliveryStarted, error)
        }
    }

    /** Delivers all units and drops inventory overflow at the buyer's feet. */
    private fun giveItem(player: Player, template: org.bukkit.inventory.ItemStack, amount: Int) {
        var remaining = amount.coerceAtLeast(1)
        while (remaining > 0) {
            val stack = template.clone().apply { this.amount = minOf(remaining, maxStackSize) }
            player.inventory.addItem(stack).values.forEach { overflow ->
                player.world.dropItemNaturally(player.location, overflow)
            }
            remaining -= stack.amount
        }
    }

    private fun currentClanPurchases(clan: Clan, productId: String): Int =
        ledger.clanPurchases[clan.id]?.get(productId) ?: 0

    private fun currentGlobalPurchases(productId: String): Int = ledger.globalPurchases[productId] ?: 0

    /** Persist the daily-limit reservation before any irreversible reward is delivered. */
    private fun reservePurchase(clan: Clan, productId: String): Boolean {
        ledger.clanPurchases.computeIfAbsent(clan.id) { mutableMapOf() }.merge(productId, 1, Int::plus)
        ledger.globalPurchases.merge(productId, 1, Int::plus)
        if (saveLedger()) return true
        decrementPurchase(clan, productId)
        return false
    }

    private fun releasePurchase(clan: Clan, productId: String): Boolean {
        decrementPurchase(clan, productId)
        return saveLedger()
    }

    private fun decrementPurchase(clan: Clan, productId: String) {
        ledger.clanPurchases[clan.id]?.let { products ->
            val next = (products[productId] ?: 0) - 1
            if (next > 0) products[productId] = next else products.remove(productId)
            if (products.isEmpty()) ledger.clanPurchases.remove(clan.id)
        }
        val globalNext = (ledger.globalPurchases[productId] ?: 0) - 1
        if (globalNext > 0) ledger.globalPurchases[productId] = globalNext else ledger.globalPurchases.remove(productId)
    }

    private fun resetDayIfNeeded(): Boolean {
        if (!ledgerHealthy) return false
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        if (ledger.date == today) return true
        ledger = ClanShopPurchaseLedger(today)
        return saveLedger()
    }

    private fun loadLedger(): ClanShopPurchaseLedger {
        if (!ledgerFile.exists()) return ClanShopPurchaseLedger()
        return runCatching { json.decodeFromString<ClanShopPurchaseLedger>(ledgerFile.readText()) }
            .onFailure { error ->
                ledgerHealthy = false
                plugin.logger.log(
                    Level.SEVERE,
                    "Cannot read ${ledgerFile.name}. Clan shop purchases are disabled until the ledger is repaired and the plugin is reloaded.",
                    error
                )
            }
            .getOrElse { ClanShopPurchaseLedger() }
    }

    private fun saveLedger(): Boolean {
        val tempFile = File(ledgerFile.parentFile, "${ledgerFile.name}.tmp")
        return runCatching {
            ledgerFile.parentFile?.mkdirs()
            tempFile.writeText(json.encodeToString(ledger))
            try {
                Files.move(
                    tempFile.toPath(),
                    ledgerFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), ledgerFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                tempFile.delete()
                ledgerHealthy = false
                plugin.logger.log(
                    Level.SEVERE,
                    "Cannot persist ${ledgerFile.name}. Clan shop purchases are disabled until reload to protect purchase limits.",
                    error
                )
                false
            }
        )
    }
}
