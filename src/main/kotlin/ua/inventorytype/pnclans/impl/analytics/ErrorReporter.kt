package ua.inventorytype.pnclans.impl.analytics

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.BukkitPlugin
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Central, 100% automatic error reporting and analytics service.
 *
 * Attaches a custom [Handler] to the plugin's [java.util.logging.Logger] upon initialization.
 * Whenever Bukkit/Paper or any plugin component logs a [Level.SEVERE] message with a [Throwable],
 * this reporter automatically intercepts it, collects full diagnostic metrics, and posts a formatted
 * embed to the configured Discord Webhook asynchronously.
 *
 * **Metrics automatically collected:**
 * - Exception type, message, and formatted stack trace.
 * - Java version, OS info, CPU architecture.
 * - Server engine (Paper/Spigot), Minecraft version, online player count vs max.
 * - Memory usage (used RAM / max RAM).
 * - Plugin version and current `config.yml` settings dump.
 * - Timestamp in ISO-8601 format.
 */
object ErrorReporter {

    /** Cooldown tracker: map of (exceptionKey) -> (lastSentTimestampMs). */
    private val lastReportTimes = ConcurrentHashMap<String, Long>()

    /** Total error count during this server session. */
    private val totalErrorsCount = AtomicInteger(0)

    /** Minimum interval in milliseconds between duplicate error reports (10 seconds). */
    private const val DUPLICATE_COOLDOWN_MS = 10_000L

    /** Maximum allowed stack trace length inside a Discord field (1020 characters). */
    private const val MAX_STACK_TRACE_LENGTH = 1020

    /** The initialized plugin instance. */
    private var pluginInstance: BukkitPlugin? = null

    /**
     * Initializes automatic error logging hooks on the plugin's logger and thread context.
     * Called once during [ua.inventorytype.pnclans.BukkitPlugin.onEnable].
     *
     * @param plugin The owning [BukkitPlugin] instance.
     */
    fun init(plugin: BukkitPlugin) {
        this.pluginInstance = plugin

        // 1. Attach central logging handler to intercept all SEVERE errors automatically
        val loggerHandler = object : Handler() {
            override fun publish(record: LogRecord?) {
                if (record == null) return
                if (record.level == Level.SEVERE && record.thrown != null) {
                    reportInternal(
                        context = record.message ?: "Automatic Logger Intercept",
                        throwable = record.thrown
                    )
                }
            }

            override fun flush() {}
            override fun close() {}
        }
        plugin.logger.addHandler(loggerHandler)

        // 2. Set default uncaught exception handler for background threads
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            reportInternal(
                context = "Uncaught Thread Exception (${thread.name})",
                throwable = throwable
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }

        plugin.logger.info("[ErrorReporter] Автоматическая система сбора ошибок и аналитики Discord подключена!")
    }

    /**
     * Manually reports an exception asynchronously to the Discord Webhook.
     *
     * @param context Description of where the error occurred.
     * @param throwable The caught exception or error.
     * @param player Optional player associated with the error context.
     * @param extraData Optional key-value pairs providing extra diagnostic state.
     */
    fun report(
        context: String,
        throwable: Throwable,
        player: Player? = null,
        extraData: Map<String, String> = emptyMap()
    ) {
        reportInternal(context, throwable, player, extraData)
    }

