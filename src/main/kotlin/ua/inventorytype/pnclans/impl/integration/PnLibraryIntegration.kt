package ua.inventorytype.pnclans.impl.integration

import ua.inventorytype.pnclans.impl.text.ClanText
import ua.inventorytype.pnclans.impl.storage.ClanItems
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.bukkit.Bukkit
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import org.pnfolder.pnlibrary.api.remote.result.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import ua.inventorytype.pnclans.BukkitPlugin
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import ua.inventorytype.pnclans.impl.placeholder.ClanPlaceholderValues
import ua.inventorytype.pnclans.impl.placeholder.ClanValues
import ua.inventorytype.pnclans.impl.placeholder.PlayerValues
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/** Owns every pnLibrary registration made by pnClans. */
class PnLibraryIntegration(private val plugin: BukkitPlugin) : AutoCloseable {

    init { Log.fallback(plugin.logger) }

    private val library: PnLibrary = PnLibraryProvider.getOrNull()
        ?: error("pnLibrary service is unavailable; install a compatible pnLibrary runtime")
    init {
        require(library.isAtLeastVersion(MINIMUM_LIBRARY_VERSION)) {
            "Установлена устаревшая pnLibrary ${library.version}; требуется версия не ниже $MINIMUM_LIBRARY_VERSION"
        }
    }
    private val reportedErrors = AtomicInteger()
    private var economyConnected = false
    private var placeholderConnected = false
    private var loadedAddons = 0
    private var closed = false

    private val registration = library.plugins.register(plugin)

    /** The context of the module while it is enabled; it changes on every enable, so it is given by [attach]. */
    lateinit var context: ModuleContext
        private set

