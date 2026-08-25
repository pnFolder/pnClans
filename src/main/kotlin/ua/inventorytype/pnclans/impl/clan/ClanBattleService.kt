package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.battle.ClanBattle
import ua.inventorytype.pnclans.api.battle.ClanBattleEndReason
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.api.event.ClanBattleChallengeEvent
import ua.inventorytype.pnclans.api.event.ClanBattleEndEvent
import ua.inventorytype.pnclans.api.event.ClanBattleStartEvent
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.config.ClanBattleArenaConfig
import ua.inventorytype.pnclans.impl.config.ClanBattleSpawnConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

internal enum class ClanBattleRejection {
    DISABLED,
    NO_CLAN,
    NO_PERMISSION,
    SAME_CLAN,
    CLAN_BUSY,
    CHALLENGE_EXISTS,
    CHALLENGE_NOT_FOUND,
    CHALLENGE_EXPIRED,
    NOT_TARGET_CLAN,
    NOT_ENOUGH_ONLINE,
    ARENA_UNAVAILABLE,
    CANCELLED_BY_EVENT,
    LOBBY_NOT_FOUND,
    LOBBY_FULL,
    NOT_ENOUGH_SELECTED
}

internal sealed interface ClanBattleOperation {
    data object Success : ClanBattleOperation
    data class Rejected(val reason: ClanBattleRejection) : ClanBattleOperation
}

internal data class ClanBattleChallenge(
    val id: UUID,
    val challengerClanId: String,
    val defenderClanId: String,
    val actorUuid: UUID,
    val createdAt: Long,
    val expiresAt: Long
)

internal data class ClanBattleLobbySnapshot(
    val id: UUID,
    val challengerClanId: String,
    val defenderClanId: String,
    val arenaId: String,
    val participantsByClan: Map<String, Set<UUID>>,
    val readyClanIds: Set<String>,
    val countdownEndsAt: Long
) {
    fun participantsFor(clanId: String): Set<UUID> = participantsByClan[clanId].orEmpty()
    fun isReady(clanId: String): Boolean = clanId in readyClanIds
    fun opponentId(clanId: String): String? = when (clanId) {
        challengerClanId -> defenderClanId
        defenderClanId -> challengerClanId
        else -> null
    }
    val countdownActive: Boolean get() = countdownEndsAt > System.currentTimeMillis()
}

private data class ClanBattleLobby(
    val id: UUID,
    val challengerClanId: String,
    val defenderClanId: String,
    val arenaId: String,
    val participantsByClan: MutableMap<String, MutableSet<UUID>>,
    val readyClanIds: MutableSet<String> = mutableSetOf(),
    val expiresAt: Long,
    var countdownEndsAt: Long = 0L,
    var countdownTask: BukkitTask? = null,
    var expiryTask: BukkitTask? = null
)

private data class ActiveClanBattle(
    val battle: ClanBattle,
    val participantsByClan: MutableMap<String, MutableSet<UUID>>,
    val returnLocations: MutableMap<UUID, Location>,
    val damageByClan: MutableMap<String, Long> = mutableMapOf(),
    var timerTask: BukkitTask? = null
)

/** Handles challenge flow, lobby rosters, arena isolation, scoring, rewards, and battle lifecycle. */
internal class ClanBattleService(private val plugin: BukkitPlugin) : Listener {
    private val activeByBattleId = ConcurrentHashMap<UUID, ActiveClanBattle>()
    private val activeByClanId = ConcurrentHashMap<String, ActiveClanBattle>()
    private val activeByPlayer = ConcurrentHashMap<UUID, ActiveClanBattle>()
    private val activeByArenaId = ConcurrentHashMap<String, ActiveClanBattle>()

    private val lobbiesById = ConcurrentHashMap<UUID, ClanBattleLobby>()
    private val lobbyByClanId = ConcurrentHashMap<String, ClanBattleLobby>()
    private val lobbyByArenaId = ConcurrentHashMap<String, ClanBattleLobby>()

    private val quarantinedArenaIds = ConcurrentHashMap.newKeySet<String>()
    private val challenges = ConcurrentHashMap<UUID, ClanBattleChallenge>()
    private val pendingRespawnLocations = ConcurrentHashMap<UUID, Location>()
    private val pendingReturnLocations = ConcurrentHashMap<UUID, Location>()
    private val pendingArenaByPlayer = ConcurrentHashMap<UUID, String>()
    private val returnLocationKey = NamespacedKey(plugin, "battle-return-location")

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun shutdown() {
        activeByBattleId.keys.toList().forEach { finish(it, null, ClanBattleEndReason.SERVER_SHUTDOWN) }
        lobbiesById.values.toSet().forEach { lobby ->
            lobby.countdownTask?.cancel()
            lobby.expiryTask?.cancel()
        }
        lobbiesById.clear()
        lobbyByClanId.clear()
        lobbyByArenaId.clear()
        challenges.clear()
        pendingRespawnLocations.clear()
        pendingReturnLocations.clear()
        pendingArenaByPlayer.clear()
    }

    fun battleForClan(clan: Clan): ClanBattle? = activeByClanId[clan.id]?.battle?.copy()

    fun lobbyForClan(clan: Clan): ClanBattleLobbySnapshot? = lobbyByClanId[clan.id]?.snapshot()

    fun activeBattleCount(): Int = activeByBattleId.size

    fun prepareReload(): Boolean = activeByBattleId.isEmpty() && lobbiesById.isEmpty()

    fun completeReload() {
        challenges.clear()
    }

    fun hasActiveBattle(clan: Clan): Boolean = activeByClanId.containsKey(clan.id)

