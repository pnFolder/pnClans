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
import org.bukkit.plugin.Plugin
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Awards a capped amount of points to clans whose online members remain active. */
class ClanActivityPointsService(private val plugin: Plugin) : Listener {
    private val lastActivity = ConcurrentHashMap<UUID, Long>()
    private val awardedToday = ConcurrentHashMap<String, Long>()
    private var currentDate = LocalDate.now()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
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
        val to = event.to ?: return
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
        val config = (plugin as? ua.inventorytype.pnclans.BukkitPlugin)?.configService?.settings?.clanActivityPoints ?: return
        if (!config.enabled || config.pointsPerInterval <= 0L || config.dailyClanLimit <= 0L) return

        val today = LocalDate.now()
        if (today != currentDate) {
            awardedToday.clear()
            currentDate = today
        }

        val now = System.currentTimeMillis()
        val timeoutMs = config.afkTimeoutSeconds.coerceAtLeast(1L) * 1000L
        val intervalMs = config.intervalSeconds.coerceAtLeast(1L) * 1000L
        val bukkitPlugin = plugin as ua.inventorytype.pnclans.BukkitPlugin

        Bukkit.getOnlinePlayers().forEach { player ->
            val clan = bukkitPlugin.clanService.getClanUser(player) ?: return@forEach
            val activeAt = lastActivity[player.uniqueId] ?: return@forEach
            if (now - activeAt > timeoutMs) return@forEach

            val key = clan.id
            val awarded = awardedToday[key] ?: 0L
            val remaining = config.dailyClanLimit - awarded
            if (remaining <= 0L || now - activeAt < 0L) return@forEach

            val lastAward = clan.pointsLogs.asReversed()
                .firstOrNull { it.source == ua.inventorytype.pnclans.api.clan.ClanPointsSource.ACTIVITY }
                ?.timestamp ?: 0L
            if (now - lastAward < intervalMs) return@forEach

            val amount = config.pointsPerInterval.coerceAtMost(remaining)
            if (bukkitPlugin.clanPointsService.award(clan, amount, ua.inventorytype.pnclans.api.clan.ClanPointsSource.ACTIVITY)) {
                awardedToday[key] = awarded + amount
            }
        }
    }
}
