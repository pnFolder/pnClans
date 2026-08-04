package ua.inventorytype.pnclans.api

import java.util.UUID

/**
 * Represents a player interacting with the clan ecosystem.
 * Acts as an abstraction layer over Minecraft/Bukkit Player instances, tracking UUID, name, and online state.
 */
interface User {

    /**
     * The unique Minecraft UUID of the player.
     */
    val uuid: UUID

    /**
     * The current in-game name (IGN) of the player.
     */
    val playerName: String

    /**
     * Alias for [playerName] providing convenient property access.
     */
    val name: String
        get() = playerName

    /**
     * Returns true if the player is currently online on the server.
     */
    val isOnline: Boolean
        get() = org.bukkit.Bukkit.getPlayer(uuid) != null
}