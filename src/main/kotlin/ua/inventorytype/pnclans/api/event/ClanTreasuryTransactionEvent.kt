package ua.inventorytype.pnclans.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import org.bukkit.entity.Player

/** Fired after a treasury transaction is applied and persisted. */
class ClanTreasuryTransactionEvent(
    val clan: Clan,
    val transaction: TreasuryTransaction,
    val actor: Player
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
