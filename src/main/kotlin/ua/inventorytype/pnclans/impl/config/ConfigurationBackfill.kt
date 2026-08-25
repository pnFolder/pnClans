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
        mergeMissingNestedFields(plugin, "battles.yml", ClanBattlesConfig.serializer(), config.battles)
        mergeMissingNestedFields(plugin, "messages.yml", MessagesConfig.serializer(), config.messages)
    }

    fun applyV122(plugin: BukkitPlugin, config: ConfigService) {
        migrateLegacyUpdaterSettings(plugin)
        mergeMissingRootFields(plugin, "config.yml", Settings.serializer(), config.settings)
        mergeMissingNestedFields(plugin, "config.yml", Settings.serializer(), config.settings)

        // A loaded Map keeps only keys physically present in YAML. Therefore menu backfill must compare
        // the administrator file against pristine defaults, not against config.menus loaded from that file.
        val menuDefaults = MenusConfig()
        mergeMissingRootFields(plugin, "menus.yml", MenusConfig.serializer(), menuDefaults)
        mergeMissingNestedFields(plugin, "menus.yml", MenusConfig.serializer(), menuDefaults)
        mergeMissingMenuItemKeys(plugin, menuDefaults)

        mergeMissingNestedFields(plugin, "messages.yml", MessagesConfig.serializer(), config.messages)
    }

    private fun migrateLegacyUpdaterSettings(plugin: BukkitPlugin) {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.exists()) return

        runCatching {
            val original = file.readText()
            if (Regex("(?m)^updateChannel\\s*:").containsMatchIn(original)) return

            val filtered = original.lineSequence()
                .filterNot { line ->
                    val trimmed = line.trim()
                    trimmed.matches(Regex("(?:checkUpdates|autoUpdate)\\s*:.*", RegexOption.IGNORE_CASE)) ||
                        trimmed.contains("Проверять ли наличие новых версий на GitHub", ignoreCase = true) ||
                        trimmed.contains("Автоматически скачивать последнюю версию плагина", ignoreCase = true)
                }
                .joinToString("\n")
                .trimEnd() + "\n"

            if (filtered != original) writeAtomic(plugin, file, filtered)
        }.onFailure { error ->
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to migrate legacy updater settings.", error)
        }
    }

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

    private fun mergeMissingMenuItemKeys(plugin: BukkitPlugin, menus: MenusConfig) {
        val file = File(plugin.dataFolder, "menus.yml")
        if (!file.exists()) return

        runCatching {
            val target = file.readText().lines().toMutableList()
            val source = yaml.encodeToString(MenusConfig.serializer(), menus).lines()
            var changed = false

            directBlocks(source, 0).forEach { sourceSection ->
                val sourceItemsBlock = directBlocks(sourceSection.lines, 2).firstOrNull { it.key == "items" }
                    ?: return@forEach
                val sourceItems = directBlocks(sourceItemsBlock.lines, 4)
                if (sourceItems.isEmpty()) return@forEach

                val targetSection = directBlocks(target, 0).firstOrNull { it.key == sourceSection.key }
                    ?: return@forEach
                val targetItemsBlock = directBlocks(targetSection.lines, 2).firstOrNull { it.key == "items" }
                    ?: return@forEach
                val existingItemKeys = directBlocks(targetItemsBlock.lines, 4).mapTo(mutableSetOf()) { it.key }
                val missingItems = sourceItems.filter { it.key !in existingItemKeys }
                if (missingItems.isEmpty()) return@forEach

                val insertAt = (targetSection.start + targetItemsBlock.endExclusive).coerceAtMost(target.size)
                val insertion = mutableListOf<String>()
                missingItems.forEachIndexed { index, block ->
                    if (index > 0) insertion += ""
                    insertion += block.lines
                }
                target.addAll(insertAt, insertion)
                changed = true
            }

            val permissionTemplate = menus.userPermissionsMenu.items["permission"]
                ?: menus.editorRolesMenu.items["permission"]
            val explicitItems = buildList {
                if (permissionTemplate != null) {
                    add(Triple("userPermissionsMenu", "permission", permissionTemplate))
                }
                add(
                    Triple(
                        "leaveConfirmMenu",
                        "battleWarningLeader",
                        GuiItemConfig(
                            slot = 0,
                            material = "PAPER",
                            name = "&#FC3737Активная битва — роспуск",
                            lore = listOf(
                                "",
                                "&#FC3737 «Активная битва»",
                                " &7- &fРоспуск немедленно завершит бой.",
                                " &7- &fКлану засчитают техническое поражение",
                                " &7- &fи снимут MMR.",
                                ""
                            )
                        )
                    )
                )
                add(
                    Triple(
                        "leaveConfirmMenu",
                        "battleWarningMember",
                        GuiItemConfig(
                            slot = 0,
                            material = "PAPER",
                            name = "&#FC3737Активная битва — выход",
                            lore = listOf(
                                "",
                                "&#FC3737 «Активная битва»",
                                " &7- &fПосле выхода вы больше не сможете",
                                " &7- &fучаствовать в текущей битве.",
                                ""
                            )
                        )
                    )
                )
                add(
                    Triple(
                        "chestMenu",
                        "lockedSlot",
                        GuiItemConfig(
                            slot = 0,
                            material = "RED_STAINED_GLASS_PANE",
                            name = "&#FF3B3B🔒 СЛОТ ЗАБЛОКИРОВАН",
                            lore = listOf(
                                "",
                                "&#9EFC65 «Информация»",
                                " &7- &fСтатус: &#FC3737Закрыт для хранения",
                                " &7- &fТребуется уровень клана: &e{level} лвл.",
                                "",
                                "&#FC65DF «Как разблокировать?»",
                                " &7- &fКаждый уровень клана открывает",
                                " &7- &fдополнительно &e9 новых слотов&f!",
                                "",
                                "&#FF8702➥ &fНажмите &eЭволюция Клана &fдля прокачки!"
                            )
                        )
                    )
                )
                listOf(46, 47, 51, 52).forEach { slot ->
                    add(
                        Triple(
                            "chestMenu",
                            "decor_$slot",
                            GuiItemConfig(slot = slot, material = "BLACK_STAINED_GLASS_PANE", name = " ")
                        )
                    )
                }
            }
            explicitItems.forEach { (section, key, item) ->
                changed = insertExplicitMenuItemIfMissing(target, section, key, item) || changed
            }

            if (changed) {
                writeAtomic(plugin, file, target.joinToString("\n").trimEnd() + "\n")
            }
        }.onFailure { error ->
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to backfill menu item IDs in menus.yml.", error)
        }
    }

    private fun insertExplicitMenuItemIfMissing(
        target: MutableList<String>,
        sectionKey: String,
        itemKey: String,
        item: GuiItemConfig
    ): Boolean {
        val section = directBlocks(target, 0).firstOrNull { it.key == sectionKey } ?: return false
        val itemsBlock = directBlocks(section.lines, 2).firstOrNull { it.key == "items" } ?: return false
        if (directBlocks(itemsBlock.lines, 4).any { it.key == itemKey }) return false

        val serialized = yaml.encodeToString(GuiItemConfig.serializer(), item).lines()
        val insertion = buildList {
            add("    $itemKey:")
            serialized.forEach { line -> add("      $line") }
        }
        val insertAt = (section.start + itemsBlock.endExclusive).coerceAtMost(target.size)
        target.addAll(insertAt, insertion)
        return true
    }

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