    /** Resolves an active battle/lobby and removes pending challenges before clan deletion. */
    fun prepareClanRemoval(clan: Clan) {
        challenges.values.filter { it.challengerClanId == clan.id || it.defenderClanId == clan.id }
            .forEach { challenges.remove(it.id, it) }
        lobbyByClanId[clan.id]?.let { lobby ->
            cancelLobby(lobby, "&#FC3737✖ &fСбор состава отменён: один из кланов был расформирован.")
        }
        val active = activeByClanId[clan.id] ?: return
        val winnerId = if (clan.id == active.battle.challengerClanId) {
            active.battle.defenderClanId
        } else {
            active.battle.challengerClanId
        }
        finish(active.battle.id, winnerId, ClanBattleEndReason.FORFEIT)
    }

    fun stopByAdmin(clan: Clan, winner: Clan? = null): Boolean {
        val active = activeByClanId[clan.id]
        if (active != null) {
            if (winner != null && !active.battle.containsClan(winner.id)) return false
            finish(active.battle.id, winner?.id, ClanBattleEndReason.ADMIN_STOP)
            return true
        }
        val lobby = lobbyByClanId[clan.id] ?: return false
        cancelLobby(lobby, "&#FC3737✖ &fСбор состава остановлен администратором.")
        return true
    }

    fun battleForPlayer(player: Player): ClanBattle? = activeByPlayer[player.uniqueId]?.battle?.copy()

    fun incomingChallenges(clan: Clan): List<ClanBattleChallenge> = challenges.values
        .filter { it.defenderClanId == clan.id && it.expiresAt > System.currentTimeMillis() }
        .sortedBy { it.createdAt }

    fun outgoingChallenges(clan: Clan): List<ClanBattleChallenge> = challenges.values
        .filter { it.challengerClanId == clan.id && it.expiresAt > System.currentTimeMillis() }
        .sortedBy { it.createdAt }

    fun availableOpponents(clan: Clan): List<Clan> {
        val challengedClanIds = challenges.values
            .filter { it.expiresAt > System.currentTimeMillis() }
            .flatMapTo(HashSet<String>()) { listOf(it.challengerClanId, it.defenderClanId) }
        return plugin.clanService.getAllClans()
            .filter {
                it.id != clan.id &&
                    it.id !in challengedClanIds &&
                    !isClanBusy(it.id) &&
                    it.onlineCount >= plugin.configService.battles.minimumOnlineMembers.coerceAtLeast(1)
            }
            .sortedWith(compareByDescending<Clan> { it.mmr }.thenBy { it.name.lowercase() })
    }

