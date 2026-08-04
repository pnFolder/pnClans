package ua.inventorytype.pnclans.api.placeholder

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player

class PlaceholderRegistry {

    // Хранилище: имя плейсхолдера -> функция получения значения
    private val placeholders = mutableMapOf<String, (Player) -> String>()

    /**
     * Регистрация одиночного плейсхолдера
     */
    fun register(key: String, provider: (Player) -> String) {
        placeholders[key.lowercase()] = provider
    }

    /**
     * Замена всех зарегистрированных плейсхолдеров в тексте
     */
    fun process(player: Player, text: String, customPlaceholders: Map<String, String> = emptyMap()): String {
        var result = text
        customPlaceholders.forEach { (key, value) -> result = result.replace("{$key}", value) }
        placeholders.forEach { (key, provider) ->
            if (result.contains("{$key}")) {
                result = result.replace("{$key}", provider(player))
            }
        }

        return PlaceholderAPI.setPlaceholders(player, result)
    }

    fun process(player: Player, lines: List<String>, customPlaceholders: Map<String, String> = emptyMap()): List<String> {
        return lines.map { process(player, it, customPlaceholders) }
    }
}