package ua.inventorytype.pnclans.impl.placeholder

import org.bukkit.Bukkit
import ua.inventorytype.pnclans.BukkitPlugin
import java.util.UUID

/** Значения плейсхолдеров `%pnclans_*%`; публикуются в PlaceholderAPI через pnLibrary. */
class PnClansPlaceholders(private val plugin: BukkitPlugin) {

    /** Ключи, которые публикуются в PlaceholderAPI. */
    val keys: List<String> = listOf(
        "clan", "name", "tag", "role", "level", "balance", "bank", "points", "mmr", "kills", "deaths",
        "battle_wins", "wins", "battle_losses", "losses", "members",
    )

    /** Возвращает значение для игрока; пустая строка, если игрока нет на сервере. */
    fun resolve(playerId: UUID?, key: String): String {
        if (playerId == null) return ""
        val bukkitPlayer = Bukkit.getPlayer(playerId) ?: return ""
        val clan = plugin.clanService.getClanUser(bukkitPlayer)

        return when (key) {
            "clan", "name" -> clan?.name ?: "Нет"
            "tag" -> if (clan != null) "§8[§6${clan.name}§8]" else ""
            "role" -> {
                if (clan == null) "Нет"
                else {
                    val user = clan.getMember(playerId)
                    if (user != null) clan.getUserRole(user).name else "Нет"
                }
            }
            "level" -> clan?.level?.toString() ?: "0"
            "balance", "bank" -> clan?.bankBalance?.toString() ?: "0"
            "points" -> clan?.points?.toString() ?: "0"
            "mmr" -> clan?.mmr?.toString() ?: "0"
            "kills" -> clan?.kills?.toString() ?: "0"
            "deaths" -> clan?.deaths?.toString() ?: "0"
            "battle_wins", "wins" -> clan?.battleWins?.toString() ?: "0"
            "battle_losses", "losses" -> clan?.battleLosses?.toString() ?: "0"
            "members" -> if (clan != null) "${clan.onlineCount}/${clan.users.size}" else "0/0"
            else -> ""
        }
    }
}
