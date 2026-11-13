package ua.inventorytype.pnclans.impl

import org.bukkit.plugin.ServicePriority
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.PnClansApi
import ua.inventorytype.pnclans.api.clan.ClanPoints
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.impl.analytics.ErrorReporter
import ua.inventorytype.pnclans.impl.api.PnClansApiImpl
import ua.inventorytype.pnclans.impl.clan.ClanActivityPointsService
import ua.inventorytype.pnclans.impl.clan.ClanBattleService
import ua.inventorytype.pnclans.impl.clan.ClanHighlightService
import ua.inventorytype.pnclans.impl.clan.ClanInviteService
import ua.inventorytype.pnclans.impl.clan.ClanPointsAntiFarmService
import ua.inventorytype.pnclans.impl.clan.ClanPointsService
import ua.inventorytype.pnclans.impl.clan.ClanQuestService
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ConfigReload
import ua.inventorytype.pnclans.impl.config.ConfigService
import ua.inventorytype.pnclans.impl.economy.EconomyService
import ua.inventorytype.pnclans.impl.integration.PnLibraryIntegration
import ua.inventorytype.pnclans.impl.inventory.listener.GuiListener
import ua.inventorytype.pnclans.impl.placeholder.ClanPlaceholderValues
import ua.inventorytype.pnclans.impl.shop.ClanShopService
import ua.inventorytype.pnclans.impl.teleport.TeleportService
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt
import ua.inventorytype.pnclans.impl.ux.TimedBossBarService

/**
 * Every service of the plugin in one place. Services reach each other through `plugin.services`, so the fields are
 * filled by [start] in dependency order and are valid until [stopClans].
 */
class ClanServices(private val plugin: BukkitPlugin) {
    lateinit var economyService: EconomyService
        private set
    lateinit var configService: ConfigService
        private set
    internal lateinit var pnLibraryIntegration: PnLibraryIntegration
        private set
    val placeholderValues = ClanPlaceholderValues(plugin)
    lateinit var placeholderRegistry: PlaceholderRegistry
        private set
    lateinit var api: PnClansApi
        private set
    lateinit var clanService: ClanService
        private set
    lateinit var clanPointsService: ClanPoints
        private set
    internal lateinit var clanPointsAntiFarmService: ClanPointsAntiFarmService
        private set
    lateinit var clanActivityPointsService: ClanActivityPointsService
        private set
    internal lateinit var clanQuestService: ClanQuestService
        private set
    internal lateinit var clanBattleService: ClanBattleService
        private set
    internal lateinit var clanShopService: ClanShopService
        private set
    lateinit var clanHighlightService: ClanHighlightService
        private set
    lateinit var inviteService: ClanInviteService
        private set
    lateinit var teleportService: TeleportService
        private set
    lateinit var timedBossBarService: TimedBossBarService
        private set
    lateinit var guiListener: GuiListener
        private set

    var economyConnected = false
        private set
    var addonsLoaded = 0
        private set

    fun start() {
        economyService = EconomyService()
        economyConnected = economyService.setup()
        configService = ConfigService(plugin)
        ConfigReload.run(plugin, configService)
        pnLibraryIntegration = PnLibraryIntegration(plugin)
        ErrorReporter.init(plugin)

        pnLibraryIntegration.registerPlaceholderTree(placeholderValues)
        placeholderRegistry = PlaceholderRegistry(pnLibraryIntegration.placeholders)
        clanService = ClanService(plugin)
        clanPointsService = ClanPointsService(clanService)
        clanPointsAntiFarmService = ClanPointsAntiFarmService(plugin)
        clanActivityPointsService = ClanActivityPointsService(plugin)
        clanQuestService = ClanQuestService(plugin)
        clanBattleService = ClanBattleService(plugin)
        clanShopService = ClanShopService(plugin)
        api = PnClansApiImpl(clanService, clanPointsService)
        plugin.server.servicesManager.register(PnClansApi::class.java, api, plugin, ServicePriority.Normal)

        addonsLoaded = api.addons.loadDirectory().size
        clanHighlightService = ClanHighlightService(plugin)
        inviteService = ClanInviteService(clanService)
        teleportService = TeleportService(plugin)
        timedBossBarService = TimedBossBarService(plugin)
        guiListener = GuiListener(plugin)
    }

    /** Stops clan services in reverse dependency order and saves clans last. Safe to call after a partial start. */
    fun stopClans() {
        if (::api.isInitialized) plugin.server.servicesManager.unregister(PnClansApi::class.java, api)
        ChatInputPrompt.shutdown()
        if (::timedBossBarService.isInitialized) timedBossBarService.clearAll()
        if (::clanActivityPointsService.isInitialized) clanActivityPointsService.shutdown()
        if (::clanBattleService.isInitialized) clanBattleService.shutdown()
        if (::inviteService.isInitialized) inviteService.clear()
        if (::guiListener.isInitialized) guiListener.forceCloseAll()
        if (::clanHighlightService.isInitialized) clanHighlightService.shutdown()
        if (::clanService.isInitialized) {
            clanService.saveAll(finalizeSessions = true)
            clanService.storage.close()
        }
    }

    /** Releases configs and the library link after [stopClans]. */
    fun close(savedClansCount: Int) {
        ErrorReporter.shutdown()
        if (::configService.isInitialized) configService.unloadAll()
        if (::pnLibraryIntegration.isInitialized) pnLibraryIntegration.close(savedClansCount)
    }

    fun clansCount(): Int = if (::clanService.isInitialized) clanService.getAllClans().size else 0
}
