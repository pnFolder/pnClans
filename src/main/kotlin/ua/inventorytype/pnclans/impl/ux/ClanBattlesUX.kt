package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import ua.inventorytype.pnclans.api.battle.ClanBattle
import ua.inventorytype.pnclans.impl.clan.ClanBattleOperation
import ua.inventorytype.pnclans.impl.clan.ClanBattleRejection
import ua.inventorytype.pnclans.impl.clan.ClanBattleService
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil
import kotlin.math.abs

/** Battle headquarters: challenge board, incoming calls, and live battle status. */
class ClanBattlesUX(
    clanService: ClanService,
    selectedPage: Int = 0
) : BaseGui(clanService) {
    private val config = clanService.plugin.configService.battles
    private val display = config.display
    private val battleService: ClanBattleService = clanService.plugin.clanBattleService
    private val guiRows = config.rows.coerceIn(1, 6)
    private val inventorySize = guiRows * 9
    private val reservedSlots = setOf(display.headerSlot, display.ownSlot, display.incomingSlot, display.backSlot, display.previousSlot, display.pageSlot, display.nextSlot, display.refreshSlot)
    private val opponentSlots = display.opponentSlots
        .filter { it in 0 until inventorySize && it !in reservedSlots }
        .distinct()
        .ifEmpty { DEFAULT_OPPONENT_SLOTS.filter { it in 0 until inventorySize && it !in reservedSlots } }
    private var page = selectedPage.coerceAtLeast(0)
    private var viewer: Player? = null

    init {
        title(config.title)
        rows(guiRows)
        hotWorldDecor(true)
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
                val battle = this@ClanBattlesUX.battleService.battleForClan(clan)
                val placeholders = this@ClanBattlesUX.headerPlaceholders(clan, battle)
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
                val placeholders = this@ClanBattlesUX.ownPlaceholders(clan, battle)
                type(this@ClanBattlesUX.material(if (battle == null) "SHIELD" else "DIAMOND_SWORD", Material.SHIELD))
                name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.ownName, placeholders))
                lore(this@ClanBattlesUX.display.ownLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                glow(battle != null)
                null
            }
        }
    }

    private fun addIncomingChallenge() {
        if (!valid(display.incomingSlot)) return
        slot(display.incomingSlot) {
            dynamicItemNullable(this@ClanBattlesUX.material(this@ClanBattlesUX.display.incomingMaterial, Material.IRON_SWORD)) { player ->
                val clan = this@ClanBattlesUX.clanService.getClanUser(player) ?: return@dynamicItemNullable null
                val challenge = this@ClanBattlesUX.battleService.incomingChallenges(clan).firstOrNull()
                    ?: return@dynamicItemNullable null
                val challenger = this@ClanBattlesUX.clanService.getClanByName(challenge.challengerClanId)
                    ?: return@dynamicItemNullable null
                val placeholders = mapOf(
                    "challenger" to challenger.name,
                    "challenger_mmr" to challenger.mmr.toString(),
                    "challenge_id" to challenge.id.toString()
                )
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
                    val opponent = this@ClanBattlesUX.opponents(clan).getOrNull(this@ClanBattlesUX.page * this@ClanBattlesUX.opponentSlots.size + index)
                        ?: run {
                            if (index != 0 || this@ClanBattlesUX.opponents(clan).isNotEmpty()) return@dynamicItemNullable null
                            type(this@ClanBattlesUX.material(this@ClanBattlesUX.display.emptyMaterial, Material.SPYGLASS))
                            name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.emptyName))
                            val placeholders = mapOf("empty_reason" to this@ClanBattlesUX.emptyReason(clan))
                            lore(this@ClanBattlesUX.display.emptyLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                            return@dynamicItemNullable build()
                        }
                    val difference = opponent.mmr - clan.mmr
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
                        "mmr_difference" to when {
                            difference > 0 -> "&#FC3737+$difference"
                            difference < 0 -> "&#5EFD7D$difference"
                            else -> "&#FFD7000"
                        }
                    )
                    name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.opponentName, placeholders))
                    lore(this@ClanBattlesUX.display.opponentLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                    glow(abs(difference) <= 150)
                    build()
                }
                onClick { player, event ->
                    if (event.isLeftClick && !event.isShiftClick) this@ClanBattlesUX.challenge(player, index)
                }
            }
        }
    }

    private fun addControls() {
        if (valid(display.backSlot)) {
            slot(display.backSlot) {
                dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.backMaterial, Material.RED_CANDLE)) { player ->
                    name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.backName))
                    lore(this@ClanBattlesUX.display.backLore.map { this@ClanBattlesUX.format(player, it) })
                    glow(true)
                    null
                }
                onClick { player, _ -> MainUX(this@ClanBattlesUX.clanService).open(player) }
            }
        }
        if (valid(display.pageSlot)) {
            slot(display.pageSlot) {
                dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.pageMaterial, Material.PAPER)) { player ->
                    val placeholders = mapOf(
                        "page" to (this@ClanBattlesUX.page + 1).toString(),
                        "pages" to this@ClanBattlesUX.pageCount().toString(),
                        "opponents" to this@ClanBattlesUX.opponentsForViewer().size.toString()
                    )
                    name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.pageName, placeholders))
                    lore(this@ClanBattlesUX.display.pageLore.map { this@ClanBattlesUX.format(player, it, placeholders) })
                    null
                }
            }
        }
        if (valid(display.previousSlot)) {
            slot(display.previousSlot) {
                dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.previousMaterial, Material.SPECTRAL_ARROW)) { player ->
                    val enabled = this@ClanBattlesUX.page > 0
                    type(if (enabled) this@ClanBattlesUX.material(this@ClanBattlesUX.display.previousMaterial, Material.SPECTRAL_ARROW) else Material.RED_DYE)
                    name(this@ClanBattlesUX.format(player, if (enabled) this@ClanBattlesUX.display.previousName else this@ClanBattlesUX.display.disabledPreviousName))
                    val lines = if (enabled) this@ClanBattlesUX.display.previousLore else this@ClanBattlesUX.display.disabledPreviousLore
                    lore(lines.map { this@ClanBattlesUX.format(player, it) })
                    glow(enabled)
                    null
                }
                onClick { player, _ -> this@ClanBattlesUX.changePage(player, -1) }
            }
        }
        if (valid(display.nextSlot)) {
            slot(display.nextSlot) {
                dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.nextMaterial, Material.SPECTRAL_ARROW)) { player ->
                    val enabled = this@ClanBattlesUX.page + 1 < this@ClanBattlesUX.pageCount()
                    type(if (enabled) this@ClanBattlesUX.material(this@ClanBattlesUX.display.nextMaterial, Material.SPECTRAL_ARROW) else Material.RED_DYE)
                    name(this@ClanBattlesUX.format(player, if (enabled) this@ClanBattlesUX.display.nextName else this@ClanBattlesUX.display.disabledNextName))
                    val lines = if (enabled) this@ClanBattlesUX.display.nextLore else this@ClanBattlesUX.display.disabledNextLore
                    lore(lines.map { this@ClanBattlesUX.format(player, it) })
                    glow(enabled)
                    null
                }
                onClick { player, _ -> this@ClanBattlesUX.changePage(player, 1) }
            }
        }
        if (valid(display.refreshSlot)) {
            slot(display.refreshSlot) {
                dynamicItem(this@ClanBattlesUX.material(this@ClanBattlesUX.display.refreshMaterial, Material.COMPASS)) { player ->
                    name(this@ClanBattlesUX.format(player, this@ClanBattlesUX.display.refreshName))
                    lore(this@ClanBattlesUX.display.refreshLore.map { this@ClanBattlesUX.format(player, it) })
                    glow(true)
                    null
                }
                onClick { player, _ -> this@ClanBattlesUX.refresh(player) }
            }
        }
    }

    private fun challenge(player: Player, index: Int) {
        val clan = clanService.getClanUser(player) ?: return
        val opponent = opponents(clan).getOrNull(page * opponentSlots.size + index) ?: return
        handle(player, battleService.sendChallenge(player, opponent))
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
        if (result is ClanBattleOperation.Success) {
            player.closeInventory()
        } else {
            refresh(player)
        }
    }

    private fun handle(player: Player, result: ClanBattleOperation) {
        val rejected = result as? ClanBattleOperation.Rejected ?: return
        val actions = when (rejected.reason) {
            ClanBattleRejection.DISABLED -> clanService.plugin.configService.messages.battles.disabled
            ClanBattleRejection.NO_PERMISSION -> clanService.plugin.configService.messages.battles.noPermission
            ClanBattleRejection.CLAN_BUSY -> clanService.plugin.configService.messages.battles.clanBusy
            ClanBattleRejection.CHALLENGE_EXISTS -> clanService.plugin.configService.messages.battles.challengeExists
            ClanBattleRejection.CHALLENGE_NOT_FOUND, ClanBattleRejection.CHALLENGE_EXPIRED -> clanService.plugin.configService.messages.battles.challengeNotFound
            ClanBattleRejection.NOT_TARGET_CLAN -> clanService.plugin.configService.messages.battles.notTarget
            ClanBattleRejection.NOT_ENOUGH_ONLINE -> clanService.plugin.configService.messages.battles.notEnoughOnline
            ClanBattleRejection.ARENA_UNAVAILABLE -> clanService.plugin.configService.messages.battles.arenaUnavailable
            ClanBattleRejection.CANCELLED_BY_EVENT -> clanService.plugin.configService.messages.battles.cancelled
            ClanBattleRejection.NO_CLAN, ClanBattleRejection.SAME_CLAN -> clanService.plugin.configService.messages.general.noPermission
        }
        clanService.plugin.configService.send(player, actions)
    }

    private fun refresh(player: Player) {
        page = page.coerceAtMost(pageCount() - 1)
        updateSlots(
            opponentSlots + listOfNotNull(
                display.headerSlot.takeIf(::valid),
                display.ownSlot.takeIf(::valid),
                display.incomingSlot.takeIf(::valid),
                display.previousSlot.takeIf(::valid),
                display.pageSlot.takeIf(::valid),
                display.nextSlot.takeIf(::valid),
                display.refreshSlot.takeIf(::valid)
            ),
            player
        )
    }

    private fun changePage(player: Player, direction: Int) {
        val nextPage = (page + direction).coerceIn(0, pageCount() - 1)
        if (nextPage == page) return
        page = nextPage
        refresh(player)
    }

    private fun opponents(clan: ua.inventorytype.pnclans.api.clan.Clan): List<ua.inventorytype.pnclans.api.clan.Clan> =
        if (
            battleService.battleForClan(clan) != null ||
            battleService.incomingChallenges(clan).isNotEmpty() ||
            battleService.outgoingChallenges(clan).isNotEmpty()
        ) {
            emptyList()
        } else {
            battleService.availableOpponents(clan)
        }

    private fun emptyReason(clan: ua.inventorytype.pnclans.api.clan.Clan): String = when {
        battleService.battleForClan(clan) != null -> "Сначала завершите текущую битву."
        battleService.incomingChallenges(clan).isNotEmpty() -> "Сначала примите или отклоните входящий вызов."
        battleService.outgoingChallenges(clan).isNotEmpty() -> "Сначала дождитесь ответа на отправленный вызов."
        else -> "Нет кланов с достаточным онлайном."
    }

    private fun opponentsForViewer(): List<ua.inventorytype.pnclans.api.clan.Clan> =
        viewer?.let(clanService::getClanUser)?.let(::opponents).orEmpty()

    private fun pageCount(): Int = ceil(opponentsForViewer().size.toDouble() / opponentSlots.size.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    private fun headerPlaceholders(clan: ua.inventorytype.pnclans.api.clan.Clan, battle: ClanBattle?): Map<String, String> {
        val opponentId = battle?.let { if (it.challengerClanId == clan.id) it.defenderClanId else it.challengerClanId }
        val opponent = opponentId?.let(clanService::getClanByName)
        val incoming = battleService.incomingChallenges(clan).firstOrNull()
        val outgoing = battleService.outgoingChallenges(clan).firstOrNull()
        val pendingOpponent = incoming?.challengerClanId?.let(clanService::getClanByName)
            ?: outgoing?.defenderClanId?.let(clanService::getClanByName)
        return mapOf(
            "clan_mmr" to clan.mmr.toString(),
            "wins" to clan.battleWins.toString(),
            "losses" to clan.battleLosses.toString(),
            "score_to_win" to config.scoreToWin.toString(),
            "opponent" to (opponent?.name ?: pendingOpponent?.name ?: "Нет"),
            "battle_state" to when {
                battle != null -> "&#FC3737Битва идёт"
                incoming != null -> "&#FFD700Есть входящий вызов"
                outgoing != null -> "&#5EA9FDВызов отправлен"
                else -> "&#5EFD7DГотов к вызову"
            },
            "score" to if (battle == null || opponentId == null) "&8—" else "${battle.scoreFor(clan.id)} : ${battle.scoreFor(opponentId)}",
            "battle_action" to when {
                battle != null -> "&#FC3737✖ &fБой уже идёт."
                incoming != null -> "&#FF8702➥ &fПримите или отклоните вызов справа."
                outgoing != null -> "&#5EA9FD⌚ &fОжидайте ответа соперника."
                else -> "&#FF8702➥ &fВыберите соперника ниже."
            }
        )
    }

    private fun ownPlaceholders(clan: ua.inventorytype.pnclans.api.clan.Clan, battle: ClanBattle?): Map<String, String> =
        headerPlaceholders(clan, battle)

    private fun material(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun format(player: Player, value: String, placeholders: Map<String, String> = emptyMap()): String =
        clanService.plugin.configService.formatMessage(player, value, placeholders)

    private fun valid(slot: Int): Boolean = slot in 0 until inventorySize

    private companion object {
        val DEFAULT_OPPONENT_SLOTS = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
    }
}
