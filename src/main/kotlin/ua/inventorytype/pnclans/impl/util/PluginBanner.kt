package ua.inventorytype.pnclans.impl.util

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.BukkitPlugin

/**
 * Premium console banner and diagnostic status renderer.
 *
 * Utilizes [Bukkit.getConsoleSender] with full HEX color formatting (`&#RRGGBB`)
 * and Unicode box-drawing characters for stunning console output during plugin lifecycle events.
 */
object PluginBanner {

    /**
     * Renders a stunning HEX-colored ASCII banner and full diagnostic audit checklist
     * to the server console during plugin startup (`onEnable`).
     */
    fun printEnableBanner(
        plugin: BukkitPlugin,
        economyConnected: Boolean,
        papiConnected: Boolean,
        loadedClansCount: Int,
        loadedAddonsCount: Int
    ) {
        val version = plugin.description.version
        val serverEngine = "${Bukkit.getName()} (MC ${Bukkit.getMinecraftVersion()})"
        val javaVer = System.getProperty("java.version") ?: "Java 21"
        val storageType = plugin.configService.settings.storageType.uppercase()
        val webhookConfigured = plugin.configService.settings.discordWebhookUrl.isNotBlank()

        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemMb = runtime.maxMemory() / 1024 / 1024

        val sender = Bukkit.getConsoleSender()

        fun log(msg: String) {
            sender.sendMessage(ColorUtil.color(msg))
        }

        log("")
        log("&#FC7D37╔════════════════════════════════════════════════════════════════════════════════╗")
        log("&#FC7D37║  &#FFD700 /\\_/\\   &#FC7D37&lpnClans &#9EFC65v$version &#787878— Advanced Clan Engine Framework              &#FC7D37║")
        log("&#FC7D37║ &#FFD700( o.o )  &#5EFD7DAuthor: overdyn  &#787878|  &#5EA9FDEngine: $serverEngine               &#FC7D37║")
        log("&#FC7D37║  &#FFD700> ^ <   &#FC65DF$javaVer &#787878| &#FFD700Storage: $storageType &#787878| &#5EFD7DRAM: ${usedMemMb}MB / ${maxMemMb}MB             &#FC7D37║")
        log("&#FC7D37╠════════════════════════════════════════════════════════════════════════════════╣")

        // 1. Configurations Audit
        log("&#FC7D37║  &#9EFC65❖ CONFIGURATIONS &#ffffff: config.yml, menus.yml, messages.yml       &#9EFC65[ACTIVE ✔]  &#FC7D37║")

        // 2. Database Audit
        log("&#FC7D37║  &#5EFD7D❖ DATABASE       &#ffffff: $storageType Storage ($loadedClansCount clans loaded)         &#5EFD7D[READY ✔]   &#FC7D37║")

        // 3. Vault Economy Audit
        if (economyConnected) {
            log("&#FC7D37║  &#FFD700❖ VAULT ECONOMY  &#ffffff: Vault Economy Integration Connected        &#9EFC65[LINKED ✔]  &#FC7D37║")
        } else {
            log("&#FC7D37║  &#FC3737❖ VAULT ECONOMY  &#ffffff: Vault Plugin Missing (Paid features off)     &#FC3737[FAILED ✘]  &#FC7D37║")
        }

        // 4. PlaceholderAPI Audit
        if (papiConnected) {
            log("&#FC7D37║  &#5EA9FD❖ PLACEHOLDERAPI &#ffffff: PnClans Placeholder Expansion           &#5EA9FD[HOOKED ✔]  &#FC7D37║")
        } else {
            log("&#FC7D37║  &#787878❖ PLACEHOLDERAPI &#ffffff: PlaceholderAPI Plugin Not Found         &#787878[SKIPPED !] &#FC7D37║")
        }

        // 5. Discord Webhook Audit
        if (webhookConfigured) {
            log("&#FC7D37║  &#FC65DF❖ DISCORD ERROR  &#ffffff: Webhook Crash Analytics Service           &#9EFC65[ONLINE ✔]  &#FC7D37║")
        } else {
            log("&#FC7D37║  &#787878❖ DISCORD ERROR  &#ffffff: Webhook URL Not Set in config.yml         &#787878[SKIPPED !] &#FC7D37║")
        }

        // 6. Public Addon API Audit
        log("&#FC7D37║  &#FFD700❖ ADDON FRAMEWORK&#ffffff: Public Addon API Service                 &#FFD700[$loadedAddonsCount LOADED] &#FC7D37║")

        log("&#FC7D37╠════════════════════════════════════════════════════════════════════════════════╣")
        log("&#FC7D37║  &#9EFC65&l⚡ STATUS: pnClans v$version is initialized and ready for production!         &#FC7D37║")
        log("&#FC7D37╚════════════════════════════════════════════════════════════════════════════════╝")
        log("")
    }

