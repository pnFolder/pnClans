package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsTransaction
import ua.inventorytype.pnclans.api.clan.ClanPointsTransactionType

/** Central service for all future reward, quest and shop point operations. */
class ClanPointsService(private val clanService: ClanService) {

    fun award(clan: Clan, amount: Long, source: String): Boolean {
        if (amount <= 0L || source.isBlank()) return false
        clan.addPoints(amount)
        record(clan, ClanPointsTransactionType.AWARD, source, amount)
        clanService.saveClan(clan)
        return true
    }

    fun spend(clan: Clan, amount: Long, source: String): Boolean {
        if (amount <= 0L || source.isBlank() || !clan.withdrawPoints(amount)) return false
        record(clan, ClanPointsTransactionType.SPEND, source, amount)
        clanService.saveClan(clan)
        return true
    }

    private fun record(clan: Clan, type: ClanPointsTransactionType, source: String, amount: Long) {
        clan.addPointsLog(ClanPointsTransaction(type, source, amount, clan.points, System.currentTimeMillis()))
    }
}