    /**
     * Internal implementation for building metrics and dispatching the HTTP webhook.
     */
    private fun reportInternal(
        context: String,
        throwable: Throwable,
        player: Player? = null,
        extraData: Map<String, String> = emptyMap()
    ) {
        val plugin = pluginInstance ?: return
        val settings = plugin.configService.settings

        if (!settings.discordWebhookEnabled) return
        val webhookUrl = settings.discordWebhookUrl.trim()
        if (webhookUrl.isEmpty() || !webhookUrl.startsWith("http")) return

        // Duplicate suppression check based on exception class + top stack element
        val exceptionKey = "${throwable.javaClass.name}:${throwable.stackTrace.firstOrNull()}"
        val now = System.currentTimeMillis()
        val lastSent = lastReportTimes.getOrDefault(exceptionKey, 0L)
        if (now - lastSent < DUPLICATE_COOLDOWN_MS) {
            return
        }
        lastReportTimes[exceptionKey] = now

        val totalCount = totalErrorsCount.incrementAndGet()

        // ── Comprehensive System & Server Analytics ──────────────────────────
        val pluginVersion = plugin.description.version
        val serverEngine = "${Bukkit.getName()} ${Bukkit.getVersion()} (MC ${Bukkit.getMinecraftVersion()})"
        val javaVersion = System.getProperty("java.version") ?: "Unknown Java"
        val osName = System.getProperty("os.name") ?: "Unknown OS"
        val osArch = System.getProperty("os.arch") ?: "x64"
        val onlinePlayers = "${Bukkit.getOnlinePlayers().size} / ${Bukkit.getMaxPlayers()}"

        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemMb = runtime.maxMemory() / 1024 / 1024
        val ramStats = "${usedMemMb}MB / ${maxMemMb}MB"

        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        // ── Stack Trace Formatting ───────────────────────────────────────────
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        val fullStackTrace = stringWriter.toString()
        val truncatedStackTrace = if (fullStackTrace.length > MAX_STACK_TRACE_LENGTH) {
            fullStackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... (truncated)"
        } else {
            fullStackTrace
        }

        // ── Config Dump Summary ──────────────────────────────────────────────
        val configDump = "Storage: ${settings.storageType} | CreateCost: ${settings.createClanCost} | InviteTimeout: ${settings.inviteLifetimeSeconds}s"

        // ── Metadata Map ─────────────────────────────────────────────────────
        val metadata = mutableMapOf<String, String>()
        metadata["Контекст"] = context
        metadata["Исключение"] = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Без сообщения"}"
        metadata["Сервер"] = serverEngine
        metadata["Онлайн"] = onlinePlayers
        metadata["ОЗУ (RAM)"] = ramStats
        metadata["Java / OS"] = "$javaVersion ($osName $osArch)"
        metadata["Конфигурация"] = configDump
        metadata["Всего ошибок"] = "#$totalCount"

        if (player != null) {
            metadata["Игрок"] = "${player.name} (${player.uniqueId})"
        }

        extraData.forEach { (k, v) -> metadata[k] = v }

        // Dispatch HTTP request asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                sendWebhookPayload(webhookUrl, pluginVersion, metadata, truncatedStackTrace, timestamp)
            } catch (ex: Exception) {
                plugin.logger.warning("[ErrorReporter] Не удалось отправить отчет об ошибке в Discord: ${ex.message}")
            }
        })
    }

    /**
     * Constructs and executes the HTTP POST request to the Discord Webhook URL.
     */
    private fun sendWebhookPayload(
        webhookUrl: String,
        pluginVersion: String,
        metadata: Map<String, String>,
        stackTrace: String,
        timestamp: String
    ) {
        val jsonPayload = buildDiscordJson(pluginVersion, metadata, stackTrace, timestamp)

        val url = URL(webhookUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("User-Agent", "pnClans-Analytics-Reporter")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doOutput = true

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            Bukkit.getLogger().warning("[pnClans ErrorReporter] Discord Webhook вернул код ответа: $responseCode")
        }
        connection.disconnect()
    }

    /**
     * Builds a Discord Webhook JSON payload with a rich styled embed layout.
     */
    private fun buildDiscordJson(
        pluginVersion: String,
        metadata: Map<String, String>,
        stackTrace: String,
        timestamp: String
    ): String {
        val fieldsJson = StringBuilder()

        metadata.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) fieldsJson.append(",")
            fieldsJson.append("""{"name": ${escapeJson(key)}, "value": ${escapeJson(value)}, "inline": true}""")
        }

        val stackTraceFormatted = "```kotlin\n$stackTrace\n```"
        fieldsJson.append(""",{"name": "Stack Trace", "value": ${escapeJson(stackTraceFormatted)}, "inline": false}""")

        return """
        {
          "username": "pnClans Analytics",
          "avatar_url": "https://i.imgur.com/8Q9Z9ZW.png",
          "embeds": [
            {
              "title": "🚨 Ошибка в плагине pnClans v$pluginVersion",
              "color": 16711680,
              "fields": [$fieldsJson],
              "footer": {
                "text": "pnClans Automatic Crash Analytics"
              },
              "timestamp": "$timestamp"
            }
          ]
        }
        """.trimIndent()
    }

    /**
     * Escapes standard JSON special characters for raw string injection.
     */
    private fun escapeJson(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
