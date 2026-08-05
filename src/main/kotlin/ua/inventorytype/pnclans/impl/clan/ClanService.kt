package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.event.ClanCreatedEvent
import ua.inventorytype.pnclans.api.event.ClanDisbandedEvent
import ua.inventorytype.pnclans.api.event.ClanSavedEvent
import ua.inventorytype.pnclans.impl.config.ConfigService
import ua.inventorytype.pnclans.impl.economy.EconomyService
import ua.inventorytype.pnclans.impl.storage.ClanStorage
import ua.inventorytype.pnclans.impl.storage.IClanStorage
import ua.inventorytype.pnclans.impl.storage.SQLiteClanStorage
import ua.inventorytype.pnclans.impl.ux.ClanChestUX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Central service managing all clan lifecycle operations, member data, and persistence.
 *
 * Maintains an in-memory [ConcurrentHashMap] of all active [Clan] instances loaded from the
 * storage backend on startup. All write operations are immediately persisted to the [IClanStorage].
 *
 * Supports hot-reloading via [loadClans] and broadcasts update events to registered listeners
 * via [subscribe] / [notifyClanUpdated].
 *
 * @param plugin The owning [BukkitPlugin] instance providing config, economy, and logger access.
 * @param configService The configuration service (defaults to [plugin.configService]).
 * @param economy The economy service used for financial operations (defaults to [plugin.economyService]).
 */
