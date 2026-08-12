package ua.inventorytype.pnclans.api.clan

/** Persisted clan-wide state for one configured quest. */
data class ClanQuestProgress(
    val progress: Long = 0L,
    val completed: Boolean = false,
    val completedAt: Long = 0L,
    val completionCount: Int = 0,
    val cycleKey: String = ""
)