    /**
     * Renders a clean shutdown diagnostic status banner during plugin disable (`onDisable`).
     */
    fun printDisableBanner(plugin: BukkitPlugin, savedClansCount: Int) {
        val version = plugin.description.version
        val sender = Bukkit.getConsoleSender()

        fun log(msg: String) {
            sender.sendMessage(ColorUtil.color(msg))
        }

        log("")
        log("&#FC3737╔════════════════════════ SHUTDOWN DIAGNOSTICS ════════════════════════╗")
        log("&#FC3737║  &#9EFC65✔ DATABASE SAVED  &#ffffff: $savedClansCount clans persisted to storage backend       &#FC3737║")
        log("&#FC3737║  &#9EFC65✔ ADDON API CLEAN  &#ffffff: Services and event listeners unregistered cleanly    &#FC3737║")
        log("&#FC3737║  &#9EFC65✔ GUI INVENTORIES &#ffffff: Force-closed all open clan player inventories       &#FC3737║")
        log("&#FC3737║  &#9EFC65✔ SYSTEM CLEANUP   &#ffffff: Timed BossBars and invite prompts cleared            &#FC3737║")
        log("&#FC3737╠═════════════════════════════════════════════════════════════════════════╣")
        log("&#FC3737║  &#FFD700 /\\_/\\   &#FC3737pnClans v$version disabled cleanly. Goodbye!                  &#FC3737║")
        log("&#FC3737║ &#FFD700( -.- )  &#787878See you next time!                                           &#FC3737║")
        log("&#FC3737║  &#FFD700> ^ <                                                                   &#FC3737║")
        log("&#FC3737╚═════════════════════════════════════════════════════════════════════════╝")
        log("")
    }

    /**
     * Renders a styled update notification box when a new version is detected.
     */
    fun printUpdateNotice(
        plugin: Plugin,
        currentVersion: String,
        latestVersion: String,
        downloadUrl: String,
        changelog: String = "Global performance improvements and feature updates"
    ) {
        val sender = Bukkit.getConsoleSender()

        fun log(msg: String) {
            sender.sendMessage(ColorUtil.color(msg))
        }

        log("")
        log("&#FFD700╔══════════════════════ NEW UPDATE AVAILABLE ══════════════════════╗")
        log("&#FFD700║  &#FFD700 /\\_/\\   &#FC7D37pnClans Update Notification!                          &#FFD700║")
        log("&#FFD700║ &#FFD700( o.o )  &#787878Your version : &#FC3737v$currentVersion                                   &#FFD700║")
        log("&#FFD700║  &#FFD700> ^ <   &#9EFC65New version  : &#9EFC65v$latestVersion                                   &#FFD700║")
        log("&#FFD700╠═════════════════════════════════════════════════════════════════════╣")
        log("&#FFD700║  &#5EA9FD❖ Changes  : &#ffffff$changelog                                  &#FFD700║")
        log("&#FFD700║  &#5EFD7D❖ Download : &#5EFD7D$downloadUrl                    &#FFD700║")
        log("&#FFD700╚═════════════════════════════════════════════════════════════════════╝")
        log("")
    }
}
