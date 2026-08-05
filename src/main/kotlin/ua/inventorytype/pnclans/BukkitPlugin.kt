package ua.inventorytype.pnclans

import club.skidware.kgui.KGui
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ConfigService
import ua.inventorytype.pnclans.impl.economy.EconomyService

class BukkitPlugin : JavaPlugin() {

    lateinit var economyService: EconomyService
        private set

    lateinit var configService : ConfigService
    lateinit var placeholderRegistry : PlaceholderRegistry
    lateinit var clanService : ClanService

    override fun onEnable() {

        KGui.setup(this)

        economyService = EconomyService()

        if (economyService.setup()) {
            logger.info("Успешно подключено к Vault Economy!")
        } else {
            logger.warning("Vault или плагин экономики не найдены! Платные функции отключены.")
        }

        configService = ConfigService(this)
        placeholderRegistry = PlaceholderRegistry()
        clanService = ClanService(this)



    }


    override fun onDisable() {

    }
}
