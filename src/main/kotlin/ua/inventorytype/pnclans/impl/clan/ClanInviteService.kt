package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ClanInvite(
    val clan: Clan,
    val senderUuid: UUID,
    val targetUuid: UUID,
    val timestamp: Long = System.currentTimeMillis()
)

class ClanInviteService(private val clanService: ClanService) {

    // targetUuid -> ClanInvite
    private val pendingInvites = ConcurrentHashMap<UUID, ClanInvite>()

    fun sendInvite(sender: Player, target: Player): Boolean {
        val senderClan = clanService.getClanUser(sender)
        if (senderClan == null) {
            sender.sendMessage("§cВы не состоите в клане.")
            return false
        }

        val senderUser = senderClan.users.find { it.uuid == sender.uniqueId }
        if (senderUser == null || !senderClan.hasPermission(senderUser, ClanPerms.Members.INVITE)) {
            sender.sendMessage("§cУ вас нет прав для приглашения игроков в клан.")
            return false
        }

        if (clanService.getClanUser(target) != null) {
            sender.sendMessage("§cИгрок ${target.name} уже состоит в каком-то клане.")
            return false
        }

        if (senderClan.users.size >= senderClan.maxMembers) {
            sender.sendMessage("§cВ клане достигнут лимит участников (${senderClan.maxMembers}).")
            return false
        }

        val invite = ClanInvite(senderClan, sender.uniqueId, target.uniqueId)
        pendingInvites[target.uniqueId] = invite

        sender.sendMessage("§aВы отправили приглашение игроку §e${target.name}§a.")
        target.sendMessage("§aИгрок §e${sender.name} §aприглашает вас в клан §e${senderClan.name}§a!")
        target.sendMessage("§aИспользуйте §e/clan accept §aили §c/clan deny §aв течение 60 секунд.")

        return true
    }

    fun acceptInvite(player: Player): Boolean {
        val invite = pendingInvites.remove(player.uniqueId)
        if (invite == null || System.currentTimeMillis() - invite.timestamp > 60_000) {
            player.sendMessage("§cУ вас нет активных приглашений в клан (или истёк срок действия).")
            return false
        }

        val clan = invite.clan
        if (clan.users.size >= clan.maxMembers) {
            player.sendMessage("§cВ клане ${clan.name} больше нет свободных мест.")
            return false
        }

        val newUser = ClanUser(player.uniqueId, player.name)
        clan.addUser(newUser, ClanRole.MEMBER)
        clanService.saveClan(clan)

        player.sendMessage("§aВы успешно вступили в клан §e${clan.name}§a!")

        clan.users.forEach { member ->
            Bukkit.getPlayer(member.uuid)?.sendMessage("§aИгрок §e${player.name} §aвступил в клан!")
        }

        return true
    }

    fun denyInvite(player: Player): Boolean {
        val invite = pendingInvites.remove(player.uniqueId)
        if (invite == null) {
            player.sendMessage("§cУ вас нет активных приглашений в клан.")
            return false
        }

        player.sendMessage("§cВы отклонили приглашение в клан ${invite.clan.name}.")
        Bukkit.getPlayer(invite.senderUuid)?.sendMessage("§cИгрок ${player.name} отклонил приглашение в клан.")

        return true
    }
}
