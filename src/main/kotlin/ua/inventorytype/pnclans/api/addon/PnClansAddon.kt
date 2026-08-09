package ua.inventorytype.pnclans.api.addon

import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.api.PnClansApi
import ua.inventorytype.pnclans.api.PnClansProvider
import java.io.File

/**
 * Optional lifecycle contract for a Bukkit plugin that extends pnClans.
 *
 * Register the implementation through [AddonRegistry.register] during the owner plugin's
 * `onEnable`. The add-on must release every task, listener, command, and menu button it owns
 * from [onDisable].
 */
interface PnClansAddon {
    val id: String
    val addonVersion: String
    val author: String get() = "Unknown"
    val summary: String get() = ""
    val website: String? get() = null
    val requiredApiVersion: Int get() = PnClansProvider.API_VERSION
    fun onEnable(context: AddonContext)
    fun onDisable() {}
}

/** Services supplied to an add-on after successful registration. */
data class AddonContext(
    val owner: Plugin,
    val api: PnClansApi
)

data class AddonDescriptor(
    val id: String,
    val addonVersion: String,
    val author: String,
    val summary: String,
    val website: String?,
    val requiredApiVersion: Int,
    val enabled: Boolean,
    val owner: Plugin,
    val source: File?
)

enum class AddonLoadStatus { LOADED, ALREADY_LOADED, INVALID_FILE, FAILED, NOT_REGISTERED }

data class AddonLoadResult(val status: AddonLoadStatus, val message: String, val addon: AddonDescriptor? = null)

/** Registry for the pnClans add-on lifecycle and optional runtime loading. */
interface AddonRegistry {
    val directory: File
    fun register(owner: Plugin, addon: PnClansAddon): Boolean
    fun unregister(id: String): Boolean
    fun enable(id: String): Boolean
    fun disable(id: String): Boolean
    fun load(file: File): AddonLoadResult
    fun loadDirectory(): List<AddonLoadResult>
    fun find(id: String): AddonDescriptor?
    fun all(): Collection<AddonDescriptor>
}
