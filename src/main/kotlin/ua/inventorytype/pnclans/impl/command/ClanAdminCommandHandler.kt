package ua.inventorytype.pnclans.impl.command

import org.bukkit.command.CommandSender
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanQuestProgress
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.impl.clan.ClanStatsPeriod
import ua.inventorytype.pnclans.impl.clan.ClanImpl
import ua.inventorytype.pnclans.impl.util.ColorUtil
import java.math.BigDecimal

/** Validated administrative mutations exposed through `/clan admin`. */
internal class ClanAdminCommandHandler(private val plugin: BukkitPlugin) {
    private val clans get() = plugin.clanService
    private val pointsAdmin = ClanAdminPointsCommandHandler(plugin)
    private val shopAdmin = ClanAdminShopCommandHandler(plugin)
    private val memberAdmin = ClanAdminMemberCommandHandler(plugin)

    fun execute(sender: CommandSender, args: List<String>): Boolean {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.reply("&#FC3737✖ &fНедостаточно прав: требуется &e$ADMIN_PERMISSION&f.")
            return true
        }

        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> sendHelp(sender)
            "info" -> showClanInfo(sender, args.getOrNull(1))
            "stats" -> showMemberStats(sender, args.drop(1))
            "mmr" -> mutateMmr(sender, args.drop(1))
            "bank", "treasury" -> mutateBank(sender, args.drop(1))
            "points" -> pointsAdmin.execute(sender, args.drop(1))
            "shop" -> shopAdmin.execute(sender, args.drop(1))
            "level" -> mutateLevel(sender, args.drop(1))
            "member" -> memberAdmin.execute(sender, args.drop(1))
            "quest" -> mutateQuest(sender, args.drop(1))
            "battle" -> mutateBattle(sender, args.drop(1))
            "save" -> {
                if (clans.saveAll()) {
                    sender.reply("&#5EFD7D✔ &fВсе кланы и активные игровые данные сохранены.")
                    audit(sender, "saved all clans")
                } else {
                    sender.reply("&#FC3737✖ &fНе все данные удалось сохранить. Проверьте журнал сервера.")
                }
            }
            "reload" -> reload(sender)
            else -> sender.reply("&#FC3737✖ &fНеизвестная админская операция. Используйте &e/clan admin help&f.")
        }
        return true
    }

    fun complete(args: List<String>): List<String> {
        if (args.isEmpty()) return ACTIONS
        if (args.firstOrNull().equals("points", true)) return pointsAdmin.complete(args.drop(1))
        if (args.firstOrNull().equals("shop", true)) return shopAdmin.complete(args.drop(1))
        if (args.firstOrNull().equals("member", true)) return memberAdmin.complete(args.drop(1))

        val current = args.last()
        val suggestions = when (args.size) {
            1 -> ACTIONS
            2 -> when (args[0].lowercase()) {
                "mmr", "bank", "treasury" -> VALUE_OPERATIONS
                "level" -> listOf("set")
                "quest" -> listOf("reset")
                "battle" -> listOf("stop")
                "info" -> clanNames()
                "stats" -> memberNames()
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "mmr", "bank", "treasury", "level", "quest", "battle" -> clanNames()
                "stats" -> ClanStatsPeriod.entries.map { it.name.lowercase() }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "quest" -> plugin.configService.quests.quests.keys.toList()
                "battle" -> clanNames()
                else -> emptyList()
            }
            else -> emptyList()
        }
        return suggestions.filter { it.startsWith(current, ignoreCase = true) }
    }

    private fun showClanInfo(sender: CommandSender, clanName: String?) {
        val clan = clanName?.let(clans::getClanByName) ?: return usage(sender, "/clan admin info <clan>")
        val battle = plugin.clanBattleService.battleForClan(clan)
        sender.reply("")
        sender.reply("&#FC7D37✦ &fКлан &8• &#5EA9FD${clan.name}")
        sender.reply("&8ID: &f${clan.id} &8• Уровень: &#5EFD7D${clan.level} &8• MMR: &#FFD700${clan.mmr}")
        sender.reply("&8Казна: &#FFD700${number(clan.bankBalance)} &8• Очки: &#FC65DF${clan.points}")
        sender.reply("&8Состав: &#5EA9FD${clan.onlineCount}/${clan.users.size} &8• PvP: &#5EFD7D${clan.kills} &7/ &#FC3737${clan.deaths}")
        sender.reply("&8Битвы: &#5EFD7D${clan.battleWins} побед &7/ &#FC3737${clan.battleLosses} поражений")
        sender.reply("&8Активный бой: ${if (battle == null) "&#5EFD7Dнет" else "&#FC3737да &8(${battle.challengerScore}:${battle.defenderScore})"}")
        sender.reply("")
    }

    private fun showMemberStats(sender: CommandSender, args: List<String>) {
        val playerName = args.getOrNull(0) ?: return usage(sender, "/clan admin stats <player> [day|week|month|all]")
        val (clan, member) = clans.findMemberByName(playerName) ?: run {
            sender.reply("&#FC3737✖ &fУчастник &e$playerName &fне найден ни в одном клане.")
            return
        }
        val period = args.getOrNull(1)?.let(ClanStatsPeriod::fromInput)
        if (args.size > 1 && period == null) return usage(sender, "/clan admin stats <player> [day|week|month|all]")
        ClanStatsPresenter.send(
            sender,
            clan,
            member,
            plugin.configService.getRoleDisplayName(clan.getUserRole(member)),
            clans,
            period
        )
    }

    private fun mutateMmr(sender: CommandSender, args: List<String>) {
        val parsed = parseLongMutation(sender, args, "/clan admin mmr <add|remove|set> <clan> <amount>") ?: return
        val previousMmr = parsed.clan.mmr
        val target = when (parsed.operation) {
            "add" -> runCatching { Math.addExact(parsed.clan.mmr.toLong(), parsed.amount) }.getOrNull()
            "remove" -> parsed.clan.mmr.toLong() - parsed.amount
            "set" -> parsed.amount
            else -> return usage(sender, "/clan admin mmr <add|remove|set> <clan> <amount>")
        }
        if (target == null || target !in 0..Int.MAX_VALUE.toLong()) {
            sender.reply("&#FC3737✖ &fИтоговый MMR должен быть от &e0 &fдо &e${Int.MAX_VALUE}&f.")
            return
        }
        parsed.clan.mmr = target.toInt()
        if (!saveAndNotify(sender, parsed.clan) { parsed.clan.mmr = previousMmr }) return
        sender.reply("&#5EFD7D✔ &fMMR клана &#5EA9FD${parsed.clan.name} &fустановлен на &#FFD700$target&f.")
        audit(sender, "${parsed.operation} MMR ${parsed.clan.id} amount=${parsed.amount} result=$target")
    }

    private fun mutateBank(sender: CommandSender, args: List<String>) {
        val parsed = parseDoubleMutation(sender, args, "/clan admin bank <add|remove|set> <clan> <amount>") ?: return
        val previousBalance = parsed.clan.bankBalance
        val previousLogs = parsed.clan.treasuryLogs
        val target = when (parsed.operation) {
            "add" -> parsed.clan.bankBalance + parsed.amount
            "remove" -> parsed.clan.bankBalance - parsed.amount
            "set" -> parsed.amount
            else -> return usage(sender, "/clan admin bank <add|remove|set> <clan> <amount>")
        }
        if (!target.isFinite() || target < 0.0) {
            sender.reply("&#FC3737✖ &fИтоговый баланс казны не может быть отрицательным.")
            return
        }
        val delta = target - parsed.clan.bankBalance
        when {
            delta > 0.0 -> parsed.clan.depositBank(delta)
            delta < 0.0 && !parsed.clan.withdrawBank(-delta) -> {
                sender.reply("&#FC3737✖ &fНе удалось списать средства из казны.")
                return
            }
        }
        if (delta != 0.0) {
            parsed.clan.addTreasuryLog(
                TreasuryTransaction(
                    if (delta > 0.0) TreasuryTransactionType.DEPOSIT else TreasuryTransactionType.WITHDRAW,
                    "ADMIN:${sender.name}",
                    kotlin.math.abs(delta),
                    System.currentTimeMillis()
                )
            )
        }
        if (!saveAndNotify(sender, parsed.clan) {
                parsed.clan.bankBalance = previousBalance
                (parsed.clan as? ClanImpl)?.restoreTreasuryLogs(previousLogs)
            }
        ) return
        sender.reply("&#5EFD7D✔ &fКазна клана &#5EA9FD${parsed.clan.name}&f: &#FFD700${number(parsed.clan.bankBalance)}&f.")
        audit(sender, "${parsed.operation} bank ${parsed.clan.id} amount=${parsed.amount} result=${parsed.clan.bankBalance}")
    }

    private fun mutateLevel(sender: CommandSender, args: List<String>) {
        if (!args.getOrNull(0).equals("set", true)) return usage(sender, "/clan admin level set <clan> <1..5>")
        val clan = args.getOrNull(1)?.let(clans::getClanByName) ?: return usage(sender, "/clan admin level set <clan> <1..5>")
        val level = args.getOrNull(2)?.toIntOrNull()
        if (level == null || level !in 1..5) return usage(sender, "/clan admin level set <clan> <1..5>")
        val previousLevel = clan.level
        clan.level = level
        if (!saveAndNotify(sender, clan) { clan.level = previousLevel }) return
        sender.reply("&#5EFD7D✔ &fУровень клана &#5EA9FD${clan.name} &fустановлен на &e$level&f.")
        audit(sender, "set level ${clan.id} result=$level")
    }

    private fun mutateQuest(sender: CommandSender, args: List<String>) {
        if (!args.getOrNull(0).equals("reset", true)) return usage(sender, "/clan admin quest reset <clan> <quest-id>")
        val clan = args.getOrNull(1)?.let(clans::getClanByName) ?: return usage(sender, "/clan admin quest reset <clan> <quest-id>")
        val questId = args.getOrNull(2)?.takeIf { plugin.configService.quests.quests.containsKey(it) }
            ?: return usage(sender, "/clan admin quest reset <clan> <quest-id>")
        val previousProgress = clan.questProgress[questId] ?: ClanQuestProgress()
        clan.setQuestProgress(questId, ClanQuestProgress())
        if (!saveAndNotify(sender, clan) { clan.setQuestProgress(questId, previousProgress) }) return
        sender.reply("&#5EFD7D✔ &fПрогресс квеста &#5EA9FD$questId &fдля клана &e${clan.name} &fполностью сброшен.")
        audit(sender, "reset quest $questId for ${clan.id}")
    }

    private fun mutateBattle(sender: CommandSender, args: List<String>) {
        if (!args.getOrNull(0).equals("stop", true)) return usage(sender, "/clan admin battle stop <clan> [winner-clan]")
        val clan = args.getOrNull(1)?.let(clans::getClanByName) ?: return usage(sender, "/clan admin battle stop <clan> [winner-clan]")
        val winner = args.getOrNull(2)?.let(clans::getClanByName)
        if (args.size > 2 && winner == null) return usage(sender, "/clan admin battle stop <clan> [winner-clan]")
        if (!plugin.clanBattleService.stopByAdmin(clan, winner)) {
            sender.reply("&#FC3737✖ &fАктивный бой клана не найден или победитель не участвует в нём.")
            return
        }
        sender.reply("&#5EFD7D✔ &fБой клана &#5EA9FD${clan.name} &fзавершён администратором.")
        audit(sender, "stopped battle for ${clan.id} winner=${winner?.id ?: "score"}")
    }

    private fun reload(sender: CommandSender) {
        if (!plugin.clanBattleService.prepareReload()) {
            sender.reply("&#FC3737✖ &fНельзя перезагрузить pnClans во время активной битвы. Завершите её через &e/clan admin battle stop&f.")
            return
        }
        if (!clans.saveAll()) {
            sender.reply("&#FC3737✖ &fПерезагрузка отменена: не все данные удалось сохранить.")
            return
        }
        val activeStorageType = plugin.configService.settings.storageType
        plugin.guiListener.forceCloseAll()
        plugin.reloadConfigurations()
        val requestedStorageType = plugin.configService.settings.storageType
        val storageChangePending = !requestedStorageType.equals(activeStorageType, ignoreCase = true)
        if (storageChangePending) plugin.configService.settings.storageType = activeStorageType
        clans.loadClans()
        plugin.clanBattleService.completeReload()
        sender.reply("&#5EFD7D✔ &fКонфигурации и данные pnClans перезагружены.")
        if (storageChangePending) {
            sender.reply("&#FFD700! &fСмена хранилища на &e$requestedStorageType &fприменится только после полного перезапуска сервера.")
        }
        audit(sender, "reloaded plugin data")
    }

    private fun parseLongMutation(sender: CommandSender, args: List<String>, usageText: String): LongMutation? {
        val operation = args.getOrNull(0)?.lowercase()?.takeIf { it in VALUE_OPERATIONS } ?: return usage(sender, usageText).let { null }
        val clan = args.getOrNull(1)?.let(clans::getClanByName) ?: return usage(sender, usageText).let { null }
        val amount = args.getOrNull(2)?.toLongOrNull()?.takeIf { it >= 0L } ?: return usage(sender, usageText).let { null }
        return LongMutation(operation, clan, amount)
    }

    private fun parseDoubleMutation(sender: CommandSender, args: List<String>, usageText: String): DoubleMutation? {
        val operation = args.getOrNull(0)?.lowercase()?.takeIf { it in VALUE_OPERATIONS } ?: return usage(sender, usageText).let { null }
        val clan = args.getOrNull(1)?.let(clans::getClanByName) ?: return usage(sender, usageText).let { null }
        val amount = args.getOrNull(2)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return usage(sender, usageText).let { null }
        return DoubleMutation(operation, clan, amount)
    }

    private fun saveAndNotify(sender: CommandSender, clan: Clan, rollback: () -> Unit = {}): Boolean {
        if (!clans.saveClan(clan)) {
            rollback()
            sender.reply("&#FC3737✖ &fИзменение отменено: сохранить клан не удалось.")
            return false
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.reply("")
        sender.reply("&#FC7D37✦ &fАдмин-команды pnClans")
        sender.reply("&#5EA9FD/clan admin info <clan> &8• &fполная сводка клана")
        sender.reply("&#5EA9FD/clan admin stats <player> [period] &8• &fстатистика участника")
        sender.reply("&#5EA9FD/clan admin mmr <add|remove|set> <clan> <amount>")
        sender.reply("&#5EA9FD/clan admin bank <add|remove|set> <clan> <amount>")
        sender.reply("&#5EA9FD/clan admin points <...> &8• &fистория, reset, rollback и anti-farm")
        sender.reply("&#5EA9FD/clan admin shop <...> &8• &fтовары, цены, категории, addhand")
        sender.reply("&#5EA9FD/clan admin member <...> &8• &fсостав, роли и передача лидерства")
        sender.reply("&#5EA9FD/clan admin level set <clan> <1..5>")
        sender.reply("&#5EA9FD/clan admin quest reset <clan> <quest-id>")
        sender.reply("&#5EA9FD/clan admin battle stop <clan> [winner-clan]")
        sender.reply("&#5EA9FD/clan admin save &8• &#5EA9FD/clan admin reload")
        sender.reply("")
    }

    private fun usage(sender: CommandSender, command: String) {
        sender.reply("&#FFD700Использование: &f$command")
    }

    private fun clanNames(): List<String> = clans.getAllClans().map { it.name }.sortedBy { it.lowercase() }

    private fun memberNames(): List<String> = clans.getAllClans().flatMap { it.users }.map { it.playerName }.distinct().sortedBy { it.lowercase() }

    private fun number(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun audit(sender: CommandSender, operation: String) {
        plugin.logger.info("[pnClans/Admin] ${sender.name}: $operation")
    }

    private fun CommandSender.reply(text: String) {
        sendMessage(ColorUtil.color(text))
    }

    private data class LongMutation(val operation: String, val clan: Clan, val amount: Long)
    private data class DoubleMutation(val operation: String, val clan: Clan, val amount: Double)

    private companion object {
        const val ADMIN_PERMISSION = "pnclans.admin"
        val ACTIONS = listOf("help", "info", "stats", "mmr", "bank", "points", "shop", "member", "level", "quest", "battle", "save", "reload")
        val VALUE_OPERATIONS = listOf("add", "remove", "set")
    }
}
