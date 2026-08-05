package ua.inventorytype.pnclans.api.addon

import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.api.PnClansApi
import java.io.File

/** Lifecycle contract for an add-on hosted by another Bukkit plugin. */
interface PnClansAddon {
    val id: String
    val addonVersion: String
    val author: String get() = "Unknown"
    val summary: String get() = ""
    val website: String? get() = null
    val requiredApiVersion: Int get() = 1
    fun onEnable(context: AddonContext)
    fun onDisable() {}
}

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
