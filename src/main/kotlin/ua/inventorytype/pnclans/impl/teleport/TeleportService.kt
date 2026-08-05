package ua.inventorytype.pnclans.impl.teleport

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TeleportService(private val plugin: BukkitPlugin) {

    private val pendingTeleports = ConcurrentHashMap<UUID, BukkitRunnable>()

    fun teleportToHome(player: Player, clan: Clan, homeName: String, targetLocation: Location) {
        cancelTeleport(player.uniqueId)

        val delaySeconds = (6 - clan.level).coerceAtLeast(1)
        val cfg = plugin.configService

        val startX = startLocation.x
        val startY = startLocation.y
        val startZ = startLocation.z

        cfg.send(player, cfg.messages.teleport.started, mapOf(
            "home" to homeName,
            "seconds" to delaySeconds.toString()
        ))

        val runnable = object : BukkitRunnable() {
            var secondsLeft = delaySeconds

            override fun run() {
                if (!player.isOnline) {
                    cancelTeleport(player.uniqueId)
                    return
                }

                val currentLoc = player.location
                val dx = currentLoc.x - startX
                val dy = currentLoc.y - startY
                val dz = currentLoc.z - startZ
                val distSq = dx * dx + dy * dy + dz * dz

                if (currentLoc.world != startLocation.world || distSq > MAX_MOVE_DISTANCE_SQUARED || player.health < startHealth) {
                    cfg.send(player, cfg.messages.teleport.cancelled)
                    cancelTeleport(player.uniqueId)
                    return
                }

                secondsLeft--

                if (secondsLeft <= 0) {
                    if (runCatching { player.teleportAsync(targetLocation) }.isFailure) {
                        player.teleport(targetLocation)
                    }
                    cfg.send(player, cfg.messages.teleport.completed, mapOf("home" to homeName))
                    pendingTeleports.remove(player.uniqueId)
                    cancel()
                }
            }
        }

        pendingTeleports[player.uniqueId] = runnable
        runnable.runTaskTimer(plugin, 20L, 20L)
    }

    fun cancelTeleport(uuid: UUID) {
        pendingTeleports.remove(uuid)?.cancel()
    }

    private companion object {
        const val MAX_MOVE_DISTANCE_SQUARED = 0.25
    }
}
