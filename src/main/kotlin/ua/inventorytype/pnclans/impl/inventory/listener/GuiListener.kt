package ua.inventorytype.pnclans.impl.inventory.listener

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
 * Event listener managing custom GUI interactions, title dynamic updates,
 * and inventory lifecycle events.
 *
 * @param plugin The owning plugin instance.
 */
class GuiListener(val plugin: BukkitPlugin) : Listener {

    private val activeGuis = ConcurrentHashMap<UUID, BaseGui>()

    init {
        plugin.clanService.subscribe { playerUuid ->
            val player = plugin.server.getPlayer(playerUuid) ?: return@subscribe
            val activeGui = activeGuis[player.uniqueId] ?: return@subscribe
            
            if (activeGui is ua.inventorytype.pnclans.impl.ux.TreasuryUX) {
                val centerSlot = plugin.configService.menus.treasuryMenu.items["center"]?.slot ?: 13
                activeGui.updateSlot(centerSlot, player)
            } else if (activeGui !is ua.inventorytype.pnclans.impl.ux.ClanChestUX) {
                activeGui.update(player)
            }
        }
    }

    fun register(player: Player, gui: BaseGui) {
        activeGuis[player.uniqueId] = gui
    }

    fun forceCloseAll() {
        activeGuis.keys.forEach { uuid ->
            plugin.server.getPlayer(uuid)?.closeInventory()
        }
        activeGuis.clear()
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

        if (event.clickedInventory == null) return
        gui.handleClick(event)
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
        activeGuis.remove(event.player.uniqueId)
    }

    private fun scheduleInventoryUpdate(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.updateInventory()
            }
        }, 1L)
    }
}