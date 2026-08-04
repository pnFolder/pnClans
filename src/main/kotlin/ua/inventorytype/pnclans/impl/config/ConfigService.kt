package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import org.bukkit.plugin.Plugin
import java.io.File

class ConfigService(private val plugin: Plugin) {

    val yaml: Yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            // Вот эта опция включат синтаксис с !message, !sound, !command
            polymorphismStyle = PolymorphismStyle.Tag
        )
    )

    // Хранилище всех загруженных настроек
    lateinit var settings: Settings private set

    /**
     * Перезагрузка или первоначальная загрузка всех файлов
     */
    fun loadAll() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        // Загружаем каждый конфиг через один универсальный метод
        settings = loadOrCreate("config.yml", Settings.serializer(), Settings())
    }

    /**
     * Универсальный метод: загружает файл или создает его с дефолтными значениями
     */
    private fun <T> loadOrCreate(fileName: String, serializer: KSerializer<T>, default: T): T {
        val file = File(plugin.dataFolder, fileName)

        if (!file.exists()) {
            val encoded = yaml.encodeToString(serializer, default)
            file.writeText(encoded)
            return default
        }

        val content = file.readText()
        return yaml.decodeFromString(serializer, content)
    }
}