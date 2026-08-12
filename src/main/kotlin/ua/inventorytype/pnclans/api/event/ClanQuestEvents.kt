package ua.inventorytype.pnclans.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan

/** Fired before a clan's shared quest progress is accepted. */
class ClanQuestProgressEvent @JvmOverloads constructor(
    val clan: Clan,
    val questId: String,
    var progress: Long,
    val target: Long,
    val actor: Player? = null
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

/** Fired before a quest is marked complete and its configured rewards are granted. */
class ClanQuestCompleteEvent @JvmOverloads constructor(
    val clan: Clan,
    val questId: String,
    val actor: Player? = null
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
