package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import ua.inventorytype.pnclans.BukkitPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level

/**
 * Makes newly introduced configurable fields visible in administrator-owned YAML files without
 * replacing the whole file. Existing values, unknown addon keys and surrounding comments stay in place.
 */
internal object ConfigurationBackfill {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            polymorphismStyle = PolymorphismStyle.Tag
        )
    )

    fun applyV121(plugin: BukkitPlugin, config: ConfigService) {
        mergeMissingRootFields(plugin, "battles.yml", ClanBattlesConfig.serializer(), config.battles)
        mergeMissingNestedFields(plugin, "messages.yml", MessagesConfig.serializer(), config.messages)
    }

    fun applyV122(plugin: BukkitPlugin, config: ConfigService) {
        mergeMissingRootFields(plugin, "config.yml", Settings.serializer(), config.settings)
        mergeMissingNestedFields(plugin, "messages.yml", MessagesConfig.serializer(), config.messages)
    }

    /** Appends only root-level fields that are absent from the administrator's existing file. */
    private fun <T> mergeMissingRootFields(
        plugin: BukkitPlugin,
        fileName: String,
        serializer: KSerializer<T>,
        value: T
    ) {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) return

        runCatching {
            val existingLines = file.readText().lines().toMutableList()
            val sourceLines = yaml.encodeToString(serializer, value).lines()
            val existingKeys = directBlocks(existingLines, 0).mapTo(mutableSetOf()) { it.key }
            val missing = directBlocks(sourceLines, 0).filter { it.key !in existingKeys }
            if (missing.isEmpty()) return

            trimTrailingEmptyLines(existingLines)
            missing.forEach { block ->
                if (existingLines.isNotEmpty()) existingLines += ""
                existingLines += block.lines
            }
            writeAtomic(plugin, file, existingLines.joinToString("\n").trimEnd() + "\n")
        }.onFailure { error ->
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to backfill $fileName without rewriting it.", error)
        }
    }

    /**
     * Adds missing direct children to every existing top-level section. If an entire section is absent,
     * the serialized section is appended as one block. Existing child values are never overwritten.
     */
    private fun <T> mergeMissingNestedFields(
        plugin: BukkitPlugin,
        fileName: String,
        serializer: KSerializer<T>,
        value: T
    ) {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) return

        runCatching {
            val target = file.readText().lines().toMutableList()
            val source = yaml.encodeToString(serializer, value).lines()
            val sourceSections = directBlocks(source, 0)
            var changed = false

            sourceSections.forEach { sourceSection ->
                val targetSections = directBlocks(target, 0)
                val targetSection = targetSections.firstOrNull { it.key == sourceSection.key }
                if (targetSection == null) {
                    trimTrailingEmptyLines(target)
                    if (target.isNotEmpty()) target += ""
                    target += sourceSection.lines
                    changed = true
                    return@forEach
                }

                val sourceChildren = directBlocks(sourceSection.lines, 2)
                if (sourceChildren.isEmpty()) return@forEach

                val freshSections = directBlocks(target, 0)
                val freshSection = freshSections.firstOrNull { it.key == sourceSection.key } ?: return@forEach
                val existingChildKeys = directBlocks(freshSection.lines, 2).mapTo(mutableSetOf()) { it.key }
                val missingChildren = sourceChildren.filter { it.key !in existingChildKeys }
                if (missingChildren.isEmpty()) return@forEach

                var insertAt = freshSection.endExclusive
                if (insertAt > target.size) insertAt = target.size
                val insertion = mutableListOf<String>()
                missingChildren.forEachIndexed { index, block ->
                    if (index > 0) insertion += ""
                    insertion += block.lines
                }
                target.addAll(insertAt, insertion)
                changed = true
            }

            if (changed) {
                writeAtomic(plugin, file, target.joinToString("\n").trimEnd() + "\n")
            }
        }.onFailure { error ->
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to merge missing fields into $fileName.", error)
        }
    }

    /** Finds YAML mapping fields declared exactly at [indent] and keeps each complete value block. */
    private fun directBlocks(lines: List<String>, indent: Int): List<YamlBlock> {
        val keyIndexes = lines.indices.mapNotNull { index ->
            keyAtIndent(lines[index], indent)?.let { key -> index to key }
        }
        if (keyIndexes.isEmpty()) return emptyList()

        return keyIndexes.mapIndexed { position, (start, key) ->
            val end = keyIndexes.getOrNull(position + 1)?.first ?: lines.size
            YamlBlock(key, start, end, lines.subList(start, end).dropLastWhile(String::isBlank))
        }
    }

    private fun keyAtIndent(line: String, indent: Int): String? {
        if (line.isBlank()) return null
        val actualIndent = line.takeWhile { it == ' ' }.length
        if (actualIndent != indent) return null
        val trimmed = line.trim()
        if (trimmed.startsWith('#') || trimmed.startsWith('-')) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val key = trimmed.substring(0, colon).trim().trim('"', '\'')
        if (key.isEmpty() || key.any(Char::isWhitespace)) return null
        return key
    }

    private fun trimTrailingEmptyLines(lines: MutableList<String>) {
        while (lines.lastOrNull()?.isBlank() == true) lines.removeAt(lines.lastIndex)
    }

    private fun writeAtomic(plugin: BukkitPlugin, file: File, content: String) {
        val temp = File(file.parentFile, "${file.name}.backfill.tmp")
        temp.writeText(content)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
        plugin.logger.fine("[pnClans] Added missing configuration fields to ${file.name} without replacing existing values.")
    }

    private data class YamlBlock(
        val key: String,
        val start: Int,
        val endExclusive: Int,
        val lines: List<String>
    )
}
