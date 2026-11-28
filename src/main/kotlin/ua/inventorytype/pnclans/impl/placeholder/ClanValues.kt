package ua.inventorytype.pnclans.impl.placeholder

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply
import ua.inventorytype.pnclans.api.clan.Clan
import java.util.UUID

/**
 * Значения клана для библиотеки: `{name}` в текстах плагина и `%pnclans_name%` в PlaceholderAPI. Что показывает
 * каждое значение, решает [ClanPlaceholderValues]; здесь только адреса и описания.
 */
@Placeholders(root = true)
class ClanValues(
    private val values: ClanPlaceholderValues,
    private val clanOf: (UUID?) -> Clan?,
) {
    @Placeholder("Название клана по формату из настроек")
    fun name(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "name")

    @Placeholder("Тег клана по формату из настроек")
    fun tag(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "tag")

    @Placeholder("Роль игрока в клане")
    fun role(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "role")

    @Placeholder("Уровень клана")
    fun level(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "level")

    @Placeholder("Баланс казны клана")
    fun balance(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "balance")

    @Placeholder("Очки клана")
    fun points(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "points")

    @Placeholder("Рейтинг клана в боях")
    fun mmr(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "mmr")

    @Placeholder("Убийства клана")
    fun kills(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "kills")

    @Placeholder("Смерти клана")
    fun deaths(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "deaths")

    @Placeholder("Победы клана в боях")
    fun wins(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "wins")

    @Placeholder("Поражения клана в боях")
    fun losses(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "losses")

    @Placeholder("Игроков онлайн из всех в клане")
    fun members(@Viewer viewer: ViewerInfo): Reply<String> = text(viewer, "members")

    private fun text(viewer: ViewerInfo, key: String): Reply<String> {
        val id = viewer.playerId
        return Reply.of(values.value(key, clanOf(id), id, roleTitle = false))
    }
}
