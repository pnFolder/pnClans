package ua.inventorytype.pnclans.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency

/** Fired before shop currency is withdrawn. Listeners may cancel or change [price]. */
class ClanShopPurchasePreEvent(
    val clan: Clan,
    val player: Player,
    val productId: String,
    val currency: ClanShopCurrency,
    var price: Long
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

/** Fired after payment, rewards, limits, and persistence complete successfully. */
class ClanShopPurchaseEvent(
    val clan: Clan,
    val player: Player,
    val productId: String,
    val currency: ClanShopCurrency,
    val price: Long
) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
