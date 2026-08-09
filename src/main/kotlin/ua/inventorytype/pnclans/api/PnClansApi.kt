package ua.inventorytype.pnclans.api

import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.api.addon.AddonRegistry
import ua.inventorytype.pnclans.api.command.ClanSubcommandRegistry
import ua.inventorytype.pnclans.api.menu.ClanMenuRegistry
import ua.inventorytype.pnclans.api.clan.ClanPoints
import ua.inventorytype.pnclans.api.operation.ClanOperations
import ua.inventorytype.pnclans.api.gui.ClanAddonGuiRegistry

/**
 * Stable entry point for external Bukkit plugins.
 *
 * Obtain the service after pnClans is enabled; never depend on implementation packages.
 */
interface PnClansApi {
    /** Version of the public pnClans API implemented by this service. */
    val apiVersion: Int
    /** Read and persistence access to clans. */
    val clans: ClanRepository
    /** Shared clan reward-points operations. */
    val points: ClanPoints
    /** Event-aware mutations for roles, settings, homes, and the clan chest. */
    val operations: ClanOperations
    /** Config-bound add-on GUI item providers and click actions. */
    val gui: ClanAddonGuiRegistry
    /** Placeholder registration and text resolution. */
    val placeholders: PlaceholderRegistry
    /** pnClans add-on lifecycle registry. */
    val addons: AddonRegistry
    /** Registry for add-on `/clan` subcommands. */
    val subcommands: ClanSubcommandRegistry
    /** Registry for add-on main-menu buttons. */
    val menus: ClanMenuRegistry
}

object PnClansProvider {
    const val API_VERSION = 4

    fun get(): PnClansApi? = Bukkit.getServicesManager()
        .getRegistration(PnClansApi::class.java)
        ?.provider

    fun require(): PnClansApi = get()
        ?: error("pnClans API is unavailable. Add depend: [pnClans] and access it after onEnable.")
}
