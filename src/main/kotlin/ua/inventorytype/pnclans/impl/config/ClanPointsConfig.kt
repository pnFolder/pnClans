package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

/** Point rules stored in points.yml. Balances and runtime history remain in clan storage. */
@Serializable
data class ClanPointsConfig(
    val schemaVersion: Int = 1,
    @YamlComment("Настройки защиты от фарма клановых очков на убийствах игроков.")
    val antiFarm: ClanPointsAntiFarmConfig = ClanPointsAntiFarmConfig(),
    @YamlComment("Настройки личных очков участника, которые отображаются в статистике клана.")
    val memberPoints: ClanMemberPointsConfig = ClanMemberPointsConfig(),
    @YamlComment("Ограничения сохраняемой истории очков и anti-farm tracking.")
    val history: ClanPointsHistoryConfig = ClanPointsHistoryConfig(),
    @YamlComment("Поведение административных операций с очками.")
    val admin: ClanPointsAdminConfig = ClanPointsAdminConfig()
)

@Serializable
data class ClanPointsAntiFarmConfig(
    @YamlComment("Главный переключатель встроенной anti-farm проверки. API events продолжают вызываться даже когда false.")
    val enabled: Boolean = true,
    @YamlComment("Не применять обычный anti-farm к убийствам внутри организованной clan battle.")
    val ignoreOrganizedBattles: Boolean = true,
    val sameClan: SameClanKillRule = SameClanKillRule(),
    val repeatedKill: RepeatedKillRule = RepeatedKillRule(),
    val sameVictim: SameVictimRule = SameVictimRule(),
    val pairLimit: PairKillRule = PairKillRule(),
    val sameIp: SameIpKillRule = SameIpKillRule(),
    val victimRequirements: VictimRequirementsRule = VictimRequirementsRule(),
    val diminishingReturns: DiminishingReturnsRule = DiminishingReturnsRule(),
    val dailyLimits: PointDailyLimitsRule = PointDailyLimitsRule()
)

@Serializable
data class SameClanKillRule(
    @YamlComment("Запрещать награду за убийство участника собственного клана.")
    val enabled: Boolean = true,
    @YamlComment("Множитель награды при срабатывании правила. 0.0 полностью блокирует очки.")
    val multiplier: Double = 0.0
)

@Serializable
data class RepeatedKillRule(
    @YamlComment("Проверять слишком быстрое повторное убийство той же жертвы.")
    val enabled: Boolean = true,
    @YamlComment("Минимальное время между оплачиваемыми убийствами той же жертвы.")
    val cooldownSeconds: Long = 600L,
    val multiplier: Double = 0.0
)

@Serializable
data class SameVictimRule(
    @YamlComment("Ограничивать количество убийств одной и той же жертвы одним игроком за UTC-день.")
    val enabled: Boolean = true,
    val maxKillsPerDay: Int = 10,
    val multiplierAfterLimit: Double = 0.0
)

@Serializable
data class PairKillRule(
    @YamlComment("Ограничивать фарм одной пары игроков A↔B.")
    val enabled: Boolean = true,
    @YamlComment("Если true, A→B и B→A считаются одной парой.")
    val bidirectional: Boolean = true,
    val maxKillsPerDay: Int = 12,
    val multiplierAfterLimit: Double = 0.0
)

@Serializable
data class SameIpKillRule(
    @YamlComment("Проверять совпадение IP убийцы и жертвы. По умолчанию выключено из-за возможных proxy/VPN/NAT конфигураций.")
    val enabled: Boolean = false,
    @YamlComment("Сколько same-IP убийств одного убийцы может принести очки за UTC-день.")
    val maxKillsPerDay: Int = 2,
    val multiplierAfterLimit: Double = 0.0
)

@Serializable
data class VictimRequirementsRule(
    @YamlComment("Не давать полную награду за совсем новые аккаунты/твинки.")
    val enabled: Boolean = true,
    @YamlComment("Минимальное суммарное игровое время жертвы на сервере.")
    val minimumPlaytimeMinutes: Long = 30L,
    @YamlComment("Минимальный возраст аккаунта на сервере с момента firstPlayed.")
    val minimumFirstJoinAgeMinutes: Long = 60L,
    val multiplier: Double = 0.0
)

@Serializable
data class DiminishingReturnsRule(
    @YamlComment("Постепенно уменьшать награду за многократное убийство одной жертвы за день.")
    val enabled: Boolean = true,
    @YamlComment("Множитель по номеру убийства одной жертвы. Последнее значение используется для всех последующих убийств.")
    val multipliers: List<Double> = listOf(1.0, 1.0, 0.75, 0.5, 0.25, 0.0)
)

@Serializable
data class PointDailyLimitsRule(
    @YamlComment("Максимум клановых очков от обычных убийств, который один игрок может принести за UTC-день. 0 = без лимита.")
    val playerPointsPerDay: Long = 500L,
    @YamlComment("Максимум клановых очков от обычных убийств для одного клана за UTC-день. 0 = без лимита.")
    val clanPointsPerDay: Long = 3000L
)

@Serializable
data class ClanMemberPointsConfig(
    @YamlComment("Личные очки участника за обычное убийство игрока.")
    val playerKillReward: Int = 3,
    @YamlComment("Сколько личных очков участник теряет после смерти.")
    val deathPenalty: Int = 1,
    @YamlComment("Умножать личную награду за убийство на результат anti-farm проверки.")
    val applyAntiFarmMultiplier: Boolean = true
)

@Serializable
data class ClanPointsHistoryConfig(
    @YamlComment("Максимальное количество операций pointsLogs, сохраняемых для одного клана.")
    val maxTransactionsPerClan: Int = 2000,
    @YamlComment("Максимальное количество записей anti-farm tracking для одного клана.")
    val maxAntiFarmRecordsPerClan: Int = 5000,
    @YamlComment("Удалять anti-farm записи старше указанного количества дней. 0 = не удалять по возрасту.")
    val antiFarmRetentionDays: Int = 14
)

@Serializable
data class ClanPointsAdminConfig(
    @YamlComment("Требовать текст причины для reset и rollback. Для add/remove/set причина остаётся необязательной.")
    val requireReasonForDestructiveActions: Boolean = true,
    @YamlComment("Количество записей истории на одну страницу /clan admin points history.")
    val historyPageSize: Int = 10
)
