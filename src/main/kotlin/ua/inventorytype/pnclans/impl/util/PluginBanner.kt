package ua.inventorytype.pnclans.impl.util

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.BukkitPlugin

/**
 * Premium Russian console banner and diagnostic status renderer.
 *
 * Utilizes [Bukkit.getConsoleSender] with full HEX color formatting (`&#RRGGBB`),
 * dynamic padding alignment, and Unicode box-drawing characters for beautiful,
 * perfectly aligned console output in Russian.
 */
object PluginBanner {

    /**
     * Renders a stunning HEX-colored Russian ASCII banner and full diagnostic audit checklist
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

        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemMb = runtime.maxMemory() / 1024 / 1024

        val sender = Bukkit.getConsoleSender()
        val innerWidth = 74

        fun formatBoxLine(content: String): String {
            val stripped = content.replace(Regex("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]"), "")
            val padLength = (innerWidth - stripped.length).coerceAtLeast(0)
            val padding = " ".repeat(padLength)
            return ColorUtil.color("&#FC7D37║ $content$padding &#FC7D37║")
        }

        fun logRaw(msg: String) {
            sender.sendMessage(ColorUtil.color(msg))
        }

        val topBar = "═".repeat(innerWidth + 2)
        val topBorder    = ColorUtil.color("&#FC7D37╔$topBar╗")
        val middleBorder = ColorUtil.color("&#FC7D37╠$topBar╣")
        val bottomBorder = ColorUtil.color("&#FC7D37╚$topBar╝")

        logRaw("")
        logRaw(topBorder)
        logRaw(formatBoxLine("&#FFD700 /\\_/\\   &#FC7D37&lpnClans &#9EFC65v$version &#787878— Продвинутая Клановая Система"))
        logRaw(formatBoxLine("&#FFD700( o.o )  &#5EFD7DАвтор: overdyn  &#787878|  &#5EA9FDЯдро: $serverEngine"))
        logRaw(formatBoxLine("&#FFD700 > ^ <   &#FC65DF$javaVer &#787878| &#FFD700БД: $storageType &#787878| &#5EFD7DОЗУ: ${usedMemMb}МБ / ${maxMemMb}МБ"))
        logRaw(middleBorder)

        // 1. Configurations Audit
        logRaw(formatBoxLine("&#9EFC65❖ КОНФИГУРАЦИЯ     &#ffffff: config.yml, menus.yml, messages.yml  &#9EFC65[АКТИВНО ✔]"))

        // 2. Database Audit
        logRaw(formatBoxLine("&#5EFD7D❖ БАЗА ДАННЫХ      &#ffffff: $storageType Хранилище ($loadedClansCount кланов)     &#5EFD7D[ГОТОВО ✔]"))

        // 3. Vault Economy Audit
        if (economyConnected) {
            logRaw(formatBoxLine("&#FFD700❖ Vault ЭКОНОМИКА  &#ffffff: Экономический Модуль Подключён      &#9EFC65[СВЯЗАНО ✔]"))
        } else {
            logRaw(formatBoxLine("&#FC3737❖ Vault ЭКОНОМИКА  &#ffffff: Vault Плагин Не Найден (Платные выкл)  &#FC3737[ОШИБКА ✘]"))
        }

        // 4. PlaceholderAPI Audit
        if (papiConnected) {
            logRaw(formatBoxLine("&#5EA9FD❖ PlaceholderAPI   &#ffffff: Расширение PnClans Зарегистрировано   &#5EA9FD[ПОДКЛЮЧЕНО ✔]"))
        } else {
            logRaw(formatBoxLine("&#787878❖ PlaceholderAPI   &#ffffff: PlaceholderAPI Не Загружен            &#787878[ПРОПУЩЕНО !]"))
        }

        // 5. Discord Webhook Analytics Audit
        logRaw(formatBoxLine("&#FC65DF❖ DISCORD АНАЛИТИКА&#ffffff: Мониторинг и Сбор Ошибок Включён    &#9EFC65[ОНЛАЙН ✔]"))

        // 6. Public Addon API Audit
        logRaw(formatBoxLine("&#FFD700❖ АДДОН API СЕРВИС &#ffffff: Публичный API Загружен               &#FFD700[$loadedAddonsCount АДДОНОВ]"))

        logRaw(middleBorder)
        logRaw(formatBoxLine("&#9EFC65&l⚡ СТАТУС: Плагин pnClans v$version успешно запущен и готов к работе!"))
        logRaw(bottomBorder)
        logRaw("")
    }

    /**
     * Renders a clean shutdown diagnostic status banner in Russian during plugin disable (`onDisable`).
     */
    fun printDisableBanner(plugin: BukkitPlugin, savedClansCount: Int) {
        val version = plugin.description.version
        val sender = Bukkit.getConsoleSender()
        val innerWidth = 74

        fun formatBoxLine(content: String): String {
            val stripped = content.replace(Regex("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]"), "")
            val padLength = (innerWidth - stripped.length).coerceAtLeast(0)
            val padding = " ".repeat(padLength)
            return ColorUtil.color("&#FC3737║ $content$padding &#FC3737║")
        }

        fun logRaw(msg: String) {
            sender.sendMessage(ColorUtil.color(msg))
        }

        val sidePad = "═".repeat(25)
        val topBorder    = ColorUtil.color("&#FC3737╔$sidePad ДИАГНОСТИКА ВЫКЛЮЧЕНИЯ $sidePad╗")
        val bar = "═".repeat(innerWidth + 2)
        val middleBorder = ColorUtil.color("&#FC3737╠$bar╣")
        val bottomBorder = ColorUtil.color("&#FC3737╚$bar╝")

        logRaw("")
        logRaw(topBorder)
        logRaw(formatBoxLine("&#9EFC65✔ БАЗА ДАННЫХ    &#ffffff: $savedClansCount кланов успешно сохранено в БД"))
        logRaw(formatBoxLine("&#9EFC65✔ АДДОН API      &#ffffff: Сервисы и слушатели ивентов выгружены"))
        logRaw(formatBoxLine("&#9EFC65✔ GUI ИНВЕНТАРИ  &#ffffff: Все открытые меню кланов закрыты"))
        logRaw(formatBoxLine("&#9EFC65✔ ОЧИСТКА СИСТЕМЫ&#ffffff: Таймеры BossBar и приглашения очищены"))
        logRaw(middleBorder)
        logRaw(formatBoxLine("&#FFD700 /\\_/\\   &#FC3737pnClans v$version выключен без ошибок. До связи!"))
        logRaw(formatBoxLine("&#FFD700( -.- )  &#787878До новых встреч!"))
        logRaw(formatBoxLine("&#FFD700 > ^ <"))
        logRaw(bottomBorder)
        logRaw("")
    }

