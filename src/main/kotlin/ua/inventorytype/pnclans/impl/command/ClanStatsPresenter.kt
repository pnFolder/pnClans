package ua.inventorytype.pnclans.impl.command

import org.bukkit.command.CommandSender
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.impl.clan.ClanStatsPeriod
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.clan.ClanUser
import ua.inventorytype.pnclans.impl.util.ColorUtil
import java.util.Locale

internal object ClanStatsPresenter {
    fun send(
        sender: CommandSender,
        clan: Clan,
        user: User,
        roleName: String,
        clanService: ClanService,
        period: ClanStatsPeriod? = null
    ) {
        val member = user as? ClanUser
        sender.colored("")
        sender.colored("&#FC7D37✦ &fСтатистика участника &8• &#5EA9FD${user.playerName}")
        sender.colored("&8Клан: &f${clan.name} &8• &7Должность: $roleName")

        if (member == null) {
            sender.colored("&#FC3737Детальная статистика этого участника недоступна.")
            return
        }

        val periods = if (period != null) {
            listOf(period)
        } else {
            listOf(ClanStatsPeriod.DAY, ClanStatsPeriod.WEEK, ClanStatsPeriod.MONTH, ClanStatsPeriod.ALL)
        }
        periods.forEach { selected ->
            val stats = member.combatStats(selected)
            sender.colored(
                "${periodColor(selected)}${selected.label}: " +
                    "&#5EFD7D${stats.kills} убийств &8• &#FC3737${stats.deaths} смертей " +
                    "&8• &#FFD700K/D ${String.format(Locale.US, "%.2f", stats.kdr)}"
            )
        }

        val playtime = clanService.playtimeTracker.playtime(member.uuid, clanService, member.playtimeSeconds)
        sender.colored("&8Вклад: &#FC65DF${member.points} очк. &8• Время в клане: &#5EA9FD${formatPlaytime(playtime)}")
        sender.colored("&8Периоды считаются по UTC и относятся только к текущему клану.")
        sender.colored("")
    }

    private fun periodColor(period: ClanStatsPeriod): String = when (period) {
        ClanStatsPeriod.DAY -> "&#9EFC65"
        ClanStatsPeriod.WEEK -> "&#5EA9FD"
        ClanStatsPeriod.MONTH -> "&#FC65DF"
        ClanStatsPeriod.ALL -> "&#FFD700"
    }

    private fun formatPlaytime(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val days = safe / 86_400L
        val hours = (safe % 86_400L) / 3_600L
        val minutes = (safe % 3_600L) / 60L
        return when {
            days > 0L -> "${days}д ${hours}ч ${minutes}м"
            hours > 0L -> "${hours}ч ${minutes}м"
            else -> "${minutes}м"
        }
    }

    private fun CommandSender.colored(text: String) {
        sendMessage(ColorUtil.color(text))
    }
}
