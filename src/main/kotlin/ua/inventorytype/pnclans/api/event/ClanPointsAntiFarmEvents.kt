package ua.inventorytype.pnclans.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan

/** Stable reason codes produced by the built-in player-kill anti-farm engine. */
enum class ClanPointsAntiFarmReason {
    SAME_CLAN,
    REPEATED_KILL_COOLDOWN,
    SAME_VICTIM_LIMIT,
    PAIR_LIMIT,
    SAME_IP_LIMIT,
    VICTIM_PLAYTIME,
    VICTIM_ACCOUNT_AGE,
    DIMINISHING_RETURNS,
    PLAYER_DAILY_LIMIT,
    CLAN_DAILY_LIMIT,
    EXTERNAL_CANCELLED
}

/**
 * Fired before built-in anti-farm rules are evaluated for a normal player kill.
 *
 * Other plugins may change [baseAmount], set [bypassBuiltInChecks] to true, or cancel the event to
 * suppress the points reward completely. This event is fired even when the built-in anti-farm
 * configuration is disabled so integrations can implement their own policy.
 */
class ClanPointsAntiFarmPreCheckEvent(
    val clan: Clan,
    val killer: Player,
    val victim: Player,
    var baseAmount: Long,
    var bypassBuiltInChecks: Boolean = false
) : Event(), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

/**
 * Fired after pnClans has calculated the built-in anti-farm result but before points are awarded.
 *
 * [amount] is mutable, so integrations can reduce, restore, or replace the final reward. Cancelling
 * the event changes the final reward to zero. [reasons] contains every built-in rule that reduced
 * or capped the reward.
 */
class ClanPointsAntiFarmResultEvent(
    val clan: Clan,
    val killer: Player,
    val victim: Player,
    val baseAmount: Long,
    var amount: Long,
    val reasons: Set<ClanPointsAntiFarmReason>
) : Event(), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
