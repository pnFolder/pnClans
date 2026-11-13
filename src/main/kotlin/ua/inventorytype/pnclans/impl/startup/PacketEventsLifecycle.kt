package ua.inventorytype.pnclans.impl.startup

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.util.TimeStampMode
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import org.bukkit.plugin.java.JavaPlugin
import ua.inventorytype.pnclans.impl.integration.Log

/** Loads, starts and stops PacketEvents in the order the server requires. */
internal object PacketEventsLifecycle {
    fun load(plugin: JavaPlugin) {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin))
        PacketEvents.getAPI().load()
        Log.info("[pnClans] PacketEvents loaded from ${PacketEvents::class.java.protectionDomain.codeSource?.location}")
    }

    fun start() {
        PacketEvents.getAPI().settings.debug(false).checkForUpdates(false).timeStampMode(TimeStampMode.MILLIS).reEncodeByDefault(true)
        PacketEvents.getAPI().init()
    }

    fun isStarted(): Boolean = PacketEvents.getAPI()?.isInitialized == true

    fun stop() {
        if (isStarted()) PacketEvents.getAPI().terminate()
    }
}
