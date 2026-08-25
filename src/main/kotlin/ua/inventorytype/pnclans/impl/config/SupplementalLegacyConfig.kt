package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.MessageAction
import ua.inventorytype.pnclans.api.SoundAction
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level

@Serializable
data class ClanCreationSettingsConfig(
    val promptTimeoutSeconds: Int = 30,
    val cancelInputs: List<String> = listOf("cancel", "отмена")
)

@Serializable
data class SupplementalSettingsConfig(
    val clanCreation: ClanCreationSettingsConfig = ClanCreationSettingsConfig()
)

@Serializable
data class ClanCreationMessagesConfig(
    val promptStarted: List<Action> = listOf(
        MessageAction("&#5EA9FD✎ &fВведите название нового клана в чат. Для отмены напишите &c{cancel}&f. Осталось: &e{seconds} сек.&f"),
        SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.2f)
    ),
    val cancelled: List<Action> = listOf(
        MessageAction("&#FC3737✖ &fСоздание клана отменено."),
        SoundAction("ENTITY_VILLAGER_NO", 0.8f, 1.1f)
    ),
    val timedOut: List<Action> = listOf(
        MessageAction("&#FC3737✖ &fВремя на ввод названия клана истекло."),
        SoundAction("ENTITY_VILLAGER_NO", 0.8f, 1.1f)
    )
)

@Serializable
data class SupplementalMessagesConfig(
    val clanCreation: ClanCreationMessagesConfig = ClanCreationMessagesConfig()
)

/**
 * Loads small legacy sections from the normal public YAML files without expanding the already-large
 * core config models. Missing sections are appended; existing administrator values are never replaced.
 */
internal object SupplementalLegacyLoader {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            polymorphismStyle = PolymorphismStyle.Tag
        )
    )

    fun loadSettings(plugin: BukkitPlugin): SupplementalSettingsConfig =
        loadAndBackfill(plugin, "config.yml", SupplementalSettingsConfig.serializer(), SupplementalSettingsConfig(), listOf("clanCreation"))

    fun loadMessages(plugin: BukkitPlugin): SupplementalMessagesConfig =
        loadAndBackfill(plugin, "messages.yml", SupplementalMessagesConfig.serializer(), SupplementalMessagesConfig(), listOf("clanCreation"))

    private fun <T> loadAndBackfill(
        plugin: BukkitPlugin,
        fileName: String,
        serializer: KSerializer<T>,
        defaults: T,
        rootKeys: List<String>
    ): T {
        val file = File(plugin.dataFolder, fileName)
        val raw = file.takeIf(File::exists)?.readText().orEmpty()
        val loaded = runCatching { yaml.decodeFromString(serializer, raw) }
            .onFailure { error -> plugin.logger.log(Level.WARNING, "[pnClans] Failed to load supplemental section from $fileName; defaults will be used.", error) }
            .getOrElse { defaults }

        if (file.exists()) {
            appendMissingRoots(plugin, file, raw, yaml.encodeToString(serializer, loaded), rootKeys)
        }
        return loaded
    }

    private fun appendMissingRoots(
        plugin: BukkitPlugin,
        file: File,
        existing: String,
        source: String,
        rootKeys: List<String>
    ) {
        val blocks = rootBlocks(source.lines())
        val missing = rootKeys.mapNotNull { key ->
            if (Regex("(?m)^${Regex.escape(key)}\\s*:").containsMatchIn(existing)) null else blocks[key]
        }
        if (missing.isEmpty()) return

        val merged = buildString {
            append(existing.trimEnd())
            if (isNotEmpty()) append("\n\n")
            append(missing.joinToString("\n\n") { it.joinToString("\n") })
            append('\n')
        }
        writeAtomic(plugin, file, merged)
    }

    private fun rootBlocks(lines: List<String>): Map<String, List<String>> {
        val starts = lines.indices.mapNotNull { index ->
            val line = lines[index]
            if (line.isBlank() || line.startsWith(' ') || line.trimStart().startsWith('#')) return@mapNotNull null
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            index to line.substring(0, colon).trim().trim('"', '\'')
        }
        return starts.mapIndexed { position, (start, key) ->
            val end = starts.getOrNull(position + 1)?.first ?: lines.size
            key to lines.subList(start, end).dropLastWhile(String::isBlank)
        }.toMap()
    }

    private fun writeAtomic(plugin: BukkitPlugin, file: File, content: String) {
        val temp = File(file.parentFile, "${file.name}.supplemental.tmp")
        runCatching {
            temp.writeText(content)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { error ->
            temp.delete()
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to append supplemental config to ${file.name}.", error)
        }
    }
}
