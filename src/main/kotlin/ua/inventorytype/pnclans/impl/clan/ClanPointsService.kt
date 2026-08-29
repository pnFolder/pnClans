package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsTransaction
import ua.inventorytype.pnclans.api.clan.ClanPointsTransactionType
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.api.clan.ClanPoints
import ua.inventorytype.pnclans.api.event.ClanPointsAntiFarmReason
import ua.inventorytype.pnclans.api.event.ClanPointsTransactionEvent

/** Central service for reward, quest, shop and administrative point operations. */
internal class ClanPointsService(private val clanService: ClanService) : ClanPoints {

    override fun award(clan: Clan, amount: Long, source: ClanPointsSource): Boolean =
        mutate(clan, ClanPointsTransactionType.AWARD, amount, source)

    override fun spend(clan: Clan, amount: Long, source: ClanPointsSource): Boolean =
        mutate(clan, ClanPointsTransactionType.SPEND, amount, source)

    internal fun awardPlayerKill(
        clan: Clan,
        amount: Long,
        killerName: String,
        victimName: String,
        antiFarmReasons: Set<ClanPointsAntiFarmReason>
    ): Boolean = mutate(
        clan = clan,
        type = ClanPointsTransactionType.AWARD,
        amount = amount,
        source = ClanPointsSource.PLAYER_KILL,
        actor = killerName,
        target = victimName,
        reason = antiFarmReasons.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }
    )

    internal fun adminAdjust(
        clan: Clan,
        delta: Long,
        actor: String,
        reason: String?,
        relatedTransactionId: String? = null
    ): Boolean {
        if (delta == 0L) return true
        if (delta == Long.MIN_VALUE) return false
        return mutate(
            clan = clan,
            type = if (delta > 0L) ClanPointsTransactionType.AWARD else ClanPointsTransactionType.SPEND,
            amount = kotlin.math.abs(delta),
            source = ClanPointsSource.ADMIN,
            actor = actor,
            reason = reason,
            relatedTransactionId = relatedTransactionId
        )
    }

    internal fun rollback(
        clan: Clan,
        transaction: ClanPointsTransaction,
        actor: String,
        reason: String
    ): Boolean {
        if (transaction.relatedTransactionId != null) return false
        if (clan.pointsLogs.any { it.relatedTransactionId == transaction.id }) return false
        val delta = when (transaction.type) {
            ClanPointsTransactionType.AWARD -> -transaction.amount
            ClanPointsTransactionType.SPEND -> transaction.amount
            ClanPointsTransactionType.ADMIN_ADJUSTMENT -> return false
        }
        return adminAdjust(
            clan = clan,
            delta = delta,
            actor = actor,
            reason = "ROLLBACK: $reason",
            relatedTransactionId = transaction.id
        )
    }

    private fun mutate(
        clan: Clan,
        type: ClanPointsTransactionType,
        amount: Long,
        source: ClanPointsSource,
        actor: String? = null,
        target: String? = null,
        reason: String? = null,
        relatedTransactionId: String? = null
    ): Boolean {
        if (amount <= 0L) return false
        val event = ClanPointsTransactionEvent(clan, type, source, amount)
        clanService.plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled || event.amount <= 0L) return false

        val previousPoints = clan.points
        val previousLogs = clan.pointsLogs
        when (type) {
            ClanPointsTransactionType.AWARD -> {
                if (clan.points > Long.MAX_VALUE - event.amount) return false
                clan.addPoints(event.amount)
            }
            ClanPointsTransactionType.SPEND -> if (!clan.withdrawPoints(event.amount)) return false
            ClanPointsTransactionType.ADMIN_ADJUSTMENT -> return false
        }

        record(
            clan = clan,
            type = type,
            source = source,
            amount = event.amount,
            actor = actor,
            target = target,
            reason = reason,
            relatedTransactionId = relatedTransactionId
        )
        (clan as? ClanImpl)?.prunePointsLogs(clanService.plugin.configService.points.history.maxTransactionsPerClan)

        if (!clanService.saveClan(clan)) {
            clan.points = previousPoints
            (clan as? ClanImpl)?.restorePointsLogs(previousLogs)
            return false
        }
        return true
    }

    private fun record(
        clan: Clan,
        type: ClanPointsTransactionType,
        source: ClanPointsSource,
        amount: Long,
        actor: String?,
        target: String?,
        reason: String?,
        relatedTransactionId: String?
    ) {
        clan.addPointsLog(
            ClanPointsTransaction(
                type = type,
                source = source,
                amount = amount,
                balanceAfter = clan.points,
                timestamp = System.currentTimeMillis(),
                actor = actor,
                target = target,
                reason = reason,
                relatedTransactionId = relatedTransactionId
            )
        )
    }
}
