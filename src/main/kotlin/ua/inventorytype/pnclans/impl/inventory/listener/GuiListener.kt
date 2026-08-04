package ua.inventorytype.pnclans.impl.inventory.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.plugin.java.JavaPlugin
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.HolderGui
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuiListener(private val plugin: BukkitPlugin) : Listener {

    private val activeGuis = ConcurrentHashMap<UUID, BaseGui>()

    init {
        plugin.clanService.subscribe { playerUuid ->
            plugin.server.getPlayer(playerUuid)?.let { player ->
                player.closeInventory()
                activeGuis.remove(player.uniqueId)
                scheduleInventoryUpdate(player)
            }
        }
    }

    /**
     * Регистрация открытого GUI для UUID игрока
     */
    fun register(player: Player, gui: BaseGui) {
        activeGuis[player.uniqueId] = gui
    }

    @EventHandler
    fun onClick(event: InventoryOpenEvent) {
        val holder = event.view.topInventory.holder as? HolderGui ?: return
        register(event.player as Player, holder as BaseGui)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.clickedInventory == null || event.clickedInventory == event.view.bottomInventory) return

        val holder = event.inventory.holder as? HolderGui ?: return
        (holder as BaseGui).handleClick(event)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? HolderGui ?: return
        (holder as BaseGui).handleClose(event)

        val player = event.player as? Player ?: return
        scheduleInventoryUpdate(player)
        activeGuis.remove(player.uniqueId)
    }

//    @EventHandler
//    fun onQuit(event: PlayerQuitEvent) {
//        event.player.openInventory.topInventory.holder as? HolderGui ?: return
//        event.player.closeInventory()
//    }

    /**
     * Закрыть все кастомные GUI (вызывать в onDisable плагина)
     */
    fun forceCloseAll() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.holder as? HolderGui ?: continue

            player.closeInventory()
            scheduleInventoryUpdate(player)
        }
    }

    /**
     * Запланировать обновление инвентаря для предотвращения визуальных багов со сплитом предметов
     */
    private fun scheduleInventoryUpdate(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            player.updateInventory()
        }, 2L)
    }
}