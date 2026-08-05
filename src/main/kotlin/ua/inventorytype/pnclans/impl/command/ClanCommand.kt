package ua.inventorytype.pnclans.impl.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.impl.clan.ClanInviteService
import ua.inventorytype.pnclans.impl.ux.MainUX
import ua.inventorytype.pnclans.impl.ux.TopClansUX
import ua.inventorytype.pnclans.api.command.ClanCommandContext
import ua.inventorytype.pnclans.api.event.ClanSubcommandExecuteEvent

class ClanCommand(
    private val plugin: BukkitPlugin,
    private val inviteService: ClanInviteService
) : CommandExecutor, TabCompleter {

    private val clanService = plugin.clanService
    private val configService = plugin.configService
    private val cfg = configService.settings

    private fun msg(player: Player, template: String, customPlaceholders: Map<String, String> = emptyMap()): String {
        return configService.formatMessage(player, template, customPlaceholders)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cЭту команду может выполнять только игрок.")
            return true
        }

        if (args.isEmpty()) {
            val clan = clanService.getClanUser(sender)
            if (clan == null) {
                sender.sendMessage("§6=== pnClans Команды ===")
                sender.sendMessage("§e/clan create <название> §7- Создать клан")
                sender.sendMessage("§e/clan accept §7- Принять приглашение")
                sender.sendMessage("§e/clan deny §7- Отклонить приглашение")
                sender.sendMessage("§e/clan top §7- Топ кланов")
            } else {
                MainUX(clanService).open(sender)
            }
            return true
        }

        when (args[0].lowercase()) {
            "menu" -> {
                val clan = clanService.getClanUser(sender)
                if (clan == null) {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                } else {
                    MainUX(clanService).open(sender)
                }
            }

            "create" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cИспользование: /clan create <название>")
                    return true
                }
                clanService.createClan(args[1], sender)
            }

            "invite" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cИспользование: /clan invite <ник>")
                    return true
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("§cИгрок не найден или оффлайн.")
                    return true
                }
                inviteService.sendInvite(sender, target)
            }

            "accept" -> {
                inviteService.acceptInvite(sender)
            }

            "deny" -> {
                inviteService.denyInvite(sender)
            }

            "kick" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cИспользование: /clan kick <ник>")
                    return true
                }
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (!clan.hasPermission(myUser, ClanPerms.Members.KICK)) {
                    sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    return true
                }

                val targetUser = clan.users.find { it.playerName.equals(args[1], ignoreCase = true) }
                if (targetUser == null) {
                    sender.sendMessage("§cУчастник ${args[1]} не найден в вашем клане.")
                    return true
                }

                val myRole = clan.getUserRole(myUser)
                val targetRole = clan.getUserRole(targetUser)
                if (targetUser.uuid == sender.uniqueId || myRole.weight <= targetRole.weight) {
                    configService.send(sender, configService.messages.members.cannotManageHigherRank)
                    return true
                }

                clan.removeUser(targetUser.uuid)
                clanService.saveClan(clan)
                clanService.notifyClanUpdated(targetUser.uuid)
                configService.send(sender, configService.messages.members.kicked, mapOf("player" to targetUser.playerName))
                Bukkit.getPlayer(targetUser.uuid)?.let { target ->
                    configService.send(target, configService.messages.members.kickedTarget, mapOf("clan" to clan.name))
                }
            }

            "leave" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (clan.getUserRole(myUser) == ClanRole.LEADER) {
                    configService.send(sender, configService.messages.clan.leaderCannotLeave)
                    return true
                }

                clan.removeUser(sender.uniqueId)
                clanService.saveClan(clan)
                clanService.notifyClanUpdated(sender.uniqueId)
                configService.send(sender, configService.messages.clan.left, mapOf("clan" to clan.name))
            }

            "disband" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (clan.getUserRole(myUser) != ClanRole.LEADER) {
                    configService.send(sender, configService.messages.general.noPermission)
                    return true
                }

                clanService.disbandClan(clan)
            }

            "deposit" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cИспользование: /clan deposit <сумма>")
                    return true
                }
                val amount = args[1].toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage("§cУкажите корректную сумму.")
                    return true
                }

                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }

                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (!clan.hasPermission(myUser, ClanPerms.Bank.DEPOSIT)) {
                    configService.send(sender, configService.messages.treasury.noPermissionDeposit)
                    return true
                }

                if (!plugin.economyService.withdraw(sender, amount)) {
                    sender.sendMessage("§cУ вас недостаточно средств.")
                    return true
                }

                clan.depositBank(amount)
                clanService.saveClan(clan)
                sender.sendMessage(msg(sender, cfg.msgDepositSuccess, mapOf("clan" to clan.name, "amount" to amount.toString())))
            }

            "withdraw" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cИспользование: /clan withdraw <сумма>")
                    return true
                }
                val amount = args[1].toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage("§cУкажите корректную сумму.")
                    return true
                }

                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }

                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (!clan.hasPermission(myUser, ClanPerms.Bank.WITHDRAW)) {
                    sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    return true
                }

                if (!clan.withdrawBank(amount)) {
                    sender.sendMessage("§cВ казне клана недостаточно средств.")
                    return true
                }

                plugin.economyService.depositPlayer(sender, amount)
                clanService.saveClan(clan)
                sender.sendMessage(msg(sender, cfg.msgWithdrawSuccess, mapOf("clan" to clan.name, "amount" to amount.toString())))
            }

            "home" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val homeEntry = configuredHome(if (args.size > 1) args[1] else defaultHomeKey()) ?: run {
                    configService.send(sender, configService.messages.homes.unknownHome, mapOf("home" to args.getOrElse(1) { defaultHomeKey() }))
                    return true
                }

                val loc = clan.homes[homeEntry.key]
                if (loc == null) {
                    configService.send(sender, configService.messages.homes.notSet, mapOf("home" to homeEntry.label))
                    return true
                }
                plugin.teleportService.teleportToHome(sender, clan, homeEntry.label, loc)
            }

            "sethome" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (!clan.hasPermission(myUser, ClanPerms.Homes.SET)) {
                    sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    return true
                }
                val homeEntry = configuredHome(if (args.size > 1) args[1] else defaultHomeKey()) ?: run {
                    configService.send(sender, configService.messages.homes.unknownHome, mapOf("home" to args.getOrElse(1) { defaultHomeKey() }))
                    return true
                }

                clan.setHome(homeEntry.key, sender.location)
                clanService.saveClan(clan)
                configService.send(sender, configService.messages.homes.set, mapOf("home" to homeEntry.label))
            }

            "delhome" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (!clan.hasPermission(myUser, ClanPerms.Homes.DELETE_ANY)) {
                    sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    return true
                }
                val homeEntry = configuredHome(if (args.size > 1) args[1] else defaultHomeKey()) ?: run {
                    configService.send(sender, configService.messages.homes.unknownHome, mapOf("home" to args.getOrElse(1) { defaultHomeKey() }))
                    return true
                }
                if (clan.deleteHome(homeEntry.key)) {
                    clanService.saveClan(clan)
                    configService.send(sender, configService.messages.homes.deleted, mapOf("home" to homeEntry.label))
                } else {
                    configService.send(sender, configService.messages.homes.notSet, mapOf("home" to homeEntry.label))
                }
            }

            "top" -> {
                TopClansUX(clanService).open(sender)
            }

            "reload" -> {
                if (!sender.hasPermission("pnclans.admin")) {
                    sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    return true
                }
                plugin.configService.loadAll()
                clanService.loadClans()
                sender.sendMessage("§aКонфигурация и кланы успешно перезагружены!")
            }

            "test_error" -> {
                if (!sender.hasPermission("pnclans.admin")) {
                    sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    return true
                }
                sender.sendMessage("§e[pnClans Analytics] Имитация ошибки... Отправка отчета в Discord!")
                throw IllegalStateException("Тестовая ошибка аналитики Discord Webhook в pnClans! (Симуляция)")
            }

            else -> {
                val extension = plugin.publicSubcommand(args[0])
                if (extension != null) {
                    val context = ClanCommandContext(sender, args.drop(1), clanService.getClanUser(sender))
                    val event = ClanSubcommandExecuteEvent(extension, context)
                    Bukkit.getPluginManager().callEvent(event)
                    if (!event.isCancelled) extension.execute(context)
                } else {
                    sender.sendMessage("§cНеизвестная подкоманда. Используйте /clan.")
                }
            }
        }

        return true
    }

    private fun configuredHome(key: String) =
        configService.menus.homesMenu.homes.firstOrNull { it.key.equals(key, ignoreCase = true) }

    private fun defaultHomeKey(): String =
        configService.menus.homesMenu.homes.firstOrNull()?.key.orEmpty()

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subcommands = listOf(
                "menu", "create", "invite", "accept", "deny", "kick", "leave",
                "disband", "deposit", "withdraw", "home", "sethome", "delhome", "top", "reload", "test_error"
            )
            return (subcommands + plugin.publicSubcommandNames()).filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "invite" -> return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "kick" -> {
                    val p = sender as? Player ?: return emptyList()
                    val clan = clanService.getClanUser(p) ?: return emptyList()
                    return clan.users.map { it.playerName }.filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "home", "sethome", "delhome" -> {
                    val p = sender as? Player ?: return emptyList()
                    val clan = clanService.getClanUser(p) ?: return emptyList()
                    return configService.menus.homesMenu.homes
                        .map { it.key }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }
        }

        return emptyList()
    }
}
