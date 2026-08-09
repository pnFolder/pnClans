package ua.inventorytype.pnclans.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanSetting

class ClanSettingChangeEvent(
    val clan: Clan,
    val setting: ClanSetting,
    val oldValue: Boolean,
    var newValue: Boolean
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
