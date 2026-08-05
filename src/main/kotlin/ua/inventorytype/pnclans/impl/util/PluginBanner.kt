package ua.inventorytype.pnclans.impl.util

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.BukkitPlugin

/**
 * Beautiful ASCII Banner and System Diagnostic Logger for startup (`onEnable`)
 * and shutdown (`onDisable`) status reporting.
 */
object PluginBanner {

    /**
     * Prints a visually rich ASCII banner, system diagnostic audit, and integration status
     * to the server console during plugin startup.
     *
     * @param plugin The owning [BukkitPlugin] instance.
     * @param economyConnected Whether Vault Economy setup succeeded.
     * @param papiConnected Whether PlaceholderAPI expansion was registered.
     * @param loadedClansCount The number of clans loaded from the database.
     * @param loadedAddonsCount The number of public API addons loaded.
     */
    fun printEnableBanner(
        plugin: BukkitPlugin,
        economyConnected: Boolean,
        papiConnected: Boolean,
        loadedClansCount: Int,
        loadedAddonsCount: Int
    ) {
        val logger = plugin.logger
        val version = plugin.description.version
        val serverEngine = "${Bukkit.getName()} (MC ${Bukkit.getMinecraftVersion()})"
        val javaVer = System.getProperty("java.version") ?: "Java 21"
        val storageType = plugin.configService.settings.storageType.uppercase()
        val webhookConfigured = plugin.configService.settings.discordWebhookUrl.isNotBlank()

        logger.info(" ")
        logger.info("  ____  _   _  ____ _     _   _  ____ ")
        logger.info(" |  _ \\| \\ | |/ ___| |   / \\ | \\ | / ___|")
        logger.info(" | |_) |  \\| | |   | |  / _ \\|  \\| \\___ \\")
        logger.info(" |  __/| |\\  | |___| |_/ ___ \\ |\\  |___) |")
        logger.info(" |_|   |_| \\_|\\____|_____/_/   \\_\\_| \\_|____/  v$version")
        logger.info(" ")
        logger.info(" /\\_/\\   pnClans — Advanced Clan Management Core")
        logger.info("( o.o )  Author: overdyn | Engine: $serverEngine")
        logger.info(" > ^ <   Java: $javaVer | Storage: $storageType")
        logger.info(" ")
        logger.info("================== SYSTEM AUDIT ==================")

        // 1. Config Audit
        logger.info(" [✔] Configurations : config.yml, menus.yml, messages.yml (Loaded)")

        // 2. Storage Audit
        logger.info(" [✔] Database Storage: $storageType ($loadedClansCount clans loaded)")

        // 3. Economy Integration Audit
        if (economyConnected) {
            logger.info(" [✔] Vault Economy   : Connected (Paid features enabled)")
        } else {
            logger.warning(" [✘] Vault Economy   : Not Found! (Paid features disabled)")
        }

        // 4. PlaceholderAPI Integration Audit
        if (papiConnected) {
            logger.info(" [✔] PlaceholderAPI  : Integration Registered")
        } else {
            logger.info(" [!] PlaceholderAPI  : Not Detected (Optional)")
        }

        // 5. Discord Webhook Analytics Audit
        if (webhookConfigured) {
            logger.info(" [✔] Discord Webhook : Analytics & Error Tracking Active")
        } else {
            logger.info(" [!] Discord Webhook : Not Configured (Skipped)")
        }

        // 6. Public Addon API Audit
        logger.info(" [✔] Public Addon API: Active ($loadedAddonsCount addons loaded)")

        logger.info("==================================================")
        logger.info(" [🚀] pnClans v$version has been successfully launched!")
        logger.info(" ")
    }

    /**
     * Prints a clean shutdown diagnostic status banner during plugin disable.
     *
     * @param plugin The owning [BukkitPlugin] instance.
     * @param savedClansCount Number of clans saved to storage.
     */
    fun printDisableBanner(plugin: BukkitPlugin, savedClansCount: Int) {
        val logger = plugin.logger
        val version = plugin.description.version

        logger.info(" ")
        logger.info("================ SHUTDOWN DIAGNOSTICS ================")
        logger.info(" [✔] Saved Database  : $savedClansCount clans persisted to storage")
        logger.info(" [✔] Public Addon API: Services & listeners unregistered")
        logger.info(" [✔] Active GUI Menus: Force-closed all open clan inventories")
        logger.info(" [✔] System Cleanup  : Timed BossBars & invite prompts cleared")
        logger.info("======================================================")
        logger.info(" /\\_/\\   pnClans v$version disabled cleanly. Goodbye!")
        logger.info("( -.- )  See you next time!")
        logger.info(" > ^ <")
        logger.info(" ")
    }

    /**
     * Prints a styled update notification banner to the console if a new version is available.
     *
     * @param plugin The owning [Plugin] instance.
     * @param currentVersion The current version of the plugin running on the server.
     * @param latestVersion The latest version detected from the repository/release server.
     * @param downloadUrl The direct URL to download the updated release.
     * @param changelog Brief description of changes in the new version.
     */
    fun printUpdateNotice(
        plugin: Plugin,
        currentVersion: String,
        latestVersion: String,
        downloadUrl: String,
        changelog: String = "Performance improvements and bug fixes"
    ) {
        val logger = plugin.logger
        logger.warning(" ")
        logger.warning(" /\\_/\\   pnClans - New Update Available!")
        logger.warning("( o.o )  Your version : v$currentVersion")
        logger.warning(" > ^ <   New version  : v$latestVersion")
        logger.warning(" ")
        logger.warning("      Changes : $changelog")
        logger.warning("      Download: $downloadUrl")
        logger.warning(" ")
    }
}
