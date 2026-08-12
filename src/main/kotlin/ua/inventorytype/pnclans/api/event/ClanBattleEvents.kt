package ua.inventorytype.pnclans.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.battle.ClanBattle
import ua.inventorytype.pnclans.api.battle.ClanBattleEndReason
import ua.inventorytype.pnclans.api.clan.Clan

/** Fired before a player sends a clan battle challenge. */
class ClanBattleChallengeEvent(
    val challenger: Clan,
    val defender: Clan,
    val actor: Player
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** Fired before two clans are teleported into an arena and a battle starts. */
class ClanBattleStartEvent(
    val battle: ClanBattle,
    val challenger: Clan,
    val defender: Clan
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** Fired after an active battle has been resolved and rewards have been applied. */
class ClanBattleEndEvent(
    val battle: ClanBattle,
    val winner: Clan?,
    val loser: Clan?,
    val reason: ClanBattleEndReason
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
