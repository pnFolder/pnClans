package ua.inventorytype.pnclans.impl.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bukkit.Bukkit
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.impl.config.UpdateChannel
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.logging.Level

/**
 * Asynchronous GitHub Releases updater for pnClans.
 *
 * Release classification is based only on the semantic version in `tag_name`:
 * - `vX.Y.Z` -> STABLE
 * - `vX.Y.Z-rc.N` -> release candidate, accepted by BETA/ALPHA channels
 * - `vX.Y.Z-beta.N` -> BETA
 * - `vX.Y.Z-alpha.N` -> ALPHA
 * - drafts and malformed/unknown tags are ignored
 *
 * GitHub's `prerelease` flag is intentionally ignored. It may be enabled or disabled for display
 * purposes without changing which pnClans update channel receives the release.
 *
 * The configured channel always selects the newest compatible semantic version. Automatic download
 * is enabled by default; administrators may explicitly add `autoUpdate: false` to config.yml to
 * disable downloading while keeping update checks active.
 */
class AutoUpdater(private val plugin: BukkitPlugin) {

    private val currentVersion: String = plugin.description.version
    private val repo: String = "pnFolder/pnClans"
    private val json = Json { ignoreUnknownKeys = true }

    /** Schedules an asynchronous update check and old-JAR cleanup on server startup. */
    fun checkForUpdatesAsync() {
        cleanupOldJarsAsync()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                performCheck()
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "[pnClans] Не удалось проверить обновления на GitHub: ${e.message}", e)
            }
        })
    }

    /** Scans `/plugins/` for obsolete versioned pnClans JARs and removes them. */
    fun cleanupOldJarsAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val pluginsFolder = plugin.dataFolder.parentFile ?: return@Runnable
                val current = SemanticVersion.parse(currentVersion) ?: return@Runnable

                val jarFiles = pluginsFolder.listFiles { file ->
                    file.isFile && file.name.endsWith(".jar") && file.name.contains("pnClans", ignoreCase = true)
                } ?: return@Runnable

                for (jar in jarFiles) {
                    if (jar.name.equals("pnClans.jar", ignoreCase = true)) continue
                    val versionText = JAR_VERSION_REGEX.find(jar.name)?.groupValues?.getOrNull(1) ?: continue
                    val fileVersion = SemanticVersion.parse(versionText) ?: continue
                    if (current > fileVersion) {
                        if (jar.delete()) {
                            plugin.logger.info("[pnClans] Автоматически удалён устаревший файл плагина: ${jar.name}")
                        } else {
                            jar.deleteOnExit()
                        }
                    }
                }
            } catch (_: Exception) {
                // Cleanup is best-effort and must never block plugin startup.
            }
        })
    }

    private fun performCheck() {
        val channel = plugin.configService.settings.updateChannel
        val releases = fetchReleases()
        val candidate = releases
            .mapNotNull(::parseRelease)
            .filter { it.channel.acceptedBy(channel) }
            .maxByOrNull { it.version }

        val current = SemanticVersion.parse(currentVersion)
        if (current == null) {
            plugin.logger.warning("[pnClans] Текущая версия '$currentVersion' не соответствует SemVer; автообновление пропущено.")
            return
        }

        if (candidate == null) {
            plugin.logger.info("[pnClans] Для канала $channel подходящих GitHub Releases не найдено.")
            return
        }

        plugin.logger.info("[pnClans] Канал обновлений: $channel • текущая ${current.display} • доступная ${candidate.version.display}")
        if (candidate.version <= current) {
            plugin.logger.info("[pnClans] Вы используете актуальную версию плагина (${current.display}).")
            return
        }

        plugin.logger.info("=======================================================")
        plugin.logger.info("[pnClans] ОБНАРУЖЕНО НОВОЕ ОБНОВЛЕНИЕ ПЛАГИНА")
        plugin.logger.info("[pnClans] ${current.display} -> ${candidate.version.display} • канал ${candidate.channel}")
        plugin.logger.info("[pnClans] Релиз: https://github.com/$repo/releases/tag/${candidate.tag}")

        if (!autoUpdateEnabled()) {
            plugin.logger.info("[pnClans] Автоскачивание отключено вручную через скрытый параметр autoUpdate: false.")
        } else if (candidate.downloadUrl == null) {
            plugin.logger.warning("[pnClans] В релизе ${candidate.tag} нет подходящего ${targetArtifactSuffix()} JAR.")
        } else {
            plugin.logger.info("[pnClans] Начинаем автоматическую загрузку ${candidate.version.display}...")
            downloadUpdate(candidate.downloadUrl, candidate.version.rawWithoutPrefix)
        }
        plugin.logger.info("=======================================================")
    }

    private fun fetchReleases(): List<kotlinx.serialization.json.JsonObject> {
        val apiUrl = "https://api.github.com/repos/$repo/releases?per_page=$RELEASE_PAGE_SIZE"
        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "pnClans-AutoUpdater")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        try {
            if (connection.responseCode != 200) {
                throw IllegalStateException("GitHub API returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.use { it.bufferedReader().readText() }
            return json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(release: kotlinx.serialization.json.JsonObject): ReleaseCandidate? {
        if (release["draft"]?.jsonPrimitive?.booleanOrNull == true) return null

        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val version = SemanticVersion.parse(tag) ?: return null

        val releaseChannel = when (version.stage) {
            ReleaseStage.STABLE -> UpdateChannel.STABLE
            ReleaseStage.RC, ReleaseStage.BETA -> UpdateChannel.BETA
            ReleaseStage.ALPHA -> UpdateChannel.ALPHA
        }

        val assetSuffix = targetArtifactSuffix()
        val downloadUrl = release["assets"]?.jsonArray
            ?.asSequence()
            ?.map { it.jsonObject }
            ?.mapNotNull { asset -> asset["browser_download_url"]?.jsonPrimitive?.contentOrNull }
            ?.firstOrNull { url ->
                url.startsWith("https://github.com/$repo/releases/download/") && url.endsWith("-$assetSuffix.jar")
            }

        return ReleaseCandidate(tag, version, releaseChannel, downloadUrl)
    }

    /**
     * Hidden opt-out. The key is intentionally not part of generated Settings YAML. Missing means true.
     */
    private fun autoUpdateEnabled(): Boolean {
        val file = File(plugin.dataFolder, "config.yml")
        val content = runCatching(file::readText).getOrDefault("")
        return AUTO_UPDATE_REGEX.find(content)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: true
    }

    private fun downloadUpdate(downloadUrl: String, version: String) {
        try {
            val updateFolder = Bukkit.getUpdateFolderFile()
            if (!updateFolder.exists()) updateFolder.mkdirs()

            val targetFile = File(updateFolder, "pnClans.jar")
            val tempFile = File(updateFolder, "pnClans.jar.tmp")
            if (tempFile.exists()) tempFile.delete()

            val urlConnection = followRedirects(downloadUrl)
            try {
                val contentLength = urlConnection.contentLengthLong
                if (contentLength > MAX_UPDATE_BYTES) {
                    throw IllegalStateException("Update is larger than the ${MAX_UPDATE_BYTES / 1024 / 1024} MB limit")
                }
                urlConnection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_UPDATE_BYTES) {
                                throw IllegalStateException("Update exceeded the ${MAX_UPDATE_BYTES / 1024 / 1024} MB limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } finally {
                urlConnection.disconnect()
            }

            if (tempFile.exists() && tempFile.length() > 0 && isExpectedPluginJar(tempFile, version)) {
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                plugin.logger.info("[pnClans] Обновление v$version скачано в ${updateFolder.name}/pnClans.jar.")
                plugin.logger.info("[pnClans] Новая версия заменит текущий JAR при следующем перезапуске сервера.")
            } else {
                tempFile.delete()
                plugin.logger.warning("[pnClans] Скачанный JAR v$version пуст, повреждён или не соответствует pnClans.")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "[pnClans] Ошибка при скачивании автообновления: ${e.message}", e)
        }
    }

    private fun followRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 5) {
            val uri = URI(currentUrl)
            require(uri.scheme.equals("https", ignoreCase = true)) { "Update URL must use HTTPS" }
            require(uri.host.lowercase() in ALLOWED_DOWNLOAD_HOSTS) { "Update URL host is not trusted: ${uri.host}" }
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "pnClans-AutoUpdater")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                if (location != null) {
                    currentUrl = uri.resolve(location).toString()
                    conn.disconnect()
                    redirects++
                    continue
                }
            }
            return conn
        }
        throw IllegalStateException("Too many HTTP redirects following $initialUrl")
    }

    private fun targetArtifactSuffix(): String =
        if (Runtime.version().feature() >= 25) "paper-java25" else "paper-java21"

    private fun isExpectedPluginJar(file: File, version: String): Boolean = runCatching {
        JarFile(file).use { jar ->
            val pluginYaml = jar.getJarEntry("plugin.yml") ?: return false
            val content = jar.getInputStream(pluginYaml).bufferedReader().use { it.readText() }
            Regex("(?m)^name:\\s*['\"]?pnClans['\"]?\\s*$").containsMatchIn(content) &&
                Regex("(?m)^version:\\s*['\"]?${Regex.escape(version)}['\"]?\\s*$").containsMatchIn(content)
        }
    }.getOrDefault(false)

    private data class ReleaseCandidate(
        val tag: String,
        val version: SemanticVersion,
        val channel: UpdateChannel,
        val downloadUrl: String?
    )

    private enum class ReleaseStage(val precedence: Int) {
        ALPHA(0),
        BETA(1),
        RC(2),
        STABLE(3)
    }

    private data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val stage: ReleaseStage,
        val stageNumber: Int,
        val rawWithoutPrefix: String
    ) : Comparable<SemanticVersion> {
        val display: String get() = "v$rawWithoutPrefix"

        override fun compareTo(other: SemanticVersion): Int {
            compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
            compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
            compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
            compareValues(stage.precedence, other.stage.precedence).takeIf { it != 0 }?.let { return it }
            return compareValues(stageNumber, other.stageNumber)
        }

        companion object {
            fun parse(input: String): SemanticVersion? {
                val clean = input.trim().removePrefix("v").removePrefix("V")
                val match = VERSION_REGEX.matchEntire(clean) ?: return null
                val stageName = match.groupValues[4]
                val stage = when (stageName.lowercase()) {
                    "" -> ReleaseStage.STABLE
                    "rc" -> ReleaseStage.RC
                    "beta" -> ReleaseStage.BETA
                    "alpha" -> ReleaseStage.ALPHA
                    else -> return null
                }
                return SemanticVersion(
                    major = match.groupValues[1].toInt(),
                    minor = match.groupValues[2].toInt(),
                    patch = match.groupValues[3].toInt(),
                    stage = stage,
                    stageNumber = match.groupValues[5].toIntOrNull() ?: 0,
                    rawWithoutPrefix = clean
                )
            }
        }
    }

    private fun UpdateChannel.acceptedBy(selected: UpdateChannel): Boolean = when (selected) {
        UpdateChannel.STABLE -> this == UpdateChannel.STABLE
        UpdateChannel.BETA -> this == UpdateChannel.STABLE || this == UpdateChannel.BETA
        UpdateChannel.ALPHA -> true
    }

    private companion object {
        const val MAX_UPDATE_BYTES = 50L * 1024L * 1024L
        const val RELEASE_PAGE_SIZE = 50
        val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )
        val AUTO_UPDATE_REGEX = Regex("(?m)^\\s*autoUpdate\\s*:\\s*(true|false)\\s*(?:#.*)?$", RegexOption.IGNORE_CASE)
        val VERSION_REGEX = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(alpha|beta|rc)\\.(\\d+))?$", RegexOption.IGNORE_CASE)
        val JAR_VERSION_REGEX = Regex("""pnClans[^\d]*(\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)\.\d+)?).*""", RegexOption.IGNORE_CASE)
    }
}
