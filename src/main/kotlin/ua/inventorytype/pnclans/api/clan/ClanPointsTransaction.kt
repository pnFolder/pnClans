package ua.inventorytype.pnclans.api.clan

import java.util.UUID

/** A persisted change to the clan reward-points balance. */
data class ClanPointsTransaction(
    val type: ClanPointsTransactionType,
    val source: ClanPointsSource,
    val amount: Long,
    val balanceAfter: Long,
    val timestamp: Long,
    /** Stable identifier used by administrator history and rollback commands. */
    val id: String = UUID.randomUUID().toString(),
    /** Player/admin/plugin actor that caused the operation when known. */
    val actor: String? = null,
    /** Optional target (victim, product, quest, etc.) related to the operation. */
    val target: String? = null,
    /** Human-readable administrative or anti-farm audit reason. */
    val reason: String? = null,
    /** Transaction being reversed when this entry represents a rollback. */
    val relatedTransactionId: String? = null
)

enum class ClanPointsTransactionType {
    AWARD,
    SPEND,
    ADMIN_ADJUSTMENT
}

/** Typed origin of a points operation. Keep gameplay sources here instead of free-form strings. */
enum class ClanPointsSource {
    PLAYER_KILL,
    MOB_KILL,
    ACTIVITY,
    BATTLE,
    QUEST,
    SHOP,
    ADMIN
}
