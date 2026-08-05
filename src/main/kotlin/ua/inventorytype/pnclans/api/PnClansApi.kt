package ua.inventorytype.pnclans.api

import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.api.addon.AddonRegistry
import ua.inventorytype.pnclans.api.command.ClanSubcommandRegistry
import ua.inventorytype.pnclans.api.menu.ClanMenuRegistry

/**
 * Stable entry point for external Bukkit plugins.
 *
 * Obtain the service after pnClans is enabled; never depend on implementation packages.
 */
interface PnClansApi {
    val apiVersion: Int
    val clans: ClanRepository
    val placeholders: PlaceholderRegistry
    val addons: AddonRegistry
    val subcommands: ClanSubcommandRegistry
    val menus: ClanMenuRegistry
}

object PnClansProvider {
    const val API_VERSION = 1

    fun get(): PnClansApi? = Bukkit.getServicesManager()
        .getRegistration(PnClansApi::class.java)
        ?.provider

    fun require(): PnClansApi = get()
        ?: error("pnClans API is unavailable. Add depend: [pnClans] and access it after onEnable.")
}
