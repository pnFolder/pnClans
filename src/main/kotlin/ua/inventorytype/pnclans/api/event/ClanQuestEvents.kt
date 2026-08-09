package ua.inventorytype.pnclans.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan

/** Contracts reserved for the upcoming quest module. IDs stay typed as strings until quest models exist. */
class ClanQuestProgressEvent(val clan: Clan, val questId: String, var progress: Long, val target: Long) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class ClanQuestCompleteEvent(val clan: Clan, val questId: String) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
