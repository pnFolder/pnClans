package ua.inventorytype.pnclans.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.command.ClanCommandContext
import ua.inventorytype.pnclans.api.command.ClanSubcommand

/** Fired after a clan is persisted through the public repository or core service. */
class ClanSavedEvent(val clan: Clan) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** Fired before an addon-owned /clan subcommand executes. */
class ClanSubcommandExecuteEvent(val subcommand: ClanSubcommand, val context: ClanCommandContext) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
