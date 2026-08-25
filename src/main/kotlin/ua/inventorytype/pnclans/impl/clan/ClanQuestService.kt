package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanQuestProgress
import ua.inventorytype.pnclans.api.event.ClanQuestCompleteEvent
import ua.inventorytype.pnclans.api.event.ClanQuestProgressEvent
import ua.inventorytype.pnclans.impl.config.ClanQuestConfig
import ua.inventorytype.pnclans.impl.config.ClanQuestObjective
import ua.inventorytype.pnclans.impl.config.ClanQuestReset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

internal enum class ClanQuestStatus(val label: String, val icon: String) {
    AVAILABLE("&#FF8702Доступен", "&#FF8702➥"),
    IN_PROGRESS("&#5EA9FDВ процессе", "&#5EA9FD⌚"),
    COMPLETED("&#5EFD7DЗавершён", "&#5EFD7D✔"),
    LOCKED("&#FC3737Заблокирован", "&#FC3737✖")
}

/** Owns clan-wide quest progress, completion, rewards, and reset cycles. */
internal class ClanQuestService(private val plugin: BukkitPlugin) {
    private val rewardQueue = ClanQuestRewardQueue(plugin)

    fun state(clan: Clan, questId: String): ClanQuestProgress {
        val quest = plugin.configService.quests.quests[questId]
        val current = clan.questProgress[questId] ?: ClanQuestProgress()
        if (quest == null || !quest.repeatable || quest.reset == ClanQuestReset.NONE) return current
        val cycle = cycleKey(quest.reset)
        return if (current.cycleKey == cycle) current else current.copy(progress = 0L, completed = false, cycleKey = cycle)
    }

    fun isCompleted(clan: Clan, questId: String): Boolean = state(clan, questId).completed

    /** Returns whether a quest has ever been completed, including a previous repeat cycle. */
    fun hasCompletedAtLeastOnce(clan: Clan, questId: String): Boolean {
        val progress = clan.questProgress[questId] ?: return false
        return progress.completed || progress.completionCount > 0
    }

    /** Counts all completed quest cycles for clan-level progression. */
    fun completedQuestCount(clan: Clan): Int = clan.questProgress.values.sumOf {
        maxOf(it.completionCount, if (it.completed) 1 else 0)
    }

    /** Shop requirements use the current quest cycle; campaign prerequisites intentionally use completion history. */
    fun requiredQuestsMet(clan: Clan, questIds: Set<String>): Boolean =
        !plugin.configService.quests.enabled || questIds.all { questId ->
            plugin.configService.quests.quests.containsKey(questId) && isCompleted(clan, questId)
        }

    fun status(clan: Clan, questId: String): ClanQuestStatus {
        if (!plugin.configService.quests.enabled) return ClanQuestStatus.LOCKED
        val quest = plugin.configService.quests.quests[questId] ?: return ClanQuestStatus.LOCKED
        if (quest.prerequisites.any { !hasCompletedAtLeastOnce(clan, it) }) return ClanQuestStatus.LOCKED
        val progress = state(clan, questId)
        if (progress.completed) return ClanQuestStatus.COMPLETED
        if (progress.progress > 0L) return ClanQuestStatus.IN_PROGRESS
        return ClanQuestStatus.AVAILABLE
    }

