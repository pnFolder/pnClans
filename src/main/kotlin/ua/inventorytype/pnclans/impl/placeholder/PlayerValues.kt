package ua.inventorytype.pnclans.impl.placeholder

import org.bukkit.Bukkit
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply

/** `{player.name}` в сообщениях действий. */
@Placeholders("player")
class PlayerValues {
    @Placeholder("Имя игрока")
    fun name(@Viewer viewer: ViewerInfo): Reply<String> =
        Reply.of(viewer.playerId?.let(Bukkit::getPlayer)?.name)
}
