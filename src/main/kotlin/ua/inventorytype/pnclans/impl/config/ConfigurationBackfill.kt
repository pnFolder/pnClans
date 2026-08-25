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

/**
 * Makes newly introduced configurable fields visible in existing administrator-owned YAML files.
 * Existing values are loaded first by ConfigService, then re-serialized only when a v1.2.1 marker
 * is missing. This avoids silently running new features from code defaults that an administrator
 * cannot discover in their config files.
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
        rewriteIfMissing(
            plugin,
            "battles.yml",
            listOf("lobbyTimeoutSeconds:", "countdownSeconds:", "keepInventoryOnDeath:", "similarMmrGlowThreshold:"),
            ClanBattlesConfig.serializer(),
            config.battles
        )
        rewriteIfMissing(
            plugin,
            "messages.yml",
            listOf("lobbyOpened:", "lobbyNotEnoughSelected:", "moduleDisabled:"),
            MessagesConfig.serializer(),
            config.messages
        )
    }

    private fun <T> rewriteIfMissing(
        plugin: BukkitPlugin,
        fileName: String,
        markers: List<String>,
        serializer: KSerializer<T>,
        value: T
    ) {
        val file = File(plugin.dataFolder, fileName)
        val existing = file.takeIf(File::exists)?.readText().orEmpty()
        if (markers.all(existing::contains)) return

        runCatching {
            val content = yaml.encodeToString(serializer, value)
            val temp = File(plugin.dataFolder, "$fileName.v121.tmp")
            temp.writeText(content)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { error ->
            plugin.logger.warning("[pnClans] Failed to backfill $fileName: ${error.message}")
        }
    }
}
