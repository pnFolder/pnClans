package ua.inventorytype.pnclans.impl

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.impl.command.ClanCommand
import ua.inventorytype.pnclans.impl.integration.Log
import ua.inventorytype.pnclans.impl.integration.PnLibraryIntegration
import ua.inventorytype.pnclans.impl.listener.ClanListener

/**
 * The only module of pnClans. While it is enabled its services run and its listeners and `/clan` are registered; when
 * an administrator switches it off, or it restarts, all of that is taken back, so nothing keeps working half-way.
 */
class ClansModule(
    private val plugin: BukkitPlugin,
    private val integration: PnLibraryIntegration,
) : PluginModule {
    private var listeners: List<Listener> = emptyList()

    /** Loaded by pnLibrary before every start; it does not pull transitive dependencies, so they are listed. Kotlin stdlib stays in plugin.yml: `BukkitPlugin` itself needs it. */
    override fun libraries(): List<ModuleLibrary> = listOf(
        ModuleLibrary.of("org.xerial:sqlite-jdbc:3.46.0.0"),
        ModuleLibrary.of("com.charleskorn.kaml:kaml-jvm:0.67.0"),
        ModuleLibrary.of("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.3"),
        ModuleLibrary.of("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3"),
        ModuleLibrary.of("it.krzeminski:snakeyaml-engine-kmp-jvm:3.0.3"),
        ModuleLibrary.of("com.squareup.okio:okio-jvm:3.9.1"),
    )

    override fun onEnable(context: ModuleContext) {
        integration.attach(context)
        try {
            plugin.services.start(integration)
            registerListeners()
            registerCommand()
            connectPlaceholders()
        } catch (failure: Throwable) {
            release()
            throw failure
        }
    }

    override fun onDisable(context: ModuleContext) {
        val savedClans = plugin.services.clansCount()
        release()
        context.lifecycle.disabled()
            .ok("Хранилище", "сохранено кланов: $savedClans")
            .ok("Диагностика", "регистрация закрыта")
            .ok("Метрики", "сессия остановлена")
            .show()
        Log.detach()
    }

    /** Gives back the listeners and the command, then stops the services; safe after a partial start. */
    private fun release() {
        listeners.forEach(HandlerList::unregisterAll)
        listeners = emptyList()
        plugin.getCommand("clan")?.let {
            it.setExecutor { sender, _, _, _ -> disabledAnswer(sender) }
            it.tabCompleter = null
        }
        plugin.services.stop()
    }

    private fun disabledAnswer(sender: CommandSender): Boolean {
        sender.sendMessage("pnClans сейчас выключен. Включить: /pn modules")
        return true
    }

    private fun registerListeners() = with(plugin.services) {
        listeners = listOf(guiListener, ClanListener(plugin), clanHighlightService)
        listeners.forEach { plugin.server.pluginManager.registerEvents(it, plugin) }
        clanHighlightService.syncAll()
        clanQuestService.deliverPendingRewardsForOnlinePlayers()
    }

    private fun registerCommand() {
        val command = ClanCommand(plugin, plugin.services.inviteService)
        plugin.getCommand("clan")?.let {
            it.setExecutor(command)
            it.tabCompleter = command
        }
    }

    private fun connectPlaceholders() = with(plugin.services) {
        pnLibraryIntegration.started(
            economyConnected = economyConnected,
            papiConnected = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"),
            loadedClansCount = clansCount(),
            loadedAddonsCount = addonsLoaded,
        )
    }
}
