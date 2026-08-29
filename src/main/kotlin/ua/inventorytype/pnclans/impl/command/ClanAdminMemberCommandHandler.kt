package ua.inventorytype.pnclans.impl.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.impl.clan.ClanUser
import ua.inventorytype.pnclans.impl.util.ColorUtil

/** Administrator operations for membership and role management using ClanService event-safe mutations. */
internal class ClanAdminMemberCommandHandler(private val plugin: BukkitPlugin) {
    private val clans get() = plugin.clanService

    fun execute(sender: CommandSender, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> help(sender)
            "list" -> list(sender, args.drop(1))
            "info" -> info(sender, args.drop(1))
            "add" -> add(sender, args.drop(1))
            "remove" -> remove(sender, args.drop(1))
            "role" -> role(sender, args.drop(1))
            "transfer" -> transfer(sender, args.drop(1))
            else -> usage(sender)
        }
    }

    fun complete(args: List<String>): List<String> {
        val actions = listOf("help", "list", "info", "add", "remove", "role", "transfer")
        if (args.isEmpty()) return actions
        val current = args.last()
        val candidates = when (args.size) {
            1 -> actions
            2 -> when (args[0].lowercase()) {
                "list", "add", "transfer" -> clanNames()
                "info", "remove", "role" -> memberNames()
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "add" -> Bukkit.getOnlinePlayers().map { it.name }
                "role" -> mutableRoles().map { it.name.lowercase() }
                "transfer" -> membersOf(args[1])
                else -> emptyList()
            }
            4 -> if (args[0].equals("add", true)) mutableRoles().map { it.name.lowercase() } else emptyList()
            else -> emptyList()
        }
        return candidates.filter { it.startsWith(current, ignoreCase = true) }
    }

    private fun list(sender: CommandSender, args: List<String>) {
        val clan = clan(args.firstOrNull()) ?: return usage(sender)
        sender.reply("")
        sender.reply("&#FC7D37✦ &fСостав клана &#5EA9FD${clan.name} &8• &f${clan.users.size} участников")
        clan.users.sortedWith(compareByDescending<ua.inventorytype.pnclans.api.User> { clan.getUserRole(it).weight }.thenBy { it.playerName.lowercase() })
            .forEach { member ->
                val online = Bukkit.getPlayer(member.uuid)?.isOnline == true
                sender.reply("${if (online) "&#5EFD7D●" else "&8○"} &f${member.playerName} &8• &#5EA9FD${clan.getUserRole(member).name}")
            }
        sender.reply("")
    }

    private fun info(sender: CommandSender, args: List<String>) {
        val name = args.firstOrNull() ?: return usage(sender)
        val (clan, member) = clans.findMemberByName(name) ?: run {
            sender.reply("&#FC3737✖ &fУчастник &e$name &fне найден.")
            return
        }
        val user = member as? ClanUser
        sender.reply("")
        sender.reply("&#FC7D37✦ &fУчастник &#5EA9FD${member.playerName}")
        sender.reply("&8Клан: &f${clan.name} &8• Роль: &#5EA9FD${clan.getUserRole(member).name}")
        sender.reply("&8UUID: &f${member.uuid}")
        if (user != null) {
            sender.reply("&8Kills: &#5EFD7D${user.kills} &8• Deaths: &#FC3737${user.deaths} &8• Personal points: &#FC65DF${user.points}")
            sender.reply("&8Playtime: &f${user.playtimeSeconds}s")
        }
        sender.reply("")
    }

    private fun add(sender: CommandSender, args: List<String>) {
        val clan = clan(args.getOrNull(0)) ?: return usage(sender)
        val playerName = args.getOrNull(1) ?: return usage(sender)
        val player = Bukkit.getOnlinePlayers().firstOrNull { it.name.equals(playerName, true) } ?: run {
            sender.reply("&#FC3737✖ &fДля безопасного добавления игрок &e$playerName &fдолжен быть онлайн.")
            return
        }
        if (clans.getClanByUuid(player.uniqueId) != null) {
            sender.reply("&#FC3737✖ &fИгрок уже состоит в клане.")
            return
        }
        val role = args.getOrNull(2)?.parseRole() ?: ClanRole.MEMBER
        if (role == ClanRole.LEADER) {
            sender.reply("&#FC3737✖ &fЧерез add нельзя назначить LEADER. Используйте transfer после добавления.")
            return
        }
        val user = ClanUser(player.uniqueId, player.name)
        if (!clans.addUserToClan(clan, user, role)) {
            sender.reply("&#FC3737✖ &fДобавление отменено событием или ошибкой сохранения.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        sender.reply("&#5EFD7D✔ &fИгрок &e${player.name} &fдобавлен в &#5EA9FD${clan.name} &fс ролью &#5EA9FD${role.name}&f.")
        audit(sender, "added member ${player.uniqueId} to ${clan.id} role=${role.name}")
    }

    private fun remove(sender: CommandSender, args: List<String>) {
        val playerName = args.firstOrNull() ?: return usage(sender)
        val (clan, member) = clans.findMemberByName(playerName) ?: run {
            sender.reply("&#FC3737✖ &fУчастник &e$playerName &fне найден.")
            return
        }
        if (clan.getUserRole(member) == ClanRole.LEADER) {
            sender.reply("&#FC3737✖ &fЛидера нельзя удалить. Сначала передайте лидерство через /clan admin member transfer.")
            return
        }
        if (!clans.removeUserFromClan(clan, member.uuid, kicked = true)) {
            sender.reply("&#FC3737✖ &fУдаление отменено событием или ошибкой сохранения.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        clans.notifyClanUpdated(member.uuid)
        Bukkit.getPlayer(member.uuid)?.sendMessage(ColorUtil.color("&#FC3737✖ &fАдминистратор исключил вас из клана &#5EA9FD${clan.name}&f."))
        sender.reply("&#5EFD7D✔ &fИгрок &e${member.playerName} &fудалён из клана &#5EA9FD${clan.name}&f.")
        audit(sender, "removed member ${member.uuid} from ${clan.id}")
    }

    private fun role(sender: CommandSender, args: List<String>) {
        val playerName = args.getOrNull(0) ?: return usage(sender)
        val newRole = args.getOrNull(1)?.parseRole() ?: return usage(sender)
        if (newRole == ClanRole.LEADER) {
            sender.reply("&#FC3737✖ &fДля назначения лидера используйте /clan admin member transfer <clan> <player>.")
            return
        }
        val (clan, member) = clans.findMemberByName(playerName) ?: run {
            sender.reply("&#FC3737✖ &fУчастник &e$playerName &fне найден.")
            return
        }
        if (clan.getUserRole(member) == ClanRole.LEADER) {
            sender.reply("&#FC3737✖ &fТекущего лидера нельзя понизить напрямую. Сначала передайте лидерство.")
            return
        }
        val result = clans.changeMemberRole(clan, member, newRole)
        if (!result.isSuccess) {
            sender.reply("&#FC3737✖ &fИзменение роли отклонено: &e$result&f.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        sender.reply("&#5EFD7D✔ &fРоль &e${member.playerName}&f: &#5EA9FD${newRole.name}&f.")
        audit(sender, "changed role ${member.uuid} in ${clan.id} to ${newRole.name}")
    }

    private fun transfer(sender: CommandSender, args: List<String>) {
        val clan = clan(args.getOrNull(0)) ?: return usage(sender)
        val playerName = args.getOrNull(1) ?: return usage(sender)
        val newLeader = clan.users.firstOrNull { it.playerName.equals(playerName, true) } ?: run {
            sender.reply("&#FC3737✖ &fИгрок &e$playerName &fне состоит в этом клане.")
            return
        }
        val currentLeader = clan.users.firstOrNull { clan.getUserRole(it) == ClanRole.LEADER } ?: run {
            sender.reply("&#FC3737✖ &fУ клана не найден текущий лидер; проверьте данные клана.")
            return
        }
        if (currentLeader.uuid == newLeader.uuid) {
            sender.reply("&#FFD700! &fИгрок уже является лидером этого клана.")
            return
        }
        val result = clans.transferLeadership(clan, currentLeader, newLeader)
        if (!result.isSuccess) {
            sender.reply("&#FC3737✖ &fПередача лидерства отклонена: &e$result&f.")
            return
        }
        clan.users.forEach { clans.notifyClanUpdated(it.uuid) }
        sender.reply("&#5EFD7D✔ &fЛидерство &#5EA9FD${clan.name} &fпередано игроку &e${newLeader.playerName}&f.")
        audit(sender, "transferred leadership ${clan.id} ${currentLeader.uuid} -> ${newLeader.uuid}")
    }

    private fun help(sender: CommandSender) {
        sender.reply("")
        sender.reply("&#FC7D37✦ &fУправление участниками")
        sender.reply("&#5EA9FD/clan admin member list <clan>")
        sender.reply("&#5EA9FD/clan admin member info <player>")
        sender.reply("&#5EA9FD/clan admin member add <clan> <online-player> [member|elder|deputy]")
        sender.reply("&#5EA9FD/clan admin member remove <player>")
        sender.reply("&#5EA9FD/clan admin member role <player> <member|elder|deputy>")
        sender.reply("&#5EA9FD/clan admin member transfer <clan> <player>")
        sender.reply("")
    }

    private fun usage(sender: CommandSender) {
        sender.reply("&#FFD700Использование: &f/clan admin member <help|list|info|add|remove|role|transfer> ...")
    }

    private fun clan(name: String?): Clan? = name?.let(clans::getClanByName)
    private fun clanNames(): List<String> = clans.getAllClans().map { it.name }.sortedBy { it.lowercase() }
    private fun memberNames(): List<String> = clans.getAllClans().flatMap { it.users }.map { it.playerName }.distinct().sortedBy { it.lowercase() }
    private fun membersOf(clanName: String): List<String> = clan(clanName)?.users?.map { it.playerName }?.sortedBy { it.lowercase() }.orEmpty()
    private fun mutableRoles(): List<ClanRole> = listOf(ClanRole.MEMBER, ClanRole.ELDER, ClanRole.DEPUTY)
    private fun String.parseRole(): ClanRole? = runCatching { ClanRole.valueOf(uppercase()) }.getOrNull()

    private fun audit(sender: CommandSender, operation: String) {
        plugin.logger.info("[pnClans/Admin] ${sender.name}: $operation")
    }

    private fun CommandSender.reply(text: String) {
        sendMessage(ColorUtil.color(text))
    }
}
