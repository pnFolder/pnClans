package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Statistic
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.event.ClanPointsAntiFarmPreCheckEvent
import ua.inventorytype.pnclans.api.event.ClanPointsAntiFarmReason
import ua.inventorytype.pnclans.api.event.ClanPointsAntiFarmResultEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.floor

/** Persisted compact record used only for anti-farm decisions and administrator inspection. */
internal data class ClanPointKillRecord(
    val killerUuid: UUID,
    val victimUuid: UUID,
    val sameIp: Boolean,
    val baseAmount: Long,
    val grantedAmount: Long,
    val reasons: Set<ClanPointsAntiFarmReason>,
    val timestamp: Long
)

/** Final result of one player-kill points evaluation. */
internal data class ClanPointAntiFarmDecision(
    val baseAmount: Long,
    val amount: Long,
    val reasons: Set<ClanPointsAntiFarmReason>
) {
    val multiplier: Double
        get() = if (baseAmount <= 0L) 0.0 else amount.toDouble() / baseAmount.toDouble()
}

/**
 * Central anti-farm engine for ordinary PvP rewards.
 *
 * The service never cancels the death or combat itself. It only determines how many clan/member
 * points a kill is allowed to produce. Every attempt is persisted in the clan payload so limits
 * survive restarts for both JSON and SQLite storage backends.
 */
internal class ClanPointsAntiFarmService(private val plugin: BukkitPlugin) {

    fun evaluate(clan: Clan, killer: Player, victim: Player, configuredBaseAmount: Long): ClanPointAntiFarmDecision {
        val pre = ClanPointsAntiFarmPreCheckEvent(
            clan = clan,
            killer = killer,
            victim = victim,
            baseAmount = configuredBaseAmount.coerceAtLeast(0L)
        )
        plugin.server.pluginManager.callEvent(pre)

        if (pre.isCancelled) {
            val reasons = setOf(ClanPointsAntiFarmReason.EXTERNAL_CANCELLED)
            persist(clan, killer, victim, pre.baseAmount.coerceAtLeast(0L), 0L, reasons)
            return ClanPointAntiFarmDecision(pre.baseAmount.coerceAtLeast(0L), 0L, reasons)
        }

        val baseAmount = pre.baseAmount.coerceAtLeast(0L)
        val cfg = plugin.configService.points.antiFarm
        val reasons = linkedSetOf<ClanPointsAntiFarmReason>()
        var amount = baseAmount

        if (cfg.enabled && !pre.bypassBuiltInChecks && baseAmount > 0L) {
            val now = System.currentTimeMillis()
            val dayStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            val records = (clan as? ClanImpl)?.pointKillRecords.orEmpty()
            val today = records.asSequence().filter { it.timestamp >= dayStart }.toList()
            val killerId = killer.uniqueId
            val victimId = victim.uniqueId

            val victimClan = plugin.clanService.getClanUser(victim)
            if (cfg.sameClan.enabled && victimClan?.id == clan.id) {
                amount = applyMultiplier(amount, cfg.sameClan.multiplier)
                reasons += ClanPointsAntiFarmReason.SAME_CLAN
            }

            if (cfg.repeatedKill.enabled && cfg.repeatedKill.cooldownSeconds > 0L) {
                val lastSameVictim = records.asReversed().firstOrNull {
                    it.killerUuid == killerId && it.victimUuid == victimId
                }
                if (lastSameVictim != null && now - lastSameVictim.timestamp < cfg.repeatedKill.cooldownSeconds * 1000L) {
                    amount = applyMultiplier(amount, cfg.repeatedKill.multiplier)
                    reasons += ClanPointsAntiFarmReason.REPEATED_KILL_COOLDOWN
                }
            }

            val sameVictimCount = today.count { it.killerUuid == killerId && it.victimUuid == victimId }
            if (cfg.sameVictim.enabled && cfg.sameVictim.maxKillsPerDay >= 0 && sameVictimCount >= cfg.sameVictim.maxKillsPerDay) {
                amount = applyMultiplier(amount, cfg.sameVictim.multiplierAfterLimit)
                reasons += ClanPointsAntiFarmReason.SAME_VICTIM_LIMIT
            }

            if (cfg.pairLimit.enabled && cfg.pairLimit.maxKillsPerDay >= 0) {
                val pairCount = today.count { record ->
                    if (cfg.pairLimit.bidirectional) {
                        (record.killerUuid == killerId && record.victimUuid == victimId) ||
                            (record.killerUuid == victimId && record.victimUuid == killerId)
                    } else {
                        record.killerUuid == killerId && record.victimUuid == victimId
                    }
                }
                if (pairCount >= cfg.pairLimit.maxKillsPerDay) {
                    amount = applyMultiplier(amount, cfg.pairLimit.multiplierAfterLimit)
                    reasons += ClanPointsAntiFarmReason.PAIR_LIMIT
                }
            }

            val sameIp = sameAddress(killer, victim)
            if (cfg.sameIp.enabled && sameIp && cfg.sameIp.maxKillsPerDay >= 0) {
                val sameIpCount = today.count { it.killerUuid == killerId && it.sameIp }
                if (sameIpCount >= cfg.sameIp.maxKillsPerDay) {
                    amount = applyMultiplier(amount, cfg.sameIp.multiplierAfterLimit)
                    reasons += ClanPointsAntiFarmReason.SAME_IP_LIMIT
                }
            }

            if (cfg.victimRequirements.enabled) {
                if (cfg.victimRequirements.minimumPlaytimeMinutes > 0L) {
                    @Suppress("DEPRECATION")
                    val playedTicks = runCatching { victim.getStatistic(Statistic.PLAY_ONE_MINUTE).toLong() }.getOrDefault(0L)
                    val playedMinutes = playedTicks / (20L * 60L)
                    if (playedMinutes < cfg.victimRequirements.minimumPlaytimeMinutes) {
                        amount = applyMultiplier(amount, cfg.victimRequirements.multiplier)
                        reasons += ClanPointsAntiFarmReason.VICTIM_PLAYTIME
                    }
                }

                if (cfg.victimRequirements.minimumFirstJoinAgeMinutes > 0L) {
                    val firstPlayed = victim.firstPlayed
                    val accountAgeMinutes = if (firstPlayed <= 0L) 0L else (now - firstPlayed).coerceAtLeast(0L) / 60_000L
                    if (accountAgeMinutes < cfg.victimRequirements.minimumFirstJoinAgeMinutes) {
                        amount = applyMultiplier(amount, cfg.victimRequirements.multiplier)
                        reasons += ClanPointsAntiFarmReason.VICTIM_ACCOUNT_AGE
                    }
                }
            }

            if (cfg.diminishingReturns.enabled && cfg.diminishingReturns.multipliers.isNotEmpty()) {
                val index = sameVictimCount.coerceAtMost(cfg.diminishingReturns.multipliers.lastIndex)
                val multiplier = cfg.diminishingReturns.multipliers[index]
                val reduced = applyMultiplier(amount, multiplier)
                if (reduced < amount) reasons += ClanPointsAntiFarmReason.DIMINISHING_RETURNS
                amount = reduced
            }

            val limits = cfg.dailyLimits
            if (limits.playerPointsPerDay > 0L) {
                val playerEarned = today.asSequence()
                    .filter { it.killerUuid == killerId }
                    .sumOf { it.grantedAmount.coerceAtLeast(0L) }
                val remaining = (limits.playerPointsPerDay - playerEarned).coerceAtLeast(0L)
                if (amount > remaining) {
                    amount = remaining
                    reasons += ClanPointsAntiFarmReason.PLAYER_DAILY_LIMIT
                }
            }
            if (limits.clanPointsPerDay > 0L) {
                val clanEarned = today.sumOf { it.grantedAmount.coerceAtLeast(0L) }
                val remaining = (limits.clanPointsPerDay - clanEarned).coerceAtLeast(0L)
                if (amount > remaining) {
                    amount = remaining
                    reasons += ClanPointsAntiFarmReason.CLAN_DAILY_LIMIT
                }
            }
        }

        val resultEvent = ClanPointsAntiFarmResultEvent(
            clan = clan,
            killer = killer,
            victim = victim,
            baseAmount = baseAmount,
            amount = amount.coerceAtLeast(0L),
            reasons = reasons.toSet()
        )
        plugin.server.pluginManager.callEvent(resultEvent)
        if (resultEvent.isCancelled) {
            reasons += ClanPointsAntiFarmReason.EXTERNAL_CANCELLED
            amount = 0L
        } else {
            amount = resultEvent.amount.coerceAtLeast(0L)
        }

        persist(clan, killer, victim, baseAmount, amount, reasons)
        return ClanPointAntiFarmDecision(baseAmount, amount, reasons)
    }

