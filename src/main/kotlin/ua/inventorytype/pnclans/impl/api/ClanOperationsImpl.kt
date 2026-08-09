package ua.inventorytype.pnclans.impl.api

import org.bukkit.Location
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.operation.ClanOperationResult
import ua.inventorytype.pnclans.api.operation.ClanOperations
import ua.inventorytype.pnclans.impl.clan.ClanService

/** Public-operation adapter that keeps add-ons outside implementation packages. */
internal class ClanOperationsImpl(private val service: ClanService) : ClanOperations {
    override fun changeRole(clan: Clan, member: User, newRole: ClanRole): ClanOperationResult =
        service.changeMemberRole(clan, member, newRole)

    override fun transferLeadership(clan: Clan, currentLeader: User, newLeader: User): ClanOperationResult =
        service.transferLeadership(clan, currentLeader, newLeader)

    override fun changeSetting(clan: Clan, setting: ClanSetting, newValue: Boolean): ClanOperationResult =
        service.changeSetting(clan, setting, newValue)

    override fun setHome(clan: Clan, actor: Player, homeId: String, location: Location): ClanOperationResult =
        service.setClanHome(clan, actor, homeId, location)

    override fun deleteHome(clan: Clan, actor: Player, homeId: String): ClanOperationResult =
        service.deleteClanHome(clan, actor, homeId)

    override fun openChest(clan: Clan, player: Player): ClanOperationResult =
        service.openClanChest(player, clan)
}
