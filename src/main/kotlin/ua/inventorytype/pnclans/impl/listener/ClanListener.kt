package ua.inventorytype.pnclans.impl.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.ClanSetting

class ClanListener(private val plugin: BukkitPlugin) : Listener {

    private val clanService = plugin.clanService
    private val configService = plugin.configService
    private val cfg = configService.settings

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (attacker.uniqueId == victim.uniqueId) return

        val attackerClan = clanService.getClanUser(attacker) ?: return
        val victimClan = clanService.getClanUser(victim) ?: return

        if (attackerClan.id == victimClan.id) {
            if (!attackerClan.isSettingEnabled(ClanSetting.PVP)) {
                event.isCancelled = true
                val msg = configService.formatMessage(attacker, cfg.msgPvpDisabled)
                attacker.sendMessage(msg)
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer

        val victimClan = clanService.getClanUser(victim)
        if (victimClan != null) {
            victimClan.deaths += 1
            if (victimClan.mmr > 0) victimClan.mmr -= 5
            val victimUser = victimClan.users.find { it.uuid == victim.uniqueId } as? ua.inventorytype.pnclans.impl.clan.ClanUser
            victimUser?.deaths = victimUser.deaths + 1
            victimUser?.points = (victimUser.points - 1).coerceAtLeast(0)
            clanService.saveClan(victimClan)
        }

        if (killer != null && killer.uniqueId != victim.uniqueId) {
            val killerClan = clanService.getClanUser(killer)
            if (killerClan != null) {
                killerClan.kills += 1
                killerClan.mmr += 10
                val killerUser = killerClan.users.find { it.uuid == killer.uniqueId } as? ua.inventorytype.pnclans.impl.clan.ClanUser
                killerUser?.kills = killerUser.kills + 1
                killerUser?.points = (killerUser.points + 3).coerceAtLeast(0)
                clanService.saveClan(killerClan)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val message = event.message
        if (!message.startsWith("!")) return

        val player = event.player
        val clan = clanService.getClanUser(player) ?: return

        if (!clan.isSettingEnabled(ClanSetting.CHAT)) {
            player.sendMessage("§cЧат вашего клана заблокирован лидером.")
            event.isCancelled = true
            return
        }

        event.isCancelled = true

        val cleanMsg = message.substring(1).trim()
        if (cleanMsg.isEmpty()) return

        val user = clan.users.find { it.uuid == player.uniqueId } ?: return
        val role = clan.getUserRole(user)
        val roleName = configService.getRoleDisplayName(role)

        val formattedMessage = configService.formatMessage(
            player,
            cfg.msgChatFormat,
            mapOf("clan" to clan.name, "role" to roleName, "player" to player.name, "message" to cleanMsg)
        )

        clan.users.forEach { member ->
            Bukkit.getPlayer(member.uuid)?.sendMessage(formattedMessage)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val clan = clanService.getClanUser(player) ?: return

        // Обновляем сохраненный ник игрока, если он изменился в Minecraft
        val user = clan.users.find { it.uuid == player.uniqueId }
        if (user != null && user.playerName != player.name) {
            (user as? ua.inventorytype.pnclans.impl.clan.ClanUser)?.playerName = player.name
            clanService.saveClan(clan)
        }

        clanService.playtimeTracker.markOnline(player.uniqueId, clan.id)

        if (clan.isSettingEnabled(ClanSetting.JOIN)) {
            val msg = configService.formatMessage(player, cfg.msgJoinNotice, mapOf("player" to player.name))
            clan.users.forEach { member ->
                if (member.uuid != player.uniqueId) {
                    Bukkit.getPlayer(member.uuid)?.sendMessage(msg)
                }
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val clan = clanService.getClanUser(player) ?: return

        clanService.playtimeTracker.flushSession(player.uniqueId, clanService)

        if (clan.isSettingEnabled(ClanSetting.JOIN)) {
            val msg = configService.formatMessage(player, cfg.msgQuitNotice, mapOf("player" to player.name))
            clan.users.forEach { member ->
                if (member.uuid != player.uniqueId) {
                    Bukkit.getPlayer(member.uuid)?.sendMessage(msg)
                }
            }
        }
    }
}
