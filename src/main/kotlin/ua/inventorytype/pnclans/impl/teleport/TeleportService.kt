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

        // Задержка телепортации зависит от уровня клана:
        // 1 уровень -> 5 сек, 2 уровень -> 4 сек, 3 уровень -> 3 сек, 4 уровень -> 2 сек, 5 уровень -> 1 сек!
        val delaySeconds = (6 - clan.level).coerceAtLeast(1)

        val startLocation = player.location.clone()
        val startHealth = player.health

        player.sendMessage("§eТелепортация на дом '$homeName' произойдёт через §b$delaySeconds сек. §eНе двигайтесь!")

        val runnable = object : BukkitRunnable() {
            var secondsLeft = delaySeconds

            override fun run() {
                if (!player.isOnline) {
                    cancelTeleport(player.uniqueId)
                    return
                }

                if (player.location.distance(startLocation) > 0.5 || player.health < startHealth) {
                    player.sendMessage("§cТелепортация отменена! Вы сдвинулись или получили урон.")
                    cancelTeleport(player.uniqueId)
                    return
                }

                secondsLeft--

                if (secondsLeft <= 0) {
                    player.teleport(targetLocation)
                    player.sendMessage("§aТелепортация на клановый дом '$homeName' успешна!")
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
}
