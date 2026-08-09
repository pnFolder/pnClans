package ua.inventorytype.pnclans.impl.api

import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.api.gui.ClanAddonGuiAction
import ua.inventorytype.pnclans.api.gui.ClanAddonGuiItemProvider
import ua.inventorytype.pnclans.api.gui.ClanAddonGuiRegistry
import java.util.concurrent.ConcurrentHashMap

internal class ClanAddonGuiRegistryImpl : ClanAddonGuiRegistry {
    private data class Entry<T>(val owner: Plugin, val value: T)

    private val items = ConcurrentHashMap<String, Entry<ClanAddonGuiItemProvider>>()
    private val actions = ConcurrentHashMap<String, Entry<ClanAddonGuiAction>>()

    override fun registerItem(owner: Plugin, id: String, provider: ClanAddonGuiItemProvider): Boolean =
        register(items, owner, id, provider)

    override fun registerAction(owner: Plugin, id: String, action: ClanAddonGuiAction): Boolean =
        register(actions, owner, id, action)

    override fun unregisterItem(owner: Plugin, id: String): Boolean = unregister(items, owner, id)
    override fun unregisterAction(owner: Plugin, id: String): Boolean = unregister(actions, owner, id)

    override fun findItem(id: String): ClanAddonGuiItemProvider? = items[id.lowercase()]?.value
    override fun findAction(id: String): ClanAddonGuiAction? = actions[id.lowercase()]?.value

    override fun unregisterAll(owner: Plugin): Int {
        val itemsRemoved = items.entries.count { it.value.owner === owner }
        val actionsRemoved = actions.entries.count { it.value.owner === owner }
        items.entries.removeIf { it.value.owner === owner }
        actions.entries.removeIf { it.value.owner === owner }
        return itemsRemoved + actionsRemoved
    }

    private fun <T> register(entries: ConcurrentHashMap<String, Entry<T>>, owner: Plugin, id: String, value: T): Boolean {
        val key = id.lowercase()
        if (!owner.isEnabled || !ID_PATTERN.matches(key)) return false
        return entries.putIfAbsent(key, Entry(owner, value)) == null
    }

    private fun <T> unregister(entries: ConcurrentHashMap<String, Entry<T>>, owner: Plugin, id: String): Boolean {
        val key = id.lowercase()
        val entry = entries[key] ?: return false
        return entry.owner === owner && entries.remove(key, entry)
    }

    private companion object {
        val ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]*:[a-z0-9][a-z0-9-]*$")
    }
}
