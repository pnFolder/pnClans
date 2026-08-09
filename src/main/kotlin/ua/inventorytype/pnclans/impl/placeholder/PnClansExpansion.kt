package ua.inventorytype.pnclans.impl.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin

class PnClansExpansion(private val plugin: BukkitPlugin) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "pnclans"

    override fun getAuthor(): String = "overdyn"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""
        val bukkitPlayer = player.player ?: return ""
        val clan = plugin.clanService.getClanUser(bukkitPlayer)

        return when (params.lowercase()) {
            "clan", "name" -> clan?.name ?: "Нет"
            "tag" -> if (clan != null) "§8[§6${clan.name}§8]" else ""
            "role" -> {
                if (clan == null) "Нет"
                else {
                    val user = clan.getMember(player.uniqueId)
                    if (user != null) clan.getUserRole(user).name else "Нет"
                }
            }
            "level" -> clan?.level?.toString() ?: "0"
            "balance", "bank" -> clan?.bankBalance?.toString() ?: "0"
            "points" -> clan?.points?.toString() ?: "0"
            "mmr" -> clan?.mmr?.toString() ?: "0"
            "kills" -> clan?.kills?.toString() ?: "0"
            "deaths" -> clan?.deaths?.toString() ?: "0"
            "members" -> if (clan != null) "${clan.onlineCount}/${clan.users.size}" else "0/0"
            else -> null
        }
    }
}
