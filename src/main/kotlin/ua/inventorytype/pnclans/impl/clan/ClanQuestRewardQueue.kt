package ua.inventorytype.pnclans.impl.clan

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.ActionContext
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.impl.config.ClanQuestConfig
import ua.inventorytype.pnclans.impl.config.ClanQuestRewardRecipient
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level

@Serializable
private enum class QuestRewardDeliveryState {
    PENDING,
    CLAIMING,
    DELIVERED,
    FAILED
}

@Serializable
private data class QuestRewardDelivery(
    val id: String,
    val clanId: String,
    val questId: String,
    val completionCount: Int,
    val rewardPoints: Long,
    var pointsState: QuestRewardDeliveryState,
    val recipients: MutableMap<String, QuestRewardDeliveryState> = mutableMapOf(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
private data class QuestRewardLedger(
    val deliveries: MutableMap<String, QuestRewardDelivery> = mutableMapOf()
)

/**
 * Durable at-most-once delivery queue for quest rewards.
 *
 * Arbitrary configured actions may have irreversible side effects. A delivery is therefore persisted as
 * CLAIMING before execution. If the server crashes in that tiny window, the claim is marked FAILED on the
 * next load instead of being repeated and potentially duplicated.
 */
internal class ClanQuestRewardQueue(private val plugin: BukkitPlugin) {
    private val file = File(plugin.dataFolder, "quest-rewards.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var healthy = true
    private var ledger = loadLedger()

    init {
        recoverAmbiguousClaims()
    }

    fun enqueue(
        clan: Clan,
        questId: String,
        completionCount: Int,
        quest: ClanQuestConfig,
        actor: Player?
    ): Boolean {
        if (!healthy) return false
        val id = deliveryId(clan.id, questId, completionCount)
        if (ledger.deliveries.containsKey(id)) return true

        val recipientIds = if (quest.rewards.isEmpty()) {
            emptySet()
        } else {
            when (quest.rewardRecipient) {
                ClanQuestRewardRecipient.ACTOR -> setOfNotNull(actor?.uniqueId ?: clan.getLeader()?.uuid)
                ClanQuestRewardRecipient.LEADER -> setOfNotNull(clan.getLeader()?.uuid)
                ClanQuestRewardRecipient.ONLINE_MEMBERS -> clan.users
                    .mapNotNull { Bukkit.getPlayer(it.uuid)?.takeIf(Player::isOnline)?.uniqueId }
                    .toSet()
            }
        }

        val delivery = QuestRewardDelivery(
            id = id,
            clanId = clan.id,
            questId = questId,
            completionCount = completionCount,
            rewardPoints = quest.rewardPoints.coerceAtLeast(0L),
            pointsState = if (quest.rewardPoints > 0L) QuestRewardDeliveryState.PENDING else QuestRewardDeliveryState.DELIVERED,
            recipients = recipientIds.associate { it.toString() to QuestRewardDeliveryState.PENDING }.toMutableMap()
        )
        ledger.deliveries[id] = delivery
        if (saveLedger()) return true
        ledger.deliveries.remove(id)
        return false
    }

    fun cancel(clanId: String, questId: String, completionCount: Int) {
        if (!healthy) return
        val removed = ledger.deliveries.remove(deliveryId(clanId, questId, completionCount)) ?: return
        if (!saveLedger()) {
            ledger.deliveries[removed.id] = removed
        }
    }

    fun processForClan(clan: Clan) {
        if (!healthy) return
        ledger.deliveries.values
            .filter { it.clanId == clan.id }
            .map { it.id }
            .forEach { id -> processDelivery(id, clan, null) }
    }

    fun processForPlayer(player: Player) {
        if (!healthy) return
        val clan = plugin.clanService.getClanUser(player) ?: return
        val playerId = player.uniqueId.toString()
        ledger.deliveries.values
            .filter { it.clanId == clan.id && it.recipients[playerId] == QuestRewardDeliveryState.PENDING }
            .map { it.id }
            .forEach { id -> processDelivery(id, clan, player) }
    }

    fun processOnlinePlayers() {
        if (!healthy) return
        plugin.clanService.getAllClans().forEach(::processForClan)
    }

    private fun processDelivery(id: String, clan: Clan, onlyPlayer: Player?) {
        if (!healthy) return
        val delivery = ledger.deliveries[id] ?: return
        val progress = clan.questProgress[delivery.questId]
        if (progress == null || progress.completionCount < delivery.completionCount) {
            ledger.deliveries.remove(id)
            saveLedger()
            return
        }

        if (onlyPlayer == null) processPoints(delivery, clan)
        if (!healthy) return

        val quest = plugin.configService.quests.quests[delivery.questId]
        if (quest == null) {
            markPendingRecipientsFailed(delivery, "Quest '${delivery.questId}' no longer exists; queued action rewards require manual review.")
            return
        }

        if (onlyPlayer != null) {
            processRecipient(delivery, clan, quest, onlyPlayer)
        } else {
            delivery.recipients
                .filterValues { it == QuestRewardDeliveryState.PENDING }
                .keys
                .mapNotNull { uuid -> runCatching { java.util.UUID.fromString(uuid) }.getOrNull() }
                .mapNotNull(Bukkit::getPlayer)
                .filter(Player::isOnline)
                .forEach { player -> processRecipient(delivery, clan, quest, player) }
        }
        cleanupIfDelivered(delivery)
    }

    private fun processPoints(delivery: QuestRewardDelivery, clan: Clan) {
        if (delivery.pointsState != QuestRewardDeliveryState.PENDING || delivery.rewardPoints <= 0L) return

        delivery.pointsState = QuestRewardDeliveryState.CLAIMING
        if (!saveLedger()) {
            delivery.pointsState = QuestRewardDeliveryState.PENDING
            return
        }

        val awarded = plugin.clanPointsService.award(clan, delivery.rewardPoints, ClanPointsSource.QUEST)
        delivery.pointsState = if (awarded) QuestRewardDeliveryState.DELIVERED else QuestRewardDeliveryState.PENDING
        if (!saveLedger()) return

        if (!awarded) {
            plugin.logger.warning(
                "[pnClans] Quest reward points remain pending: clan=${clan.id}, quest=${delivery.questId}, amount=${delivery.rewardPoints}"
            )
        }
    }

    private fun processRecipient(
        delivery: QuestRewardDelivery,
        clan: Clan,
        quest: ClanQuestConfig,
        player: Player
    ) {
        val playerId = player.uniqueId.toString()
        if (delivery.recipients[playerId] != QuestRewardDeliveryState.PENDING) return

        delivery.recipients[playerId] = QuestRewardDeliveryState.CLAIMING
        if (!saveLedger()) {
            delivery.recipients[playerId] = QuestRewardDeliveryState.PENDING
            return
        }

        val placeholders = mapOf(
            "quest" to delivery.questId,
            "quest_name" to quest.name,
            "player" to player.name,
            "player_name" to player.name,
            "clan" to clan.name,
            "progress" to quest.target.toString(),
            "target" to quest.target.toString(),
            "reward_points" to delivery.rewardPoints.toString()
        )
        val context = ActionContext(player, plugin.placeholderRegistry, placeholders, plugin)
        val result = runCatching {
            quest.rewards.forEach { action -> action.execute(context) }
        }

        delivery.recipients[playerId] = if (result.isSuccess) {
            QuestRewardDeliveryState.DELIVERED
        } else {
            QuestRewardDeliveryState.FAILED
        }
        saveLedger()

        result.exceptionOrNull()?.let { error ->
            plugin.logger.log(
                Level.SEVERE,
                "[pnClans] Quest reward action failed after claim: clan=${clan.id}, quest=${delivery.questId}, player=${player.name}. Automatic retry is disabled to prevent duplicate rewards.",
                error
            )
        }
    }

    private fun markPendingRecipientsFailed(delivery: QuestRewardDelivery, reason: String) {
        var changed = false
        delivery.recipients.forEach { (uuid, state) ->
            if (state == QuestRewardDeliveryState.PENDING) {
                delivery.recipients[uuid] = QuestRewardDeliveryState.FAILED
                changed = true
            }
        }
        if (changed) {
            plugin.logger.severe("[pnClans] $reason delivery=${delivery.id}")
            saveLedger()
        }
    }

    private fun cleanupIfDelivered(delivery: QuestRewardDelivery) {
        if (delivery.pointsState != QuestRewardDeliveryState.DELIVERED) return
        if (delivery.recipients.values.any { it != QuestRewardDeliveryState.DELIVERED }) return
        ledger.deliveries.remove(delivery.id)
        saveLedger()
    }

    private fun recoverAmbiguousClaims() {
        if (!healthy) return
        var changed = false
        ledger.deliveries.values.forEach { delivery ->
            if (delivery.pointsState == QuestRewardDeliveryState.CLAIMING) {
                delivery.pointsState = QuestRewardDeliveryState.FAILED
                changed = true
                plugin.logger.severe(
                    "[pnClans] Ambiguous quest point reward found after restart: ${delivery.id}. It will not be retried automatically to prevent duplication."
                )
            }
            delivery.recipients.forEach { (uuid, state) ->
                if (state == QuestRewardDeliveryState.CLAIMING) {
                    delivery.recipients[uuid] = QuestRewardDeliveryState.FAILED
                    changed = true
                    plugin.logger.severe(
                        "[pnClans] Ambiguous quest action reward found after restart: ${delivery.id}, player=$uuid. It will not be retried automatically to prevent duplication."
                    )
                }
            }
        }
        if (changed) saveLedger()
    }

    private fun loadLedger(): QuestRewardLedger {
        if (!file.exists()) return QuestRewardLedger()
        return runCatching { json.decodeFromString<QuestRewardLedger>(file.readText()) }
            .onFailure { error ->
                healthy = false
                plugin.logger.log(
                    Level.SEVERE,
                    "Cannot read ${file.name}. Quest completion rewards are disabled until the file is repaired and the plugin is reloaded.",
                    error
                )
            }
            .getOrElse { QuestRewardLedger() }
    }

    private fun saveLedger(): Boolean {
        if (!healthy) return false
        val temp = File(file.parentFile, "${file.name}.tmp")
        return runCatching {
            file.parentFile?.mkdirs()
            temp.writeText(json.encodeToString(ledger))
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                temp.delete()
                healthy = false
                plugin.logger.log(
                    Level.SEVERE,
                    "Cannot persist ${file.name}. Quest reward delivery is disabled to avoid duplicates.",
                    error
                )
                false
            }
        )
    }

    private fun deliveryId(clanId: String, questId: String, completionCount: Int): String =
        "$clanId:$questId:$completionCount"
}