    fun recordPlayerKill(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.PLAYER_KILL, actor, 1L)
    }

    fun recordMobKill(clan: Clan, actor: Player, entityType: String) {
        advance(clan, ClanQuestObjective.MOB_KILL, actor, 1L, entityType)
    }

    fun recordActivityInterval(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.ACTIVITY_INTERVAL, actor, 1L)
    }

    fun recordMemberJoined(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.MEMBER_JOINED, actor, 1L)
    }

    fun recordTreasuryDeposit(clan: Clan, actor: Player, amount: Double) {
        advance(clan, ClanQuestObjective.TREASURY_DEPOSIT, actor, amount.toLong().coerceAtLeast(0L))
    }

    fun recordHomeSet(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.HOME_SET, actor, 1L)
    }

    fun recordShopPurchase(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.SHOP_PURCHASE, actor, 1L)
    }

    fun recordBattleParticipation(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.BATTLE_PARTICIPATION, actor, 1L)
    }

    fun recordBattleWin(clan: Clan, actor: Player?) {
        advance(clan, ClanQuestObjective.BATTLE_WIN, actor, 1L)
    }

    fun recordBattleKill(clan: Clan, actor: Player) {
        advance(clan, ClanQuestObjective.BATTLE_KILL, actor, 1L)
    }

    fun recordBattleDamage(clan: Clan, actor: Player?, amount: Long) {
        advance(clan, ClanQuestObjective.BATTLE_DAMAGE, actor, amount)
    }

    fun deliverPendingRewards(player: Player) {
        rewardQueue.processForPlayer(player)
    }

    fun deliverPendingRewardsForOnlinePlayers() {
        rewardQueue.processOnlinePlayers()
    }

    private fun advance(
        clan: Clan,
        objective: ClanQuestObjective,
        actor: Player?,
        delta: Long,
        entityType: String? = null
    ) {
        if (!plugin.configService.quests.enabled) return
        if (delta <= 0L) return
        val quests = plugin.configService.quests.quests.filter { (_, quest) ->
            quest.objective == objective &&
                quest.target > 0L &&
                (quest.objective != ClanQuestObjective.MOB_KILL || quest.entityTypes.isEmpty() || quest.entityTypes.any { it.equals(entityType, ignoreCase = true) }) &&
                quest.prerequisites.all { hasCompletedAtLeastOnce(clan, it) }
        }
        if (quests.isEmpty()) return

        var changed = false
        quests.forEach { (questId, quest) ->
            val current = state(clan, questId)
            if (current.completed) return@forEach

            val target = quest.target.coerceAtLeast(1L)
            val nextProgress = (current.progress + delta).coerceAtMost(target)
            if (nextProgress <= current.progress) return@forEach

            val progressEvent = ClanQuestProgressEvent(clan, questId, nextProgress, target, actor)
            Bukkit.getPluginManager().callEvent(progressEvent)
            if (progressEvent.isCancelled) return@forEach

            val acceptedProgress = progressEvent.progress.coerceIn(current.progress, target)
            if (acceptedProgress >= target) {
                if (!completeQuest(clan, questId, quest, current, actor, target)) return@forEach
                changed = true
                return@forEach
            }

            val next = current.copy(progress = acceptedProgress, cycleKey = currentCycle(quest))
            clan.setQuestProgress(questId, next)
            if (!plugin.clanService.saveClan(clan)) {
                clan.setQuestProgress(questId, current)
                return@forEach
            }
            changed = true
        }

        if (changed) {
            clan.users.forEach { plugin.clanService.notifyClanUpdated(it.uuid) }
        }
    }

    private fun completeQuest(
        clan: Clan,
        questId: String,
        quest: ClanQuestConfig,
        current: ClanQuestProgress,
        actor: Player?,
        target: Long
    ): Boolean {
        val completeEvent = ClanQuestCompleteEvent(clan, questId, actor)
        Bukkit.getPluginManager().callEvent(completeEvent)
        if (completeEvent.isCancelled) return false

        val completionCount = current.completionCount + 1
        if (!rewardQueue.enqueue(clan, questId, completionCount, quest, actor)) {
            plugin.logger.severe(
                "[pnClans] Quest '$questId' reached its target for clan ${clan.id}, but reward bookkeeping could not be persisted. Completion was not committed."
            )
            return false
        }

        val next = current.copy(
            progress = target,
            completed = true,
            completedAt = System.currentTimeMillis(),
            completionCount = completionCount,
            cycleKey = currentCycle(quest)
        )
        clan.setQuestProgress(questId, next)
        if (!plugin.clanService.saveClan(clan)) {
            clan.setQuestProgress(questId, current)
            rewardQueue.cancel(clan.id, questId, completionCount)
            return false
        }

        rewardQueue.processForClan(clan)
        sendCompletionMessage(clan, questId, quest, target)
        return true
    }

    private fun sendCompletionMessage(clan: Clan, questId: String, quest: ClanQuestConfig, target: Long) {
        val placeholders = mapOf(
            "quest" to questId,
            "quest_name" to quest.name,
            "clan" to clan.name,
            "progress" to target.toString(),
            "target" to target.toString(),
            "reward_points" to quest.rewardPoints.toString()
        )
        clan.users.mapNotNull { Bukkit.getPlayer(it.uuid) }
            .forEach { recipient ->
                plugin.configService.send(recipient, plugin.configService.messages.quests.completed, placeholders)
            }
    }

    private fun currentCycle(quest: ClanQuestConfig): String =
        if (quest.repeatable) cycleKey(quest.reset) else ""

    private fun cycleKey(reset: ClanQuestReset): String = when (reset) {
        ClanQuestReset.NONE -> ""
        ClanQuestReset.DAILY -> LocalDate.now(ZoneOffset.UTC).toString()
        ClanQuestReset.WEEKLY -> LocalDate.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toString()
    }
}
