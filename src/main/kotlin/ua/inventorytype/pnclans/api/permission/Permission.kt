package ua.inventorytype.pnclans.api.permission

import org.bukkit.Material

/**
 * The base interface for all clan-related permissions.
 */
interface Permission {

    /**
     * The default state of this permission if not explicitly set.
     */
    val flag: Flag

    val displayName: String
        get() = (this as Enum<*>).name

    val description: String
        get() = "Описание не задано"

    val icon: Material
        get() = Material.PAPER

    /**
     * Automatically generates a unique string identifier (node) for this permission.
     * Example: "bank.deposit", "homes.delete_own".
     */
    val node: String
        get() = "${this::class.simpleName}.${(this as Enum<*>).name}".lowercase()

    /**
     * Represents the boolean state of a permission.
     */
    enum class Flag {
        /** Permission is granted by default. */
        TRUE,

        /** Permission is denied by default. */
        FALSE,

        DENY,

        ;
    }
}

// Превращает Flag в обычный Boolean
val Permission.Flag.isTrue: Boolean
    get() = this == Permission.Flag.TRUE