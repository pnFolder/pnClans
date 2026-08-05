package ua.inventorytype.pnclans.impl.inventory.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerQuitEvent
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.HolderGui
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Event listener managing inventory GUI interactions, click routing, close handlers,
 * and automatic lifecycle cleanup.
 */
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

    fun register(player: Player, gui: BaseGui) {
        activeGuis[player.uniqueId] = gui
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val holder = event.view.topInventory.holder as? HolderGui ?: return
        val player = event.player as? Player ?: return
        register(player, holder as BaseGui)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val topHolder = event.view.topInventory.holder as? HolderGui ?: return
        val gui = topHolder as BaseGui

        // Prevent dragging items into/out of managed GUIs
        event.isCancelled = true

        if (event.clickedInventory == null) return
        if (event.clickedInventory == event.view.topInventory) {
            gui.handleClick(event)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? HolderGui ?: return
        val gui = holder as BaseGui
        gui.handleClose(event)

        val player = event.player as? Player ?: return
        activeGuis.remove(player.uniqueId)
        scheduleInventoryUpdate(player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val holder = event.player.openInventory.topInventory.holder as? HolderGui ?: return
        event.player.closeInventory()
        activeGuis.remove(event.player.uniqueId)
    }

    fun forceCloseAll() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.holder as? HolderGui ?: continue
            player.closeInventory()
            scheduleInventoryUpdate(player)
        }
        activeGuis.clear()
    }

    private fun scheduleInventoryUpdate(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            player.updateInventory()
        }, 2L)
    }
}