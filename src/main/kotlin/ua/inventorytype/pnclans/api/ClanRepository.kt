package ua.inventorytype.pnclans.api

import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import java.util.UUID

/** Stable read and persistence access for pnClans add-ons. */
interface ClanRepository {
    fun all(): Collection<Clan>
    fun find(idOrName: String): Clan?
    fun findByMember(memberId: UUID): Clan?
    fun findByMember(player: Player): Clan? = findByMember(player.uniqueId)
    fun save(clan: Clan)
}