    /** Registers the module: the library calls its `onEnable` right away and later `/pn modules` switches it. */
    fun registerModule(module: PluginModule) {
        try {
            registration.registerModule(MODULE_ID, module) { setup ->
                // A policy that cannot be checked (offline server, JRE without a compiler) never stops pnClans;
                // only an explicit denial from the policy does.
                setup.minecraft { mc ->
                    mc.versions(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_11))
                    mc.whenNewer(NewerAction.BLOCK)
                }

                setup.remotePolicy(RemotePolicySettings.builder()
                    .source(POLICY_SOURCE)
                    .onError(PolicyErrorAction.KEEP_RUNNING)
                    .build())
                setup.metrics { metrics ->
                    metrics.bStats(BSTATS_PROJECT_ID).fastStats(FASTSTATS_TOKEN).charts { charts ->
                        charts.simplePie("clan_chat_mode") { plugin.services.configService.settings.clanChat.mode.name }
                        charts.simplePie("storage_type") { plugin.services.configService.settings.storageType.uppercase() }
                        charts.simplePie("update_channel") { plugin.services.configService.settings.updateChannel.name }
                        charts.simplePie("economy_available") { economyConnected.toString() }
                        charts.simplePie("placeholder_api") { placeholderConnected.toString() }
                        charts.singleLineChart("loaded_addons") { loadedAddons }
                    }
                }
                setup.diagnostics(plugin.dataFolder.toPath(), DiagnosticContainer.builder("pnclans")
                    .snapshot(Supplier {
                        val clans = runCatching { plugin.services.clanService.getAllClans() }.getOrDefault(emptyList())
                        mapOf(
                            "version" to plugin.description.version,
                            "storageType" to plugin.services.configService.settings.storageType.uppercase(),
                            "storageImplementation" to runCatching { plugin.services.clanService.storage.javaClass.name }.getOrNull(),
                            "loadedClans" to clans.size,
                            "loadedMembers" to clans.sumOf { it.users.size },
                            "onlineClanMembers" to Bukkit.getOnlinePlayers().count {
                                runCatching { plugin.services.clanService.getClanByUuid(it.uniqueId) != null }.getOrDefault(false)
                            },
                            "loadedAddons" to loadedAddons,
                            "economyConnected" to economyConnected,
                            "placeholderApiConnected" to placeholderConnected,
                            "packetEventsInitialized" to runCatching {
                                com.github.retrooper.packetevents.PacketEvents.getAPI().isInitialized
                            }.getOrDefault(false),
                            "reportedErrors" to reportedErrors.get(),
                        )
                    })
                    .configuration(DiagnosticConfiguration.builder("config.yml")
                        .secretKeyRegex("(?i).*(password|passwd|token|secret|api[-_]?key|webhook|credential).*")
                        .build())
                    .configuration("menus.yml")
                    .configuration("messages.yml")
                    .configuration("points.yml")
                    .configuration("shop.yml")
                    .configuration("quests.yml")
                    .configuration("battles.yml")
                    .build())
                setup.updates("pnFolder", "pnClans") { updater ->
                    updater
                        .supportedApi(3, 3)
                        .artifact("(?i)^pnClans-.*-paper-.*-java21\\.jar$", 21, 24)
                        .artifact("(?i)^pnClans-.*-paper-.*-java25\\.jar$", 25)
                }
            }
        } catch (failure: RuntimeException) {
            registration.close()
            throw failure
        }
    }

    /** Connects what lives only while the module is enabled: its logger, texts and items. */
    fun attach(context: ModuleContext) {
        this.context = context
        Log.attach(context.logger)
        ClanText.connect(context.components)
        ClanItems.connect(library.platforms.require(BukkitPlatform::class.java).items)
    }

    val tasks: TaskScope get() = context.tasks
    val placeholders: PlaceholderService get() = context.placeholders

    fun started(
        economyConnected: Boolean,
        papiConnected: Boolean,
        loadedClansCount: Int,
        loadedAddonsCount: Int,
    ) {
        this.economyConnected = economyConnected
        this.placeholderConnected = papiConnected
        this.loadedAddons = loadedAddonsCount

        library.diagnostics.status(
            plugin.name, "storage", "READY",
            "${plugin.services.configService.settings.storageType.uppercase()} storage initialized",
            mapOf("loadedClans" to loadedClansCount),
        )
        library.diagnostics.status(
            plugin.name, "economy", if (economyConnected) "CONNECTED" else "UNAVAILABLE",
            if (economyConnected) "Economy provider connected" else "Optional economy provider is unavailable",
            emptyMap(),
        )
        library.diagnostics.status(
            plugin.name, "placeholderapi", if (papiConnected) "CONNECTED" else "UNAVAILABLE",
            if (papiConnected) "pnClans placeholders registered via pnLibrary" else "Optional PlaceholderAPI is unavailable",
            emptyMap(),
        )
        library.diagnostics.status(
            plugin.name, "addons", "READY", "Addon directory loaded",
            mapOf("loaded" to loadedAddonsCount),
        )


        val box = context.lifecycle.enabled()
            .ok("Конфигурация", "7 файлов загружено")
            .ok("База данных", "${plugin.services.configService.settings.storageType.uppercase()}, кланов: $loadedClansCount")
            .ok("Аддоны", "загружено: $loadedAddonsCount")
            .ok("bStats", "project ID: $BSTATS_PROJECT_ID")
            .ok("Диагностика", "/pn debug pnClans")

        if (economyConnected) box.ok("Vault", "экономика подключена")
        else box.warn("Vault", "провайдер экономики не найден")
        if (papiConnected) box.ok("PlaceholderAPI", "расширение зарегистрировано")
        else box.skip("PlaceholderAPI", "плагин не установлен")
        box.show()
    }

    /** Регистрирует значения клана и игрока в pnLibrary: `{clan.name}` в текстах и `%pnclans_clan_name%` в PlaceholderAPI. */
    fun registerPlaceholderTree(values: ClanPlaceholderValues) {
        // clanService появляется позже, чем регистрируется дерево, поэтому берётся при каждом вызове
        val clanOf = { id: java.util.UUID? -> id?.let(plugin.services.clanService::getClanByUuid) }
        context.placeholders.registerPublished(PLACEHOLDER_NAMESPACE, ClanValues(values, clanOf), PlayerValues())
    }

    fun reportError(context: String, throwable: Throwable, fields: Map<String, Any?> = emptyMap()) {
        reportedErrors.incrementAndGet()
        library.diagnostics.record(
            plugin.name,
            DiagnosticLevel.ERROR,
            "runtime",
            normalizeCode(context),
            context,
            throwable,
            fields,
        )
    }

    /** Closes the registration; the library disables the module first, so [ClansModule.onDisable] runs. */
    override fun close() {
        if (closed) return
        closed = true
        registration.close()
    }

    private fun normalizeCode(context: String): String {
        val normalized = context.uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .take(80)
        return if (normalized.isEmpty()) "PNCLANS_ERROR" else normalized
    }

    companion object {
        private const val MODULE_ID = "pnclans"
        private const val PLACEHOLDER_NAMESPACE = "pnclans"
        private const val BSTATS_PROJECT_ID = 33208
        private const val FASTSTATS_TOKEN = "735be9bdf5db9ca9ee9cf05dc73fbfcc"
        private const val POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnClansPolicy.java"
        const val MINIMUM_LIBRARY_VERSION = "0.1.0-dev.6"
    }
}