class ClanService(
    val plugin: BukkitPlugin,
    val configService: ConfigService = plugin.configService,
    val economy: EconomyService = plugin.economyService,
) {

    private val _clans = ConcurrentHashMap.newKeySet<Clan>()

    /** Tracks online playtime for each member while they remain in a clan. */
    internal val playtimeTracker: PlaytimeTracker = PlaytimeTracker()

    /** Registered listeners notified on clan state updates (e.g. member join/leave, level change). */
    private val clanUpdateListeners = mutableListOf<(playerUuid: UUID) -> Unit>()

    /**
     * The storage backend (SQLite or YAML) selected based on [ConfigService.settings.storageType].
     * Defaults to [SQLiteClanStorage] when the storage type is `"SQLITE"` (case-insensitive).
     */
    val storage: IClanStorage = if (configService.settings.storageType.equals("SQLITE", ignoreCase = true)) {
        SQLiteClanStorage(plugin)
    } else {
        ClanStorage(plugin)
    }

    init {
        loadClans()
    }

    /**
     * Clears the in-memory clan cache and reloads all clans from the storage backend.
     * Called automatically during [init] and can be triggered for hot-reloads.
     */
    fun loadClans() {
        _clans.clear()
        _clans.addAll(storage.loadAllClans())
        playtimeTracker.clear()
        plugin.logger.info("Загружено кланов из хранилища (${storage::class.simpleName}): ${_clans.size}")
    }

    /**
     * Persists all currently loaded clans to the storage backend.
     * Should be called during plugin shutdown to flush any unsaved changes.
     */
    fun saveAll() {
        playtimeTracker.flushAll(this)
        _clans.forEach { storage.saveClan(it) }
    }

    /**
     * Registers a listener callback that is invoked whenever a specific player's clan state changes.
     *
     * @param onUpdate Callback receiving the UUID of the affected player.
     */
    fun subscribe(onUpdate: (playerUuid: UUID) -> Unit) {
        clanUpdateListeners.add(onUpdate)
    }

    /**
     * Notifies all registered listeners that the clan state of the specified player has changed.
     *
     * @param playerUuid The UUID of the player whose clan data was updated.
     */
    fun notifyClanUpdated(playerUuid: UUID) {
        clanUpdateListeners.forEach { listener -> listener.invoke(playerUuid) }
    }

    /**
     * Notifies all registered listeners that the clan was disbanded, iterating over each member UUID.
     *
     * @param memberUuids The UUIDs of all members who were part of the disbanded clan.
     */
    fun notifyClanDisbanded(memberUuids: List<UUID>) {
        memberUuids.forEach { notifyClanUpdated(it) }
    }

    /**
     * Returns a snapshot list of all currently loaded clans.
     *
     * @return An immutable list of all active [Clan] instances.
     */
    fun getAllClans(): List<Clan> = _clans.toList()

    /**
     * Finds a clan by its display name or ID (case-insensitive).
     *
     * @param name The clan name or ID to search for.
     * @return The matching [Clan], or null if not found.
     */
    fun getClanByName(name: String): Clan? =
        _clans.find { it.name.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true) }

    /**
     * Finds the clan that the given [Player] belongs to.
     *
     * @param player The Bukkit player to look up.
     * @return The player's [Clan], or null if they are not in any clan.
     */
    fun getClanUser(player: Player): Clan? =
        _clans.find { clan -> clan.users.any { user -> user.uuid == player.uniqueId } }

    /**
     * Finds the clan that a player with the given UUID belongs to.
     *
     * @param uuid The UUID of the player to look up.
     * @return The player's [Clan], or null if they are not in any clan.
     */
    fun getClanByUuid(uuid: UUID): Clan? =
        _clans.find { clan -> clan.users.any { user -> user.uuid == uuid } }

    /**
     * Creates a new clan with the given name, assigning [leader] as the LEADER.
     *
     * Validates the name for length, allowed characters, uniqueness, and ensures the leader
     * is not already in another clan. Deducts the creation cost from the leader's economy balance
     * if [ConfigService.settings.createClanCost] is greater than zero.
     *
     * @param name The desired clan name (2–16 characters, letters/digits/underscore only).
     * @param leader The player who will become the clan leader.
     * @return The newly created [Clan], or null if validation fails.
     */
    fun createClan(name: String, leader: Player): Clan? {
        val cfg = configService
        val cleanName = name.replace("&", "").trim()
        if (cleanName.length < 2) {
            cfg.send(leader, cfg.messages.clan.nameTooShort)
            return null
        }
        if (cleanName.length > 16) {
            cfg.send(leader, cfg.messages.clan.nameTooLong)
            return null
        }

        val nameRegex = Regex("^[a-zA-Z0-9_а-яА-Я]+$")
        if (!nameRegex.matches(cleanName)) {
            cfg.send(leader, cfg.messages.clan.nameInvalidChars)
            return null
        }

        if (getClanByName(cleanName) != null) {
            cfg.send(leader, cfg.messages.clan.nameAlreadyExists)
            return null
        }

        if (getClanUser(leader) != null) {
            cfg.send(leader, cfg.messages.clan.alreadyInClan)
            return null
        }

        val cost = configService.settings.createClanCost
        if (cost > 0 && !economy.has(leader, cost)) {
            cfg.send(leader, cfg.messages.clan.notEnoughMoney, mapOf("cost" to cost.toString()))
            return null
        }

        val leaderUser = ClanUser(leader.uniqueId, leader.name)
        val id = cleanName.lowercase()
        val clan = ClanImpl(id, cleanName, setOf(leaderUser to ClanRole.LEADER))
        val createEvent = ClanCreatedEvent(clan)
        Bukkit.getPluginManager().callEvent(createEvent)
        if (createEvent.isCancelled) return null
        if (cost > 0 && !economy.withdraw(leader, cost)) {
            cfg.send(leader, cfg.messages.clan.notEnoughMoney, mapOf("cost" to cost.toString()))
            return null
        }

        _clans.add(clan)
        saveClan(clan)
        configService.send(leader, configService.messages.clan.created, mapOf("clan" to cleanName))

        playtimeTracker.markOnline(leader.uniqueId, clan.id)
        return clan
    }

    /**
     * Checks whether a clan storage chest currently contains stored items.
     */
    fun hasChestItems(clan: Clan): Boolean {
        val chestItems = getChestItems(clan.id)
        return chestItems.any { it != null && it.type != Material.AIR }
    }

    /**
     * Permanently disbands a clan with safety validation:
     * - Blocks disbanding if items remain inside the clan chest storage.
     * - Automatically refunds any remaining treasury balance to the leader's account.
     * - Cleans up all storage entries and notifies former members.
     *
     * @param clan The [Clan] to disband.
     * @param leaderPlayer The leader player executing the disband action (for messages and refund).
     * @return Error message string if disbanding was blocked, or null if successful.
     */
    fun disbandClan(clan: Clan, leaderPlayer: Player? = null): String? {
        // 1. Safety check for stored chest items
        if (hasChestItems(clan)) {
            return "§c[pnClans] Ошибка: Нельзя распустить клан, пока в хранилище хранятся предметы! Заберите все вещи перед удалением."
        }

        // 2. Refund remaining treasury balance to leader
        if (clan.bankBalance > 0.0) {
            val refundAmount = clan.bankBalance
            val leaderUser = clan.users.find { clan.getUserRole(it) == ClanRole.LEADER }
            val leader = leaderPlayer ?: (leaderUser?.let { Bukkit.getPlayer(it.uuid) })

            if (leader != null) {
                economy.depositPlayer(leader, refundAmount)
                leader.sendMessage("§a[pnClans] Средства из казны (§e${refundAmount.toBigDecimal().stripTrailingZeros().toPlainString()} ⛁§a) переведены на ваш баланс!")
            }
        }

        val disbandEvent = ClanDisbandedEvent(clan)
        Bukkit.getPluginManager().callEvent(disbandEvent)
        if (disbandEvent.isCancelled) return "§c[pnClans] Распуск клана был отменён событием."

        val memberUuids = clan.users.map { it.uuid }
        _clans.remove(clan)
        storage.deleteClan(clan)
        notifyClanDisbanded(memberUuids)

        memberUuids.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { member ->
                configService.send(member, configService.messages.clan.disbanded, mapOf("clan" to clan.name))
            }
        }

        return null
    }

    /**
     * Opens the clan chest GUI ([ClanChestUX]) for the given player.
     *
     * @param player The player to open the chest for.
     * @param clan The clan whose chest contents to display.
     */
    fun openClanChest(player: Player, clan: Clan) {
        ClanChestUX(this, clan).open(player)
    }

    /**
     * Loads stored chest item contents for the given clan from the storage backend.
     *
     * @param clanId The clan ID whose chest to load.
     * @return An array of [ItemStack] slots (indices 0–53), with nulls for empty slots.
     */
    fun getChestItems(clanId: String): Array<ItemStack?> {
        return storage.loadChest(clanId)
    }

    /**
     * Persists the given chest item contents for the specified clan to the storage backend.
     *
     * @param clanId The clan ID whose chest to update.
     * @param items An array of [ItemStack] slots to save (indices 0–53).
     */
    fun saveChestItems(clanId: String, items: Array<ItemStack?>) {
        storage.saveChest(clanId, items)
    }

    /**
     * Persists a single [Clan] to the storage backend.
     * Should be called after any mutation to clan data (role changes, balance updates, etc.).
     *
     * @param clan The [Clan] instance to persist.
     */
    fun saveClan(clan: Clan) {
        storage.saveClan(clan)
        Bukkit.getPluginManager().callEvent(ClanSavedEvent(clan))
    }
}