    fun sendChallenge(actor: Player, defender: Clan): ClanBattleOperation {
        val config = plugin.configService.battles
        if (!config.enabled) return ClanBattleOperation.Rejected(ClanBattleRejection.DISABLED)
        val challenger = plugin.clanService.getClanUser(actor)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        val member = challenger.getMember(actor.uniqueId)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        if (!challenger.hasPermission(member, ClanPerms.Action.START_BATTLE)) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.NO_PERMISSION)
        }
        if (challenger.id == defender.id) return ClanBattleOperation.Rejected(ClanBattleRejection.SAME_CLAN)
        if (isClanBusy(challenger.id) || isClanBusy(defender.id)) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.CLAN_BUSY)
        }
        if (challenges.values.any { it.challengerClanId == challenger.id || it.defenderClanId == challenger.id }) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.CHALLENGE_EXISTS)
        }
        if (challenges.values.any { it.challengerClanId == defender.id || it.defenderClanId == defender.id }) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.CHALLENGE_EXISTS)
        }

        val event = ClanBattleChallengeEvent(challenger, defender, actor)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return ClanBattleOperation.Rejected(ClanBattleRejection.CANCELLED_BY_EVENT)

        val now = System.currentTimeMillis()
        val challenge = ClanBattleChallenge(
            id = UUID.randomUUID(),
            challengerClanId = challenger.id,
            defenderClanId = defender.id,
            actorUuid = actor.uniqueId,
            createdAt = now,
            expiresAt = now + config.challengeTimeoutSeconds.coerceAtLeast(1L) * 1000L
        )
        challenges[challenge.id] = challenge
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { expireChallenge(challenge.id) }, config.challengeTimeoutSeconds.coerceAtLeast(1L) * 20L)

        notifyClan(challenger, plugin.configService.messages.battles.challengeSent, mapOf("opponent" to defender.name, "challenge_id" to challenge.id.toString()))
        notifyClan(defender, plugin.configService.messages.battles.challengeReceived, mapOf("challenger" to challenger.name, "challenge_id" to challenge.id.toString()))
        notifyGui(challenger)
        notifyGui(defender)
        return ClanBattleOperation.Success
    }

    /** Accepting a challenge opens a roster lobby; it no longer teleports arbitrary online members. */
    fun acceptChallenge(actor: Player, challengeId: UUID): ClanBattleOperation {
        val config = plugin.configService.battles
        if (!config.enabled) return ClanBattleOperation.Rejected(ClanBattleRejection.DISABLED)
        val challenge = challenges[challengeId]
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.CHALLENGE_NOT_FOUND)
        if (challenge.expiresAt <= System.currentTimeMillis()) {
            challenges.remove(challengeId)
            return ClanBattleOperation.Rejected(ClanBattleRejection.CHALLENGE_EXPIRED)
        }
        val defender = plugin.clanService.getClanUser(actor)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        if (defender.id != challenge.defenderClanId) return ClanBattleOperation.Rejected(ClanBattleRejection.NOT_TARGET_CLAN)
        val member = defender.getMember(actor.uniqueId)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        if (!defender.hasPermission(member, ClanPerms.Action.START_BATTLE)) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.NO_PERMISSION)
        }
        val challenger = plugin.clanService.getClanByName(challenge.challengerClanId)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.CHALLENGE_NOT_FOUND)
        if (isClanBusy(challenger.id) || isClanBusy(defender.id)) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.CLAN_BUSY)
        }

        val minimum = config.minimumOnlineMembers.coerceAtLeast(1)
        if (eligibleOnlineMembers(challenger).size < minimum || eligibleOnlineMembers(defender).size < minimum) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.NOT_ENOUGH_ONLINE)
        }

        val arena = resolveArena() ?: return ClanBattleOperation.Rejected(ClanBattleRejection.ARENA_UNAVAILABLE)
        val now = System.currentTimeMillis()
        val participants = mutableMapOf(
            challenger.id to mutableSetOf<UUID>(),
            defender.id to mutableSetOf<UUID>()
        )
        Bukkit.getPlayer(challenge.actorUuid)
            ?.takeIf { isEligibleLobbyPlayer(it, challenger) }
            ?.let { participants.getValue(challenger.id).add(it.uniqueId) }
        if (isEligibleLobbyPlayer(actor, defender)) {
            participants.getValue(defender.id).add(actor.uniqueId)
        }

        val lobby = ClanBattleLobby(
            id = challenge.id,
            challengerClanId = challenger.id,
            defenderClanId = defender.id,
            arenaId = arena.first,
            participantsByClan = participants,
            expiresAt = now + config.challengeTimeoutSeconds.coerceAtLeast(1L) * 1000L
        )
        if (lobbyByArenaId.putIfAbsent(lobby.arenaId, lobby) != null) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.ARENA_UNAVAILABLE)
        }
        if (lobbyByClanId.putIfAbsent(challenger.id, lobby) != null || lobbyByClanId.putIfAbsent(defender.id, lobby) != null) {
            lobbyByClanId.remove(challenger.id, lobby)
            lobbyByClanId.remove(defender.id, lobby)
            lobbyByArenaId.remove(lobby.arenaId, lobby)
            return ClanBattleOperation.Rejected(ClanBattleRejection.CLAN_BUSY)
        }
        lobbiesById[lobby.id] = lobby
        challenges.remove(challengeId)
        lobby.expiryTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            expireLobby(lobby.id)
        }, config.challengeTimeoutSeconds.coerceAtLeast(1L) * 20L)

        notifyLobbyMessage(
            lobby,
            "&#5EA9FD⌚ &fВызов принят. Открыт сбор состава: &eЛКМ по «Боевой готовности» — войти/выйти, ПКМ — READY стороны."
        )
        notifyLobbyChanged(lobby)
        return ClanBattleOperation.Success
    }

    fun declineChallenge(actor: Player, challengeId: UUID): ClanBattleOperation {
        val challenge = challenges[challengeId]
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.CHALLENGE_NOT_FOUND)
        val defender = plugin.clanService.getClanUser(actor)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        if (defender.id != challenge.defenderClanId) return ClanBattleOperation.Rejected(ClanBattleRejection.NOT_TARGET_CLAN)
        val member = defender.getMember(actor.uniqueId)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        if (!defender.hasPermission(member, ClanPerms.Action.START_BATTLE)) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.NO_PERMISSION)
        }
        challenges.remove(challengeId)
        plugin.clanService.getClanByName(challenge.challengerClanId)?.let {
            notifyClan(it, plugin.configService.messages.battles.declined, mapOf("opponent" to defender.name))
        }
        notifyClan(defender, plugin.configService.messages.battles.declinedByYou, emptyMap())
        notifyGui(defender)
        plugin.clanService.getClanByName(challenge.challengerClanId)?.let(::notifyGui)
        return ClanBattleOperation.Success
    }

    fun toggleLobbyParticipation(actor: Player): ClanBattleOperation {
        val clan = plugin.clanService.getClanUser(actor)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        val lobby = lobbyByClanId[clan.id]
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.LOBBY_NOT_FOUND)
        val roster = lobby.participantsByClan[clan.id]
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.LOBBY_NOT_FOUND)

        if (actor.uniqueId in roster) {
            roster.remove(actor.uniqueId)
            lobby.readyClanIds.remove(clan.id)
            cancelCountdown(lobby)
            actor.sendMessage(plugin.configService.formatMessage(actor, "&#FFD700⌚ &fВы вышли из боевого состава."))
        } else {
            val maximum = plugin.configService.battles.maximumParticipants.coerceAtLeast(1)
            if (roster.size >= maximum) return ClanBattleOperation.Rejected(ClanBattleRejection.LOBBY_FULL)
            if (!isEligibleLobbyPlayer(actor, clan)) return ClanBattleOperation.Rejected(ClanBattleRejection.CLAN_BUSY)
            roster.add(actor.uniqueId)
            lobby.readyClanIds.remove(clan.id)
            cancelCountdown(lobby)
            actor.sendMessage(plugin.configService.formatMessage(actor, "&#5EFD7D✔ &fВы вошли в боевой состав."))
        }
        notifyLobbyChanged(lobby)
        return ClanBattleOperation.Success
    }

    fun toggleLobbyReady(actor: Player): ClanBattleOperation {
        val clan = plugin.clanService.getClanUser(actor)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        val member = clan.getMember(actor.uniqueId)
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.NO_CLAN)
        if (!clan.hasPermission(member, ClanPerms.Action.START_BATTLE)) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.NO_PERMISSION)
        }
        val lobby = lobbyByClanId[clan.id]
            ?: return ClanBattleOperation.Rejected(ClanBattleRejection.LOBBY_NOT_FOUND)

        pruneUnavailableLobbyParticipants(lobby)
        val roster = lobby.participantsByClan[clan.id].orEmpty()
        val minimum = plugin.configService.battles.minimumOnlineMembers.coerceAtLeast(1)
        if (roster.size < minimum) {
            lobby.readyClanIds.remove(clan.id)
            cancelCountdown(lobby)
            notifyLobbyChanged(lobby)
            return ClanBattleOperation.Rejected(ClanBattleRejection.NOT_ENOUGH_SELECTED)
        }

        if (clan.id in lobby.readyClanIds) {
            lobby.readyClanIds.remove(clan.id)
            cancelCountdown(lobby)
            actor.sendMessage(plugin.configService.formatMessage(actor, "&#FFD700⌚ &fГотовность вашей стороны снята."))
        } else {
            lobby.readyClanIds.add(clan.id)
            actor.sendMessage(plugin.configService.formatMessage(actor, "&#5EFD7D✔ &fВаша сторона готова к бою."))
            if (lobby.challengerClanId in lobby.readyClanIds && lobby.defenderClanId in lobby.readyClanIds) {
                startCountdown(lobby)
            }
        }
        notifyLobbyChanged(lobby)
        return ClanBattleOperation.Success
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBattleDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player
            ?: (event.damager as? Projectile)?.shooter as? Player
            ?: return
        val victim = event.entity as? Player ?: return
        val attackerBattle = activeByPlayer[attacker.uniqueId]
        val victimBattle = activeByPlayer[victim.uniqueId]
        val active = attackerBattle ?: victimBattle ?: return
        if (attackerBattle == null || victimBattle == null || attackerBattle !== victimBattle) {
            event.isCancelled = true
            return
        }

        val attackerClan = plugin.clanService.getClanUser(attacker) ?: run {
            event.isCancelled = true
            return
        }
        val victimClan = plugin.clanService.getClanUser(victim) ?: run {
            event.isCancelled = true
            return
        }
        if (!active.battle.containsClan(attackerClan.id) || !active.battle.containsClan(victimClan.id)) {
            event.isCancelled = true
            return
        }
        if (attackerClan.id == victimClan.id) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun recordBattleDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player
            ?: (event.damager as? Projectile)?.shooter as? Player
            ?: return
        val victim = event.entity as? Player ?: return
        val active = activeByPlayer[attacker.uniqueId] ?: return
        if (activeByPlayer[victim.uniqueId] !== active) return
        val attackerClan = plugin.clanService.getClanUser(attacker) ?: return
        val victimClan = plugin.clanService.getClanUser(victim) ?: return
        if (attackerClan.id == victimClan.id) return
        if (!active.battle.containsClan(attackerClan.id) || !active.battle.containsClan(victimClan.id)) return

        val damage = event.finalDamage.coerceAtLeast(0.0).roundToLong()
        if (damage > 0L) {
            active.damageByClan.merge(attackerClan.id, damage, Long::plus)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBattleDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val active = activeByPlayer[victim.uniqueId] ?: return
        event.keepInventory = true
        event.drops.clear()
        event.keepLevel = true
        event.droppedExp = 0

        val killer = victim.killer ?: return
        if (activeByPlayer[killer.uniqueId] !== active) return
        val killerClan = plugin.clanService.getClanUser(killer) ?: return
        val victimClan = plugin.clanService.getClanUser(victim) ?: return
        if (!active.battle.containsClan(killerClan.id) || !active.battle.containsClan(victimClan.id)) return
        if (killerClan.id == victimClan.id) return

        if (killerClan.id == active.battle.challengerClanId) {
            active.battle.challengerScore++
        } else if (killerClan.id == active.battle.defenderClanId) {
            active.battle.defenderScore++
        } else {
            return
        }
        plugin.clanQuestService.recordBattleKill(killerClan, killer)
        val points = plugin.configService.battles.pointsPerKill
        if (points > 0L) plugin.clanPointsService.award(killerClan, points, ClanPointsSource.BATTLE)
        plugin.clanService.getClanByName(active.battle.challengerClanId)?.let(::notifyGui)
        plugin.clanService.getClanByName(active.battle.defenderClanId)?.let(::notifyGui)

        if (active.battle.challengerScore >= plugin.configService.battles.scoreToWin.coerceAtLeast(1)) {
            finish(active.battle.id, active.battle.challengerClanId, ClanBattleEndReason.SCORE_LIMIT)
        } else if (active.battle.defenderScore >= plugin.configService.battles.scoreToWin.coerceAtLeast(1)) {
            finish(active.battle.id, active.battle.defenderClanId, ClanBattleEndReason.SCORE_LIMIT)
        }
    }

    @EventHandler
    fun onBattleRespawn(event: PlayerRespawnEvent) {
        pendingRespawnLocations.remove(event.player.uniqueId)?.let {
            event.respawnLocation = it
            completeReturn(event.player)
            return
        }
        val active = activeByPlayer[event.player.uniqueId] ?: return
        val clan = plugin.clanService.getClanUser(event.player) ?: return
        val spawn = arenaLocation(active.battle.arenaId, clan.id == active.battle.challengerClanId) ?: return
        event.respawnLocation = spawn
    }

    @EventHandler
    fun onBattleJoin(event: PlayerJoinEvent) {
        val player = event.player
        val active = activeByPlayer[player.uniqueId]
        val returnLocation = if (active == null) {
            pendingReturnLocations[player.uniqueId] ?: loadReturnLocation(player)
        } else {
            null
        }
        val destination = when {
            returnLocation != null -> returnLocation
            active != null -> {
                val clanId = participantClanId(active, player.uniqueId) ?: return
                arenaLocation(active.battle.arenaId, clanId == active.battle.challengerClanId) ?: return
            }
            else -> return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            if (player.teleport(destination) && returnLocation != null) {
                completeReturn(player)
            }
        })
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBattleMove(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) return
        val active = activeByPlayer[event.player.uniqueId] ?: return
        val destination = event.to ?: return
        if (!isInsideArena(active, destination)) event.to = event.from
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBattleTeleport(event: PlayerTeleportEvent) {
        val active = activeByPlayer[event.player.uniqueId] ?: return
        if (!isInsideArena(active, event.to)) event.isCancelled = true
    }

    @EventHandler
    fun onBattleQuit(event: PlayerQuitEvent) {
        pendingRespawnLocations.remove(event.player.uniqueId)
        removeLobbyParticipant(event.player)

        val active = activeByPlayer[event.player.uniqueId] ?: return
        val clanId = participantClanId(active, event.player.uniqueId) ?: return
        val members = active.participantsByClan[clanId].orEmpty()
        val someoneOnline = members.any { uuid -> Bukkit.getPlayer(uuid)?.isOnline == true && uuid != event.player.uniqueId }
        if (!someoneOnline) {
            val winnerId = if (clanId == active.battle.challengerClanId) active.battle.defenderClanId else active.battle.challengerClanId
            finish(active.battle.id, winnerId, ClanBattleEndReason.FORFEIT)
        }
    }

    fun handleMemberRemoved(clan: Clan, memberUuid: UUID) {
        lobbyByClanId[clan.id]?.let { lobby ->
            val roster = lobby.participantsByClan[clan.id]
            if (roster?.remove(memberUuid) == true) {
                lobby.readyClanIds.remove(clan.id)
                cancelCountdown(lobby)
                notifyLobbyChanged(lobby)
            }
        }

        val active = activeByClanId[clan.id] ?: return
        if (!active.participantsByClan[clan.id].orEmpty().contains(memberUuid)) return
        activeByPlayer.remove(memberUuid, active)
        active.participantsByClan[clan.id]?.remove(memberUuid)
        active.returnLocations.remove(memberUuid)?.let { returnLocation ->
            val player = Bukkit.getPlayer(memberUuid)?.takeIf(Player::isOnline)
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.teleport(returnLocation)) {
                        completeReturn(player)
                    } else {
                        queueReturn(memberUuid, returnLocation, active.battle.arenaId)
                    }
                })
            } else {
                queueReturn(memberUuid, returnLocation, active.battle.arenaId)
            }
        }
        val remainingOnline = active.participantsByClan[clan.id].orEmpty()
            .any { Bukkit.getPlayer(it)?.isOnline == true }
        if (!remainingOnline) {
            val winnerId = if (clan.id == active.battle.challengerClanId) active.battle.defenderClanId else active.battle.challengerClanId
            finish(active.battle.id, winnerId, ClanBattleEndReason.FORFEIT)
        }
    }

    private fun startCountdown(lobby: ClanBattleLobby) {
        if (lobby.countdownTask != null) return
        pruneUnavailableLobbyParticipants(lobby)
        if (lobby.challengerClanId !in lobby.readyClanIds || lobby.defenderClanId !in lobby.readyClanIds) return
        val minimum = plugin.configService.battles.minimumOnlineMembers.coerceAtLeast(1)
        if (lobby.participantsByClan[lobby.challengerClanId].orEmpty().size < minimum ||
            lobby.participantsByClan[lobby.defenderClanId].orEmpty().size < minimum
        ) {
            lobby.readyClanIds.clear()
            return
        }

        lobby.countdownEndsAt = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L
        notifyLobbyMessage(lobby, "&#FC3737⚔ &fОбе стороны готовы. Перемещение на арену через &e$COUNTDOWN_SECONDS секунд&f.")
        lobby.countdownTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            lobby.countdownTask = null
            lobby.countdownEndsAt = 0L
            startLobbyBattle(lobby.id)
        }, COUNTDOWN_SECONDS * 20L)
    }

    private fun startLobbyBattle(lobbyId: UUID) {
        val lobby = lobbiesById[lobbyId] ?: return
        val config = plugin.configService.battles
        if (!config.enabled) {
            cancelLobby(lobby, "&#FC3737✖ &fСбор состава отменён: модуль битв отключён.")
            return
        }

        pruneUnavailableLobbyParticipants(lobby)
        val minimum = config.minimumOnlineMembers.coerceAtLeast(1)
        val challengerIds = lobby.participantsByClan[lobby.challengerClanId].orEmpty().toList()
        val defenderIds = lobby.participantsByClan[lobby.defenderClanId].orEmpty().toList()
        if (lobby.challengerClanId !in lobby.readyClanIds || lobby.defenderClanId !in lobby.readyClanIds ||
            challengerIds.size < minimum || defenderIds.size < minimum
        ) {
            lobby.readyClanIds.clear()
            notifyLobbyMessage(lobby, "&#FFD700⌚ &fСтарт отменён: состав одной из сторон изменился. Подтвердите READY заново.")
            notifyLobbyChanged(lobby)
            return
        }

        val challenger = plugin.clanService.getClanByName(lobby.challengerClanId) ?: run {
            cancelLobby(lobby, "&#FC3737✖ &fСбор состава отменён: клан-соперник больше недоступен.")
            return
        }
        val defender = plugin.clanService.getClanByName(lobby.defenderClanId) ?: run {
            cancelLobby(lobby, "&#FC3737✖ &fСбор состава отменён: клан-соперник больше недоступен.")
            return
        }
        if (activeByClanId[challenger.id] != null || activeByClanId[defender.id] != null) {
            cancelLobby(lobby, "&#FC3737✖ &fСбор состава отменён: один из кланов уже участвует в другом бою.")
            return
        }

        val challengerParticipants = challengerIds.mapNotNull(Bukkit::getPlayer).filter(Player::isOnline)
        val defenderParticipants = defenderIds.mapNotNull(Bukkit::getPlayer).filter(Player::isOnline)
        if (challengerParticipants.size != challengerIds.size || defenderParticipants.size != defenderIds.size) {
            lobby.readyClanIds.clear()
            notifyLobbyMessage(lobby, "&#FFD700⌚ &fСтарт отменён: один из выбранных игроков вышел с сервера. Подтвердите READY заново.")
            notifyLobbyChanged(lobby)
            return
        }

        val arena = arenaLocations(lobby.arenaId) ?: run {
            cancelLobby(lobby, "&#FC3737✖ &fАрена стала недоступна. Сообщите администратору.")
            return
        }
        val now = System.currentTimeMillis()
        val battle = ClanBattle(
            id = lobby.id,
            challengerClanId = challenger.id,
            defenderClanId = defender.id,
            arenaId = lobby.arenaId,
            startedAt = now,
            endsAt = now + config.battleDurationSeconds.coerceAtLeast(1L) * 1000L
        )
        val startEvent = ClanBattleStartEvent(battle.copy(), challenger, defender)
        Bukkit.getPluginManager().callEvent(startEvent)
        if (startEvent.isCancelled) {
            lobby.readyClanIds.clear()
            notifyLobbyMessage(lobby, "&#FFD700⌚ &fСтарт боя отменён другим плагином. READY сторон сброшен.")
            notifyLobbyChanged(lobby)
            return
        }

        val participants = mutableMapOf(
            challenger.id to challengerIds.toMutableSet(),
            defender.id to defenderIds.toMutableSet()
        )
        val returnLocations = (challengerParticipants + defenderParticipants)
            .associateTo(mutableMapOf()) { it.uniqueId to it.location.clone() }
        val active = ActiveClanBattle(battle, participants, returnLocations)
        if (activeByArenaId.putIfAbsent(battle.arenaId, active) != null) {
            cancelLobby(lobby, "&#FC3737✖ &fАрена занята другим боем. Сбор состава отменён.")
            return
        }

        (challengerParticipants + defenderParticipants).forEach { player ->
            returnLocations[player.uniqueId]?.let { storeReturnLocation(player, it) }
        }
        val teleported = mutableListOf<Player>()
        val destinations = challengerParticipants.map { it to arena.second } + defenderParticipants.map { it to arena.third }
        val teleportSucceeded = destinations.all { (player, destination) ->
            player.teleport(destination.clone()).also { succeeded -> if (succeeded) teleported += player }
        }
        if (!teleportSucceeded) {
            var rollbackFailed = false
            (challengerParticipants + defenderParticipants).forEach { player ->
                val returnLocation = returnLocations[player.uniqueId] ?: return@forEach
                if (player !in teleported || player.teleport(returnLocation)) {
                    clearReturnLocation(player)
                } else {
                    queueReturn(player.uniqueId, returnLocation, battle.arenaId)
                    rollbackFailed = true
                }
            }
            if (rollbackFailed) quarantinedArenaIds += battle.arenaId
            activeByArenaId.remove(battle.arenaId, active)
            cancelLobby(lobby, "&#FC3737✖ &fНе удалось безопасно переместить весь состав на арену. Бой отменён.")
            return
        }

        removeLobby(lobby)
        activeByBattleId[battle.id] = active
        activeByClanId[challenger.id] = active
        activeByClanId[defender.id] = active
        participants.values.flatten().forEach { activeByPlayer[it] = active }

        challengerParticipants.firstOrNull()?.let { recordParticipation(challenger, it) }
        defenderParticipants.firstOrNull()?.let { recordParticipation(defender, it) }
        notifyClan(challenger, plugin.configService.messages.battles.started, battlePlaceholders(battle, challenger, defender))
        notifyClan(defender, plugin.configService.messages.battles.started, battlePlaceholders(battle, challenger, defender))
        notifyGui(challenger)
        notifyGui(defender)

        active.timerTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            finish(battle.id, null, ClanBattleEndReason.TIME_LIMIT)
        }, config.battleDurationSeconds.coerceAtLeast(1L) * 20L)
    }

    private fun finish(battleId: UUID, forcedWinnerId: String?, reason: ClanBattleEndReason) {
        val active = activeByBattleId.remove(battleId) ?: return
        active.timerTask?.cancel()
        activeByClanId.remove(active.battle.challengerClanId, active)
        activeByClanId.remove(active.battle.defenderClanId, active)
        activeByArenaId.remove(active.battle.arenaId, active)
        active.participantsByClan.values.flatten().forEach { activeByPlayer.remove(it, active) }

        val challenger = plugin.clanService.getClanByName(active.battle.challengerClanId)
        val defender = plugin.clanService.getClanByName(active.battle.defenderClanId)
        val winnerId = forcedWinnerId ?: when {
            active.battle.challengerScore > active.battle.defenderScore -> active.battle.challengerClanId
            active.battle.defenderScore > active.battle.challengerScore -> active.battle.defenderClanId
            else -> null
        }
        val winner = when (winnerId) {
            challenger?.id -> challenger
            defender?.id -> defender
            else -> null
        }
        val loser = when {
            winner === challenger -> defender
            winner === defender -> challenger
            else -> null
        }

        if (reason != ClanBattleEndReason.SERVER_SHUTDOWN) {
            recordDamage(active, challenger)
            recordDamage(active, defender)
            if (winner != null && loser != null) {
                if (winner.battleWins < Int.MAX_VALUE) winner.battleWins++
                if (loser.battleLosses < Int.MAX_VALUE) loser.battleLosses++
                val ratingWin = plugin.configService.battles.ratingWin.coerceAtLeast(0)
                val ratingLoss = plugin.configService.battles.ratingLoss.coerceAtLeast(0)
                winner.mmr = (winner.mmr.toLong() + ratingWin).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                loser.mmr = (loser.mmr - ratingLoss).coerceAtLeast(0)
                plugin.clanQuestService.recordBattleWin(winner, participantFor(active, winner.id))
                if (plugin.configService.battles.pointsWin > 0L) {
                    plugin.clanPointsService.award(winner, plugin.configService.battles.pointsWin, ClanPointsSource.BATTLE)
                }
                plugin.clanService.saveClan(winner)
                plugin.clanService.saveClan(loser)
            } else {
                challenger?.let(plugin.clanService::saveClan)
                defender?.let(plugin.clanService::saveClan)
            }
        }

        active.returnLocations.forEach { (uuid, location) ->
            val player = Bukkit.getPlayer(uuid)?.takeIf(Player::isOnline)
            when {
                player == null -> queueReturn(uuid, location, active.battle.arenaId)
                player.isDead -> {
                    pendingRespawnLocations[uuid] = location
                    queueReturn(uuid, location, active.battle.arenaId)
                }
                player.teleport(location) -> clearReturnLocation(player)
                else -> queueReturn(uuid, location, active.battle.arenaId)
            }
        }
        val endEvent = ClanBattleEndEvent(active.battle.copy(), winner, loser, reason)
        Bukkit.getPluginManager().callEvent(endEvent)
        val placeholders = mapOf(
            "challenger" to (challenger?.name ?: active.battle.challengerClanId),
            "defender" to (defender?.name ?: active.battle.defenderClanId),
            "winner" to (winner?.name ?: "Ничья"),
            "challenger_score" to active.battle.challengerScore.toString(),
            "defender_score" to active.battle.defenderScore.toString(),
            "reason" to reason.name
        )
        challenger?.let { notifyClan(it, plugin.configService.messages.battles.finished, placeholders) }
        defender?.let { notifyClan(it, plugin.configService.messages.battles.finished, placeholders) }
        challenger?.let(::notifyGui)
        defender?.let(::notifyGui)
    }

    private fun recordDamage(active: ActiveClanBattle, clan: Clan?) {
        if (clan == null) return
        val amount = active.damageByClan[clan.id] ?: return
        if (amount <= 0L) return
        plugin.clanQuestService.recordBattleDamage(clan, participantFor(active, clan.id), amount)
    }

    private fun recordParticipation(clan: Clan, actor: Player) {
        plugin.clanQuestService.recordBattleParticipation(clan, actor)
    }

    private fun participantClanId(active: ActiveClanBattle, playerUuid: UUID): String? =
        active.participantsByClan.entries.firstOrNull { playerUuid in it.value }?.key

    private fun participantFor(active: ActiveClanBattle, clanId: String): Player? =
        active.participantsByClan[clanId].orEmpty()
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .firstOrNull(Player::isOnline)

    private fun eligibleOnlineMembers(clan: Clan): List<Player> = clan.users
        .mapNotNull { Bukkit.getPlayer(it.uuid) }
        .filter(Player::isOnline)
        .filter { activeByPlayer[it.uniqueId] == null }
        .filterNot(::hasStoredReturnLocation)
        .sortedBy { it.name.lowercase() }

    private fun isEligibleLobbyPlayer(player: Player, clan: Clan): Boolean =
        player.isOnline &&
            clan.getMember(player.uniqueId) != null &&
            activeByPlayer[player.uniqueId] == null &&
            !hasStoredReturnLocation(player)

    private fun pruneUnavailableLobbyParticipants(lobby: ClanBattleLobby) {
        lobby.participantsByClan.forEach { (clanId, roster) ->
            val clan = plugin.clanService.getClanByName(clanId)
            val removed = roster.removeIf { uuid ->
                val player = Bukkit.getPlayer(uuid)
                player == null || !player.isOnline || clan == null || !isEligibleLobbyPlayer(player, clan)
            }
            if (removed) lobby.readyClanIds.remove(clanId)
        }
        if (lobby.challengerClanId !in lobby.readyClanIds || lobby.defenderClanId !in lobby.readyClanIds) {
            cancelCountdown(lobby)
        }
    }

    private fun removeLobbyParticipant(player: Player) {
        val clan = plugin.clanService.getClanUser(player) ?: return
        val lobby = lobbyByClanId[clan.id] ?: return
        val roster = lobby.participantsByClan[clan.id] ?: return
        if (!roster.remove(player.uniqueId)) return
        lobby.readyClanIds.remove(clan.id)
        cancelCountdown(lobby)
        notifyLobbyChanged(lobby)
    }

    private fun resolveArena(): Triple<String, Location, Location>? = plugin.configService.battles.arenas.entries
        .firstNotNullOfOrNull { (id, arena) ->
            if (activeByArenaId.containsKey(id) || lobbyByArenaId.containsKey(id) || id in quarantinedArenaIds) {
                return@firstNotNullOfOrNull null
            }
            val world = Bukkit.getWorld(arena.world.trim()) ?: return@firstNotNullOfOrNull null
            if (!isValidArena(arena)) return@firstNotNullOfOrNull null
            Triple(id, toLocation(world, arena.challenger), toLocation(world, arena.defender))
        }

    private fun arenaLocations(arenaId: String): Triple<String, Location, Location>? {
        val arena = plugin.configService.battles.arenas[arenaId] ?: return null
        val world = Bukkit.getWorld(arena.world.trim()) ?: return null
        if (!isValidArena(arena)) return null
        return Triple(arenaId, toLocation(world, arena.challenger), toLocation(world, arena.defender))
    }

    private fun arenaLocation(arenaId: String, challengerSide: Boolean): Location? {
        val arena = plugin.configService.battles.arenas[arenaId] ?: return null
        val world = Bukkit.getWorld(arena.world.trim()) ?: return null
        if (!isValidArena(arena)) return null
        return toLocation(world, if (challengerSide) arena.challenger else arena.defender)
    }

    private fun toLocation(world: World, spawn: ClanBattleSpawnConfig): Location =
        Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)

    private fun isValidArena(arena: ClanBattleArenaConfig): Boolean =
        arena.world.isNotBlank() &&
            arena.radius.isFinite() && arena.radius > 0.0 &&
            isValidSpawn(arena.challenger) && isValidSpawn(arena.defender) &&
            spawnDistanceFromCentreSquared(arena) <= arena.radius * arena.radius

    private fun isValidSpawn(spawn: ClanBattleSpawnConfig): Boolean =
        spawn.x.isFinite() && spawn.y.isFinite() && spawn.z.isFinite() &&
            spawn.yaw.isFinite() && spawn.pitch.isFinite()

    private fun spawnDistanceFromCentreSquared(arena: ClanBattleArenaConfig): Double {
        val x = (arena.challenger.x - arena.defender.x) / 2.0
        val y = (arena.challenger.y - arena.defender.y) / 2.0
        val z = (arena.challenger.z - arena.defender.z) / 2.0
        return x * x + y * y + z * z
    }

    private fun isInsideArena(active: ActiveClanBattle, location: Location): Boolean {
        val arena = plugin.configService.battles.arenas[active.battle.arenaId] ?: return false
        val world = Bukkit.getWorld(arena.world.trim()) ?: return false
        if (location.world?.uid != world.uid || !isValidArena(arena)) return false
        val centreX = (arena.challenger.x + arena.defender.x) / 2.0
        val centreY = (arena.challenger.y + arena.defender.y) / 2.0
        val centreZ = (arena.challenger.z + arena.defender.z) / 2.0
        val x = location.x - centreX
        val y = location.y - centreY
        val z = location.z - centreZ
        return x * x + y * y + z * z <= arena.radius * arena.radius
    }

    private fun storeReturnLocation(player: Player, location: Location) {
        val world = location.world ?: return
        val value = listOf(
            world.uid,
            location.x,
            location.y,
            location.z,
            location.yaw,
            location.pitch
        ).joinToString(";")
        player.persistentDataContainer.set(returnLocationKey, PersistentDataType.STRING, value)
    }

    private fun loadReturnLocation(player: Player): Location? {
        val value = player.persistentDataContainer.get(returnLocationKey, PersistentDataType.STRING) ?: return null
        val parts = value.split(';')
        if (parts.size != RETURN_LOCATION_PARTS) return null
        val worldId = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
        val world = Bukkit.getWorld(worldId) ?: return null
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        val yaw = parts[4].toFloatOrNull() ?: return null
        val pitch = parts[5].toFloatOrNull() ?: return null
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !yaw.isFinite() || !pitch.isFinite()) return null
        return Location(world, x, y, z, yaw, pitch)
    }

    private fun clearReturnLocation(player: Player) {
        player.persistentDataContainer.remove(returnLocationKey)
    }

    private fun hasStoredReturnLocation(player: Player): Boolean =
        player.persistentDataContainer.has(returnLocationKey, PersistentDataType.STRING)

    private fun queueReturn(playerUuid: UUID, location: Location, arenaId: String) {
        pendingReturnLocations[playerUuid] = location
        pendingArenaByPlayer[playerUuid] = arenaId
        quarantinedArenaIds += arenaId
    }

    private fun completeReturn(player: Player) {
        pendingReturnLocations.remove(player.uniqueId)
        clearReturnLocation(player)
        val arenaId = pendingArenaByPlayer.remove(player.uniqueId) ?: return
        if (pendingArenaByPlayer.values.none { it == arenaId }) quarantinedArenaIds.remove(arenaId)
    }

    private fun isClanBusy(clanId: String): Boolean =
        activeByClanId.containsKey(clanId) || lobbyByClanId.containsKey(clanId)

    private fun ClanBattleLobby.snapshot(): ClanBattleLobbySnapshot = ClanBattleLobbySnapshot(
        id = id,
        challengerClanId = challengerClanId,
        defenderClanId = defenderClanId,
        arenaId = arenaId,
        participantsByClan = participantsByClan.mapValues { (_, value) -> value.toSet() },
        readyClanIds = readyClanIds.toSet(),
        countdownEndsAt = countdownEndsAt
    )

    private fun cancelCountdown(lobby: ClanBattleLobby) {
        lobby.countdownTask?.cancel()
        lobby.countdownTask = null
        lobby.countdownEndsAt = 0L
    }

    private fun removeLobby(lobby: ClanBattleLobby) {
        lobby.countdownTask?.cancel()
        lobby.expiryTask?.cancel()
        lobby.countdownTask = null
        lobby.expiryTask = null
        lobbiesById.remove(lobby.id, lobby)
        lobbyByClanId.remove(lobby.challengerClanId, lobby)
        lobbyByClanId.remove(lobby.defenderClanId, lobby)
        lobbyByArenaId.remove(lobby.arenaId, lobby)
    }

    private fun cancelLobby(lobby: ClanBattleLobby, message: String) {
        notifyLobbyMessage(lobby, message)
        removeLobby(lobby)
        notifyLobbyChanged(lobby)
    }

    private fun expireLobby(id: UUID) {
        val lobby = lobbiesById[id] ?: return
        if (lobby.expiresAt > System.currentTimeMillis()) return
        cancelLobby(lobby, "&#FFD700⌛ &fВремя на сбор состава истекло. Боевой вызов отменён.")
    }

    private fun notifyLobbyChanged(lobby: ClanBattleLobby) {
        plugin.clanService.getClanByName(lobby.challengerClanId)?.let(::notifyGui)
        plugin.clanService.getClanByName(lobby.defenderClanId)?.let(::notifyGui)
    }

    private fun notifyLobbyMessage(lobby: ClanBattleLobby, text: String) {
        val clans = listOfNotNull(
            plugin.clanService.getClanByName(lobby.challengerClanId),
            plugin.clanService.getClanByName(lobby.defenderClanId)
        ).distinctBy(Clan::id)
        clans.forEach { clan ->
            clan.users.mapNotNull { Bukkit.getPlayer(it.uuid) }.forEach { player ->
                player.sendMessage(plugin.configService.formatMessage(player, text))
            }
        }
    }

    private fun notifyClan(clan: Clan, actions: List<ua.inventorytype.pnclans.api.Action>, placeholders: Map<String, String>) {
        clan.users.mapNotNull { Bukkit.getPlayer(it.uuid) }.forEach { player ->
            plugin.configService.send(player, actions, placeholders)
        }
    }

    private fun notifyGui(clan: Clan) {
        clan.users.forEach { plugin.clanService.notifyClanUpdated(it.uuid) }
    }

    private fun battlePlaceholders(battle: ClanBattle, challenger: Clan, defender: Clan): Map<String, String> = mapOf(
        "challenger" to challenger.name,
        "defender" to defender.name,
        "challenger_score" to battle.challengerScore.toString(),
        "defender_score" to battle.defenderScore.toString(),
        "seconds" to ((battle.endsAt - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toString()
    )

    private fun expireChallenge(id: UUID) {
        val challenge = challenges[id] ?: return
        if (challenge.expiresAt > System.currentTimeMillis()) return
        challenges.remove(id)
        plugin.clanService.getClanByName(challenge.challengerClanId)?.let {
            notifyClan(it, plugin.configService.messages.battles.challengeExpired, emptyMap())
            notifyGui(it)
        }
        plugin.clanService.getClanByName(challenge.defenderClanId)?.let {
            notifyClan(it, plugin.configService.messages.battles.challengeExpired, emptyMap())
            notifyGui(it)
        }
    }

    private companion object {
        const val RETURN_LOCATION_PARTS = 6
        const val COUNTDOWN_SECONDS = 5L
    }
}