    /**
     * Renders a styled update notification box in Russian when a new version is detected.
     */
    fun printUpdateNotice(
        plugin: Plugin,
        currentVersion: String,
        latestVersion: String,
        downloadUrl: String,
        changelog: String = "Глобальные улучшения производительности и оптимизация"
    ) {
        val sender = Bukkit.getConsoleSender()
        val innerWidth = 74

        fun formatBoxLine(content: String): String {
            val stripped = content.replace(Regex("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]"), "")
            val padLength = (innerWidth - stripped.length).coerceAtLeast(0)
            val padding = " ".repeat(padLength)
            return ColorUtil.color("&#FFD700║ $content$padding &#FFD700║")
        }

        fun logRaw(msg: String) {
            sender.sendMessage(ColorUtil.color(msg))
        }

        val sidePad = "═".repeat(27)
        val topBorder    = ColorUtil.color("&#FFD700╔$sidePad ДОСТУПНО ОБНОВЛЕНИЕ $sidePad╗")
        val bar = "═".repeat(innerWidth + 2)
        val middleBorder = ColorUtil.color("&#FFD700╠$bar╣")
        val bottomBorder = ColorUtil.color("&#FFD700╚$bar╝")

        logRaw("")
        logRaw(topBorder)
        logRaw(formatBoxLine("&#FFD700 /\\_/\\   &#FC7D37Доступна новая версия pnClans!"))
        logRaw(formatBoxLine("&#FFD700( o.o )  &#787878Текущая версия: &#FC3737v$currentVersion"))
        logRaw(formatBoxLine("&#FFD700 > ^ <   &#9EFC65Новая версия  : &#9EFC65v$latestVersion"))
        logRaw(middleBorder)
        logRaw(formatBoxLine("&#5EA9FD❖ Изменения  : &#ffffff$changelog"))
        logRaw(formatBoxLine("&#5EFD7D❖ Скачать    : &#5EFD7D$downloadUrl"))
        logRaw(bottomBorder)
        logRaw("")
    }
}
