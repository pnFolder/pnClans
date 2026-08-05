package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Immutable pending invitation snapshot.
 *
 * Clan identity is stored by ID instead of a mutable [ua.inventorytype.pnclans.api.clan.Clan]
 * reference so reloads and disbands cannot make an old invitation persist stale clan state.
 */
data class ClanInvite(
    val clanId: String,
    val clanName: String,
    val senderUuid: UUID,
    val targetUuid: UUID,
    val expiresAt: Long
) {
    /** Returns whether this invitation can no longer be accepted or denied. */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt
}

/**
 * Single authority for clan invitation creation, validation, expiration, acceptance, and denial.
 *
 * Both command and GUI flows delegate here so membership, permission, capacity, and expiry checks
 * cannot drift apart.
 *
 * @param clanService The clan service that resolves current clan state and persists changes.
 */
class ClanInviteService(private val clanService: ClanService) {

    private val pendingInvites = ConcurrentHashMap<UUID, ClanInvite>()

    /**
     * Validates and sends a clan invitation to an online player.
     *
     * @return True only when a new active invitation was created.
     */
    fun sendInvite(sender: Player, target: Player): Boolean {
        val cfg = clanService.plugin.configService
        val senderClan = clanService.getClanUser(sender) ?: run {
            cfg.send(sender, cfg.messages.invite.inviterNoClan)
            return false
        }
        val senderUser = senderClan.users.find { it.uuid == sender.uniqueId }

        if (senderUser == null || !senderClan.hasPermission(senderUser, ClanPerms.Members.INVITE)) {
            cfg.send(sender, cfg.messages.invite.noPermission)
            return false
        }

        if (target.uniqueId == sender.uniqueId) {
            cfg.send(sender, cfg.messages.invite.cannotInviteSelf)
            return false
        }

        val targetClan = clanService.getClanByUuid(target.uniqueId)
        if (targetClan != null) {
            val actions = if (targetClan.id == senderClan.id) {
                cfg.messages.invite.targetAlreadyInYourClan
            } else {
                cfg.messages.invite.targetAlreadyInOtherClan
            }
            cfg.send(sender, actions, mapOf("player" to target.name, "clan" to targetClan.name))
            return false
        }

        if (senderClan.users.size >= senderClan.maxMembers) {
            cfg.send(sender, cfg.messages.invite.clanFull, mapOf(
                "clan" to senderClan.name,
                "limit" to senderClan.maxMembers.toString()
            ))
            return false
        }

        val now = System.currentTimeMillis()
        pendingInvites[target.uniqueId]?.let { existing ->
            if (!existing.isExpired(now)) {
                cfg.send(sender, cfg.messages.invite.targetHasPendingInvite, mapOf("player" to target.name))
                return false
            }
            pendingInvites.remove(target.uniqueId, existing)
        }

        val lifetimeSeconds = cfg.settings.inviteLifetimeSeconds.coerceAtLeast(MIN_DURATION_SECONDS)
        val invite = ClanInvite(
            clanId = senderClan.id,
            clanName = senderClan.name,
            senderUuid = sender.uniqueId,
            targetUuid = target.uniqueId,
            expiresAt = now + lifetimeSeconds * MILLIS_PER_SECOND
        )
        pendingInvites[target.uniqueId] = invite
        scheduleExpiration(invite, lifetimeSeconds)

        val placeholders = mapOf(
            "player" to target.name,
            "sender" to sender.name,
            "clan" to senderClan.name,
            "seconds" to lifetimeSeconds.toString()
        )
        cfg.send(sender, cfg.messages.invite.inviteSent, placeholders)
        cfg.send(target, cfg.messages.invite.inviteReceived, placeholders)
        cfg.send(target, cfg.messages.invite.inviteInstructions, placeholders)
        return true
    }

    /**
     * Accepts the player's current invitation after re-validating all mutable clan state.
     *
     * @return True only when the player was successfully added to the invited clan.
     */
    fun acceptInvite(player: Player): Boolean {
        val cfg = clanService.plugin.configService
        val invite = pendingInvites.remove(player.uniqueId) ?: run {
            cfg.send(player, cfg.messages.invite.noActiveInvite)
            return false
        }

        if (invite.isExpired()) {
            cfg.send(player, cfg.messages.invite.inviteExpired)
            return false
        }

        if (clanService.getClanByUuid(player.uniqueId) != null) {
            cfg.send(player, cfg.messages.invite.acceptAlreadyInClan)
            return false
        }

        val clan = clanService.getClanByName(invite.clanId) ?: run {
            cfg.send(player, cfg.messages.invite.inviteInvalid)
            return false
        }
        val sender = clan.users.find { it.uuid == invite.senderUuid }
        if (sender == null || !clan.hasPermission(sender, ClanPerms.Members.INVITE)) {
            cfg.send(player, cfg.messages.invite.inviteInvalid)
            return false
        }

        synchronized(clan) {
            if (clanService.getClanByUuid(player.uniqueId) != null) {
                cfg.send(player, cfg.messages.invite.acceptAlreadyInClan)
                return false
            }

            if (clan.users.size >= clan.maxMembers) {
                cfg.send(player, cfg.messages.invite.clanFull, mapOf(
                    "clan" to clan.name,
                    "limit" to clan.maxMembers.toString()
                ))
                return false
            }

            val newUser = ClanUser(player.uniqueId, player.name)
            if (!clanService.addUserToClan(clan, newUser, ClanRole.MEMBER)) {
                cfg.send(player, cfg.messages.invite.inviteInvalid)
                return false
            }
            clanService.saveClan(clan)
        }

        cfg.send(player, cfg.messages.invite.accepted, mapOf("clan" to clan.name))
        clan.users.forEach { member ->
            clanService.notifyClanUpdated(member.uuid)
            if (member.uuid != player.uniqueId) {
                Bukkit.getPlayer(member.uuid)?.let { onlineMember ->
                    cfg.send(onlineMember, cfg.messages.invite.memberJoined, mapOf("player" to player.name))
                }
            }
        }
        return true
    }

    /**
     * Denies the player's current invitation.
     *
     * @return True when an unexpired invitation was removed.
     */
    fun denyInvite(player: Player): Boolean {
        val cfg = clanService.plugin.configService
        val invite = pendingInvites.remove(player.uniqueId) ?: run {
            cfg.send(player, cfg.messages.invite.noActiveInvite)
            return false
        }

        if (invite.isExpired()) {
            cfg.send(player, cfg.messages.invite.inviteExpired)
            return false
        }

        cfg.send(player, cfg.messages.invite.denied, mapOf("clan" to invite.clanName))
        Bukkit.getPlayer(invite.senderUuid)?.let { sender ->
            cfg.send(sender, cfg.messages.invite.deniedByTarget, mapOf("player" to player.name))
        }
        return true
    }

    /** Clears all pending invitations, for example during plugin shutdown. */
    fun clear() {
        pendingInvites.clear()
    }

    private fun scheduleExpiration(invite: ClanInvite, lifetimeSeconds: Int) {
        Bukkit.getScheduler().runTaskLater(
            clanService.plugin,
            Runnable { pendingInvites.remove(invite.targetUuid, invite) },
            lifetimeSeconds.toLong() * TICKS_PER_SECOND
        )
    }

    private companion object {
        const val MIN_DURATION_SECONDS = 1
        const val MILLIS_PER_SECOND = 1_000L
        const val TICKS_PER_SECOND = 20L
    }
}
