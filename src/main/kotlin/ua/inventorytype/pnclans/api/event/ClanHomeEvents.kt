package ua.inventorytype.pnclans.api.event

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan

/** Fired before a clan home is created or moved. Cancelling prevents the home mutation. */
class ClanHomeSetEvent(val clan: Clan, val actor: Player, val homeId: String, var location: Location) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

/** Fired before a clan home is removed. Cancelling prevents the deletion. */
class ClanHomeDeleteEvent(val clan: Clan, val actor: Player, val homeId: String, val location: Location) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

/** Fired before a clan chest is opened. Cancelling prevents the GUI from opening. */
class ClanChestOpenEvent(val clan: Clan, val player: Player) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
