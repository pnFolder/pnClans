package ua.inventorytype.pnclans.impl.util

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import ua.inventorytype.pnclans.BukkitPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, NMS-free chat input listener for numeric/text prompts.
 * Compatible with all Paper & Spigot versions without reflection or NMS dependencies.
 */
object ChatInputPrompt {

    private val activePrompts = ConcurrentHashMap<UUID, (String) -> Unit>()
    private var isListenerRegistered = false

    private fun ensureListener(plugin: BukkitPlugin) {
        if (!isListenerRegistered) {
            isListenerRegistered = true
            Bukkit.getPluginManager().registerEvents(object : Listener {
                @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
                fun onChat(event: AsyncPlayerChatEvent) {
                    val callback = activePrompts.remove(event.player.uniqueId) ?: return
                    event.isCancelled = true
                    val message = event.message.trim()

                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        callback(message)
                    })
                }
            }, plugin)
        }
    }

    fun prompt(
        plugin: BukkitPlugin,
        player: Player,
        titleMessage: String,
        onInput: (String) -> Unit
    ) {
        ensureListener(plugin)
        activePrompts[player.uniqueId] = onInput
        player.closeInventory()
        player.sendMessage(titleMessage)
    }

    fun cancel(player: Player) {
        activePrompts.remove(player.uniqueId)
    }
}
