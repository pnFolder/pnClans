package ua.inventorytype.pnclans.impl.placeholder

import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.impl.config.PlaceholderConfig
import ua.inventorytype.pnclans.impl.text.ClanText
import java.util.UUID

/**
 * Единственное место, где описано, что показывает каждый плейсхолдер клана. Отсюда берут значения `{clan_name}` в
 * меню и сообщениях, `%pnclans_name%` в PlaceholderAPI и `{pnclans/pnclans:name}` в дереве pnLibrary.
 */
class ClanPlaceholderValues(
    private val formats: () -> PlaceholderConfig,
    private val roleName: (ClanRole) -> String,
    private val color: (String) -> String = ClanText::legacy,
) {

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
        "clan", "name" -> formats().let { PlaceholderFormat.render(if (clan != null) it.clanFormat else it.noClan, clan?.name, color) }
        "tag" -> formats().let { PlaceholderFormat.render(if (clan != null) it.tagFormat else it.noTag, clan?.name, color) }
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
        return if (roleTitle) roleName(role) else role.name
    }

    private companion object {
        const val NONE = "Нет"
        const val ZERO = "0"
    }
}
