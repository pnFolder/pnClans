package ua.inventorytype.pnclans.api.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.api.clan.Clan

/** Context provided when pnClans renders or clicks a config-bound add-on GUI item. */
data class ClanAddonGuiContext(
    val player: Player,
    val clan: Clan,
    val configId: String
)

fun interface ClanAddonGuiItemProvider {
    /** Returns the current item or null to hide the configured slot for this player. */
    fun createItem(context: ClanAddonGuiContext): ItemStack?
}

fun interface ClanAddonGuiAction {
    /** Handles a click on a configured add-on GUI item. */
    fun execute(context: ClanAddonGuiContext)
}

/**
 * Registry for add-on GUI providers and click actions referenced by `menus.yml`.
 *
 * IDs must be globally unique and use the `addon-id:item-id` format. Configuration decides
 * which registered entries are rendered and where; add-ons retain ownership of their code.
 */
interface ClanAddonGuiRegistry {
    fun registerItem(owner: Plugin, id: String, provider: ClanAddonGuiItemProvider): Boolean
    fun registerAction(owner: Plugin, id: String, action: ClanAddonGuiAction): Boolean
    fun unregisterItem(owner: Plugin, id: String): Boolean
    fun unregisterAction(owner: Plugin, id: String): Boolean
    /** Removes every item provider and action owned by [owner]. */
    fun unregisterAll(owner: Plugin): Int
    fun findItem(id: String): ClanAddonGuiItemProvider?
    fun findAction(id: String): ClanAddonGuiAction?
}
