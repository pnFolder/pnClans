package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger

/**
 * One YAML file backed by a serializable model. A missing file is created from the defaults; a file that cannot be
 * read is copied to `<name>.broken` and replaced by the defaults, so the plugin always starts with a valid model.
 */
class ManagedYamlFile<T>(
    private val file: File,
    private val defaults: T,
    private val logger: Logger,
    private val yaml: Yaml,
    private val serializer: KSerializer<T>,
) {
    fun reloadValue(): T {
        if (!file.exists()) {
            save(defaults)
            return defaults
        }
        return try {
            yaml.decodeFromString(serializer, file.readText(Charsets.UTF_8))
        } catch (failure: Exception) {
            val broken = File(file.parentFile, file.name + ".broken")
            runCatching { Files.copy(file.toPath(), broken.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            logger.log(Level.SEVERE, "Не удалось прочитать ${file.name}; копия сохранена как ${broken.name}, используются значения по умолчанию.", failure)
            save(defaults)
            defaults
        }
    }

    fun save(value: T) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(yaml.encodeToString(serializer, value), Charsets.UTF_8)
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
