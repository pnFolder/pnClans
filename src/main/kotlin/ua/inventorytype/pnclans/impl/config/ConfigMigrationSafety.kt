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
 * Preserves administrator-owned values around legacy schema migrations that historically replaced
 * built-in quest definitions and the battle display with new defaults.
 */
internal class ConfigMigrationSafety private constructor(
    private val legacyQuests: ClanQuestsConfig?,
    private val legacyBattles: ClanBattlesConfig?
) {
    /**
     * Restores administrator-owned values after ConfigService has upgraded an old schema.
     * Returns true when files were rewritten and ConfigService must load them once more.
     */
    fun reconcile(plugin: BukkitPlugin, config: ConfigService): Boolean {
        var changed = false

        legacyQuests?.let { legacy ->
            val migrated = config.quests
            val safe = migrated.copy(
                schemaVersion = migrated.schemaVersion,
                display = legacy.display,
                // New built-ins from the target schema stay available, while every administrator-owned
                // quest with the same ID wins over the generated default.
                quests = migrated.quests + legacy.quests
            )
            if (writeAtomic(plugin, "quests.yml", ClanQuestsConfig.serializer(), safe)) {
                changed = true
            }
        }

        legacyBattles?.let { legacy ->
            val migrated = config.battles
            // The legacy migration already preserves battle rules and arenas; only display was forcibly reset.
            val safe = migrated.copy(display = legacy.display)
            if (writeAtomic(plugin, "battles.yml", ClanBattlesConfig.serializer(), safe)) {
                changed = true
            }
        }

        return changed
    }

    companion object {
        private val yaml = Yaml(
            configuration = YamlConfiguration(
                encodeDefaults = true,
                strictMode = false,
                polymorphismStyle = PolymorphismStyle.Tag
            )
        )

        fun capture(plugin: BukkitPlugin): ConfigMigrationSafety {
            val questDefaults = ClanQuestsConfig()
            val battleDefaults = ClanBattlesConfig()
            return ConfigMigrationSafety(
                legacyQuests = readOldSchema(
                    plugin,
                    "quests.yml",
                    ClanQuestsConfig.serializer(),
                    questDefaults.schemaVersion
                ),
                legacyBattles = readOldSchema(
                    plugin,
                    "battles.yml",
                    ClanBattlesConfig.serializer(),
                    battleDefaults.schemaVersion
                )
            )
        }

        private fun <T> readOldSchema(
            plugin: BukkitPlugin,
            fileName: String,
            serializer: KSerializer<T>,
            currentVersion: Int
        ): T? {
            val file = File(plugin.dataFolder, fileName)
            if (!file.exists()) return null
            val content = runCatching(file::readText).getOrElse { error ->
                plugin.logger.log(Level.WARNING, "[pnClans] Cannot snapshot $fileName before migration.", error)
                return null
            }
            val version = Regex("(?m)^schemaVersion\\s*:\\s*(\\d+)")
                .find(content)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            if (version >= currentVersion) return null

            return runCatching { yaml.decodeFromString(serializer, content) }
                .onFailure { error ->
                    plugin.logger.log(
                        Level.WARNING,
                        "[pnClans] Cannot parse $fileName for safe migration; normal config loading will report the underlying problem.",
                        error
                    )
                }
                .getOrNull()
        }

        private fun <T> writeAtomic(
            plugin: BukkitPlugin,
            fileName: String,
            serializer: KSerializer<T>,
            value: T
        ): Boolean {
            val file = File(plugin.dataFolder, fileName)
            val temp = File(plugin.dataFolder, "$fileName.safe-migration.tmp")
            return runCatching {
                temp.writeText(yaml.encodeToString(serializer, value))
                try {
                    Files.move(
                        temp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }.fold(
                onSuccess = { true },
                onFailure = { error ->
                    temp.delete()
                    plugin.logger.log(Level.SEVERE, "[pnClans] Failed to preserve administrator values in $fileName.", error)
                    false
                }
            )
        }
    }
}
