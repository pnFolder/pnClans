package ua.inventorytype.pnclans.api

import ua.inventorytype.pnclans.api.permission.Permission
import java.util.UUID

/**
 * Represents a user interacting with the clan system.
 * Typically wraps a Bukkit/Paper Player and stores their clan-related data.
 */
interface User {

    val uuid: UUID

    val playerName: String

}