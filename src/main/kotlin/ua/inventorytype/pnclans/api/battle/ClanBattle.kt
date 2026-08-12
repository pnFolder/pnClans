package ua.inventorytype.pnclans.api.battle

import java.util.UUID

/** Public snapshot of an active or recently completed clan battle. */
data class ClanBattle(
    val id: UUID,
    val challengerClanId: String,
    val defenderClanId: String,
    val arenaId: String,
    val startedAt: Long,
    val endsAt: Long,
    var challengerScore: Int = 0,
    var defenderScore: Int = 0
) {
    fun scoreFor(clanId: String): Int = when (clanId) {
        challengerClanId -> challengerScore
        defenderClanId -> defenderScore
        else -> 0
    }

    fun containsClan(clanId: String): Boolean = clanId == challengerClanId || clanId == defenderClanId
}

enum class ClanBattleEndReason {
    SCORE_LIMIT,
    TIME_LIMIT,
    FORFEIT,
    ADMIN_STOP,
    SERVER_SHUTDOWN
}
