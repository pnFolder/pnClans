package ua.inventorytype.pnclans.impl.placeholder

import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import java.util.UUID

/**
 * Единственное место, где описано, что показывает каждый плейсхолдер клана. Отсюда берут значения `{clan_name}` в
 * меню и сообщениях, `%pnclans_name%` в PlaceholderAPI и `{pnclans/pnclans:name}` в дереве pnLibrary.
 */
class ClanPlaceholderValues(private val plugin: BukkitPlugin) {

    private val formats get() = plugin.services.configService.settings.placeholder

    /** Ключи без префикса `clan_`. */
    val keys: List<String> = listOf(
        "clan", "name", "tag", "role", "level", "balance", "bank", "points", "mmr", "kills", "deaths",
        "battle_wins", "wins", "battle_losses", "losses", "members",
    )

    /**
     * Значение для игрока [playerId] в клане [clan]. [roleTitle] включает название роли из настроек вместо имени роли.
     * Пустая строка, если ключа нет.
     */
    fun value(key: String, clan: Clan?, playerId: UUID?, roleTitle: Boolean): String = when (key) {
        "clan", "name" -> PlaceholderFormat.render(if (clan != null) formats.clanFormat else formats.noClan, clan?.name)
        "tag" -> PlaceholderFormat.render(if (clan != null) formats.tagFormat else formats.noTag, clan?.name)
        "role" -> role(clan, playerId, roleTitle)
        "level" -> clan?.level?.toString() ?: ZERO
        "balance", "bank" -> clan?.bankBalance?.toString() ?: ZERO
        "points" -> clan?.points?.toString() ?: ZERO
        "mmr" -> clan?.mmr?.toString() ?: ZERO
        "kills" -> clan?.kills?.toString() ?: ZERO
        "deaths" -> clan?.deaths?.toString() ?: ZERO
        "battle_wins", "wins" -> clan?.battleWins?.toString() ?: ZERO
        "battle_losses", "losses" -> clan?.battleLosses?.toString() ?: ZERO
        "members" -> if (clan != null) "${clan.onlineCount}/${clan.users.size}" else "0/0"
        else -> ""
    }

    private fun role(clan: Clan?, playerId: UUID?, roleTitle: Boolean): String {
        if (clan == null || playerId == null) return NONE
        val user = clan.getMember(playerId) ?: return NONE
        val role = clan.getUserRole(user)
        return if (roleTitle) plugin.services.configService.getRoleDisplayName(role) else role.name
    }

    private companion object {
        const val NONE = "Нет"
        const val ZERO = "0"
    }
}
