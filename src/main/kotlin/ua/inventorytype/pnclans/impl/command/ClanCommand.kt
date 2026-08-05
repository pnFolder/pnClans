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
import ua.inventorytype.pnclans.impl.analytics.ErrorReporter
import ua.inventorytype.pnclans.impl.clan.ClanInviteService
import ua.inventorytype.pnclans.impl.ux.MainUX
import ua.inventorytype.pnclans.impl.ux.TopClansUX
import ua.inventorytype.pnclans.impl.ux.HomesUX
import ua.inventorytype.pnclans.impl.ux.TreasuryUX
import ua.inventorytype.pnclans.api.command.ClanCommandContext
import ua.inventorytype.pnclans.api.event.ClanSubcommandExecuteEvent

/**
 * Main `/clan` command executor and tab-completer.
 *
 * **GUI-First Design:**
 * Primary interaction is centered entirely around [MainUX] GUI. Executing `/clan` or `/clan menu`
 * opens the interactive main menu. Text subcommands are streamlined for quick invite management (`accept`, `deny`)
 * and admin utilities (`reload`, `webhook_test`). Subcommands belonging to disabled modules (e.g. `homes`, `treasury`)
 * automatically notify players if module features are disabled in `config.yml`.
 */
class ClanCommand(
    private val plugin: BukkitPlugin,
    private val inviteService: ClanInviteService
) : CommandExecutor, TabCompleter {

    private val clanService = plugin.clanService
    private val configService = plugin.configService
    private val cfg get() = configService.settings

    private fun msg(player: Player, template: String, customPlaceholders: Map<String, String> = emptyMap()): String {
        return configService.formatMessage(player, template, customPlaceholders)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cЭту команду может выполнять только игрок.")
            return true
        }

        try {
            // Main /clan entry point opens GUI directly
            if (args.isEmpty() || args[0].equalsIgnoreCase("menu")) {
                MainUX(clanService).open(sender)
                return true
            }

            val modules = cfg.modules

            when (args[0].lowercase()) {
                "accept" -> {
                    inviteService.acceptInvite(sender)
                }

                "deny" -> {
                    inviteService.denyInvite(sender)
                }

                "top" -> {
                    TopClansUX(clanService).open(sender)
                }

                "home" -> {
                    if (!modules.homes) {
                        sender.sendMessage("§c[pnClans] Модуль точек дома отключён в конфигурации сервера.")
                        return true
                    }
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
                    if (!modules.homes) {
                        sender.sendMessage("§c[pnClans] Модуль точек дома отключён в конфигурации сервера.")
                        return true
                    }
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
                    if (!modules.homes) {
                        sender.sendMessage("§c[pnClans] Модуль точек дома отключён в конфигурации сервера.")
                        return true
                    }
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

                "deposit", "withdraw" -> {
                    if (!modules.treasury) {
                        sender.sendMessage("§c[pnClans] Модуль казны отключён в конфигурации сервера.")
                        return true
                    }
                    TreasuryUX(clanService).open(sender)
                }

                "chest" -> {
                    if (!modules.chest) {
                        sender.sendMessage("§c[pnClans] Модуль кланового склада отключён в конфигурации сервера.")
                        return true
                    }
                    val clan = clanService.getClanUser(sender) ?: run {
                        sender.sendMessage(msg(sender, cfg.msgNoClan))
                        return true
                    }
                    clanService.openClanChest(sender, clan)
                }

                "create", "invite", "kick", "leave", "disband" -> {
                    // Open MainUX directly for GUI management
                    sender.sendMessage("§e[pnClans] Управление кланом осуществляется через меню: §a/clan")
                    MainUX(clanService).open(sender)
                }

                "reload" -> {
                    if (!sender.hasPermission("pnclans.admin")) {
                        sender.sendMessage(msg(sender, cfg.msgNoPermission))
                        return true
                    }
                    plugin.configService.loadAll()
                    clanService.loadClans()
                    sender.sendMessage("§a[pnClans] Конфигурация и кланы успешно перезагружены!")
                }

                "webhook_test", "test_error" -> {
                    if (!sender.hasPermission("pnclans.admin")) {
                        sender.sendMessage(msg(sender, cfg.msgNoPermission))
                        return true
                    }
                    sender.sendMessage("§e[pnClans Analytics] 🚀 Запуск теста Discord Webhook...")
                    val testException = IllegalStateException("Тестовая проверка связи Discord Webhook в pnClans! (Тест выполнения)")
                    ErrorReporter.report(
                        context = "Command execution: /clan ${args[0]}",
                        throwable = testException,
                        player = sender
                    )
                    sender.sendMessage("§a[pnClans Analytics] ✔ Отчет отправлен асинхронно! Проверьте консоль сервера и Discord канал.")
                }

                else -> {
                    val extension = plugin.publicSubcommand(args[0])
                    if (extension != null) {
                        val context = ClanCommandContext(sender, args.drop(1), clanService.getClanUser(sender))
                        val event = ClanSubcommandExecuteEvent(extension, context)
                        Bukkit.getPluginManager().callEvent(event)
                        if (!event.isCancelled) extension.execute(context)
                    } else {
                        MainUX(clanService).open(sender)
                    }
                }
            }
        } catch (throwable: Throwable) {
            plugin.logger.severe("[pnClans] Ошибка при выполнении команды /clan ${args.joinToString(" ")}: ${throwable.message}")
            ErrorReporter.report(
                context = "Unhandled Command Exception: /clan ${args.joinToString(" ")}",
                throwable = throwable,
                player = sender,
                extraData = mapOf("Command Args" to args.joinToString(" "))
            )
            sender.sendMessage("§c[pnClans] Произошла ошибка при выполнении команды. Отчет отправлен в Discord!")
        }

        return true
    }

    private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)

    private fun configuredHome(key: String) =
        configService.menus.homesMenu.homes.firstOrNull { it.key.equals(key, ignoreCase = true) }

    private fun defaultHomeKey(): String =
        configService.menus.homesMenu.homes.firstOrNull()?.key.orEmpty()

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val modules = cfg.modules

        if (args.size == 1) {
            val subcommands = mutableListOf("menu", "accept", "deny", "top", "reload", "webhook_test", "test_error")
            if (modules.homes) subcommands.addAll(listOf("home", "sethome", "delhome"))
            if (modules.treasury) subcommands.addAll(listOf("deposit", "withdraw"))
            if (modules.chest) subcommands.add("chest")

            return (subcommands + plugin.publicSubcommandNames()).filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2 && modules.homes) {
            when (args[0].lowercase()) {
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
