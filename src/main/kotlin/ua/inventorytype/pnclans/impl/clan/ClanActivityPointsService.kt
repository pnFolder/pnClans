package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.scheduler.BukkitTask
import ua.inventorytype.pnclans.BukkitPlugin
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Awards a capped amount of points to clans whose online members remain active. */
class ClanActivityPointsService(private val plugin: BukkitPlugin) : Listener {
    private val lastActivity = ConcurrentHashMap<UUID, Long>()
    private val lastQuestInterval = ConcurrentHashMap<String, Long>()
    private val task: BukkitTask

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun shutdown() {
        task.cancel()
        lastActivity.clear()
        lastQuestInterval.clear()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) = markActivity(event.player)

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastActivity.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) markActivity(event.player)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) = markActivity(event.player)

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) = markActivity(event.player)

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) = markActivity(event.player)

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        (event.damager as? Player)?.let(::markActivity)
    }

    private fun markActivity(player: Player) {
        lastActivity[player.uniqueId] = System.currentTimeMillis()
    }

    private fun tick() {
        val config = plugin.configService.settings.clanActivityPoints
        if (!config.enabled) {
            lastQuestInterval.clear()
            return
        }

        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val timeoutMs = config.afkTimeoutSeconds.coerceAtLeast(1L) * 1000L
        val intervalMs = config.intervalSeconds.coerceAtLeast(1L) * 1000L
        val processedClans = HashSet<String>()
        Bukkit.getOnlinePlayers().forEach { player ->
            val clan = plugin.clanService.getClanUser(player) ?: return@forEach
            val activeAt = lastActivity[player.uniqueId] ?: return@forEach
            if (now - activeAt > timeoutMs) return@forEach
            if (!processedClans.add(clan.id)) return@forEach

            if (clan.activityPointsDate != today) {
                clan.activityPointsDate = today
                clan.activityPointsAwardedToday = 0L
            }
            val lastQuestAt = lastQuestInterval.putIfAbsent(clan.id, now)
            if (lastQuestAt != null && now - lastQuestAt >= intervalMs) {
                plugin.clanQuestService.recordActivityInterval(clan, player)
                lastQuestInterval[clan.id] = now
            }
            if (config.pointsPerInterval <= 0L || config.dailyClanLimit <= 0L) return@forEach
            val awarded = clan.activityPointsAwardedToday
            val remaining = config.dailyClanLimit - awarded
            if (remaining <= 0L) return@forEach

            val lastAward = clan.pointsLogs.asReversed()
                .firstOrNull { it.source == ua.inventorytype.pnclans.api.clan.ClanPointsSource.ACTIVITY }
                ?.timestamp ?: 0L
            if (now - lastAward < intervalMs) return@forEach

            val amount = config.pointsPerInterval.coerceAtMost(remaining)
            if (plugin.clanPointsService.award(clan, amount, ua.inventorytype.pnclans.api.clan.ClanPointsSource.ACTIVITY)) {
                clan.activityPointsAwardedToday = awarded + amount
                plugin.clanService.saveClan(clan)
            }
        }
        lastQuestInterval.keys.removeIf { it !in processedClans }
    }
}
