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

/**
 * Asynchronous error reporting and analytics service for dispatching exception tracebacks
 * and diagnostic details to a configured Discord Webhook.
 *
 * Designed for real-time monitoring and crash diagnostics without affecting server performance.
 * Features:
 * - Fully asynchronous HTTP POST delivery (runs on Bukkit async scheduler).
 * - Automatic rate-limiting and duplicate suppression (cooldown per exception pattern).
 * - Styled Discord Embed formatting with server specs, Java version, OS, context, and stack trace.
 * - Graceful fallback: silently logs to console if webhook URL is empty, invalid, or disabled.
 */
object ErrorReporter {

    /** Cooldown tracker: map of (exceptionKey) -> (lastSentTimestampMs). */
    private val lastReportTimes = ConcurrentHashMap<String, Long>()

    /** Total error count during this server session. */
    private val totalErrorsCount = AtomicInteger(0)

    /** Minimum interval in milliseconds between duplicate error reports (10 seconds). */
    private const val DUPLICATE_COOLDOWN_MS = 10_000L

    /** Maximum allowed stack trace length inside a Discord field (1000 characters). */
    private const val MAX_STACK_TRACE_LENGTH = 1000

    /**
     * Reports an exception asynchronously to the Discord Webhook specified in `config.yml`.
     *
     * @param plugin The owning [BukkitPlugin] instance.
     * @param context High-level description of where the error occurred (e.g. `"Event: InventoryClickEvent"`).
     * @param throwable The caught exception or error.
     * @param player Optional player associated with the error context.
     * @param extraData Optional key-value pairs providing extra diagnostic state.
     */
    fun report(
        plugin: Plugin,
        context: String,
        throwable: Throwable,
        player: Player? = null,
        extraData: Map<String, String> = emptyMap()
    ) {
        val totalCount = totalErrorsCount.incrementAndGet()

        val bukkitPlugin = plugin as? BukkitPlugin ?: return
        val settings = bukkitPlugin.configService.settings

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

        // Gather diagnostic metadata on the current thread
        val pluginVersion = plugin.description.version
        val serverVersion = "${Bukkit.getName()} ${Bukkit.getVersion()} (MC ${Bukkit.getMinecraftVersion()})"
        val javaVersion = System.getProperty("java.version") ?: "Unknown Java"
        val osInfo = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        val fullStackTrace = stringWriter.toString()
        val truncatedStackTrace = if (fullStackTrace.length > MAX_STACK_TRACE_LENGTH) {
            fullStackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... (truncated)"
        } else {
            fullStackTrace
        }

        val metadata = mutableMapOf<String, String>()
        metadata["Контекст"] = context
        metadata["Исключение"] = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Без сообщения"}"
        metadata["Сервер"] = serverVersion
        metadata["Java / OS"] = "$javaVersion ($osInfo)"
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
     * Builds a Discord Webhook JSON payload with a styled embed layout.
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
                "text": "pnClans Analytics System"
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
