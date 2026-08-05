package ua.inventorytype.pnclans.impl.updater

import org.bukkit.Bukkit
import ua.inventorytype.pnclans.BukkitPlugin
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level

/**
 * Asynchronous background updater and cleanup engine for pnClans.
 *
 * - Checks GitHub Releases API (`https://api.github.com/repos/overdyn/pnClans/releases/latest`)
 *   for newer plugin versions upon server startup.
 * - Downloads the updated Fat JAR into the Bukkit update folder (`/plugins/update/pnClans.jar`),
 *   allowing Paper/Spigot to automatically swap it on the next server restart.
 * - Automatically scans and deletes leftover older JAR files (e.g. `pnClans-1.0.0-all.jar`) from `/plugins/`
 *   once the new version is active.
 *
 * @param plugin The main Bukkit plugin instance.
 */
class AutoUpdater(private val plugin: BukkitPlugin) {

    private val currentVersion: String = plugin.description.version
    private val repo: String = "overdyn/pnClans"

    /**
     * Schedules asynchronous update check and old JAR cleanup on server startup.
     */
    fun checkForUpdatesAsync() {
        val settings = plugin.configService.settings

        // Always attempt old JAR cleanup first
        cleanupOldJarsAsync()

        if (!settings.checkUpdates && !settings.autoUpdate) return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                performCheck()
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "[pnClans] Не удалось проверить обновления на GitHub: ${e.message}")
            }
        })
    }

    /**
     * Scans the `/plugins/` folder for any obsolete versioned `pnClans-*.jar` files and deletes them.
     */
    fun cleanupOldJarsAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val pluginsFolder = plugin.dataFolder.parentFile ?: return@Runnable
                val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()

                val jarFiles = pluginsFolder.listFiles { file ->
                    file.isFile && file.name.endsWith(".jar") && file.name.contains("pnClans", ignoreCase = true)
                } ?: return@Runnable

                for (jar in jarFiles) {
                    val name = jar.name
                    if (name.equals("pnClans.jar", ignoreCase = true)) continue

                    val verMatch = JAR_VERSION_REGEX.find(name)
                    if (verMatch != null) {
                        val verInFile = verMatch.groupValues[1]
                        if (isNewerVersion(cleanCurrent, verInFile)) {
                            if (jar.delete()) {
                                plugin.logger.info("[pnClans] 🧹 Автоматически удален устаревший файл плагина: ${jar.name}")
                            } else {
                                jar.deleteOnExit()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        })
    }

    private fun performCheck() {
        val settings = plugin.configService.settings
        val apiUrl = "https://api.github.com/repos/$repo/releases/latest"

        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "pnClans-AutoUpdater")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        if (connection.responseCode != 200) {
            plugin.logger.warning("[pnClans] GitHub API вернул статус ${connection.responseCode} при проверке обновлений.")
            return
        }

        val responseText = connection.inputStream.use { it.bufferedReader().readText() }
        connection.disconnect()
        val latestTag = extractJsonField(responseText, "tag_name") ?: return
        val downloadUrl = extractJarDownloadUrl(responseText)

        val cleanLatest = latestTag.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()

        if (isNewerVersion(cleanLatest, cleanCurrent)) {
            plugin.logger.info("=======================================================")
            plugin.logger.info("[pnClans] 🚀 ОБНАРУЖЕНО НОВОЕ ОБНОВЛЕНИЕ ПЛАГИНА!")
            plugin.logger.info("[pnClans] Текущая версия: v$cleanCurrent ➜ Новая версия: v$cleanLatest")
            plugin.logger.info("[pnClans] Ссылка на релиз: https://github.com/$repo/releases/tag/$latestTag")

            if (settings.autoUpdate && downloadUrl != null) {
                plugin.logger.info("[pnClans] 📥 Начинаем автоматическую загрузку обновления...")
                downloadUpdate(downloadUrl, cleanLatest)
            } else {
                plugin.logger.info("[pnClans] Авто-скачивание отключено в config.yml (autoUpdate: false).")
            }
            plugin.logger.info("=======================================================")
        } else {
            plugin.logger.info("[pnClans] Вы используете актуальную версию плагина (v$cleanCurrent).")
        }
    }

    private fun downloadUpdate(downloadUrl: String, version: String) {
        try {
            val updateFolder = Bukkit.getUpdateFolderFile()
            if (!updateFolder.exists()) {
                updateFolder.mkdirs()
            }

            val targetFile = File(updateFolder, "pnClans.jar")
            val tempFile = File(updateFolder, "pnClans.jar.tmp")

            if (tempFile.exists()) tempFile.delete()

            val urlConnection = followRedirects(downloadUrl)
            urlConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                plugin.logger.info("[pnClans] ✔ Обновление v$version успешно скачано в папку ${updateFolder.name}/pnClans.jar!")
                plugin.logger.info("[pnClans] 🔄 Новая версия автоматически заменит текущий JAR при перезапуске сервера!")
            } else {
                tempFile.delete()
                plugin.logger.warning("[pnClans] ✖ Скачанный файл авто-обновления v$version оказался пустым или повреждённым.")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "[pnClans] Ошибка при скачивании авто-обновления: ${e.message}", e)
        }
    }

    private fun followRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "pnClans-AutoUpdater")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                val loc = conn.getHeaderField("Location")
                if (loc != null) {
                    currentUrl = loc
                    redirects++
                    continue
                }
            }
            return conn
        }
        throw IllegalStateException("Too many HTTP redirects following $initialUrl")
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val key = "\"$fieldName\":"
        val index = json.indexOf(key)
        if (index == -1) return null

        val startQuote = json.indexOf('"', index + key.length)
        if (startQuote == -1) return null

        val endQuote = json.indexOf('"', startQuote + 1)
        if (endQuote == -1) return null

        return json.substring(startQuote + 1, endQuote)
    }

    private fun extractJarDownloadUrl(json: String): String? {
        val key = "\"browser_download_url\":"
        var searchIndex = 0
        while (searchIndex < json.length) {
            val index = json.indexOf(key, searchIndex)
            if (index == -1) break

            val startQuote = json.indexOf('"', index + key.length)
            if (startQuote != -1) {
                val endQuote = json.indexOf('"', startQuote + 1)
                if (endQuote != -1) {
                    val url = json.substring(startQuote + 1, endQuote)
                    if (url.endsWith(".jar")) {
                        return url
                    }
                }
            }
            searchIndex = index + key.length
        }
        return null
    }
    private companion object {
        val JAR_VERSION_REGEX = Regex("""pnClans[^\d]*(\d+\.\d+\.\d+).*""", RegexOption.IGNORE_CASE)
    }
}
