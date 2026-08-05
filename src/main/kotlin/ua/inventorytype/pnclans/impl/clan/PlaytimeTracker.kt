package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
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
        val deltaSeconds = ((System.currentTimeMillis() - session.lastTickMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
        if (deltaSeconds == 0L) return
        val clan = clanService.getClanByUuid(playerId) ?: return
        val user = (clan.users.firstOrNull { it.uuid == playerId } as? ClanUser) ?: return
        user.playtimeSeconds += deltaSeconds
        clanService.saveClan(clan)
    }

    /** Flushes every active session. Used on plugin shutdown. */
    fun flushAll(clanService: ClanService) {
        activeSessions.keys.toList().forEach { flushSession(it, clanService) }
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
        val deltaSeconds = ((System.currentTimeMillis() - session.lastTickMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
        return persistedSeconds + deltaSeconds
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * Convenience helper that flushes the live session for a [Player] if the player is currently online.
 *
 * Used by [ClanListener] to keep the playtime counter accurate when members leave their clan
 * through commands or the GUI.
 */
fun Player.flushClanPlaytime(clanService: ClanService) {
    clanService.playtimeTracker.flushSession(this.uniqueId, clanService)
}
