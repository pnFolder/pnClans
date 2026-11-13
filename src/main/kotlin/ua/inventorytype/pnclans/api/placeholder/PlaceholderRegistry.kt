package ua.inventorytype.pnclans.api.placeholder

import ua.inventorytype.pnclans.impl.text.ClanText
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import java.util.concurrent.ConcurrentHashMap

/**
 * Подставляет плейсхолдеры в текст меню и сообщений через [placeholders] из pnLibrary: значения клана, ключи
 * аддонов, `%...%` из PlaceholderAPI. Здесь остаются только цвета.
 */
class PlaceholderRegistry(private val placeholders: PlaceholderService) {

    private val addonKeys = ConcurrentHashMap<String, AutoCloseable>()

    /** Registers or replaces a `{key}` placeholder provider; it is published to pnLibrary like any other value. */
    fun register(key: String, provider: (Player) -> String) {
        val name = key.lowercase()
        val registration = placeholders.register(name, String::class.java) { builder ->
            builder.resolve { request -> request.playerId?.let(Bukkit::getPlayer)?.let(provider) ?: "" }
        }
        addonKeys.put(name, registration)?.close()
    }

    /** Fills `{key}` tokens from [customPlaceholders] first, then everything registered in pnLibrary, then colours. */
    fun process(player: Player, text: String, customPlaceholders: Map<String, String> = emptyMap(), colorize: Boolean = true): String {
        if (text.isEmpty()) return ""
        var result = text
        if (result.contains('{') || result.contains('%')) {
            // Значения клана считаются сразу, поэтому ответ готов без ожидания
            result = placeholders.render(result, player.uniqueId, customPlaceholders).toCompletableFuture().getNow(result)
        }
        if (colorize && (result.contains('&') || result.contains('§') || result.contains('#'))) {
            result = ClanText.legacy(result)
        }
        return result
    }

    /** Resolves every entry in [lines] using the same rules as [process]. */
    fun process(player: Player, lines: List<String>, customPlaceholders: Map<String, String> = emptyMap(), colorize: Boolean = true): List<String> =
        lines.map { process(player, it, customPlaceholders, colorize) }
}
