package ua.inventorytype.pnclans.impl.config

import org.bukkit.plugin.Plugin

/** Reports unsafe or contradictory values from points.yml without replacing administrator values. */
object PointsConfigValidator {
    fun validate(plugin: Plugin, configService: ConfigService) {
        val cfg = configService.points
        val issues = mutableListOf<String>()

        fun nonNegative(path: String, value: Long) {
            if (value < 0L) issues += "$path must be >= 0 (current: $value)"
        }
        fun nonNegative(path: String, value: Int) = nonNegative(path, value.toLong())
        fun multiplier(path: String, value: Double) {
            if (!value.isFinite() || value < 0.0) issues += "$path must be a finite value >= 0 (current: $value)"
        }

        val anti = cfg.antiFarm
        nonNegative("antiFarm.repeatedKill.cooldownSeconds", anti.repeatedKill.cooldownSeconds)
        nonNegative("antiFarm.sameVictim.maxKillsPerDay", anti.sameVictim.maxKillsPerDay)
        nonNegative("antiFarm.pairLimit.maxKillsPerDay", anti.pairLimit.maxKillsPerDay)
        nonNegative("antiFarm.sameIp.maxKillsPerDay", anti.sameIp.maxKillsPerDay)
        nonNegative("antiFarm.victimRequirements.minimumPlaytimeMinutes", anti.victimRequirements.minimumPlaytimeMinutes)
        nonNegative("antiFarm.victimRequirements.minimumFirstJoinAgeMinutes", anti.victimRequirements.minimumFirstJoinAgeMinutes)
        nonNegative("antiFarm.dailyLimits.playerPointsPerDay", anti.dailyLimits.playerPointsPerDay)
        nonNegative("antiFarm.dailyLimits.clanPointsPerDay", anti.dailyLimits.clanPointsPerDay)
        multiplier("antiFarm.sameClan.multiplier", anti.sameClan.multiplier)
        multiplier("antiFarm.repeatedKill.multiplier", anti.repeatedKill.multiplier)
        multiplier("antiFarm.sameVictim.multiplierAfterLimit", anti.sameVictim.multiplierAfterLimit)
        multiplier("antiFarm.pairLimit.multiplierAfterLimit", anti.pairLimit.multiplierAfterLimit)
        multiplier("antiFarm.sameIp.multiplierAfterLimit", anti.sameIp.multiplierAfterLimit)
        multiplier("antiFarm.victimRequirements.multiplier", anti.victimRequirements.multiplier)
        anti.diminishingReturns.multipliers.forEachIndexed { index, value ->
            multiplier("antiFarm.diminishingReturns.multipliers[$index]", value)
        }
        if (anti.diminishingReturns.enabled && anti.diminishingReturns.multipliers.isEmpty()) {
            issues += "antiFarm.diminishingReturns.multipliers must not be empty while diminishing returns are enabled"
        }

        nonNegative("memberPoints.playerKillReward", cfg.memberPoints.playerKillReward)
        nonNegative("memberPoints.deathPenalty", cfg.memberPoints.deathPenalty)
        if (cfg.history.maxTransactionsPerClan < 1) issues += "history.maxTransactionsPerClan must be >= 1"
        if (cfg.history.maxAntiFarmRecordsPerClan < 1) issues += "history.maxAntiFarmRecordsPerClan must be >= 1"
        nonNegative("history.antiFarmRetentionDays", cfg.history.antiFarmRetentionDays)
        if (cfg.admin.historyPageSize !in 1..50) issues += "admin.historyPageSize must be between 1 and 50"

        issues.forEach { plugin.logger.warning("[pnClans/PointsConfig] points.yml.$it") }
        if (anti.sameIp.enabled) {
            plugin.logger.warning("[pnClans/PointsConfig] points.yml antiFarm.sameIp is enabled. Ensure Paper receives real client IPs behind Velocity/Bungee; otherwise all players may appear to share the proxy address.")
        }
        if (issues.isNotEmpty()) {
            plugin.logger.warning("[pnClans/PointsConfig] Found ${issues.size} invalid point configuration value(s). Administrator values were not overwritten.")
        }
    }
}
