package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import ua.inventorytype.pnclans.api.battle.ClanBattle
import ua.inventorytype.pnclans.impl.clan.ClanBattleLobbySnapshot
import ua.inventorytype.pnclans.impl.clan.ClanBattleOperation
import ua.inventorytype.pnclans.impl.clan.ClanBattleRejection
import ua.inventorytype.pnclans.impl.clan.ClanBattleService
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.abs
import kotlin.math.ceil

/** Battle headquarters driven entirely by battles.yml display and behavior settings. */
class ClanBattlesUX(
    clanService: ClanService,
    selectedPage: Int = 0
) : BaseGui(clanService) {
    private val config = clanService.plugin.configService.battles
    private val display = config.display
    private val battleService: ClanBattleService = clanService.plugin.clanBattleService
    private val guiRows = config.rows.coerceIn(1, 6)
    private val inventorySize = guiRows * 9
    private val reservedSlots = setOf(
        display.headerSlot,
        display.ownSlot,
        display.incomingSlot,
        display.backSlot,
        display.previousSlot,
        display.pageSlot,
        display.nextSlot,
        display.refreshSlot
    )
    private val opponentSlots = display.opponentSlots.filter { it in 0 until inventorySize && it !in reservedSlots }.distinct()
    private var page = selectedPage.coerceAtLeast(0)
    private var viewer: Player? = null

    init {
        title(config.title)
        rows(guiRows)
        background(clanService.plugin.configService.menus.background)
        addHeader()
        addOwnStatus()
        addIncomingChallenge()
        addOpponents()
        addControls()
    }

    override fun open(player: Player) {
        if (!config.enabled) {
            clanService.plugin.configService.send(player, clanService.plugin.configService.messages.battles.disabled)
            return
        }
        if (clanService.getClanUser(player) == null) {
            NoClanUX(clanService).open(player)
            return
        }
        viewer = player
        page = page.coerceAtMost(pageCount() - 1)
        super.open(player)
    }

    private fun addHeader() {
        if (!valid(display.headerSlot)) return
        slot(display.headerSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.headerMaterial, Material.CROSSBOW)) { player ->
                val clan = this@ClanBattlesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val placeholders = this@ClanBattlesUX.statePlaceholders(player, clan, this@ClanBattlesUX.battleService.battleForClan(clan), this@ClanBattlesUX.battleService.lobbyForClan(clan))
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.headerName, placeholders))
                lore(this@ClanBattlesUX.display.headerLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                glow(true)
                null
            }
        }
    }

    private fun addOwnStatus() {
        if (!valid(display.ownSlot)) return
        slot(display.ownSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.ownMaterial, Material.SHIELD)) { player ->
                val clan = this@ClanBattlesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val battle = this@ClanBattlesUX.battleService.battleForClan(clan)
                val lobby = this@ClanBattlesUX.battleService.lobbyForClan(clan)
                val placeholders = this@ClanBattlesUX.statePlaceholders(player, clan, battle, lobby)
                type(when {
                    battle != null -> this@ClanBattlesUX.material(this@ClanBattlesUX.display.activeBattleMaterial, Material.DIAMOND_SWORD)
                    lobby?.countdownActive == true -> this@ClanBattlesUX.material(this@ClanBattlesUX.display.countdownMaterial, Material.CLOCK)
                    lobby != null -> this@ClanBattlesUX.material(this@ClanBattlesUX.display.lobbyMaterial, Material.SHIELD)
                    else -> this@ClanBattlesUX.material(this@ClanBattlesUX.display.ownMaterial, Material.SHIELD)
                })
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.ownName, placeholders))
                lore(this@ClanBattlesUX.display.ownLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                glow(battle != null || lobby != null)
                null
            }
            onClick { player, event -> this@ClanBattlesUX.handleOwnStatus(player, event) }
        }
    }

    private fun addIncomingChallenge() {
        if (!valid(display.incomingSlot)) return
        slot(display.incomingSlot) {
            dynamicItemNullable(this@ClanBattlesUX.material(this@ClanBattlesUX.display.incomingMaterial, Material.IRON_SWORD)) { player ->
                val clan = this@ClanBattlesUX.clanService.getClanUser(player) ?: return@dynamicItemNullable null
                if (this@ClanBattlesUX.battleService.lobbyForClan(clan) != null) return@dynamicItemNullable null
                val challenge = this@ClanBattlesUX.battleService.incomingChallenges(clan).firstOrNull() ?: return@dynamicItemNullable null
                val challenger = this@ClanBattlesUX.clanService.getClanByName(challenge.challengerClanId) ?: return@dynamicItemNullable null
                val placeholders = mapOf("challenger" to challenger.name, "challenger_mmr" to challenger.mmr.toString(), "challenge_id" to challenge.id.toString())
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.incomingName, placeholders))
                lore(this@ClanBattlesUX.display.incomingLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                glow(true)
                build()
            }
            onClick { player, event -> this@ClanBattlesUX.handleIncoming(player, event) }
        }
    }

    private fun addOpponents() {
        opponentSlots.forEachIndexed { index, slotIndex ->
            slot(slotIndex) {
                dynamicItemNullable(this@ClanBattlesUX.material(this@ClanBattlesUX.display.opponentMaterial, Material.IRON_SWORD)) { player ->
                    val clan = this@ClanBattlesUX.clanService.getClanUser(player) ?: return@dynamicItemNullable null
                    val available = this@ClanBattlesUX.opponents(clan)
                    val opponent = available.getOrNull(this@ClanBattlesUX.page * this@ClanBattlesUX.opponentSlots.size.coerceAtLeast(1) + index) ?: run {
                        if (index != 0 || available.isNotEmpty()) return@dynamicItemNullable null
                        type(this@ClanBattlesUX.material(this@ClanBattlesUX.display.emptyMaterial, Material.SPYGLASS))
                        name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.emptyName))
                        val placeholders = mapOf("empty_reason" to this@ClanBattlesUX.emptyReason(clan))
                        lore(this@ClanBattlesUX.display.emptyLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                        return@dynamicItemNullable build()
                    }
                    val difference = opponent.mmr - clan.mmr
                    val differenceTemplate = when {
                        difference > 0 -> this@ClanBattlesUX.display.mmrDifferencePositive
                        difference < 0 -> this@ClanBattlesUX.display.mmrDifferenceNegative
                        else -> this@ClanBattlesUX.display.mmrDifferenceEqual
                    }
                    val placeholders = mapOf(
                        "clan" to opponent.name,
                        "opponent" to opponent.name,
                        "opponent_mmr" to opponent.mmr.toString(),
                        "online" to opponent.onlineCount.toString(),
                        "max_participants" to this@ClanBattlesUX.config.maximumParticipants.toString(),
                        "score_to_win" to this@ClanBattlesUX.config.scoreToWin.toString(),
                        "win_mmr" to this@ClanBattlesUX.config.ratingWin.toString(),
                        "loss_mmr" to this@ClanBattlesUX.config.ratingLoss.toString(),
                        "win_points" to this@ClanBattlesUX.config.pointsWin.toString(),
                        "mmr_difference" to this@ClanBattlesUX.format(player, differenceTemplate, mapOf("difference" to difference.toString()))
                    )
                    name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.opponentName, placeholders))
                    lore(this@ClanBattlesUX.display.opponentLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                    val threshold = this@ClanBattlesUX.config.similarMmrGlowThreshold
                    glow(threshold >= 0 && abs(difference) <= threshold)
                    build()
                }
                onClick { player, event -> if (event.isLeftClick && !event.isShiftClick) this@ClanBattlesUX.challenge(player, index) }
            }
        }
    }

    private fun addControls() {
        if (valid(display.backSlot)) slot(display.backSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.backMaterial, Material.OAK_DOOR)) { player ->
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.backName))
                lore(this@ClanBattlesUX.display.backLore.map { this@ClanBattlesUX.format(player, it) })
                glow(true)
                null
            }
            onClick { player, _ -> MainUX(this@ClanBattlesUX.clanService).open(player) }
        }
        if (valid(display.pageSlot)) slot(display.pageSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.pageMaterial, Material.PAPER)) { player ->
                val placeholders = mapOf("page" to (this@ClanBattlesUX.page + 1).toString(), "pages" to this@ClanBattlesUX.pageCount().toString(), "opponents" to this@ClanBattlesUX.opponentsForViewer().size.toString())
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.pageName, placeholders))
                lore(this@ClanBattlesUX.display.pageLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                null
            }
        }
        if (valid(display.previousSlot)) slot(display.previousSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.previousMaterial, Material.SPECTRAL_ARROW)) { player ->
                val enabled = this@ClanBattlesUX.page > 0
                type(this@ClanBattlesUX.material(if (enabled) this@ClanBattlesUX.display.previousMaterial else this@ClanBattlesUX.display.disabledPreviousMaterial, if (enabled) Material.SPECTRAL_ARROW else Material.RED_DYE))
                name(this@ClanBattlesUX.format(player, if (enabled) this@ClanBattlesUX.display.previousName else this@ClanBattlesUX.display.disabledPreviousName))
                lore((if (enabled) this@ClanBattlesUX.display.previousLore else this@ClanBattlesUX.display.disabledPreviousLore).map { this@ClanBattlesUX.format(player, it) })
                glow(enabled)
                null
            }
            onClick { player, _ -> this@ClanBattlesUX.changePage(player, -1) }
        }
        if (valid(display.nextSlot)) slot(display.nextSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.nextMaterial, Material.SPECTRAL_ARROW)) { player ->
                val enabled = this@ClanBattlesUX.page + 1 < this@ClanBattlesUX.pageCount()
                type(this@ClanBattlesUX.material(if (enabled) this@ClanBattlesUX.display.nextMaterial else this@ClanBattlesUX.display.disabledNextMaterial, if (enabled) Material.SPECTRAL_ARROW else Material.RED_DYE))
                name(this@ClanBattlesUX.format(player, if (enabled) this@ClanBattlesUX.display.nextName else this@ClanBattlesUX.display.disabledNextName))
                lore((if (enabled) this@ClanBattlesUX.display.nextLore else this@ClanBattlesUX.display.disabledNextLore).map { this@ClanBattlesUX.format(player, it) })
                glow(enabled)
                null
            }
            onClick { player, _ -> this@ClanBattlesUX.changePage(player, 1) }
        }
        if (valid(display.refreshSlot)) slot(display.refreshSlot) {
            dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.refreshMaterial, Material.COMPASS)) { player ->
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.refreshName))
                lore(this@ClanBattlesUX.display.refreshLore.map { this@ClanBattlesUX.format(player, it) })
                glow(true)
                null
            }
            onClick { player, _ -> this@ClanBattlesUX.refresh(player) }
        }
    }

    private fun challenge(player: Player, index: Int) {
        val clan = clanService.getClanUser(player) ?: return
        val opponent = opponents(clan).getOrNull(page * opponentSlots.size.coerceAtLeast(1) + index) ?: return
        handle(player, battleService.sendChallenge(player, opponent))
        refresh(player)
    }

    private fun handleOwnStatus(player: Player, event: InventoryClickEvent) {
        val clan = clanService.getClanUser(player) ?: return
        if (battleService.battleForClan(clan) != null || battleService.lobbyForClan(clan) == null) return
        val result = when {
            event.isLeftClick && !event.isShiftClick -> battleService.toggleLobbyParticipation(player)
            event.isRightClick && !event.isShiftClick -> battleService.toggleLobbyReady(player)
            else -> return
        }
        handle(player, result)
        refresh(player)
    }

    private fun handleIncoming(player: Player, event: InventoryClickEvent) {
        val clan = clanService.getClanUser(player) ?: return
        val challenge = battleService.incomingChallenges(clan).firstOrNull() ?: return
        val result = when {
            event.isLeftClick && !event.isShiftClick -> battleService.acceptChallenge(player, challenge.id)
            event.isRightClick && !event.isShiftClick -> battleService.declineChallenge(player, challenge.id)
            else -> return
        }
        handle(player, result)
        refresh(player)
    }

    private fun handle(player: Player, result: ClanBattleOperation) {
        val rejected = result as? ClanBattleOperation.Rejected ?: return
        val messages = clanService.plugin.configService.messages
        val actions = when (rejected.reason) {
            ClanBattleRejection.DISABLED -> messages.battles.disabled
            ClanBattleRejection.NO_PERMISSION -> messages.battles.noPermission
            ClanBattleRejection.CLAN_BUSY -> messages.battles.clanBusy
            ClanBattleRejection.CHALLENGE_EXISTS -> messages.battles.challengeExists
            ClanBattleRejection.CHALLENGE_NOT_FOUND,
            ClanBattleRejection.CHALLENGE_EXPIRED -> messages.battles.challengeNotFound
            ClanBattleRejection.NOT_TARGET_CLAN -> messages.battles.notTarget
            ClanBattleRejection.NOT_ENOUGH_ONLINE -> messages.battles.notEnoughOnline
            ClanBattleRejection.ARENA_UNAVAILABLE -> messages.battles.arenaUnavailable
            ClanBattleRejection.CANCELLED_BY_EVENT -> messages.battles.cancelled
            ClanBattleRejection.NO_CLAN,
            ClanBattleRejection.SAME_CLAN -> messages.general.noPermission
            ClanBattleRejection.LOBBY_NOT_FOUND -> messages.battles.lobbyNotFound
            ClanBattleRejection.LOBBY_FULL -> messages.battles.lobbyFull
            ClanBattleRejection.NOT_ENOUGH_SELECTED -> messages.battles.lobbyNotEnoughSelected
        }
        clanService.plugin.configService.send(player, actions, mapOf("minimum" to config.minimumOnlineMembers.coerceAtLeast(1).toString(), "maximum" to config.maximumParticipants.coerceAtLeast(1).toString()))
    }

    private fun refresh(player: Player) {
        page = page.coerceAtMost(pageCount() - 1)
        updateSlots(opponentSlots + listOfNotNull(display.headerSlot.takeIf(::valid), display.ownSlot.takeIf(::valid), display.incomingSlot.takeIf(::valid), display.previousSlot.takeIf(::valid), display.pageSlot.takeIf(::valid), display.nextSlot.takeIf(::valid), display.refreshSlot.takeIf(::valid)), player)
    }

    private fun changePage(player: Player, direction: Int) {
        val nextPage = (page + direction).coerceIn(0, pageCount() - 1)
        if (nextPage == page) return
        page = nextPage
        refresh(player)
    }

    private fun opponents(clan: ua.inventorytype.pnclans.api.clan.Clan): List<ua.inventorytype.pnclans.api.clan.Clan> =
        if (battleService.battleForClan(clan) != null || battleService.lobbyForClan(clan) != null || battleService.incomingChallenges(clan).isNotEmpty() || battleService.outgoingChallenges(clan).isNotEmpty()) emptyList() else battleService.availableOpponents(clan)

    private fun emptyReason(clan: ua.inventorytype.pnclans.api.clan.Clan): String = when {
        battleService.battleForClan(clan) != null -> display.emptyReasonBattle
        battleService.lobbyForClan(clan) != null -> display.emptyReasonLobby
        battleService.incomingChallenges(clan).isNotEmpty() -> display.emptyReasonIncoming
        battleService.outgoingChallenges(clan).isNotEmpty() -> display.emptyReasonOutgoing
        else -> display.emptyReasonNoOpponents
    }

    private fun opponentsForViewer(): List<ua.inventorytype.pnclans.api.clan.Clan> = viewer?.let(clanService::getClanUser)?.let(::opponents).orEmpty()
    private fun pageCount(): Int = ceil(opponentsForViewer().size.toDouble() / opponentSlots.size.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    private fun statePlaceholders(player: Player, clan: ua.inventorytype.pnclans.api.clan.Clan, battle: ClanBattle?, lobby: ClanBattleLobbySnapshot?): Map<String, String> {
        val battleOpponentId = battle?.let { if (it.challengerClanId == clan.id) it.defenderClanId else it.challengerClanId }
        val lobbyOpponentId = lobby?.opponentId(clan.id)
        val opponent = (battleOpponentId ?: lobbyOpponentId)?.let(clanService::getClanByName)
        val incoming = if (lobby == null) battleService.incomingChallenges(clan).firstOrNull() else null
        val outgoing = if (lobby == null) battleService.outgoingChallenges(clan).firstOrNull() else null
        val pendingOpponent = incoming?.challengerClanId?.let(clanService::getClanByName) ?: outgoing?.defenderClanId?.let(clanService::getClanByName)
        val ownRoster = lobby?.participantsFor(clan.id).orEmpty()
        val enemyRoster = lobbyOpponentId?.let { lobby?.participantsFor(it) }.orEmpty()
        val selected = player.uniqueId in ownRoster
        val ownReady = lobby?.isReady(clan.id) == true
        val enemyReady = lobbyOpponentId?.let { lobby?.isReady(it) } == true
        val countdownSeconds = lobby?.takeIf { it.countdownActive }?.let { ((it.countdownEndsAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L }
        val common = mapOf(
            "seconds" to (countdownSeconds ?: 0L).toString(),
            "own_ready" to readyLabel(ownReady), "enemy_ready" to readyLabel(enemyReady),
            "own_selected" to ownRoster.size.toString(), "enemy_selected" to enemyRoster.size.toString(),
            "max_participants" to config.maximumParticipants.coerceAtLeast(1).toString()
        )
        val state = when {
            battle != null -> display.stateBattle
            countdownSeconds != null -> display.stateCountdown
            lobby != null -> display.stateLobby
            incoming != null -> display.stateIncoming
            outgoing != null -> display.stateOutgoing
            else -> display.stateIdle
        }
        val score = when {
            battle != null && battleOpponentId != null -> format(player, display.activeScoreFormat, common + mapOf("own_score" to battle.scoreFor(clan.id).toString(), "enemy_score" to battle.scoreFor(battleOpponentId).toString()))
            lobby != null -> format(player, display.lobbyScoreFormat, common)
            else -> display.noScoreText
        }
        val action = when {
            battle != null -> display.actionBattle
            countdownSeconds != null -> display.actionCountdown
            lobby != null && selected && ownReady -> display.actionLobbySelectedReady
            lobby != null && selected -> display.actionLobbySelected
            lobby != null -> display.actionLobbyNotSelected
            incoming != null -> display.actionIncoming
            outgoing != null -> display.actionOutgoing
            else -> display.actionIdle
        }
        return common + mapOf(
            "clan_mmr" to clan.mmr.toString(), "wins" to clan.battleWins.toString(), "losses" to clan.battleLosses.toString(),
            "score_to_win" to config.scoreToWin.toString(), "opponent" to (opponent?.name ?: pendingOpponent?.name ?: display.noOpponentText),
            "battle_state" to format(player, state, common), "score" to score, "battle_action" to format(player, action, common)
        )
    }

    private fun readyLabel(ready: Boolean): String = if (ready) display.readyText else display.waitingText
    private fun material(value: String, fallback: Material): Material = runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)
    private fun format(player: Player, value: String, placeholders: Map<String, String> = emptyMap()): String = clanService.plugin.configService.formatMessage(player, value, placeholders)
    private fun valid(slot: Int): Boolean = slot in 0 until inventorySize
}
