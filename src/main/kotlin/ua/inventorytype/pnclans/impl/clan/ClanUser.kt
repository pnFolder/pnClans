package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.User
import java.util.UUID

/**
 * Concrete data implementation of a clan member.
 *
 * Tracks persistent in-clan performance so that the player's profile, level rewards, and MMR
 * gains can be displayed consistently in the GUI and progression screens.
 *
 * @property uuid The unique UUID identifier of the member.
 * @property playerName The current in-game nickname of the member, which automatically updates upon server join.
 * @property kills Total PvP kills attributed to this clan member while in any clan.
 * @property deaths Total PvP deaths attributed to this clan member while in any clan.
 * @property playtimeSeconds Total time in seconds that this member has been in any clan.
 * @property points Personal contribution points used to unlock clan-wide rewards.
 */
data class ClanUser(
    override val uuid: UUID,
    override var playerName: String,
    var kills: Int = 0,
    var deaths: Int = 0,
    var playtimeSeconds: Long = 0L,
    var points: Int = 0
) : User
