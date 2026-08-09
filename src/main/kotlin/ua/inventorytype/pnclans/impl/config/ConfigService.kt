package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.ActionContext
import ua.inventorytype.pnclans.api.clan.ClanRole
import java.io.File

/**
 * Service responsible for loading, saving, and managing all plugin configuration files.
 *
 * Manages three YAML files:
 * - `config.yml`   → [Settings]      — general plugin settings, storage type, economy options
 * - `menus.yml`    → [MenusConfig]   — 100% config-driven GUI layout, item slots, actions
 * - `messages.yml` → [MessagesConfig] — all player-facing event responses as [Action] lists
 *
 * Uses [Yaml] with [PolymorphismStyle.Tag] to support polymorphic [ua.inventorytype.pnclans.api.Action]
 * deserialization across both `menus.yml` and `messages.yml`.
 *
 * @param plugin The owning Bukkit plugin instance.
 */
class ConfigService(private val plugin: Plugin) {

    /**
     * Kaml YAML serializer configured with:
     * - `encodeDefaults = true` — always write default values to generated config files.
     * - `strictMode = false`   — silently ignore unknown keys for forward compatibility.
     * - `polymorphismStyle = Tag` — enables `!message`, `!sound`, `!title`, etc. tag syntax.
     */
    val yaml: Yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            polymorphismStyle = PolymorphismStyle.Tag
        )
    )

    /** Loaded general plugin settings from `config.yml`. */
    lateinit var settings: Settings private set

    /** Loaded GUI menu configuration from `menus.yml`. */
    lateinit var menus: MenusConfig private set

    /**
     * Loaded player-facing event responses from `messages.yml`.
     * Each entry is a [List] of [Action] objects, allowing arbitrary combinations of
     * `!message`, `!sound`, `!title`, `!actionbar`, `!particle`, etc.
     */
    lateinit var messages: MessagesConfig private set

    /** Loaded clan shop definition from `shop.yml`. */
    lateinit var shop: ClanShopConfig private set

    /** Loaded clan quest definitions from `quests.yml`. */
    lateinit var quests: ClanQuestsConfig private set

    /**
     * Loads or generates all plugin configuration files on startup.
     *
     * If a file does not yet exist, its default values are serialized and written to disk.
     * Called once during [ua.inventorytype.pnclans.BukkitPlugin.onEnable].
     */
    fun loadAll() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        appendMissingClanChatSection()
        settings = loadOrCreate("config.yml", Settings.serializer(), Settings())
        menus = loadOrCreate("menus.yml", MenusConfig.serializer(), MenusConfig())
        messages = loadOrCreate("messages.yml", MessagesConfig.serializer(), MessagesConfig())
        shop = loadOrCreate("shop.yml", ClanShopConfig.serializer(), ClanShopConfig())
        quests = loadOrCreate("quests.yml", ClanQuestsConfig.serializer(), ClanQuestsConfig())
    }

    /**
     * Retrieves the configurable display name for a [ClanRole] from `config.yml`.
     *
     * @param role The clan role to look up.
     * @return The localized display name string defined in [Settings].
     */
    fun getRoleDisplayName(role: ClanRole): String {
        return when (role) {
            ClanRole.LEADER -> settings.roleLeader
            ClanRole.DEPUTY -> settings.roleDeputy
            ClanRole.ELDER -> settings.roleElder
            ClanRole.MEMBER -> settings.roleMember
        }
    }

    /**
     * Formats a message template by processing PlaceholderAPI placeholders, internal `{key}` tokens,
     * hex color codes (`&#RRGGBB`), and legacy `&` color codes.
     *
     * @param player The player context used for PlaceholderAPI resolution.
     * @param template The raw template string (from any config file).
     * @param customPlaceholders Additional `{key}` → `value` pairs to replace in the template.
     * @return The fully formatted and colorized message string.
     */
    fun formatMessage(player: Player, template: String, customPlaceholders: Map<String, String> = emptyMap()): String {
        val bukkitPlugin = plugin as? BukkitPlugin ?: return template
        return bukkitPlugin.placeholderRegistry.process(player, template, customPlaceholders)
    }

    /**
     * Returns the active animation frame for the given frame list.
     *
     * The frame index is computed from the current time using [AnimationConfig.frameIntervalMs]
     * so multiple players see a synchronised animation without needing a scheduler.
     *
     * @param frames Frame list from [AnimationConfig]. Empty list returns the fallback text.
     * @param fallback Text returned when [frames] is empty.
     */
    fun animatedFrame(frames: List<String>, fallback: String = ""): String {
        if (frames.isEmpty()) return fallback
        val interval = settings.animations.frameIntervalMs.toLong().coerceAtLeast(MIN_FRAME_INTERVAL_MS)
        val frame = ((System.currentTimeMillis() / interval) % frames.size).toInt()
        return frames[frame]
    }

    /**
     * Convenience helper that resolves a named animation collection from [AnimationConfig].
     *
     * @param key One of "hiddenBalance", "upgradeIdle", "upgradeReady", "upgradeBusy".
     * @return The matching frame list, or an empty list if the key is unknown.
     */
    fun animationFrames(key: String): List<String> = when (key) {
        AnimationKey.HIDDEN_BALANCE -> settings.animations.hiddenBalance
        AnimationKey.UPGRADE_IDLE -> settings.animations.upgradeIdle
        AnimationKey.UPGRADE_READY -> settings.animations.upgradeReady
        AnimationKey.UPGRADE_BUSY -> settings.animations.upgradeBusy
        else -> emptyList()
    }

    private companion object {
        const val MIN_FRAME_INTERVAL_MS = 100L
    }

    /**
     * Executes a list of [Action] objects for the given player, applying optional placeholder tokens.
     *
     * This is the central dispatch method for all config-driven event responses.
     * Each action in the list runs sequentially in declaration order.
     *
     * Example usage:
     * ```kotlin
     * val cfg = clanService.plugin.configService
     * cfg.send(player, cfg.messages.homes.teleported, mapOf("home" to homeName))
     * ```
     *
     * @param player The recipient player.
     * @param actions The list of [Action] objects to execute (from [MessagesConfig]).
     * @param placeholders Optional map of `{key}` → `value` replacements applied to every action.
     */
    fun send(
        player: Player,
        actions: List<Action>,
        placeholders: Map<String, String> = emptyMap(),
        durationSeconds: Int? = null
    ) {
        val bukkitPlugin = plugin as? BukkitPlugin ?: return
        val context = ActionContext(
            player = player,
            placeholderRegistry = bukkitPlugin.placeholderRegistry,
            placeholders = placeholders,
            plugin = bukkitPlugin,
            durationSeconds = durationSeconds
        )
        actions.forEach { it.execute(context) }
    }

    /**
     * Loads a YAML configuration file from the plugin data folder.
     * If the file does not exist, it is created with the provided default instance.
     *
     * @param T The serializable configuration type.
     * @param fileName The name of the YAML file relative to the plugin data folder.
     * @param serializer The Kotlinx [KSerializer] for type [T].
     * @param default The default instance to serialize and write if the file is missing.
     * @return The deserialized configuration instance.
     */
    private fun <T> loadOrCreate(fileName: String, serializer: KSerializer<T>, default: T): T {
        val file = File(plugin.dataFolder, fileName)

        if (!file.exists()) {
            val encoded = yaml.encodeToString(serializer, default)
            file.writeText(encoded)
            return default
        }

        val content = file.readText()
        return yaml.decodeFromString(serializer, content)
    }

    /**
     * Adds the newly introduced clan-chat block to pre-existing installations without rewriting
     * administrator-owned configuration values or discarding forward-compatible unknown keys.
     */
    private fun appendMissingClanChatSection() {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.exists()) return

        val existingContent = file.readText()
        if (Regex("(?m)^clanChat\\s*:").containsMatchIn(existingContent)) return

        val serializedSection = yaml.encodeToString(ClanChatConfig.serializer(), ClanChatConfig())
            .lineSequence()
            .joinToString("\n") { "  $it" }
        val sectionComment = "# Настройки кланового чата: COMMAND использует /<command> <сообщение>, PREFIX — начало сообщения с prefix."
        file.appendText("\n$sectionComment\nclanChat:\n$serializedSection\n")
    }
}

/** Named animation slots exposed through [ConfigService.animationFrames]. */
object AnimationKey {
    const val HIDDEN_BALANCE = "hiddenBalance"
    const val UPGRADE_IDLE = "upgradeIdle"
    const val UPGRADE_READY = "upgradeReady"
    const val UPGRADE_BUSY = "upgradeBusy"
}
