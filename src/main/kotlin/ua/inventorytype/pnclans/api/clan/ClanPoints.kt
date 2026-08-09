package ua.inventorytype.pnclans.api.clan

/**
 * Public reward-points operations for add-ons.
 *
 * Every successful operation is persisted, recorded in the clan points log, and emits
 * [ua.inventorytype.pnclans.api.event.ClanPointsTransactionEvent] before the balance changes.
 * All calls must be made from the Bukkit server thread.
 */
interface ClanPoints {
    /** Awards a positive number of reward points to [clan]. */
    fun award(clan: Clan, amount: Long, source: ClanPointsSource): Boolean

    /** Spends a positive number of reward points if [clan] has a sufficient balance. */
    fun spend(clan: Clan, amount: Long, source: ClanPointsSource): Boolean
}
