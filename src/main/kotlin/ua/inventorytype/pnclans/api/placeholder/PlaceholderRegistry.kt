package ua.inventorytype.pnclans.api.placeholder

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.util.ColorUtil

class PlaceholderRegistry {

    private val placeholders = mutableMapOf<String, (Player) -> String>()

    /**
     * Регистрация одиночного плейсхолдера
     */
    fun register(key: String, provider: (Player) -> String) {
        placeholders[key.lowercase()] = provider
    }

    /**
     * Автоматическая регистрация всех стандартных плейсхолдеров клановой системы
     */
    fun registerDefaults(clanService: ClanService) {
        register("clan") { player -> clanService.getClanUser(player)?.name ?: "Нет" }
        register("clan_name") { player -> clanService.getClanUser(player)?.name ?: "Нет" }
        register("clan_tag") { player -> clanService.getClanUser(player)?.let { "§8[§6${it.name}§8]" } ?: "" }
        register("clan_role") { player ->
            val clan = clanService.getClanUser(player)
            if (clan != null) {
                val user = clan.users.find { it.uuid == player.uniqueId }
                if (user != null) clanService.plugin.configService.getRoleDisplayName(clan.getUserRole(user)) else "Нет"
            } else "Нет"
        }
        register("clan_level") { player -> clanService.getClanUser(player)?.level?.toString() ?: "0" }
        register("clan_balance") { player -> clanService.getClanUser(player)?.bankBalance?.toString() ?: "0" }
        register("clan_mmr") { player -> clanService.getClanUser(player)?.mmr?.toString() ?: "0" }
        register("clan_kills") { player -> clanService.getClanUser(player)?.kills?.toString() ?: "0" }
        register("clan_deaths") { player -> clanService.getClanUser(player)?.deaths?.toString() ?: "0" }
        register("clan_members") { player -> clanService.getClanUser(player)?.let { "${it.onlineCount}/${it.users.size}" } ?: "0/0" }
        register("player_name") { player -> player.name }
    }

    /**
     * Замена всех зарегистрированных плейсхолдеров и кастомных ключей в тексте
     */
    fun process(player: Player, text: String, customPlaceholders: Map<String, String> = emptyMap()): String {
        var result = text

        customPlaceholders.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        placeholders.forEach { (key, provider) ->
            if (result.contains("{$key}")) {
                result = result.replace("{$key}", provider(player))
            }
        }

        result = ColorUtil.color(result)

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result)
        }

        return result
    }

    fun process(player: Player, lines: List<String>, customPlaceholders: Map<String, String> = emptyMap()): List<String> {
        return lines.map { process(player, it, customPlaceholders) }
    }
}