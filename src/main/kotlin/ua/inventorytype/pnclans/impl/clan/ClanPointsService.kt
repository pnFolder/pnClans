package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsTransaction
import ua.inventorytype.pnclans.api.clan.ClanPointsTransactionType
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.api.event.ClanPointsTransactionEvent

/** Central service for all future reward, quest and shop point operations. */
class ClanPointsService(private val clanService: ClanService) {

    fun award(clan: Clan, amount: Long, source: ClanPointsSource): Boolean {
        val event = ClanPointsTransactionEvent(clan, ClanPointsTransactionType.AWARD, source, amount)
        clanService.plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled || event.amount <= 0L) return false
        clan.addPoints(event.amount)
        record(clan, ClanPointsTransactionType.AWARD, source, event.amount)
        clanService.saveClan(clan)
        return true
    }

    fun spend(clan: Clan, amount: Long, source: ClanPointsSource): Boolean {
        val event = ClanPointsTransactionEvent(clan, ClanPointsTransactionType.SPEND, source, amount)
        clanService.plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled || event.amount <= 0L || !clan.withdrawPoints(event.amount)) return false
        record(clan, ClanPointsTransactionType.SPEND, source, event.amount)
        clanService.saveClan(clan)
        return true
    }

    private fun record(clan: Clan, type: ClanPointsTransactionType, source: ClanPointsSource, amount: Long) {
        clan.addPointsLog(ClanPointsTransaction(type, source, amount, clan.points, System.currentTimeMillis()))
    }
}
