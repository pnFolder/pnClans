package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.AddMmrAction
import ua.inventorytype.pnclans.api.ConsoleCommandAction
import ua.inventorytype.pnclans.api.GiveItemAction
import ua.inventorytype.pnclans.api.ItemRewardAction
import ua.inventorytype.pnclans.impl.clan.ClanQuestService
import ua.inventorytype.pnclans.impl.clan.ClanQuestStatus
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ClanQuestConfig
import ua.inventorytype.pnclans.impl.config.ClanQuestObjective
import ua.inventorytype.pnclans.impl.config.ClanQuestReset
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import kotlin.math.ceil

/** Config-driven clan quest catalogue with shared progress, filters, and pagination. */
class ClanQuestsUX(
    clanService: ClanService,
    selectedFilter: QuestFilter = QuestFilter.ALL,
    selectedPage: Int = 0
) : BaseGui(clanService) {
    private val config = clanService.plugin.configService.quests
    private val display = config.display
    private val questService: ClanQuestService = clanService.plugin.clanQuestService
    private val guiRows = config.rows.coerceIn(1, 6)
    private val inventorySize = guiRows * 9
    private val controlSlots = setOf(
        display.headerSlot,
        display.backSlot,
        display.summarySlot,
        display.previousSlot,
        display.pageSlot,
        display.nextSlot,
        display.filterSlot
    )
    private val entrySlots = display.entrySlots
        .filter { it in 0 until inventorySize && it !in controlSlots }
        .distinct()
        .ifEmpty {
            DEFAULT_ENTRY_SLOTS.filter { it in 0 until inventorySize && it !in controlSlots }
        }
        .ifEmpty {
            (0 until inventorySize).filter { it !in controlSlots }
        }
    private var filter = selectedFilter
    private var page = selectedPage.coerceAtLeast(0)
    private var viewer: Player? = null

    init {
        title(config.title)
        rows(guiRows)
        background(clanService.plugin.configService.menus.background)
        addHeader()
        addQuestEntries()
        addControls()
        page = page.coerceAtMost(pageCount() - 1)
    }

    override fun open(player: Player) {
        if (!config.enabled) {
            MainUX(clanService).open(player)
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
        if (!isValid(display.headerSlot)) return
        slot(display.headerSlot) {
            dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.headerMaterial, Material.BOOK)) { player ->
                val clan = this@ClanQuestsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val placeholders = this@ClanQuestsUX.summaryPlaceholders(clan)
                name(this@ClanQuestsUX.format(player, this@ClanQuestsUX.display.headerName, placeholders))
                lore(this@ClanQuestsUX.display.headerLore.map { this@ClanQuestsUX.format(player, it, placeholders) })
                glow(true)
                null
            }
        }
    }

    private fun addQuestEntries() {
        entrySlots.forEachIndexed { index, slotIndex ->
            slot(slotIndex) {
                dynamicItemNullable(Material.BOOK) { player ->
                    val clan = this@ClanQuestsUX.clanService.getClanUser(player) ?: return@dynamicItemNullable null
                    val questId = this@ClanQuestsUX.questAt(index) ?: run {
                        if (index != 0 || this@ClanQuestsUX.filteredQuestIds().isNotEmpty()) return@dynamicItemNullable null
                        type(Material.PAPER)
                        name(this@ClanQuestsUX.format(player, this@ClanQuestsUX.display.emptyName))
                        lore(this@ClanQuestsUX.display.emptyLore.map { this@ClanQuestsUX.format(player, it) })
                        return@dynamicItemNullable build()
                    }
                    val quest = this@ClanQuestsUX.config.quests[questId] ?: return@dynamicItemNullable null
                    this@ClanQuestsUX.renderQuest(this, player, clan, questId, quest)
                    build()
                }
                onClick { player, _ -> this@ClanQuestsUX.handleQuestClick(player, index) }
            }
        }
    }

    private fun addControls() {
        if (isValid(display.backSlot)) {
            slot(display.backSlot) {
                dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.backMaterial, Material.OAK_DOOR)) { player ->
                    name(this@ClanQuestsUX.format(player, this@ClanQuestsUX.display.backName))
                    lore(this@ClanQuestsUX.display.backLore.map { this@ClanQuestsUX.format(player, it) })
                    glow(true)
                    null
                }
                onClick { player, _ -> MainUX(this@ClanQuestsUX.clanService).open(player) }
            }
        }

        if (isValid(display.summarySlot)) {
            slot(display.summarySlot) {
                dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.summaryMaterial, Material.NETHER_STAR)) { player ->
                    val clan = this@ClanQuestsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val placeholders = this@ClanQuestsUX.summaryPlaceholders(clan)
                    name(this@ClanQuestsUX.format(player, this@ClanQuestsUX.display.summaryName, placeholders))
                    lore(this@ClanQuestsUX.display.summaryLore.map { this@ClanQuestsUX.format(player, it, placeholders) })
                    glow(true)
                    null
                }
            }
        }

        if (isValid(display.previousSlot)) {
            slot(display.previousSlot) {
                dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.previousMaterial, Material.SPECTRAL_ARROW)) { player ->
                    val enabled = this@ClanQuestsUX.page > 0
                    type(if (enabled) this@ClanQuestsUX.material(this@ClanQuestsUX.display.previousMaterial, Material.SPECTRAL_ARROW) else Material.RED_DYE)
                    name(this@ClanQuestsUX.format(player, if (enabled) this@ClanQuestsUX.display.previousName else this@ClanQuestsUX.display.disabledPreviousName))
                    val lines = if (enabled) this@ClanQuestsUX.display.previousLore else this@ClanQuestsUX.display.disabledPreviousLore
                    lore(lines.map {
                        this@ClanQuestsUX.format(player, it, mapOf("page" to this@ClanQuestsUX.page.toString()))
                    })
                    glow(enabled)
                    null
                }
                onClick { player, _ -> this@ClanQuestsUX.changePage(player, -1) }
            }
        }

        if (isValid(display.pageSlot)) {
            slot(display.pageSlot) {
                dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.pageMaterial, Material.PAPER)) { player ->
                    val placeholders = this@ClanQuestsUX.pagePlaceholders()
                    name(this@ClanQuestsUX.format(player, this@ClanQuestsUX.display.pageName, placeholders))
                    lore(this@ClanQuestsUX.display.pageLore.map { this@ClanQuestsUX.format(player, it, placeholders) })
                    glow(true)
                    null
                }
            }
        }

        if (isValid(display.nextSlot)) {
            slot(display.nextSlot) {
                dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.nextMaterial, Material.SPECTRAL_ARROW)) { player ->
                    val enabled = this@ClanQuestsUX.page + 1 < this@ClanQuestsUX.pageCount()
                    type(if (enabled) this@ClanQuestsUX.material(this@ClanQuestsUX.display.nextMaterial, Material.SPECTRAL_ARROW) else Material.RED_DYE)
                    name(this@ClanQuestsUX.format(player, if (enabled) this@ClanQuestsUX.display.nextName else this@ClanQuestsUX.display.disabledNextName))
                    val lines = if (enabled) this@ClanQuestsUX.display.nextLore else this@ClanQuestsUX.display.disabledNextLore
                    lore(lines.map {
                        this@ClanQuestsUX.format(player, it, mapOf("page" to (this@ClanQuestsUX.page + 2).toString()))
                    })
                    glow(enabled)
                    null
                }
                onClick { player, _ -> this@ClanQuestsUX.changePage(player, 1) }
            }
        }

        if (isValid(display.filterSlot)) {
            slot(display.filterSlot) {
                dynamicItem(this@ClanQuestsUX.material(this@ClanQuestsUX.display.filterMaterial, Material.COMPASS)) { player ->
                    val placeholders = mapOf(
                        "filter" to this@ClanQuestsUX.filter.displayName,
                        "next_filter" to this@ClanQuestsUX.filter.next().displayName
                    )
                    type(this@ClanQuestsUX.material(this@ClanQuestsUX.filter.material, Material.COMPASS))
                    name(this@ClanQuestsUX.format(player, this@ClanQuestsUX.display.filterName, placeholders))
                    lore(this@ClanQuestsUX.display.filterLore.map { this@ClanQuestsUX.format(player, it, placeholders) })
                    glow(true)
                    null
                }
                onClick { player, _ -> this@ClanQuestsUX.changeFilter(player) }
            }
        }
    }

    private fun renderQuest(
        builder: ItemBuilder,
        player: Player,
        clan: ua.inventorytype.pnclans.api.clan.Clan,
        questId: String,
        quest: ClanQuestConfig
    ) {
        val progress = questService.state(clan, questId)
        val status = questService.status(clan, questId)
        val target = quest.target.coerceAtLeast(1L)
        val current = progress.progress.coerceIn(0L, target)
        val placeholders = questPlaceholders(clan, questId, quest, status, current, target)
        builder.type(if (status == ClanQuestStatus.LOCKED) Material.BARRIER else material(quest.material, Material.WRITABLE_BOOK))
        builder.name(format(player, quest.name, placeholders))
        val customLore = quest.lore.map { format(player, it, placeholders) }
        val configuredLore = display.entryLore.flatMap { line ->
            when (line) {
                "{reward_lines}" -> rewardLines(player, quest, placeholders)
                "{prerequisite_lines}" -> prerequisiteLines(player, clan, quest, placeholders)
                else -> listOf(format(player, line, placeholders))
            }
        }
        builder.lore(customLore + configuredLore)
        builder.glow(status == ClanQuestStatus.IN_PROGRESS || status == ClanQuestStatus.COMPLETED)
    }

    private fun handleQuestClick(player: Player, index: Int) {
        val clan = clanService.getClanUser(player) ?: return
        val questId = questAt(index) ?: return
        val quest = config.quests[questId] ?: return
        val progress = questService.state(clan, questId)
        val placeholders = questPlaceholders(
            clan,
            questId,
            quest,
            questService.status(clan, questId),
            progress.progress,
            quest.target.coerceAtLeast(1L)
        )
        when (questService.status(clan, questId)) {
            ClanQuestStatus.LOCKED -> send(player, clanService.plugin.configService.messages.quests.locked, placeholders)
            ClanQuestStatus.AVAILABLE -> send(player, clanService.plugin.configService.messages.quests.available, placeholders)
            ClanQuestStatus.IN_PROGRESS -> send(player, clanService.plugin.configService.messages.quests.inProgress, placeholders)
            ClanQuestStatus.COMPLETED -> send(player, clanService.plugin.configService.messages.quests.alreadyCompleted, placeholders)
        }
    }

    private fun changePage(player: Player, direction: Int) {
        val nextPage = (page + direction).coerceIn(0, pageCount() - 1)
        if (nextPage == page) return
        page = nextPage
        refresh(player)
    }

    private fun changeFilter(player: Player) {
        filter = filter.next()
        page = 0
        refresh(player)
    }

    private fun refresh(player: Player) {
        updateSlots(
            entrySlots + listOfNotNull(
                display.headerSlot.takeIf(::isValid),
                display.summarySlot.takeIf(::isValid),
                display.previousSlot.takeIf(::isValid),
                display.pageSlot.takeIf(::isValid),
                display.nextSlot.takeIf(::isValid),
                display.filterSlot.takeIf(::isValid)
            ),
            player
        )
    }

    private fun filteredQuestIds(): List<String> = config.quests.entries
        .sortedWith(compareBy<Map.Entry<String, ClanQuestConfig>> { it.value.slot }.thenBy { it.key })
        .map { it.key }
        .filter { questId ->
            val clan = viewer?.let(clanService::getClanUser) ?: return@filter true
            when (filter) {
                QuestFilter.ALL -> true
                QuestFilter.AVAILABLE -> questService.status(clan, questId) == ClanQuestStatus.AVAILABLE
                QuestFilter.IN_PROGRESS -> questService.status(clan, questId) == ClanQuestStatus.IN_PROGRESS
                QuestFilter.COMPLETED -> questService.status(clan, questId) == ClanQuestStatus.COMPLETED
                QuestFilter.LOCKED -> questService.status(clan, questId) == ClanQuestStatus.LOCKED
            }
        }

    private fun questAt(index: Int): String? = filteredQuestIds().getOrNull(page * entrySlots.size + index)

    private fun pageCount(): Int = ceil(filteredQuestIds().size.toDouble() / entrySlots.size.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    private fun summaryPlaceholders(clan: ua.inventorytype.pnclans.api.clan.Clan): Map<String, String> {
        val statuses = config.quests.keys.map { questService.status(clan, it) }
        return mapOf(
            "filter" to filter.displayName,
            "completed_quests" to statuses.count { it == ClanQuestStatus.COMPLETED }.toString(),
            "total_quests" to config.quests.size.toString(),
            "in_progress_quests" to statuses.count { it == ClanQuestStatus.IN_PROGRESS }.toString(),
            "available_quests" to statuses.count { it == ClanQuestStatus.AVAILABLE }.toString(),
            "locked_quests" to statuses.count { it == ClanQuestStatus.LOCKED }.toString(),
            "clan_points" to clan.points.toString()
        )
    }

    private fun pagePlaceholders(): Map<String, String> = mapOf(
        "filter" to filter.displayName,
        "page" to (page + 1).toString(),
        "pages" to pageCount().toString(),
        "visible_quests" to filteredQuestIds().size.toString()
    )

    private fun questPlaceholders(
        clan: ua.inventorytype.pnclans.api.clan.Clan,
        questId: String,
        quest: ClanQuestConfig,
        status: ClanQuestStatus,
        progress: Long,
        target: Long
    ): Map<String, String> {
        val percent = ((progress * 100L) / target).coerceIn(0L, 100L)
        return mapOf(
            "quest" to questId,
            "quest_name" to quest.name,
            "objective" to objectiveLabel(quest.objective),
            "objective_instruction" to objectiveInstruction(quest, target),
            "target" to number(target),
            "progress" to number(progress),
            "percent" to percent.toString(),
            "progress_bar" to progressBar(progress, target),
            "status" to status.label,
            "status_icon" to status.icon,
            "reward_points" to number(quest.rewardPoints),
            "reward_lines" to if (quest.rewards.isEmpty()) "&8Дополнительных наград нет" else "&f${quest.rewards.size} настроенных действий",
            "prerequisites" to prerequisitesLabel(clan, quest),
            "reset" to resetLabel(quest.reset),
            "repeatable" to if (quest.repeatable) "Да" else "Нет",
            "cycle" to resetLabel(quest.reset),
            "action" to when (status) {
                ClanQuestStatus.LOCKED -> "&#FC3737✖ &fСначала завершите предыдущие квесты."
                ClanQuestStatus.COMPLETED -> "&#5EFD7D✔ &fКвест завершён, награда уже выдана."
                ClanQuestStatus.IN_PROGRESS -> "&#5EA9FD⌚ &fПродолжайте выполнять общую цель."
                ClanQuestStatus.AVAILABLE -> "&#FF8702➥ &fНачните выполнять цель вместе с кланом."
            }
        )
    }

    private fun prerequisiteLines(
        player: Player,
        clan: ua.inventorytype.pnclans.api.clan.Clan,
        quest: ClanQuestConfig,
        placeholders: Map<String, String>
    ): List<String> {
        if (quest.prerequisites.isEmpty()) {
            return listOf(format(player, " &7- &fПредыдущие квесты: &#5EFD7DНе требуются", placeholders))
        }
        return quest.prerequisites.sorted().map { questId ->
            val completed = questService.hasCompletedAtLeastOnce(clan, questId)
            val icon = if (completed) "&#5EFD7D✔" else "&#FC3737✖"
            val name = config.quests[questId]?.name ?: questId
            format(player, " &7- $icon &f$name", placeholders)
        }
    }

    private fun prerequisitesLabel(clan: ua.inventorytype.pnclans.api.clan.Clan, quest: ClanQuestConfig): String =
        quest.prerequisites.takeIf { it.isNotEmpty() }?.joinToString(", ") { questId ->
            val done = questService.hasCompletedAtLeastOnce(clan, questId)
            val name = config.quests[questId]?.name ?: questId
            "${if (done) "&#5EFD7D✔" else "&#FC3737✖"} $name"
        } ?: "&#5EFD7DНет"

    private fun objectiveInstruction(quest: ClanQuestConfig, target: Long): String = when (quest.objective) {
        ClanQuestObjective.PLAYER_KILL -> "Убейте &#FC7D37${number(target)} игроков &fв обычном PvP."
        ClanQuestObjective.MOB_KILL -> if (quest.entityTypes.isEmpty()) {
            "Уничтожьте &#5EFD7D${number(target)} любых мобов&f."
        } else {
            "Уничтожьте &#5EFD7D${number(target)} целей&f: ${quest.entityTypes.joinToString(", ") { entityName(it) }}."
        }
        ClanQuestObjective.ACTIVITY_INTERVAL -> "Поддерживайте активность клана в течение &#5EA9FD${number(target)} интервалов&f."
        ClanQuestObjective.MEMBER_JOINED -> "Примите &#5EA9FD${number(target)} новых участников &fв состав."
        ClanQuestObjective.HOME_SET -> "Установите &#FC7D37${number(target)} новых клановых домов&f."
        ClanQuestObjective.TREASURY_DEPOSIT -> "Внесите суммарно &#FFD700${number(target)} ⛁ &fв казну."
        ClanQuestObjective.SHOP_PURCHASE -> "Завершите &#5EFD7D${number(target)} покупок &fв клановом магазине."
        ClanQuestObjective.BATTLE_PARTICIPATION -> "Примите участие в &#FC7D37${number(target)} клановых битвах&f."
        ClanQuestObjective.BATTLE_WIN -> "Одержите &#FFD700${number(target)} побед &fв клановых битвах."
        ClanQuestObjective.BATTLE_KILL -> "Сделайте &#FC3737${number(target)} убийств &fв активных битвах."
        ClanQuestObjective.BATTLE_DAMAGE -> "Нанесите &#FC65DF${number(target)} единиц урона &fна аренах."
    }

    private fun rewardLines(player: Player, quest: ClanQuestConfig, placeholders: Map<String, String>): List<String> {
        if (quest.rewards.isEmpty()) return listOf(format(player, " &7- &8Дополнительных наград нет.", placeholders))
        val recipient = when (quest.rewardRecipient) {
            ua.inventorytype.pnclans.impl.config.ClanQuestRewardRecipient.ACTOR -> "участнику, закрывшему цель"
            ua.inventorytype.pnclans.impl.config.ClanQuestRewardRecipient.LEADER -> "лидеру клана"
            ua.inventorytype.pnclans.impl.config.ClanQuestRewardRecipient.ONLINE_MEMBERS -> "всем участникам онлайн"
        }
        return quest.rewards.map { action ->
            val reward = when (action) {
                is GiveItemAction -> "${action.amount} шт. ${action.item}"
                is ItemRewardAction -> "${action.amount} шт. ${action.name ?: action.item}"
                is AddMmrAction -> "+${action.amount} MMR"
                is ConsoleCommandAction -> "серверная награда"
                else -> "особый эффект"
            }
            format(player, " &7- &#FFD700$reward &8• &f$recipient", placeholders)
        }
    }

    private fun entityName(entityType: String): String = when (entityType.uppercase()) {
        "ZOMBIE" -> "зомби"
        "SKELETON" -> "скелеты"
        "HUSK" -> "кадавры"
        "DROWNED" -> "утопленники"
        "BLAZE" -> "ифриты"
        "GHAST" -> "гасты"
        "PIGLIN_BRUTE" -> "брутальные пиглины"
        else -> entityType.lowercase().replace('_', ' ')
    }

    private fun number(value: Long): String = String.format(java.util.Locale.US, "%,d", value).replace(',', ' ')

    private fun progressBar(progress: Long, target: Long): String {
        val filled = ((progress * PROGRESS_BAR_LENGTH) / target.coerceAtLeast(1L)).toInt().coerceIn(0, PROGRESS_BAR_LENGTH)
        return "&#5EFD7D" + "■".repeat(filled) + "&8" + "■".repeat(PROGRESS_BAR_LENGTH - filled)
    }

    private fun objectiveLabel(objective: ClanQuestObjective): String = when (objective) {
        ClanQuestObjective.PLAYER_KILL -> "Убийства игроков"
        ClanQuestObjective.MOB_KILL -> "Убийства мобов"
        ClanQuestObjective.ACTIVITY_INTERVAL -> "Интервалы активности"
        ClanQuestObjective.MEMBER_JOINED -> "Новые участники"
        ClanQuestObjective.HOME_SET -> "Клановые дома"
        ClanQuestObjective.TREASURY_DEPOSIT -> "Пополнение казны"
        ClanQuestObjective.SHOP_PURCHASE -> "Покупки в магазине"
        ClanQuestObjective.BATTLE_PARTICIPATION -> "Участие в битвах"
        ClanQuestObjective.BATTLE_WIN -> "Победы в битвах"
        ClanQuestObjective.BATTLE_KILL -> "Убийства в битвах"
        ClanQuestObjective.BATTLE_DAMAGE -> "Урон в битвах"
    }

    private fun resetLabel(reset: ClanQuestReset): String = when (reset) {
        ClanQuestReset.NONE -> "Однократно"
        ClanQuestReset.DAILY -> "Ежедневно"
        ClanQuestReset.WEEKLY -> "Еженедельно"
    }

    private fun material(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun format(player: Player, value: String, placeholders: Map<String, String> = emptyMap()): String =
        clanService.plugin.configService.formatMessage(player, value, placeholders)

    private fun send(player: Player, actions: List<ua.inventorytype.pnclans.api.Action>, placeholders: Map<String, String>) {
        clanService.plugin.configService.send(player, actions, placeholders)
    }

    private fun isValid(slot: Int): Boolean = slot in 0 until inventorySize

    enum class QuestFilter(val label: String, val icon: String, val color: String, val material: String) {
        ALL("Все квесты", "✦", "&#FC65DF", "BOOK"),
        AVAILABLE("Доступные", "➥", "&#FF8702", "LIME_DYE"),
        IN_PROGRESS("В процессе", "⌚", "&#5EA9FD", "CLOCK"),
        COMPLETED("Завершённые", "✔", "&#5EFD7D", "EMERALD"),
        LOCKED("Заблокированные", "✖", "&#FC3737", "BARRIER");

        val displayName: String = "$color$icon $label"

        fun next(): QuestFilter = entries[(ordinal + 1) % entries.size]
    }

    private companion object {
        const val PROGRESS_BAR_LENGTH = 10
        val DEFAULT_ENTRY_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43)
    }
}
