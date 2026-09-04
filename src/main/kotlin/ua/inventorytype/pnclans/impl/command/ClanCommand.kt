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
import ua.inventorytype.pnclans.impl.ux.ClanBattlesUX
import ua.inventorytype.pnclans.impl.clan.ClanBattleOperation
import ua.inventorytype.pnclans.impl.clan.ClanBattleRejection
import ua.inventorytype.pnclans.impl.clan.ClanStatsPeriod
import ua.inventorytype.pnclans.api.command.ClanCommandContext
import ua.inventorytype.pnclans.api.event.ClanSubcommandExecuteEvent

/** Main `/clan` command executor and tab-completer. */
class ClanCommand(
    private val plugin: BukkitPlugin,
    private val inviteService: ClanInviteService
) : CommandExecutor, TabCompleter {
    private val clanService = plugin.clanService
    private val configService = plugin.configService
    private val adminHandler = ClanAdminCommandHandler(plugin)
    private val cfg get() = configService.settings

    private fun msg(player: Player, template: String, customPlaceholders: Map<String, String> = emptyMap()): String =
        configService.formatMessage(player, template, customPlaceholders)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        try {
            if (args.firstOrNull()?.equals("admin", ignoreCase = true) == true) {
                return adminHandler.execute(sender, args.drop(1))
            }
            if (sender !is Player) {
                sender.sendMessage("§cИгровые команды доступны только игроку. Для управления используйте /clan admin help.")
                return true
            }
            if (!sender.hasPermission("pnclans.use")) {
                sender.sendMessage(msg(sender, cfg.msgNoPermission))
                return true
            }

            if (args.isEmpty() || args[0].equals("menu", ignoreCase = true)) {
                MainUX(clanService).open(sender)
                return true
            }

            val modules = cfg.modules
            when (args[0].lowercase()) {
                "accept" -> inviteService.acceptInvite(sender, args.getOrNull(1))
                "deny" -> inviteService.denyInvite(sender, args.getOrNull(1))
                "top" -> TopClansUX(clanService).open(sender)
                "stats", "stat", "profile" -> showStats(sender, args.drop(1))

                "home" -> {
                    if (!modules.homes) {
                        configService.send(sender, configService.messages.homes.moduleDisabled)
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
                    val loc = clan.homes[homeEntry.key.lowercase()]
                    if (loc == null) {
                        configService.send(sender, configService.messages.homes.notSet, mapOf("home" to homeEntry.label))
                        return true
                    }
                    plugin.teleportService.teleportToHome(sender, clan, homeEntry.label, loc)
                }

                "sethome" -> {
                    if (!modules.homes) {
                        configService.send(sender, configService.messages.homes.moduleDisabled)
                        return true
                    }
                    val clan = clanService.getClanUser(sender) ?: run {
                        sender.sendMessage(msg(sender, cfg.msgNoClan))
                        return true
                    }
                    val myUser = clan.getMember(sender.uniqueId) ?: return true
                    if (!clan.hasPermission(myUser, ClanPerms.Homes.SET)) {
                        sender.sendMessage(msg(sender, cfg.msgNoPermission))
                        return true
                    }
                    val homeEntry = configuredHome(if (args.size > 1) args[1] else defaultHomeKey()) ?: run {
                        configService.send(sender, configService.messages.homes.unknownHome, mapOf("home" to args.getOrElse(1) { defaultHomeKey() }))
                        return true
                    }
                    if (clanService.setClanHome(clan, sender, homeEntry.key, sender.location).isSuccess) {
                        configService.send(sender, configService.messages.homes.set, mapOf("home" to homeEntry.label))
                    }
                }

                "delhome" -> {
                    if (!modules.homes) {
                        configService.send(sender, configService.messages.homes.moduleDisabled)
                        return true
                    }
                    val clan = clanService.getClanUser(sender) ?: run {
                        sender.sendMessage(msg(sender, cfg.msgNoClan))
                        return true
                    }
                    val myUser = clan.getMember(sender.uniqueId) ?: return true
                    if (!clan.hasPermission(myUser, ClanPerms.Homes.DELETE_ANY)) {
                        sender.sendMessage(msg(sender, cfg.msgNoPermission))
                        return true
                    }
                    val homeEntry = configuredHome(if (args.size > 1) args[1] else defaultHomeKey()) ?: run {
                        configService.send(sender, configService.messages.homes.unknownHome, mapOf("home" to args.getOrElse(1) { defaultHomeKey() }))
                        return true
                    }
                    if (clanService.deleteClanHome(clan, sender, homeEntry.key).isSuccess) {
                        configService.send(sender, configService.messages.homes.deleted, mapOf("home" to homeEntry.label))
                    } else {
                        configService.send(sender, configService.messages.homes.notSet, mapOf("home" to homeEntry.label))
                    }
                }

                "deposit", "withdraw" -> {
                    if (!modules.treasury) {
                        configService.send(sender, configService.messages.treasury.moduleDisabled)
                        return true
                    }
                    TreasuryUX(clanService).open(sender)
                }

                "battle", "battles" -> {
                    if (!configService.battles.enabled) {
                        configService.send(sender, configService.messages.battles.disabled)
                        return true
                    }
                    when (args.getOrNull(1)?.lowercase()) {
                        "accept", "decline" -> {
                            val challengeId = args.getOrNull(2)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                            if (challengeId == null) {
                                configService.send(sender, configService.messages.battles.challengeNotFound)
                            } else {
                                val result = if (args[1].equals("accept", true)) plugin.clanBattleService.acceptChallenge(sender, challengeId)
                                else plugin.clanBattleService.declineChallenge(sender, challengeId)
                                sendBattleResult(sender, result)
                                if (result is ClanBattleOperation.Success) ClanBattlesUX(clanService).open(sender)
                            }
                        }
                        "roster" -> {
                            sendBattleResult(sender, plugin.clanBattleService.toggleLobbyParticipation(sender))
                            ClanBattlesUX(clanService).open(sender)
                        }
                        "ready" -> {
                            sendBattleResult(sender, plugin.clanBattleService.toggleLobbyReady(sender))
                            ClanBattlesUX(clanService).open(sender)
                        }
                        null -> ClanBattlesUX(clanService).open(sender)
                        else -> {
                            val target = clanService.getClanByName(args.drop(1).joinToString(" "))
                            if (target == null) ClanBattlesUX(clanService).open(sender)
                            else sendBattleResult(sender, plugin.clanBattleService.sendChallenge(sender, target))
                        }
                    }
                }

                "chest" -> {
                    if (!modules.chest) {
                        configService.send(sender, configService.messages.chest.moduleDisabled)
                        return true
                    }
                    val clan = clanService.getClanUser(sender) ?: run {
                        sender.sendMessage(msg(sender, cfg.msgNoClan))
                        return true
                    }
                    when (clanService.openClanChest(sender, clan)) {
                        ua.inventorytype.pnclans.api.operation.ClanOperationResult.Success -> Unit
                        is ua.inventorytype.pnclans.api.operation.ClanOperationResult.Rejected -> sender.sendMessage(msg(sender, cfg.msgNoPermission))
                    }
                }

                "create" -> {
                    if (args.size >= 2) {
                        clanService.createClan(args.drop(1).joinToString(" "), sender)?.let { MainUX(clanService).open(sender) }
                    } else MainUX(clanService).open(sender)
                }

                "disband" -> {
                    val clan = clanService.getClanUser(sender) ?: run {
                        sender.sendMessage(msg(sender, cfg.msgNoClan))
                        return true
                    }
                    val member = clan.getMember(sender.uniqueId) ?: return true
                    if (clan.getUserRole(member) != ClanRole.LEADER) {
                        sender.sendMessage(msg(sender, cfg.msgNoPermission))
                        return true
                    }
                    if (!args.getOrNull(1).equals("confirm", ignoreCase = true)) {
                        sender.sendMessage("§c[pnClans] Для подтверждения роспуска клана используйте §e/clan disband confirm§c.")
                        return true
                    }
                    val errorMsg = clanService.disbandClan(
                        clan,
                        sender,
                        endActiveBattle = plugin.clanBattleService.hasActiveBattle(clan)
                    )
                    if (errorMsg != null) sender.sendMessage(errorMsg)
                }

                "invite", "kick", "leave" -> MainUX(clanService).open(sender)
                "reload" -> adminHandler.execute(sender, listOf("reload"))

                else -> {
                    val extension = plugin.publicSubcommand(args[0])
                    if (extension != null) {
                        val context = ClanCommandContext(sender, args.drop(1), clanService.getClanUser(sender))
                        val event = ClanSubcommandExecuteEvent(extension, context)
                        Bukkit.getPluginManager().callEvent(event)
                        if (!event.isCancelled) extension.execute(context)
                    } else MainUX(clanService).open(sender)
                }
            }
        } catch (throwable: Throwable) {
            plugin.logger.severe("[pnClans] Ошибка при выполнении команды /clan ${args.joinToString(" ")}: ${throwable.message}")
            ErrorReporter.report(
                context = "Unhandled Command Exception: /clan ${args.joinToString(" ")}",
                throwable = throwable,
                player = sender as? Player,
                extraData = mapOf("Command Args" to args.joinToString(" "))
            )
            (sender as? Player)?.let { configService.send(it, configService.messages.general.internalError) }
        }
        return true
    }

    private fun configuredHome(key: String) = configService.menus.homesMenu.homes.firstOrNull { it.key.equals(key, ignoreCase = true) }
    private fun defaultHomeKey(): String = configService.menus.homesMenu.homes.firstOrNull()?.key.orEmpty()

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.firstOrNull()?.equals("admin", ignoreCase = true) == true) {
            if (!sender.hasPermission("pnclans.admin")) return emptyList()
            return adminHandler.complete(args.drop(1))
        }
        val modules = cfg.modules
        if (args.size == 1) {
            val subcommands = mutableListOf("menu", "accept", "deny", "top", "stats", "disband")
            if (sender.hasPermission("pnclans.admin")) subcommands.addAll(listOf("admin", "reload"))
            if (configService.battles.enabled) subcommands.add("battle")
            if (modules.homes) subcommands.addAll(listOf("home", "sethome", "delhome"))
            if (modules.treasury) subcommands.addAll(listOf("deposit", "withdraw"))
            if (modules.chest) subcommands.add("chest")
            return (subcommands + plugin.publicSubcommandNames()).filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("disband", true)) {
            val player = sender as? Player ?: return emptyList()
            val clan = clanService.getClanUser(player) ?: return emptyList()
            val member = clan.getMember(player.uniqueId) ?: return emptyList()
            if (clan.getUserRole(member) != ClanRole.LEADER) return emptyList()
            return listOf("confirm").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 2 && modules.homes && args[0].lowercase() in setOf("home", "sethome", "delhome")) {
            val p = sender as? Player ?: return emptyList()
            if (clanService.getClanUser(p) == null) return emptyList()
            return configService.menus.homesMenu.homes.map { it.key }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("battle", true) && configService.battles.enabled) {
            return listOf("accept", "decline", "roster", "ready").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("stats", true)) {
            val player = sender as? Player ?: return emptyList()
            val clan = clanService.getClanUser(player) ?: return emptyList()
            return (clan.users.map { it.playerName } + ClanStatsPeriod.entries.map { it.name.lowercase() }).filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3 && args[0].equals("stats", true)) {
            return ClanStatsPeriod.entries.map { it.name.lowercase() }.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        if (args.size == 3 && args[0].equals("battle", true) && (args[1].equals("accept", true) || args[1].equals("decline", true))) {
            val player = sender as? Player ?: return emptyList()
            val clan = clanService.getClanUser(player) ?: return emptyList()
            return plugin.clanBattleService.incomingChallenges(clan).map { it.id.toString() }.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        val extension = args.firstOrNull()?.let(plugin::publicSubcommand)
        if (extension != null && args.size >= 2) {
            val player = sender as? Player
            val extensionArgs = args.drop(1)
            val context = ClanCommandContext(sender, extensionArgs, player?.let(clanService::getClanUser))
            return extension.tabComplete(context).filter { it.startsWith(extensionArgs.last(), ignoreCase = true) }
        }
        return emptyList()
    }

    private fun showStats(player: Player, args: List<String>) {
        val clan = clanService.getClanUser(player) ?: run {
            player.sendMessage(msg(player, cfg.msgNoClan))
            return
        }
        val own = clan.getMember(player.uniqueId) ?: return
        val first = args.getOrNull(0)
        val ownPeriod = ClanStatsPeriod.fromInput(first)
        if (first == null || ownPeriod != null) {
            ClanStatsPresenter.send(player, clan, own, configService.getRoleDisplayName(clan.getUserRole(own)), clanService, ownPeriod)
            return
        }
        val target = clan.users.firstOrNull { it.playerName.equals(first, ignoreCase = true) }
        if (target == null) {
            configService.send(player, configService.messages.general.statsPlayerNotInClan, mapOf("player" to first))
            return
        }
        val period = args.getOrNull(1)?.let(ClanStatsPeriod::fromInput)
        if (args.size > 1 && period == null) {
            configService.send(player, configService.messages.general.statsUsage)
            return
        }
        ClanStatsPresenter.send(player, clan, target, configService.getRoleDisplayName(clan.getUserRole(target)), clanService, period)
    }

    private fun sendBattleResult(player: Player, result: ClanBattleOperation) {
        val rejected = result as? ClanBattleOperation.Rejected ?: return
        val messages = configService.messages
        val actions = when (rejected.reason) {
            ClanBattleRejection.DISABLED -> messages.battles.disabled
            ClanBattleRejection.NO_PERMISSION -> messages.battles.noPermission
            ClanBattleRejection.CLAN_BUSY -> messages.battles.clanBusy
            ClanBattleRejection.CHALLENGE_EXISTS -> messages.battles.challengeExists
            ClanBattleRejection.CHALLENGE_NOT_FOUND,
            ClanBattleRejection.CHALLENGE_EXPIRED -> messages.battles.challengeNotFound
            ClanBattleRejection.NOT_TARGET_CLAN -> messages.battles.notTarget
            ClanBattleRejection.NOT_ENOUGH_ONLINE -> messages.battles.notEnoughOnline
            ClanBattleRejection.ARENA_UNAVAILABLE -> messages.battles.arenaUnavailable
            ClanBattleRejection.CANCELLED_BY_EVENT -> messages.battles.cancelled
            ClanBattleRejection.NO_CLAN,
            ClanBattleRejection.SAME_CLAN -> messages.general.noPermission
            ClanBattleRejection.LOBBY_NOT_FOUND -> messages.battles.lobbyNotFound
            ClanBattleRejection.LOBBY_FULL -> messages.battles.lobbyFull
            ClanBattleRejection.NOT_ENOUGH_SELECTED -> messages.battles.lobbyNotEnoughSelected
        }
        configService.send(player, actions, mapOf(
            "minimum" to configService.battles.minimumOnlineMembers.coerceAtLeast(1).toString(),
            "maximum" to configService.battles.maximumParticipants.coerceAtLeast(1).toString()
        ))
    }
}