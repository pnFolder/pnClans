package ua.inventorytype.pnclans

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap
import ua.inventorytype.pnclans.impl.ClanServices
import ua.inventorytype.pnclans.impl.ClansModule
import ua.inventorytype.pnclans.impl.integration.Log
import ua.inventorytype.pnclans.impl.integration.PnLibraryIntegration
import ua.inventorytype.pnclans.impl.startup.PacketEventsLifecycle

class BukkitPlugin : JavaPlugin() {
    val services = ClanServices(this)
    private var startupBlocked = false
    private var integration: PnLibraryIntegration? = null

    override fun onLoad() {
        Log.fallback(logger)
        val ready = runCatching { PnLibraryBootstrap.ensureInstalled(this, PnLibraryIntegration.MINIMUM_LIBRARY_VERSION) }
            .onFailure { Log.error("Не удалось автоматически подготовить pnLibrary. pnClans не будет запущен.", it) }
            .getOrDefault(false)
        if (!ready) {
            startupBlocked = true
            return
        }
        PacketEventsLifecycle.load(this)
    }

    override fun onEnable() {
        if (startupBlocked) return disable()
        if (server.pluginManager.getPlugin("pnLibrary")?.isEnabled != true) {
            Log.error("pnLibrary не включилась; проверьте её сообщение запуска. pnClans не будет запущен.")
            return disable()
        }
        PacketEventsLifecycle.start()
        Log.info("[pnClans] Enabled ${description.version} on ${server.version}; PacketEvents initialized=${PacketEventsLifecycle.isStarted()}")

        val link = PnLibraryIntegration(this)
        integration = link
        // Enabling the module starts the services and registers the listeners and the command; see ClansModule.
        link.registerModule(ClansModule(this, link))
    }

    override fun onDisable() {
        // Closing the registration disables the module first, which saves the clans and gives back listeners and command.
        integration?.close()
        PacketEventsLifecycle.stop()
    }

    private fun disable() = server.pluginManager.disablePlugin(this)
}
