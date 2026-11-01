package ua.inventorytype.pnclans.impl.teleport

import org.bukkit.Location
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import ua.inventorytype.pnclans.impl.integration.repeat
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TeleportService(private val plugin: BukkitPlugin) {

    private val pendingTeleports = ConcurrentHashMap<UUID, TaskHandle>()

    fun teleportToHome(player: Player, clan: Clan, homeName: String, targetLocation: Location) {
        cancelTeleport(player.uniqueId)

        val delaySeconds = (6 - clan.level).coerceAtLeast(1)
        val cfg = plugin.configService

        val startLocation = player.location
        val startHealth = player.health
        val startX = startLocation.x
        val startY = startLocation.y
        val startZ = startLocation.z

        cfg.send(player, cfg.messages.teleport.started, mapOf(
            "home" to homeName,
            "seconds" to delaySeconds.toString()
        ))

        var secondsLeft = delaySeconds
        lateinit var handle: TaskHandle
        val action = Runnable {
            run {
                if (!player.isOnline) {
                    cancelTeleport(player.uniqueId)
                    return@Runnable
                }

                val currentLoc = player.location
                val dx = currentLoc.x - startX
                val dy = currentLoc.y - startY
                val dz = currentLoc.z - startZ
                val distSq = dx * dx + dy * dy + dz * dz

                if (currentLoc.world != startLocation.world || distSq > MAX_MOVE_DISTANCE_SQUARED || player.health < startHealth) {
                    cfg.send(player, cfg.messages.teleport.cancelled)
                    cancelTeleport(player.uniqueId)
                    return@Runnable
                }

                secondsLeft--

                if (secondsLeft <= 0) {
                    if (runCatching { player.teleportAsync(targetLocation) }.isFailure) {
                        player.teleport(targetLocation)
                    }
                    cfg.send(player, cfg.messages.teleport.completed, mapOf("home" to homeName))
                    pendingTeleports.remove(player.uniqueId)
                    handle.cancel()
                }
            }
        }

        handle = plugin.pnLibraryIntegration.tasks.repeat(20L, 20L, action)
        pendingTeleports[player.uniqueId] = handle
    }

    fun cancelTeleport(uuid: UUID) {
        pendingTeleports.remove(uuid)?.cancel()
    }

    private companion object {
        const val MAX_MOVE_DISTANCE_SQUARED = 0.25
    }
}
