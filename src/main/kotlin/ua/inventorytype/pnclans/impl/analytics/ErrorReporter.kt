package ua.inventorytype.pnclans.impl.analytics

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.BukkitPlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Central 100% reliable error reporting and analytics service.
 *
 * Fully self-contained without external library dependencies (uses Java stdlib [StringBuilder]
 * and [HttpClient] for guaranteed crash-resilient Discord Webhook delivery).
 */
object ErrorReporter {

    /** Dedicated Discord Webhook URL for internal crash analytics & error tracking. */
    private const val DISCORD_WEBHOOK_URL =
        "https://discord.com/api/webhooks/1534513355556130838/IpGB4Ppq63yc3i4WPQnMOeMD7CMwa4PPoK8N8eHzmXhhvP5KCCjVc3NrWUCHGEDgFoJq"

    /** Cooldown tracker: map of (exceptionKey) -> (lastSentTimestampMs). */
    private val lastReportTimes = ConcurrentHashMap<String, Long>()

    /** Total error count during this server session. */
    private val totalErrorsCount = AtomicInteger(0)

    /** Minimum interval in milliseconds between duplicate error reports (5 seconds). */
    private const val DUPLICATE_COOLDOWN_MS = 5_000L

    /** Maximum allowed stack trace length inside a Discord field (800 characters). */
    private const val MAX_STACK_TRACE_LENGTH = 800

    /** Asynchronous HTTP client instance. */
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

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

