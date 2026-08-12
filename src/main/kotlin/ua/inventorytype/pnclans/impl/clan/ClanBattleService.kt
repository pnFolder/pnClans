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
import org.bukkit.scheduler.BukkitTask
import org.bukkit.persistence.PersistentDataType
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
    CANCELLED_BY_EVENT
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

private data class ActiveClanBattle(
    val battle: ClanBattle,
    val participantsByClan: MutableMap<String, MutableSet<UUID>>,
    val returnLocations: MutableMap<UUID, Location>,
    val damageByClan: MutableMap<String, Long> = mutableMapOf(),
    var timerTask: BukkitTask? = null
)

/** Handles challenge flow, arena isolation, scoring, rewards, and battle lifecycle. */
internal class ClanBattleService(private val plugin: BukkitPlugin) : Listener {
    private val activeByBattleId = ConcurrentHashMap<UUID, ActiveClanBattle>()
    private val activeByClanId = ConcurrentHashMap<String, ActiveClanBattle>()
    private val activeByPlayer = ConcurrentHashMap<UUID, ActiveClanBattle>()
    private val activeByArenaId = ConcurrentHashMap<String, ActiveClanBattle>()
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
        challenges.clear()
        pendingRespawnLocations.clear()
        pendingReturnLocations.clear()
        pendingArenaByPlayer.clear()
    }

    fun battleForClan(clan: Clan): ClanBattle? = activeByClanId[clan.id]?.battle?.copy()

    fun activeBattleCount(): Int = activeByBattleId.size

    fun prepareReload(): Boolean {
        return activeByBattleId.isEmpty()
    }

    fun completeReload() {
        challenges.clear()
    }

    fun hasActiveBattle(clan: Clan): Boolean = activeByClanId.containsKey(clan.id)

    /** Resolves an active battle as a forfeit and removes pending challenges before clan deletion. */
    fun prepareClanRemoval(clan: Clan) {
        challenges.values.filter { it.challengerClanId == clan.id || it.defenderClanId == clan.id }
            .forEach { challenges.remove(it.id, it) }
        val active = activeByClanId[clan.id] ?: return
        val winnerId = if (clan.id == active.battle.challengerClanId) {
            active.battle.defenderClanId
        } else {
            active.battle.challengerClanId
        }
        finish(active.battle.id, winnerId, ClanBattleEndReason.FORFEIT)
    }

    fun stopByAdmin(clan: Clan, winner: Clan? = null): Boolean {
        val active = activeByClanId[clan.id] ?: return false
        if (winner != null && !active.battle.containsClan(winner.id)) return false
        finish(active.battle.id, winner?.id, ClanBattleEndReason.ADMIN_STOP)
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
                    activeByClanId[it.id] == null &&
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
        if (activeByClanId[challenger.id] != null || activeByClanId[defender.id] != null) {
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

    fun acceptChallenge(actor: Player, challengeId: UUID): ClanBattleOperation {
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
        if (activeByClanId[challenger.id] != null || activeByClanId[defender.id] != null) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.CLAN_BUSY)
        }

        val config = plugin.configService.battles
        val challengerParticipants = onlineParticipants(challenger)
        val defenderParticipants = onlineParticipants(defender)
        if (challengerParticipants.size < config.minimumOnlineMembers.coerceAtLeast(1) ||
            defenderParticipants.size < config.minimumOnlineMembers.coerceAtLeast(1)
        ) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.NOT_ENOUGH_ONLINE)
        }

        val arena = resolveArena() ?: return ClanBattleOperation.Rejected(ClanBattleRejection.ARENA_UNAVAILABLE)
        val now = System.currentTimeMillis()
        val battle = ClanBattle(
            id = challenge.id,
            challengerClanId = challenger.id,
            defenderClanId = defender.id,
            arenaId = arena.first,
            startedAt = now,
            endsAt = now + config.battleDurationSeconds.coerceAtLeast(1L) * 1000L
        )
        val startEvent = ClanBattleStartEvent(battle.copy(), challenger, defender)
        Bukkit.getPluginManager().callEvent(startEvent)
        if (startEvent.isCancelled) return ClanBattleOperation.Rejected(ClanBattleRejection.CANCELLED_BY_EVENT)

        val participants = mutableMapOf(
            challenger.id to challengerParticipants.mapTo(mutableSetOf(), Player::getUniqueId),
            defender.id to defenderParticipants.mapTo(mutableSetOf(), Player::getUniqueId)
        )
        val returnLocations = (challengerParticipants + defenderParticipants)
            .associateTo(mutableMapOf()) { it.uniqueId to it.location.clone() }
        val active = ActiveClanBattle(battle, participants, returnLocations)
        if (activeByArenaId.putIfAbsent(battle.arenaId, active) != null) {
            return ClanBattleOperation.Rejected(ClanBattleRejection.ARENA_UNAVAILABLE)
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
            return ClanBattleOperation.Rejected(ClanBattleRejection.ARENA_UNAVAILABLE)
        }

        challenges.remove(challengeId)
        activeByBattleId[battle.id] = active
        activeByClanId[challenger.id] = active
        activeByClanId[defender.id] = active
        participants.values.flatten().forEach { activeByPlayer[it] = active }

        recordParticipation(challenger, challengerParticipants.first())
        recordParticipation(defender, defenderParticipants.first())
        notifyClan(challenger, plugin.configService.messages.battles.started, battlePlaceholders(battle, challenger, defender))
        notifyClan(defender, plugin.configService.messages.battles.started, battlePlaceholders(battle, challenger, defender))
        notifyGui(challenger)
        notifyGui(defender)

        active.timerTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            finish(battle.id, null, ClanBattleEndReason.TIME_LIMIT)
        }, config.battleDurationSeconds.coerceAtLeast(1L) * 20L)
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

    private fun onlineParticipants(clan: Clan): List<Player> = clan.users
        .mapNotNull { Bukkit.getPlayer(it.uuid) }
        .filter(Player::isOnline)
        .filterNot(::hasStoredReturnLocation)
        .sortedBy { it.name.lowercase() }
        .take(plugin.configService.battles.maximumParticipants.coerceAtLeast(1))

    private fun resolveArena(): Triple<String, Location, Location>? = plugin.configService.battles.arenas.entries
        .firstNotNullOfOrNull { (id, arena) ->
            if (activeByArenaId.containsKey(id) || id in quarantinedArenaIds) return@firstNotNullOfOrNull null
            val world = Bukkit.getWorld(arena.world.trim()) ?: return@firstNotNullOfOrNull null
            if (!isValidArena(arena)) return@firstNotNullOfOrNull null
            Triple(id, toLocation(world, arena.challenger), toLocation(world, arena.defender))
        }

    private fun arenaLocation(arenaId: String, challengerSide: Boolean): Location? {
        val arena = plugin.configService.battles.arenas[arenaId] ?: return null
        val world = Bukkit.getWorld(arena.world.trim()) ?: return null
        if (!isValidArena(arena)) return null
        return toLocation(world, if (challengerSide) arena.challenger else arena.defender)
    }

    private fun toLocation(world: World, spawn: ClanBattleSpawnConfig): Location {
        return Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)
    }

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
    }
}
