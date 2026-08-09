package ua.inventorytype.pnclans.impl.api

import ua.inventorytype.pnclans.api.ClanRepository
import ua.inventorytype.pnclans.api.PnClansApi
import ua.inventorytype.pnclans.api.PnClansProvider
import ua.inventorytype.pnclans.api.addon.AddonRegistry
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPoints
import ua.inventorytype.pnclans.api.command.ClanSubcommandRegistry
import ua.inventorytype.pnclans.api.menu.ClanMenuRegistry
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.api.operation.ClanOperations
import ua.inventorytype.pnclans.api.gui.ClanAddonGuiRegistry
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.clan.ClanPointsService
import java.util.UUID

internal class PnClansApiImpl(
    private val service: ClanService,
    override val points: ClanPoints
) : PnClansApi {
    override val apiVersion: Int = PnClansProvider.API_VERSION
    override val placeholders: PlaceholderRegistry
        get() = service.plugin.placeholderRegistry
    override val addons: AddonRegistry by lazy { AddonRegistryImpl(this, service.plugin.dataFolder) }
    override val subcommands: ClanSubcommandRegistry by lazy { ClanSubcommandRegistryImpl(service) }
    override val menus: ClanMenuRegistry by lazy { ClanMenuRegistryImpl() }
    override val operations: ClanOperations by lazy { ClanOperationsImpl(service) }
    override val gui: ClanAddonGuiRegistry by lazy { ClanAddonGuiRegistryImpl() }

    override val clans: ClanRepository = object : ClanRepository {
        override fun all(): Collection<Clan> = service.getAllClans()
        override fun find(idOrName: String): Clan? = service.getClanByName(idOrName)
        override fun findByMember(memberId: UUID): Clan? = service.getClanByUuid(memberId)
        override fun save(clan: Clan) = service.saveClan(clan)
    }
}
