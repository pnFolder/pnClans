package ua.inventorytype.pnclans

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.impl.clan.ClanInviteService
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.command.ClanCommand
import ua.inventorytype.pnclans.impl.config.ConfigService
import ua.inventorytype.pnclans.impl.economy.EconomyService
import ua.inventorytype.pnclans.impl.inventory.listener.GuiListener
import ua.inventorytype.pnclans.impl.listener.ClanListener
import ua.inventorytype.pnclans.impl.placeholder.PnClansExpansion
import ua.inventorytype.pnclans.impl.teleport.TeleportService

class BukkitPlugin : JavaPlugin() {

    lateinit var economyService: EconomyService
        private set

    lateinit var configService: ConfigService
        private set

    lateinit var placeholderRegistry: PlaceholderRegistry
        private set

    lateinit var clanService: ClanService
        private set

    lateinit var inviteService: ClanInviteService
        private set

    lateinit var teleportService: TeleportService
        private set

    lateinit var guiListener: GuiListener
        private set

    override fun onEnable() {
        logger.info("=========================================")
        logger.info("      pnClans v${description.version} - Запуск      ")
        logger.info("=========================================")

        economyService = EconomyService()
        if (economyService.setup()) {
            logger.info("Успешно подключено к Vault Economy!")
        } else {
            logger.warning("Vault или плагин экономики не найдены! Платные функции отключены.")
        }

        configService = ConfigService(this)
        configService.loadAll()

        placeholderRegistry = PlaceholderRegistry()
        clanService = ClanService(this)
        placeholderRegistry.registerDefaults(clanService)
        inviteService = ClanInviteService(clanService)
        teleportService = TeleportService(this)

        guiListener = GuiListener(this)
        server.pluginManager.registerEvents(guiListener, this)
        server.pluginManager.registerEvents(ClanListener(this), this)

        val clanCommand = ClanCommand(this, inviteService)
        getCommand("clan")?.let { cmd ->
            cmd.setExecutor(clanCommand)
            cmd.tabCompleter = clanCommand
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            PnClansExpansion(this).register()
            logger.info("Интеграция с PlaceholderAPI успешно зарегистрирована!")
        }

        logger.info("Плагин pnClans успешно включен и готов к работе!")
    }

    override fun onDisable() {
        if (::guiListener.isInitialized) {
            guiListener.forceCloseAll()
        }
        if (::clanService.isInitialized) {
            clanService.saveAll()
        }
        logger.info("Плагин pnClans выключен. Все данные сохранены.")
    }
}