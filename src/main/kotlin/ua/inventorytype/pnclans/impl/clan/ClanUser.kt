package ua.inventorytype.pnclans.impl.clan

import ua.inventorytype.pnclans.api.User
import java.util.UUID

/**
 * Concrete data implementation of a clan member.
 *
 * @property uuid The unique UUID identifier of the member.
 * @property playerName The current in-game nickname of the member, which automatically updates upon server join.
 */
data class ClanUser(
    override val uuid: UUID,
    override var playerName: String
) : User
