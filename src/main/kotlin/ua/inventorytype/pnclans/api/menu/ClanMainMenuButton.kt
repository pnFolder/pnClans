package ua.inventorytype.pnclans.api.menu

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.api.clan.Clan

data class ClanMainMenuContext(val player: Player, val clan: Clan)

interface ClanMainMenuButton {
    val id: String
    val slot: Int
    fun isVisible(context: ClanMainMenuContext): Boolean = true
    fun createItem(context: ClanMainMenuContext): ItemStack
    fun onClick(context: ClanMainMenuContext) {}
}

interface ClanMenuRegistry {
    fun registerMainButton(owner: Plugin, button: ClanMainMenuButton): Boolean
    fun unregisterMainButton(owner: Plugin, id: String): Boolean
    fun mainButtons(): Collection<ClanMainMenuButton>
}
