package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.User
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete data implementation of a clan member.
 *
 * Tracks persistent in-clan performance so that the player's profile, level rewards, and MMR
 * gains can be displayed consistently in the GUI and progression screens.
 *
 * @property uuid The unique UUID identifier of the member.
 * @property playerName The current in-game nickname of the member, which automatically updates upon server join.
 * @property kills Total PvP kills attributed to this member in the current clan record.
 * @property deaths Total PvP deaths attributed to this member in the current clan record.
 * @property playtimeSeconds Total time in seconds recorded for this member in the current clan.
 * @property points Personal contribution points used to unlock clan-wide rewards.
 */
data class ClanUser(
    override val uuid: UUID,
    override var playerName: String,
    var kills: Int = 0,
    var deaths: Int = 0,
    var playtimeSeconds: Long = 0L,
    var points: Int = 0,
    private val initialCombatHistory: Map<String, ClanDailyCombatStats> = emptyMap()
) : User {
    private val combatHistory = ConcurrentHashMap(initialCombatHistory)

    val dailyCombatStats: Map<String, ClanDailyCombatStats>
        get() = combatHistory.toMap()

    fun recordKill(today: LocalDate = ClanStatsPeriod.todayUtc()) {
        kills++
        combatHistory.compute(today.toString()) { _, current ->
            (current ?: ClanDailyCombatStats()).let { it.copy(kills = it.kills + 1) }
        }
        pruneHistory(today)
    }

    fun recordDeath(today: LocalDate = ClanStatsPeriod.todayUtc()) {
        deaths++
        combatHistory.compute(today.toString()) { _, current ->
            (current ?: ClanDailyCombatStats()).let { it.copy(deaths = it.deaths + 1) }
        }
        pruneHistory(today)
    }

    fun combatStats(period: ClanStatsPeriod, today: LocalDate = ClanStatsPeriod.todayUtc()): ClanCombatStatsSnapshot {
        if (period == ClanStatsPeriod.ALL) return ClanCombatStatsSnapshot(kills, deaths)
        val values = combatHistory.entries.filter { (dateText, _) ->
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@filter false
            period.includes(date, today)
        }.map { it.value }
        return ClanCombatStatsSnapshot(values.sumOf { it.kills }, values.sumOf { it.deaths })
    }

    private fun pruneHistory(today: LocalDate) {
        val oldest = today.minusMonths(13).withDayOfMonth(1)
        combatHistory.keys.removeIf { dateText ->
            runCatching { LocalDate.parse(dateText).isBefore(oldest) }.getOrDefault(true)
        }
    }
}