    fun recent(clan: Clan, playerUuid: UUID? = null): List<ClanPointKillRecord> =
        (clan as? ClanImpl)?.pointKillRecords.orEmpty()
            .asSequence()
            .filter { playerUuid == null || it.killerUuid == playerUuid || it.victimUuid == playerUuid }
            .sortedByDescending { it.timestamp }
            .toList()

    fun clear(clan: Clan): Boolean {
        val impl = clan as? ClanImpl ?: return false
        val previous = impl.pointKillRecords
        impl.clearPointKillRecords()
        if (!plugin.clanService.saveClan(clan)) {
            impl.restorePointKillRecords(previous)
            return false
        }
        return true
    }

    private fun persist(
        clan: Clan,
        killer: Player,
        victim: Player,
        baseAmount: Long,
        grantedAmount: Long,
        reasons: Set<ClanPointsAntiFarmReason>
    ) {
        val impl = clan as? ClanImpl ?: return
        val history = plugin.configService.points.history
        val now = System.currentTimeMillis()
        impl.addPointKillRecord(
            ClanPointKillRecord(
                killerUuid = killer.uniqueId,
                victimUuid = victim.uniqueId,
                sameIp = sameAddress(killer, victim),
                baseAmount = baseAmount,
                grantedAmount = grantedAmount,
                reasons = reasons.toSet(),
                timestamp = now
            )
        )

        val oldestAllowed = if (history.antiFarmRetentionDays > 0) {
            Instant.ofEpochMilli(now).minusSeconds(history.antiFarmRetentionDays.toLong() * 86_400L).toEpochMilli()
        } else {
            Long.MIN_VALUE
        }
        impl.prunePointKillRecords(oldestAllowed, history.maxAntiFarmRecordsPerClan.coerceAtLeast(1))
        plugin.clanService.saveClan(clan)
    }

    private fun sameAddress(first: Player, second: Player): Boolean {
        val firstAddress = first.address?.address?.hostAddress ?: return false
        val secondAddress = second.address?.address?.hostAddress ?: return false
        return firstAddress == secondAddress
    }

    private fun applyMultiplier(amount: Long, multiplier: Double): Long {
        val safe = multiplier.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        return floor(amount.toDouble() * safe).toLong().coerceAtLeast(0L)
    }
}