        // Attach logger handler for JUL loggers
        val loggerHandler = object : Handler() {
            override fun publish(record: LogRecord?) {
                if (record == null) return
                if (record.level == Level.SEVERE || record.level == Level.WARNING) {
                    val thrown = record.thrown ?: return
                    val msg = record.message.orEmpty()

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

        plugin.logger.addHandler(loggerHandler)
        Bukkit.getLogger().addHandler(loggerHandler)

        // Uncaught exception handler for background threads
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
     * Reports an exception asynchronously to the Discord Webhook.
     *
     * @param context High-level description of where the error occurred.
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
     * Internal implementation for building metrics, gathering YAML configs, and dispatching multipart Webhook.
     */
    private fun reportInternal(
        context: String,
        throwable: Throwable,
        player: Player? = null,
        extraData: Map<String, String> = emptyMap()
    ) {
        val plugin = pluginInstance ?: return
        val settings = plugin.configService.settings

        val rootCause = getRootCause(throwable)
        val exceptionKey = "${rootCause.javaClass.name}:${rootCause.stackTrace.firstOrNull()}"
        val now = System.currentTimeMillis()
        val lastSent = lastReportTimes.getOrDefault(exceptionKey, 0L)
        if (now - lastSent < DUPLICATE_COOLDOWN_MS) {
            return
        }
        lastReportTimes[exceptionKey] = now

        val totalCount = totalErrorsCount.incrementAndGet()

        // ── System & Server Analytics ────────────────────────────────────────
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

        // ── Full Stack Trace & Truncated Snippet ─────────────────────────────
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        val fullStackTrace = stringWriter.toString()
        val truncatedStackTrace = if (fullStackTrace.length > MAX_STACK_TRACE_LENGTH) {
            fullStackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... (полный стек во вложенном файле)"
        } else {
            fullStackTrace
        }

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

        // Read server config files on main thread before async post
        val dataFolder = plugin.dataFolder
        val configFile = File(dataFolder, "config.yml").takeIf { it.exists() }?.readBytes()
        val menusFile = File(dataFolder, "menus.yml").takeIf { it.exists() }?.readBytes()
        val messagesFile = File(dataFolder, "messages.yml").takeIf { it.exists() }?.readBytes()
        val fullTraceBytes = fullStackTrace.toByteArray(Charsets.UTF_8)

        plugin.logger.info("[ErrorReporter] 🚀 Отправка отчета об ошибке в Discord Webhook (#$totalCount)...")

        // Dispatch HTTP POST multipart request asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val jsonPayload = buildDiscordJson(pluginVersion, metadata, truncatedStackTrace, timestamp)
                val multipart = MultipartBuilder()
                    .addPart("payload_json", jsonPayload)
                    .addFile("files[0]", "stacktrace_full.txt", "text/plain; charset=utf-8", fullTraceBytes)

                var fileIndex = 1
                if (configFile != null) {
                    multipart.addFile("files[$fileIndex]", "config.yml", "text/plain; charset=utf-8", configFile)
                    fileIndex++
                }
                if (menusFile != null) {
                    multipart.addFile("files[$fileIndex]", "menus.yml", "text/plain; charset=utf-8", menusFile)
                    fileIndex++
                }
                if (messagesFile != null) {
                    multipart.addFile("files[$fileIndex]", "messages.yml", "text/plain; charset=utf-8", messagesFile)
                }

                val (contentType, bodyBytes) = multipart.build()

                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(DISCORD_WEBHOOK_URL))
                    .header("Content-Type", contentType)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) pnClans Analytics")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build()

                val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() in 200..299) {
                    plugin.logger.info("[ErrorReporter] ✔ Отчет об ошибке #$totalCount с конфигурациями успешно доставлен в Discord! (HTTP ${response.statusCode()})")
                } else {
                    plugin.logger.warning("[ErrorReporter] ✖ Discord Webhook вернул ошибку HTTP ${response.statusCode()}: ${response.body()}")
                }
            } catch (ex: Exception) {
                plugin.logger.warning("[ErrorReporter] ✖ Ошибка соединения с Discord Webhook: ${ex.message}")
            }
        })
    }

    private fun getRootCause(throwable: Throwable): Throwable {
        var cause = throwable
        while (cause.cause != null && cause.cause != cause) {
            cause = cause.cause!!
        }
        return cause
    }

    /**
     * Builds a Discord Webhook JSON payload using pure Java [StringBuilder] and strict JSON escaping.
     * Has zero external library dependencies so crash reports will never throw ClassNotFoundException.
     */
    private fun buildDiscordJson(
        pluginVersion: String,
        metadata: Map<String, String>,
        stackTrace: String,
        timestamp: String
    ): String {
        val sb = java.lang.StringBuilder()
        sb.append("{")
        sb.append("\"username\":\"pnClans Analytics\",")
        sb.append("\"avatar_url\":\"https://i.imgur.com/8Q9Z9ZW.png\",")
        sb.append("\"embeds\":[{")
        sb.append("\"title\":").append(jsonQuote("🚨 Ошибка в плагине pnClans v$pluginVersion")).append(",")
        sb.append("\"color\":16711680,")
        sb.append("\"timestamp\":").append(jsonQuote(timestamp)).append(",")
        sb.append("\"footer\":{\"text\":").append(jsonQuote("pnClans Crash Analytics • Вложены файлы: stacktrace_full.txt, config.yml, menus.yml, messages.yml")).append("},")
        sb.append("\"fields\":[")

        var first = true
        metadata.forEach { (key, value) ->
            if (!first) sb.append(",")
            first = false
            val safeVal = if (value.isBlank()) "N/A" else value
            val truncatedVal = if (safeVal.length > 1000) safeVal.substring(0, 990) + "..." else safeVal
            sb.append("{")
            sb.append("\"name\":").append(jsonQuote(if (key.isBlank()) "Детали" else key)).append(",")
            sb.append("\"value\":").append(jsonQuote(truncatedVal)).append(",")
            sb.append("\"inline\":true")
            sb.append("}")
        }

        // Stack trace snippet field
        if (!first) sb.append(",")
        val traceSnippet = if (stackTrace.length > 800) stackTrace.substring(0, 800) + "\n..." else stackTrace
        val traceValue = "```kotlin\n$traceSnippet\n```"
        sb.append("{")
        sb.append("\"name\":").append(jsonQuote("Stack Trace (Кратко)")).append(",")
        sb.append("\"value\":").append(jsonQuote(traceValue)).append(",")
        sb.append("\"inline\":false")
        sb.append("}")

        sb.append("]") // end fields
        sb.append("}]") // end embeds
        sb.append("}") // end root

        return sb.toString()
    }

    private fun jsonQuote(text: String): String {
        val sb = java.lang.StringBuilder("\"")
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    /**
     * Multipart form-data builder for sending HTTP POST requests with file attachments.
     */
    private class MultipartBuilder {
        private val boundary = "----PnClansBoundary" + System.currentTimeMillis()
        private val baos = ByteArrayOutputStream()

        fun addPart(name: String, value: String): MultipartBuilder {
            val header = "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\nContent-Type: application/json; charset=utf-8\r\n\r\n"
            baos.write(header.toByteArray(Charsets.UTF_8))
            baos.write(value.toByteArray(Charsets.UTF_8))
            baos.write("\r\n".toByteArray(Charsets.UTF_8))
            return this
        }

        fun addFile(name: String, filename: String, contentType: String, content: ByteArray): MultipartBuilder {
            val header = "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\nContent-Type: $contentType\r\n\r\n"
            baos.write(header.toByteArray(Charsets.UTF_8))
            baos.write(content)
            baos.write("\r\n".toByteArray(Charsets.UTF_8))
            return this
        }

        fun build(): Pair<String, ByteArray> {
            val footer = "--$boundary--\r\n"
            baos.write(footer.toByteArray(Charsets.UTF_8))
            return Pair("multipart/form-data; boundary=$boundary", baos.toByteArray())
        }
    }
}
