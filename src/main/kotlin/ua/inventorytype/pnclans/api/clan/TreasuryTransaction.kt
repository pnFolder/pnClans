package ua.inventorytype.pnclans.api.clan

/** A persisted financial operation in a clan treasury. */
data class TreasuryTransaction(
    val type: TreasuryTransactionType,
    val playerName: String,
    val amount: Double,
    val timestamp: Long
)

enum class TreasuryTransactionType {
    DEPOSIT,
    WITHDRAW,
    UPGRADE
}
