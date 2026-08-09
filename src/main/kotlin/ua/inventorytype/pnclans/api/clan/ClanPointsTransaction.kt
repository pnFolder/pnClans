package ua.inventorytype.pnclans.api.clan

/** A persisted change to the clan reward-points balance. */
data class ClanPointsTransaction(
    val type: ClanPointsTransactionType,
    val source: String,
    val amount: Long,
    val balanceAfter: Long,
    val timestamp: Long
)

enum class ClanPointsTransactionType {
    AWARD,
    SPEND,
    ADMIN_ADJUSTMENT
}
