package ua.inventorytype.pnclans.api.operation

/** Result of a state-changing pnClans operation. */
sealed interface ClanOperationResult {
    /** The requested mutation completed and was persisted. */
    data object Success : ClanOperationResult

    /** The mutation was not applied. Inspect [reason] instead of parsing player messages. */
    data class Rejected(val reason: ClanOperationRejection) : ClanOperationResult

    val isSuccess: Boolean
        get() = this is Success
}

/** Machine-readable reasons for a rejected clan operation. */
enum class ClanOperationRejection {
    CANCELLED_BY_EVENT,
    MEMBER_NOT_FOUND,
    HOME_NOT_FOUND,
    ALREADY_IN_REQUESTED_STATE,
    INVALID_ROLE_TRANSITION
}
