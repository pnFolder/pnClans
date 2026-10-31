package ua.inventorytype.pnclans.impl.integration

import org.bukkit.Bukkit
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticLevel
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import ua.inventorytype.pnclans.BukkitPlugin
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/** Owns every pnLibrary registration made by pnClans. */
class PnLibraryIntegration(private val plugin: BukkitPlugin) : AutoCloseable {

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

    private val context: ModuleContext = try {
        registration.registerModule(MODULE_ID) { setup ->
            // A policy that cannot be checked (offline server, JRE without a compiler) never stops pnClans;
            // only an explicit denial from the policy does.
            setup.remotePolicy(RemotePolicySettings.builder()
                .source(POLICY_SOURCE)
                .onError(PolicyErrorAction.KEEP_RUNNING)
                .support(PnLibraryIdentity.SUPPORT_URL)
                .build())
            setup.metrics { metrics ->
                metrics.bStats(BSTATS_PROJECT_ID).charts { charts ->
                    charts.simplePie("clan_chat_mode") { plugin.configService.settings.clanChat.mode.name }
                    charts.simplePie("storage_type") { plugin.configService.settings.storageType.uppercase() }
                    charts.simplePie("update_channel") { plugin.configService.settings.updateChannel.name }
                    charts.simplePie("economy_available") { economyConnected.toString() }
                    charts.simplePie("placeholder_api") { placeholderConnected.toString() }
                    charts.singleLineChart("loaded_addons") { loadedAddons }
                }
            }
            setup.diagnostics(plugin.dataFolder.toPath(), DiagnosticContainer.builder("pnclans")
                .snapshot(Supplier {
                    val clans = runCatching { plugin.clanService.getAllClans() }.getOrDefault(emptyList())
                    mapOf(
                        "version" to plugin.description.version,
                        "storageType" to plugin.configService.settings.storageType.uppercase(),
                        "storageImplementation" to runCatching { plugin.clanService.storage.javaClass.name }.getOrNull(),
                        "loadedClans" to clans.size,
                        "loadedMembers" to clans.sumOf { it.users.size },
                        "onlineClanMembers" to Bukkit.getOnlinePlayers().count {
                            runCatching { plugin.clanService.getClanByUuid(it.uniqueId) != null }.getOrDefault(false)
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
                    .channel(Supplier { UpdateChannel.valueOf(plugin.configService.settings.updateChannel.name) })
                    .automaticDownload(plugin.configService.settings.autoUpdate)
                    .artifact("(?i)^pnClans-.*-paper-.*-java21\\.jar$", 21, 24)
                    .artifact("(?i)^pnClans-.*-paper-.*-java25\\.jar$", 25)
            }
        }
    } catch (failure: RuntimeException) {
        registration.close()
        throw failure
    }

    val tasks: TaskScope get() = context.tasks

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
            "${plugin.configService.settings.storageType.uppercase()} storage initialized",
            mapOf("loadedClans" to loadedClansCount),
        )
        library.diagnostics.status(
            plugin.name, "economy", if (economyConnected) "CONNECTED" else "UNAVAILABLE",
            if (economyConnected) "Economy provider connected" else "Optional economy provider is unavailable",
            emptyMap(),
        )
        library.diagnostics.status(
            plugin.name, "placeholderapi", if (papiConnected) "CONNECTED" else "UNAVAILABLE",
            if (papiConnected) "pnClans expansion registered" else "Optional PlaceholderAPI is unavailable",
            emptyMap(),
        )
        library.diagnostics.status(
            plugin.name, "addons", "READY", "Addon directory loaded",
            mapOf("loaded" to loadedAddonsCount),
        )


        val box = context.lifecycle.enabled()
            .ok("Конфигурация", "7 файлов загружено")
            .ok("База данных", "${plugin.configService.settings.storageType.uppercase()}, кланов: $loadedClansCount")
            .ok("Аддоны", "загружено: $loadedAddonsCount")
            .ok("bStats", "project ID: $BSTATS_PROJECT_ID")
            .ok("Диагностика", "/pndebug pnClans")

        if (economyConnected) box.ok("Vault", "экономика подключена")
        else box.warn("Vault", "провайдер экономики не найден")
        if (papiConnected) box.ok("PlaceholderAPI", "расширение зарегистрировано")
        else box.skip("PlaceholderAPI", "плагин не установлен")
        box.show()
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

    fun close(savedClansCount: Int) {
        if (closed) return
        closed = true
        context.lifecycle.disabled()
            .ok("Хранилище", "сохранено кланов: $savedClansCount")
            .ok("Диагностика", "регистрация закрыта")
            .ok("Метрики", "сессия остановлена")
            .show()
        registration.close()
    }

    override fun close() = close(0)

    private fun normalizeCode(context: String): String {
        val normalized = context.uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .take(80)
        return if (normalized.isEmpty()) "PNCLANS_ERROR" else normalized
    }

    companion object {
        private const val MODULE_ID = "pnclans"
        private const val BSTATS_PROJECT_ID = 33208
        private const val POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnClansPolicy.java"
        const val MINIMUM_LIBRARY_VERSION = "0.0.1-dev.6"
    }
}
