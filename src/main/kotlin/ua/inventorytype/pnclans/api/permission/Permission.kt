package ua.inventorytype.pnclans.api.permission

import org.bukkit.Material

/**
 * Base contract for all clan permissions in the pnClans ecosystem.
 * Each permission represents a distinct action or capability with a unique string node,
 * user-friendly display name, description, and GUI icon.
 */
interface Permission {

    /**
     * The human-readable display name of the permission shown in GUI menus.
     */
    val displayName: String
        get() = (this as Enum<*>).name

    /**
     * Detailed description of the privileges granted by this permission.
     */
    val description: String
        get() = "No description provided."

    /**
     * Material icon used for visual representation of this permission in GUIs.
     */
    val icon: Material
        get() = Material.PAPER

    /**
     * Unique string node for permission checking (e.g. "bank.withdraw", "members.kick").
     */
    val node: String
        get() = "${this::class.simpleName}.${(this as Enum<*>).name}".lowercase()
}