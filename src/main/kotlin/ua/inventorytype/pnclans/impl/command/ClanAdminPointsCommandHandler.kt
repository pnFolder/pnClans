package ua.inventorytype.pnclans.impl.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsTransaction
import ua.inventorytype.pnclans.impl.clan.ClanPointsService
import ua.inventorytype.pnclans.impl.util.ColorUtil
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Complete administrator surface for clan points and anti-farm audit data. */
internal class ClanAdminPointsCommandHandler(private val plugin: BukkitPlugin) {
    private val clans get() = plugin.clanService
    private val pointsService get() = plugin.clanPointsService as ClanPointsService

    fun execute(sender: CommandSender, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> help(sender)
            "info" -> info(sender, args.drop(1))
            "add", "remove", "set" -> mutate(sender, args)
            "reset" -> reset(sender, args.drop(1))
            "history" -> history(sender, args.drop(1))
            "rollback" -> rollback(sender, args.drop(1))
            "antifarm" -> antiFarm(sender, args.drop(1))
            "clear-antifarm" -> clearAntiFarm(sender, args.drop(1))
            else -> usage(sender)
        }
    }

    fun complete(args: List<String>): List<String> {
        val actions = listOf("help", "info", "add", "remove", "set", "reset", "history", "rollback", "antifarm", "clear-antifarm")
        if (args.isEmpty()) return actions
        val current = args.last()
        val candidates = when (args.size) {
            1 -> actions
            2 -> if (args[0].lowercase() in actions - "help") clanNames() else emptyList()
            3 -> when (args[0].lowercase()) {
                "antifarm" -> memberNames(args[1])
                "history" -> listOf("1", "2", "3")
                else -> emptyList()
            }
            else -> emptyList()
        }
        return candidates.filter { it.startsWith(current, ignoreCase = true) }
    }

    private fun info(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        val logs = clan.pointsLogs
        val antiFarm = plugin.clanPointsAntiFarmService.recent(clan)
        val blocked = antiFarm.count { it.grantedAmount <= 0L }
        val reduced = antiFarm.count { it.grantedAmount in 1 until it.baseAmount }
        sender.reply("")
        sender.reply("&#FC7D37✦ &fОчки клана &#5EA9FD${clan.name}")
        sender.reply("&8Баланс: &#FC65DF${clan.points} &8• Транзакций: &f${logs.size}")
        sender.reply("&8Anti-farm записей: &f${antiFarm.size} &8• блок: &#FC3737$blocked &8• уменьшено: &#FFD700$reduced")
        sender.reply("&8Последняя операция: &f${logs.lastOrNull()?.let { shortTransaction(it) } ?: "нет"}")
        sender.reply("")
    }

    private fun mutate(sender: CommandSender, args: List<String>) {
        val operation = args.getOrNull(0)?.lowercase() ?: return usage(sender)
        val clan = clan(args.getOrNull(1)) ?: return usage(sender)
        val amount = args.getOrNull(2)?.toLongOrNull()?.takeIf { it >= 0L } ?: return usage(sender)
        val reason = args.drop(3).joinToString(" ").trim().ifBlank { null }
        val target = when (operation) {
            "add" -> runCatching { Math.addExact(clan.points, amount) }.getOrNull()
            "remove" -> clan.points - amount
            "set" -> amount
            else -> null
        }
        if (target == null || target < 0L) {
            sender.reply("&#FC3737✖ &fИтоговый баланс очков недопустим.")
            return
        }
        val delta = target - clan.points
        if (delta == 0L) {
            sender.reply("&#FFD700! &fБаланс уже равен &#FC65DF$target&f.")
            return
        }
        if (!pointsService.adminAdjust(clan, delta, sender.name, reason)) {
            sender.reply("&#FC3737✖ &fОперация отменена событием, проверкой баланса или ошибкой сохранения.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        sender.reply("&#5EFD7D✔ &fОчки клана &#5EA9FD${clan.name}&f: &#FC65DF${clan.points}&f.")
        audit(sender, "$operation points ${clan.id} amount=$amount result=${clan.points} reason=${reason ?: "-"}")
    }

    private fun reset(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        val reason = args.drop(1).joinToString(" ").trim()
        if (plugin.configService.points.admin.requireReasonForDestructiveActions && reason.isBlank()) {
            sender.reply("&#FC3737✖ &fДля reset укажите причину.")
            sender.reply("&#FFD700Использование: &f/clan admin points reset <clan> <reason>")
            return
        }
        if (clan.points == 0L) {
            sender.reply("&#FFD700! &fУ клана уже 0 очков.")
            return
        }
        if (!pointsService.adminAdjust(clan, -clan.points, sender.name, "RESET: ${reason.ifBlank { "manual" }}")) {
            sender.reply("&#FC3737✖ &fНе удалось сбросить очки.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        sender.reply("&#5EFD7D✔ &fОчки клана &#5EA9FD${clan.name} &fсброшены до 0.")
        audit(sender, "reset points ${clan.id} reason=${reason.ifBlank { "manual" }}")
    }

    private fun history(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        val pageSize = plugin.configService.points.admin.historyPageSize.coerceIn(1, 50)
        val logs = clan.pointsLogs.asReversed()
        val pages = maxOf(1, ceil(logs.size.toDouble() / pageSize).toInt())
        val page = (args.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, pages)
        val from = (page - 1) * pageSize
        val entries = logs.drop(from).take(pageSize)
        sender.reply("")
        sender.reply("&#FC7D37✦ &fИстория очков &#5EA9FD${clan.name} &8• &f$page/$pages")
        if (entries.isEmpty()) sender.reply("&8Операций пока нет.")
        entries.forEach { tx ->
            val sign = if (tx.type.name == "AWARD") "+" else "-"
            val id = tx.id.take(8)
            sender.reply("&8#$id &7${formatTime(tx.timestamp)} &f$sign${tx.amount} &8→ &#FC65DF${tx.balanceAfter} &8• &f${tx.source}${tx.actor?.let { " &8• &7$it" } ?: ""}")
            if (!tx.reason.isNullOrBlank()) sender.reply("  &8↳ &7${tx.reason}")
            if (!tx.relatedTransactionId.isNullOrBlank()) sender.reply("  &8↳ rollback #${tx.relatedTransactionId.take(8)}")
        }
        sender.reply("")
    }

    private fun rollback(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        val query = args.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: return usage(sender)
        val reason = args.drop(2).joinToString(" ").trim()
        if (plugin.configService.points.admin.requireReasonForDestructiveActions && reason.isBlank()) {
            sender.reply("&#FC3737✖ &fДля rollback укажите причину.")
            return
        }
        val matches = clan.pointsLogs.filter { it.id.equals(query, true) || it.id.startsWith(query, true) }
        if (matches.size != 1) {
            sender.reply(if (matches.isEmpty()) "&#FC3737✖ &fТранзакция #$query не найдена." else "&#FC3737✖ &fПрефикс #$query неоднозначен. Укажите больше символов ID.")
            return
        }
        val transaction = matches.single()
        if (!pointsService.rollback(clan, transaction, sender.name, reason.ifBlank { "manual" })) {
            sender.reply("&#FC3737✖ &fRollback невозможен: операция уже откатывалась, это rollback-запись, не хватает очков или событие отменило изменение.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        sender.reply("&#5EFD7D✔ &fТранзакция &#5EA9FD#${transaction.id.take(8)} &fоткачена. Баланс: &#FC65DF${clan.points}&f.")
        audit(sender, "rollback points ${clan.id} transaction=${transaction.id} reason=${reason.ifBlank { "manual" }}")
    }

    private fun antiFarm(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        val playerFilter = args.getOrNull(1)?.let { name ->
            val member = clan.users.firstOrNull { it.playerName.equals(name, true) }
            if (member == null) {
                sender.reply("&#FC3737✖ &fУчастник &e$name &fне найден в клане.")
                return
            }
            member.uuid
        }
        val entries = plugin.clanPointsAntiFarmService.recent(clan, playerFilter).take(15)
        sender.reply("")
        sender.reply("&#FC7D37✦ &fAnti-farm audit &#5EA9FD${clan.name}${args.getOrNull(1)?.let { " &8• &f$it" } ?: ""}")
        if (entries.isEmpty()) sender.reply("&8Записей нет.")
        entries.forEach { record ->
            val killer = Bukkit.getOfflinePlayer(record.killerUuid).name ?: record.killerUuid.toString().take(8)
            val victim = Bukkit.getOfflinePlayer(record.victimUuid).name ?: record.victimUuid.toString().take(8)
            val reason = record.reasons.joinToString(",") { it.name }.ifBlank { "OK" }
            sender.reply("&7${formatTime(record.timestamp)} &f$killer &8→ &f$victim &8• &#FC65DF${record.grantedAmount}/${record.baseAmount} &8• &7$reason")
        }
        sender.reply("")
    }

    private fun clearAntiFarm(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        val reason = args.drop(1).joinToString(" ").trim()
        if (plugin.configService.points.admin.requireReasonForDestructiveActions && reason.isBlank()) {
            sender.reply("&#FC3737✖ &fДля очистки anti-farm tracking укажите причину.")
            return
        }
        if (!plugin.clanPointsAntiFarmService.clear(clan)) {
            sender.reply("&#FC3737✖ &fНе удалось очистить anti-farm tracking.")
            return
        }
        sender.reply("&#5EFD7D✔ &fAnti-farm tracking клана &#5EA9FD${clan.name} &fочищен. Баланс очков не изменён.")
        audit(sender, "cleared anti-farm ${clan.id} reason=${reason.ifBlank { "manual" }}")
    }

    private fun help(sender: CommandSender) {
        sender.reply("")
        sender.reply("&#FC7D37✦ &fУправление клановыми очками")
        sender.reply("&#5EA9FD/clan admin points info <clan>")
        sender.reply("&#5EA9FD/clan admin points <add|remove|set> <clan> <amount> [reason]")
        sender.reply("&#5EA9FD/clan admin points reset <clan> <reason>")
        sender.reply("&#5EA9FD/clan admin points history <clan> [page]")
        sender.reply("&#5EA9FD/clan admin points rollback <clan> <transaction-id> <reason>")
        sender.reply("&#5EA9FD/clan admin points antifarm <clan> [player]")
        sender.reply("&#5EA9FD/clan admin points clear-antifarm <clan> <reason>")
        sender.reply("")
    }

    private fun usage(sender: CommandSender) {
        sender.reply("&#FFD700Использование: &f/clan admin points <help|info|add|remove|set|reset|history|rollback|antifarm|clear-antifarm> ...")
    }

    private fun clan(name: String?): Clan? = name?.let(clans::getClanByName)

    private fun clanNames(): List<String> = clans.getAllClans().map { it.name }.sortedBy { it.lowercase() }

    private fun memberNames(clanName: String): List<String> =
        clans.getClanByName(clanName)?.users?.map { it.playerName }?.sortedBy { it.lowercase() }.orEmpty()

    private fun shortTransaction(tx: ClanPointsTransaction): String {
        val sign = if (tx.type.name == "AWARD") "+" else "-"
        return "#$${tx.id.take(8)} $sign${tx.amount} (${tx.source})".replace("#$", "#")
    }

    private fun formatTime(timestamp: Long): String = TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp))

    private fun audit(sender: CommandSender, operation: String) {
        plugin.logger.info("[pnClans/Admin] ${sender.name}: $operation")
    }

    private fun CommandSender.reply(text: String) {
        sendMessage(ColorUtil.color(text))
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())
    }
}
