package ua.inventorytype.pnclans.api.operation

import org.bukkit.Location
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting

/**
 * Event-aware state-changing operations for external plugins.
 *
 * Use this service instead of mutating a [Clan] directly when an operation has a public
 * lifecycle event. Every successful operation is persisted before [ClanOperationResult.Success]
 * is returned. Calls must run on the Bukkit server thread.
 */
interface ClanOperations {
    fun changeRole(clan: Clan, member: User, newRole: ClanRole): ClanOperationResult
    fun transferLeadership(clan: Clan, currentLeader: User, newLeader: User): ClanOperationResult
    fun changeSetting(clan: Clan, setting: ClanSetting, newValue: Boolean): ClanOperationResult
    fun setHome(clan: Clan, actor: Player, homeId: String, location: Location): ClanOperationResult
    fun deleteHome(clan: Clan, actor: Player, homeId: String): ClanOperationResult
    fun openChest(clan: Clan, player: Player): ClanOperationResult
}
