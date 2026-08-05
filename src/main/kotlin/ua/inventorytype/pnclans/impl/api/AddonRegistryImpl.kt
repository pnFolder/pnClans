package ua.inventorytype.pnclans.impl.api

import org.bukkit.plugin.Plugin
import org.bukkit.Bukkit
import ua.inventorytype.pnclans.api.PnClansProvider
import ua.inventorytype.pnclans.api.PnClansApi
import ua.inventorytype.pnclans.api.addon.AddonContext
import ua.inventorytype.pnclans.api.addon.AddonDescriptor
import ua.inventorytype.pnclans.api.addon.AddonRegistry
import ua.inventorytype.pnclans.api.addon.AddonLoadResult
import ua.inventorytype.pnclans.api.addon.AddonLoadStatus
import ua.inventorytype.pnclans.api.addon.PnClansAddon
import ua.inventorytype.pnclans.api.command.ClanCommandContext
import ua.inventorytype.pnclans.api.command.ClanSubcommand
import ua.inventorytype.pnclans.api.command.ClanSubcommandRegistry
import ua.inventorytype.pnclans.impl.clan.ClanService
import java.util.concurrent.ConcurrentHashMap
import java.io.File

internal class AddonRegistryImpl(private val api: PnClansApi, dataFolder: File) : AddonRegistry {
    override val directory: File = File(dataFolder, "addons").apply { mkdirs() }
    private data class Entry(val addon: PnClansAddon, val owner: Plugin, val source: File?, var enabled: Boolean)
    private val addons = ConcurrentHashMap<String, Entry>()

    override fun register(owner: Plugin, addon: PnClansAddon): Boolean {
        if (!owner.isEnabled || addon.id.isBlank() || addon.requiredApiVersion > PnClansProvider.API_VERSION) return false
        val key = addon.id.lowercase()
        val existing = addons[key]
        if (existing != null) {
            return if (existing.owner === owner && existing.addon === addon && !existing.enabled) enable(addon.id) else false
        }
        val entry = Entry(addon, owner, sourceOf(owner), false)
        if (addons.putIfAbsent(key, entry) != null) return false
        return enable(addon.id)
    }

    override fun unregister(id: String): Boolean = addons.remove(id.lowercase())?.let {
        if (it.enabled) runCatching { it.addon.onDisable() }
        true
    } ?: false

    override fun enable(id: String): Boolean {
        val entry = addons[id.lowercase()] ?: return false
        if (entry.enabled) return true
        return runCatching {
            entry.addon.onEnable(AddonContext(entry.owner, api))
            entry.enabled = true
            true
        }.getOrElse {
            entry.owner.logger.severe("pnClans addon ${entry.addon.id} failed: ${it.message}")
            false
        }
    }

    override fun disable(id: String): Boolean {
        val entry = addons[id.lowercase()] ?: return false
        if (!entry.enabled) return true
        runCatching { entry.addon.onDisable() }
        entry.enabled = false
        return true
    }

    override fun load(file: File): AddonLoadResult {
        if (!file.isFile || !file.name.endsWith(".jar", true)) {
            return AddonLoadResult(AddonLoadStatus.INVALID_FILE, "Expected an existing .jar file.")
        }
        val before = Bukkit.getPluginManager().plugins.toSet()
        val owner = runCatching { Bukkit.getPluginManager().loadPlugin(file) }.getOrElse {
            return AddonLoadResult(AddonLoadStatus.FAILED, it.message ?: "Failed to load addon jar.")
        } ?: return AddonLoadResult(AddonLoadStatus.FAILED, "Bukkit rejected the addon jar.")
        if (owner in before) return AddonLoadResult(AddonLoadStatus.ALREADY_LOADED, "The addon jar is already loaded.")
        Bukkit.getPluginManager().enablePlugin(owner)
        val descriptor = addons.values.firstOrNull { it.owner === owner }?.let(::descriptor)
        return if (descriptor == null) {
            AddonLoadResult(AddonLoadStatus.NOT_REGISTERED, "Plugin loaded but did not register a pnClans addon.")
        } else {
            AddonLoadResult(AddonLoadStatus.LOADED, "Addon ${descriptor.id} loaded.", descriptor)
        }
    }

    override fun loadDirectory(): List<AddonLoadResult> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(".jar", true) }
        ?.sortedBy { it.name }
        ?.map(::load)
        ?: emptyList()

    override fun find(id: String): AddonDescriptor? = addons[id.lowercase()]?.let(::descriptor)
    override fun all(): Collection<AddonDescriptor> = addons.values.map(::descriptor).sortedBy { it.id }

    private fun descriptor(entry: Entry) = AddonDescriptor(
        entry.addon.id, entry.addon.addonVersion, entry.addon.author, entry.addon.summary,
        entry.addon.website, entry.addon.requiredApiVersion, entry.enabled, entry.owner, entry.source
    )

    private fun sourceOf(owner: Plugin): File? = runCatching {
        File(owner.javaClass.protectionDomain.codeSource.location.toURI())
    }.getOrNull()
}

internal class ClanSubcommandRegistryImpl(private val service: ClanService) : ClanSubcommandRegistry {
    private data class Entry(val owner: Plugin, val command: ClanSubcommand)
    private val commands = ConcurrentHashMap<String, Entry>()

    override fun register(owner: Plugin, subcommand: ClanSubcommand): Boolean {
        if (subcommand.name.isBlank()) return false
        val entry = Entry(owner, subcommand)
        val keys = setOf(subcommand.name, *subcommand.aliases.toTypedArray()).map { it.lowercase() }
        if (keys.any { commands.containsKey(it) }) return false
        keys.forEach { commands[it] = entry }
        return true
    }

    override fun unregister(owner: Plugin, name: String): Boolean {
        val entry = commands[name.lowercase()] ?: return false
        if (entry.owner !== owner) return false
        commands.entries.removeIf { it.value === entry }
        return true
    }

    override fun all(): Collection<ClanSubcommand> = commands.values.distinctBy { it.command.name }.map { it.command }

    fun find(name: String): ClanSubcommand? = commands[name.lowercase()]?.command
    fun ownerOf(name: String): Plugin? = commands[name.lowercase()]?.owner
}
