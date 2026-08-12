package ua.inventorytype.pnclans.api.clan

/** A persisted change to the clan reward-points balance. */
data class ClanPointsTransaction(
    val type: ClanPointsTransactionType,
    val source: ClanPointsSource,
    val amount: Long,
    val balanceAfter: Long,
    val timestamp: Long
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
