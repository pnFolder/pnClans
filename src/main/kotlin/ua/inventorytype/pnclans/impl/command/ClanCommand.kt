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

                clan.removeUser(targetUser.uuid)
                clanService.saveClan(clan)
                sender.sendMessage("§aВы исключили ${targetUser.playerName} из клана.")
                Bukkit.getPlayer(targetUser.uuid)?.sendMessage("§cВас исключили из клана ${clan.name}.")
            }

            "leave" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (clan.getUserRole(myUser) == ClanRole.LEADER) {
                    sender.sendMessage("§cЛидер не может покинуть клан! Используйте /clan disband для расформирования.")
                    return true
                }

                clan.removeUser(sender.uniqueId)
                clanService.saveClan(clan)
                sender.sendMessage("§cВы покинули клан ${clan.name}.")
            }

            "disband" -> {
                val clan = clanService.getClanUser(sender) ?: run {
                    sender.sendMessage(msg(sender, cfg.msgNoClan))
                    return true
                }
                val myUser = clan.users.find { it.uuid == sender.uniqueId } ?: return true
                if (clan.getUserRole(myUser) != ClanRole.LEADER) {
                    sender.sendMessage("§cТолько Лидер может распустить клан.")
                    return true
                }

                clanService.disbandClan(clan)
                sender.sendMessage(msg(sender, cfg.msgClanDisbanded, mapOf("clan" to clan.name)))
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
                val homeName = if (args.size > 1) args[1] else "main"
                val loc = clan.homes[homeName.lowercase()]
                if (loc == null) {
                    sender.sendMessage("§cТочка дома '$homeName' не найдена.")
                    return true
                }
                plugin.teleportService.teleportToHome(sender, clan, homeName, loc)
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
                val homeName = if (args.size > 1) args[1] else "main"
                clan.setHome(homeName, sender.location)
                clanService.saveClan(clan)
                sender.sendMessage("§aКлановый дом '$homeName' установлен!")
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
                val homeName = if (args.size > 1) args[1] else "main"
                if (clan.deleteHome(homeName)) {
                    clanService.saveClan(clan)
                    sender.sendMessage("§aКлановый дом '$homeName' удален.")
                } else {
                    sender.sendMessage("§cТочка дома '$homeName' не найдена.")
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

            else -> {
                sender.sendMessage("§cНеизвестная подкоманда. Используйте /clan.")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subcommands = listOf(
                "menu", "create", "invite", "accept", "deny", "kick", "leave",
                "disband", "deposit", "withdraw", "home", "sethome", "delhome", "top", "reload"
            )
            return subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "invite" -> return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "kick" -> {
                    val p = sender as? Player ?: return emptyList()
                    val clan = clanService.getClanUser(p) ?: return emptyList()
                    return clan.users.map { it.playerName }.filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "home", "delhome" -> {
                    val p = sender as? Player ?: return emptyList()
                    val clan = clanService.getClanUser(p) ?: return emptyList()
                    return clan.homes.keys.filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }
        }

        return emptyList()
    }
}
