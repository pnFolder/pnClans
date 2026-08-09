package ua.inventorytype.pnclans.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole

class ClanMemberJoinEvent(val clan: Clan, val member: User, val role: ClanRole) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class ClanMemberLeaveEvent(val clan: Clan, val member: User, val kicked: Boolean) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class ClanMemberRoleChangeEvent(
    val clan: Clan,
    val member: User,
    val oldRole: ClanRole,
    var newRole: ClanRole
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(value: Boolean) { cancelled = value }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
