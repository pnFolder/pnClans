package ua.inventorytype.pnclans.impl.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerQuitEvent
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.impl.config.ClanChatMode

class ClanListener(private val plugin: BukkitPlugin) : Listener {

    private val clanService = plugin.clanService
    private val configService = plugin.configService
    private val cfg get() = configService.settings

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
            val victimUser = victimClan.getMember(victim.uniqueId) as? ua.inventorytype.pnclans.impl.clan.ClanUser
            victimUser?.deaths = victimUser.deaths + 1
            victimUser?.points = (victimUser.points - 1).coerceAtLeast(0)
            clanService.saveClan(victimClan)
        }

        if (killer != null && killer.uniqueId != victim.uniqueId) {
            val killerClan = clanService.getClanUser(killer)
            if (killerClan != null) {
                killerClan.kills += 1
                killerClan.mmr += 10
                val killerUser = killerClan.getMember(killer.uniqueId) as? ua.inventorytype.pnclans.impl.clan.ClanUser
                killerUser?.kills = killerUser.kills + 1
                killerUser?.points = (killerUser.points + 3).coerceAtLeast(0)
                val awarded = plugin.clanPointsService.award(
                    killerClan,
                    cfg.clanPointsPerPlayerKill,
                    ClanPointsSource.PLAYER_KILL
                )
                if (!awarded) clanService.saveClan(killerClan)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onMobDeath(event: EntityDeathEvent) {
        if (event.entity is Player) return
        val killer = event.entity.killer ?: return
        val reward = cfg.clanPointsPerMobKill[event.entity.type.name] ?: return
        if (reward <= 0L) return

        val clan = clanService.getClanUser(killer) ?: return
        plugin.clanPointsService.award(clan, reward, ClanPointsSource.MOB_KILL)
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val chatConfig = cfg.clanChat
        if (chatConfig.mode != ClanChatMode.PREFIX) return

        val message = event.message
        val prefix = chatConfig.prefix
        if (prefix.isEmpty() || !message.startsWith(prefix)) return

        event.isCancelled = true
        sendClanChat(event.player, message.removePrefix(prefix).trim())
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onClanChatCommand(event: PlayerCommandPreprocessEvent) {
        val chatConfig = cfg.clanChat
        if (chatConfig.mode != ClanChatMode.COMMAND) return

        val configuredCommand = chatConfig.command.trim().removePrefix("/")
        if (configuredCommand.isEmpty()) return

        val commandLine = event.message.removePrefix("/").trim()
        val command = commandLine.substringBefore(' ')
        if (!command.equals(configuredCommand, ignoreCase = true)) return

        event.isCancelled = true
        val message = commandLine.substringAfter(' ', missingDelimiterValue = "").trim()
        if (message.isEmpty()) {
            event.player.sendMessage(
                configService.formatMessage(event.player, cfg.msgClanChatCommandUsage, mapOf("command" to configuredCommand))
            )
            return
        }

        sendClanChat(event.player, message)
    }

    private fun sendClanChat(player: Player, message: String) {
        if (message.isEmpty()) return
        val clan = clanService.getClanUser(player) ?: return

        if (!clan.isSettingEnabled(ClanSetting.CHAT)) {
            player.sendMessage(configService.formatMessage(player, cfg.msgClanChatDisabled))
            return
        }

        val user = clan.getMember(player.uniqueId) ?: return
        val role = clan.getUserRole(user)
        val roleName = configService.getRoleDisplayName(role)

        val formattedMessage = configService.formatMessage(
            player,
            cfg.msgChatFormat,
            mapOf("clan" to clan.name, "role" to roleName, "player" to player.name, "message" to message)
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
        val user = clan.getMember(player.uniqueId)
        if (user != null && user.playerName != player.name) {
            (user as? ua.inventorytype.pnclans.impl.clan.ClanUser)?.playerName = player.name
            clanService.saveClan(clan)
        }

        clanService.playtimeTracker.markOnline(player.uniqueId, clan.id)
        plugin.clanHighlightService.syncPlayer(player)

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
    fun onRespawn(event: PlayerRespawnEvent) {
        plugin.clanHighlightService.syncPlayer(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        plugin.clanHighlightService.syncPlayer(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val clan = clanService.getClanUser(player) ?: return

        plugin.clanHighlightService.forgetPlayer(player)
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
