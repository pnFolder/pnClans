package ua.inventorytype.pnclans.impl.clan

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks online playtime for clan members so the GUI can show per-member activity,
 * the level rewards can react to long sessions, and the death/kill awards can keep
 * consistent numbers across reloads.
 *
 * The tracker records the timestamp when a member goes online and adds the elapsed
 * delta to their [ClanUser.playtimeSeconds] on logout, periodic flush, and plugin shutdown.
 */
class PlaytimeTracker {

    private data class Session(val clanId: String, val lastTickMs: Long)
    private data class AppliedElapsed(val clan: ua.inventorytype.pnclans.api.clan.Clan, val milliseconds: Long)

    private val activeSessions = ConcurrentHashMap<UUID, Session>()

    /** Records that a player became online for a given clan. */
    fun markOnline(playerId: UUID, clanId: String) {
        activeSessions[playerId] = Session(clanId, System.currentTimeMillis())
    }

    /** Drops the current session without persisting accumulated time (e.g. when the player left a clan). */
    fun clearSession(playerId: UUID) {
        activeSessions.remove(playerId)
    }

    /** Finalizes the playtime delta for a player and writes it back to the matching [ClanUser]. */
    fun flushSession(playerId: UUID, clanService: ClanService) {
        val session = activeSessions.remove(playerId) ?: return
        applyElapsed(playerId, session, System.currentTimeMillis(), clanService)?.let { clanService.saveClan(it.clan) }
    }

    /** Finalizes every active session. Used only when the plugin shuts down. */
    fun flushAll(clanService: ClanService) {
        activeSessions.keys.toList().forEach { flushSession(it, clanService) }
    }

    /** Persists elapsed time while keeping every online session active. */
    fun checkpointAll(clanService: ClanService) {
        val now = System.currentTimeMillis()
        activeSessions.entries.toList().forEach { (playerId, session) ->
            val applied = applyElapsed(playerId, session, now, clanService) ?: return@forEach
            activeSessions.replace(playerId, session, Session(session.clanId, session.lastTickMs + applied.milliseconds))
        }
    }

    /** Drops all active sessions without persisting. */
    fun clear() {
        activeSessions.clear()
    }

    /**
     * Computes the live playtime for a player, including the currently running session.
     *
     * @return The total seconds the member has spent in the given clan, including the live session.
     */
    fun playtime(playerId: UUID, clanService: ClanService, persistedSeconds: Long): Long {
        val session = activeSessions[playerId] ?: return persistedSeconds
        if (clanService.getClanByUuid(playerId)?.id != session.clanId) return persistedSeconds
        val deltaSeconds = ((System.currentTimeMillis() - session.lastTickMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
        return persistedSeconds + deltaSeconds
    }

    private fun applyElapsed(
        playerId: UUID,
        session: Session,
        now: Long,
        clanService: ClanService
    ): AppliedElapsed? {
        val deltaSeconds = ((now - session.lastTickMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
        if (deltaSeconds == 0L) return null
        val clan = clanService.getClanByName(session.clanId) ?: return null
        val user = clan.users.firstOrNull { it.uuid == playerId } as? ClanUser ?: return null
        user.playtimeSeconds += deltaSeconds
        return AppliedElapsed(clan, deltaSeconds * MILLIS_PER_SECOND)
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
