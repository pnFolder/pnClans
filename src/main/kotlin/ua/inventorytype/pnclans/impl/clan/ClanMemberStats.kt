package ua.inventorytype.pnclans.impl.clan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

data class ClanDailyCombatStats(
    val kills: Int = 0,
    val deaths: Int = 0
)

data class ClanCombatStatsSnapshot(
    val kills: Int,
    val deaths: Int
) {
    val kdr: Double
        get() = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths
}

enum class ClanStatsPeriod(val label: String) {
    DAY("Сегодня"),
    WEEK("Текущая неделя"),
    MONTH("Текущий месяц"),
    ALL("Всё время в клане");

    internal fun includes(date: LocalDate, today: LocalDate): Boolean = when (this) {
        DAY -> date == today
        WEEK -> !date.isBefore(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) && !date.isAfter(today)
        MONTH -> date.year == today.year && date.month == today.month
        ALL -> true
    }

    companion object {
        fun fromInput(value: String?): ClanStatsPeriod? = when (value?.lowercase()) {
            "day", "today", "день", "сегодня" -> DAY
            "week", "неделя", "неделю" -> WEEK
            "month", "месяц" -> MONTH
            "all", "total", "все", "всё", "всего" -> ALL
            else -> null
        }

        internal fun todayUtc(): LocalDate = LocalDate.now(ZoneOffset.UTC)
    }
}
