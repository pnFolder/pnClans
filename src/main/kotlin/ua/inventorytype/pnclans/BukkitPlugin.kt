package ua.inventorytype.pnclans

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap
import ua.inventorytype.pnclans.impl.ClanServices
import ua.inventorytype.pnclans.impl.command.ClanCommand
import ua.inventorytype.pnclans.impl.integration.Log
import ua.inventorytype.pnclans.impl.integration.PnLibraryIntegration
import ua.inventorytype.pnclans.impl.listener.ClanListener
import ua.inventorytype.pnclans.impl.startup.PacketEventsLifecycle

class BukkitPlugin : JavaPlugin() {
    val services = ClanServices(this)
    private var startupBlocked = false

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

        services.start()
        registerListeners()
        registerCommand()
        connectPlaceholders()
    }

    override fun onDisable() {
        val savedClansCount = services.clansCount()
        services.stopClans()
        PacketEventsLifecycle.stop()
        services.close(savedClansCount)
    }

    private fun disable() = server.pluginManager.disablePlugin(this)

    private fun registerListeners() = with(services) {
        server.pluginManager.registerEvents(guiListener, this@BukkitPlugin)
        server.pluginManager.registerEvents(ClanListener(this@BukkitPlugin), this@BukkitPlugin)
        server.pluginManager.registerEvents(clanHighlightService, this@BukkitPlugin)
        clanHighlightService.syncAll()
        clanQuestService.deliverPendingRewardsForOnlinePlayers()
    }

    private fun registerCommand() {
        val command = ClanCommand(this, services.inviteService)
        getCommand("clan")?.let {
            it.setExecutor(command)
            it.tabCompleter = command
        }
    }

    private fun connectPlaceholders() = with(services) {
        val papiConnected = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        pnLibraryIntegration.started(
            economyConnected = economyConnected,
            papiConnected = papiConnected,
            loadedClansCount = clansCount(),
            loadedAddonsCount = addonsLoaded,
        )
    }
}
