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
 * Attaches a custom [Handler] to both [Plugin.getLogger] and [Bukkit.getLogger] (the root server logger).
 * Whenever Paper/Spigot logs a `CommandException`, event exception, or unhandled error belonging to
 * `pnClans`, this reporter automatically intercepts it, collects diagnostic metrics, and posts a formatted
 * embed to the Discord Webhook asynchronously.
 */
object ErrorReporter {

    /** Dedicated Discord Webhook URL for internal crash analytics & error tracking. */
    private const val DISCORD_WEBHOOK_URL =
        "https://discord.com/api/webhooks/1534513355556130838/IpGB4Ppq63yc3i4WPQnMOeMD7CMwa4PPoK8N8eHzmXhhvP5KCCjVc3NrWUCHGEDgFoJq"

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
     * Initializes automatic error logging hooks on both plugin and root server loggers.
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
                if (record.level == Level.SEVERE || record.level == Level.WARNING) {
                    val thrown = record.thrown ?: return
                    val msg = record.message.orEmpty()

                    // Intercept if message contains pnClans OR throwable stack trace references pnClans package
                    val isPnClansError = msg.contains("pnClans", ignoreCase = true) ||
                            thrown.stackTrace.any { it.className.contains("ua.inventorytype.pnclans") }

                    if (isPnClansError) {
                        reportInternal(
                            context = if (msg.isNotBlank()) msg else "Автоматический перехват логов",
                            throwable = thrown
                        )
                    }
                }
            }

            override fun flush() {}
            override fun close() {}
        }

        // Attach to plugin logger AND root Bukkit server logger (where Paper logs CommandExceptions)
        plugin.logger.addHandler(loggerHandler)
        Bukkit.getLogger().addHandler(loggerHandler)
        plugin.logger.parent?.addHandler(loggerHandler)

        // 2. Set default uncaught exception handler for background threads
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isPnClansError = throwable.stackTrace.any { it.className.contains("ua.inventorytype.pnclans") }
            if (isPnClansError) {
                reportInternal(
                    context = "Uncaught Thread Exception (${thread.name})",
                    throwable = throwable
                )
            }
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

        // Duplicate suppression check based on exception class + top stack element
        val rootCause = getRootCause(throwable)
        val exceptionKey = "${rootCause.javaClass.name}:${rootCause.stackTrace.firstOrNull()}"
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
        metadata["Исключение"] = "${rootCause.javaClass.simpleName}: ${rootCause.message ?: "Без сообщения"}"
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

        // Log notification to console
        plugin.logger.info("[ErrorReporter] 🚀 Отправка отчета об ошибке в Discord Webhook (#$totalCount)...")

        // Dispatch HTTP request asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                sendWebhookPayload(DISCORD_WEBHOOK_URL, pluginVersion, metadata, truncatedStackTrace, timestamp)
                plugin.logger.info("[ErrorReporter] ✔ Отчет об ошибке #$totalCount успешно доставлен в Discord!")
            } catch (ex: Exception) {
                plugin.logger.warning("[ErrorReporter] ✖ Ошибка отправки вебхука в Discord: ${ex.message}")
            }
        })
    }

    /**
     * Unwraps nested causes to find the root cause exception.
     */
    private fun getRootCause(throwable: Throwable): Throwable {
        var cause = throwable
        while (cause.cause != null && cause.cause != cause) {
            cause = cause.cause!!
        }
        return cause
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
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) pnClans Analytics")
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
        fieldsJson.append("""",{"name": "Stack Trace", "value": ${escapeJson(stackTraceFormatted)}, "inline": false}""")

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
