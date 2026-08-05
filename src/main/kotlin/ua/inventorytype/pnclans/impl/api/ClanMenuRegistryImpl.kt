package ua.inventorytype.pnclans.impl.api

import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.api.menu.ClanMainMenuButton
import ua.inventorytype.pnclans.api.menu.ClanMenuRegistry
import java.util.concurrent.ConcurrentHashMap

internal class ClanMenuRegistryImpl : ClanMenuRegistry {
    private data class Entry(val owner: Plugin, val button: ClanMainMenuButton)
    private val buttons = ConcurrentHashMap<String, Entry>()

    override fun registerMainButton(owner: Plugin, button: ClanMainMenuButton): Boolean {
        if (!owner.isEnabled || button.id.isBlank() || button.slot !in 0..53) return false
        return buttons.putIfAbsent(button.id.lowercase(), Entry(owner, button)) == null
    }

    override fun unregisterMainButton(owner: Plugin, id: String): Boolean {
        val entry = buttons[id.lowercase()] ?: return false
        if (entry.owner !== owner) return false
        return buttons.remove(id.lowercase(), entry)
    }

    override fun mainButtons(): Collection<ClanMainMenuButton> = buttons.values.map { it.button }.sortedBy { it.slot }
}
