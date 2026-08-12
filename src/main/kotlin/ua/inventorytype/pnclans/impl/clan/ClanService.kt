package ua.inventorytype.pnclans.impl.clan

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.event.ClanCreatedEvent
import ua.inventorytype.pnclans.api.event.ClanDisbandedEvent
import ua.inventorytype.pnclans.api.event.ClanSavedEvent
import ua.inventorytype.pnclans.api.event.ClanMemberJoinEvent
import ua.inventorytype.pnclans.api.event.ClanMemberLeaveEvent
import ua.inventorytype.pnclans.api.event.ClanMemberRoleChangeEvent
import ua.inventorytype.pnclans.api.event.ClanSettingChangeEvent
import ua.inventorytype.pnclans.api.event.ClanHomeSetEvent
import ua.inventorytype.pnclans.api.event.ClanHomeDeleteEvent
import ua.inventorytype.pnclans.api.event.ClanChestOpenEvent
import ua.inventorytype.pnclans.api.operation.ClanOperationResult
import ua.inventorytype.pnclans.api.operation.ClanOperationRejection
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

    /** O(1) player UUID → Clan lookup index, kept in sync with [_clans]. */
    private val _uuidToClan = ConcurrentHashMap<UUID, Clan>()

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
        val loaded = storage.loadAllClans()
        val loadedByUuid = HashMap<UUID, Clan>()
        loaded.forEach { clan -> clan.users.forEach { user -> loadedByUuid[user.uuid] = clan } }
        _clans.clear()
        _uuidToClan.clear()
        _clans.addAll(loaded)
        _uuidToClan.putAll(loadedByUuid)
        playtimeTracker.clear()
        Bukkit.getOnlinePlayers().forEach { player ->
            getClanByUuid(player.uniqueId)?.let { clan -> playtimeTracker.markOnline(player.uniqueId, clan.id) }
        }
        plugin.logger.info("Загружено кланов из хранилища (${storage::class.simpleName}): ${_clans.size}")
    }

    /**
     * Persists all currently loaded clans to the storage backend.
     * Should be called during plugin shutdown to flush any unsaved changes.
     */
    fun saveAll(finalizeSessions: Boolean = false): Boolean {
        if (finalizeSessions) {
            playtimeTracker.flushAll(this)
        } else {
            playtimeTracker.checkpointAll(this)
        }
        var saved = true
        _clans.forEach { clan -> if (!saveClan(clan)) saved = false }
        return saved
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
    fun getClanUser(player: Player): Clan? = _uuidToClan[player.uniqueId]

    /**
     * Finds the clan that a player with the given UUID belongs to.
     *
     * @param uuid The UUID of the player to look up.
     * @return The player's [Clan], or null if they are not in any clan.
     */
    fun getClanByUuid(uuid: UUID): Clan? = _uuidToClan[uuid]

    /** Finds a current clan member by their last known Minecraft name. */
    fun findMemberByName(playerName: String): Pair<Clan, ua.inventorytype.pnclans.api.User>? =
        _clans.asSequence()
            .mapNotNull { clan -> clan.users.firstOrNull { it.playerName.equals(playerName, ignoreCase = true) }?.let { clan to it } }
            .firstOrNull()

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

        if (!NAME_REGEX.matches(cleanName)) {
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
        clan.users.forEach { user -> _uuidToClan[user.uuid] = clan }
        if (!saveClan(clan)) {
            _clans.remove(clan)
            clan.users.forEach { user -> _uuidToClan.remove(user.uuid, clan) }
            if (cost > 0 && !economy.depositPlayer(leader, cost)) {
                plugin.logger.severe("[pnClans] Не удалось вернуть стоимость создания клана ${leader.name}: $cost.")
            }
            leader.sendMessage(configService.formatMessage(leader, "&#FC3737✖ &fСоздать клан не удалось из-за ошибки сохранения данных."))
            return null
        }
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
    fun disbandClan(clan: Clan, leaderPlayer: Player? = null, endActiveBattle: Boolean = false): String? {
        val settings = plugin.configService.settings

        if (plugin.clanBattleService.hasActiveBattle(clan) && !endActiveBattle) {
            return "§c[pnClans] Клан участвует в активной битве. Подтвердите роспуск через меню: бой будет завершён техническим поражением."
        }

        // 1. Safety check for stored chest items
        if (settings.disbandRequireEmptyChest && hasChestItems(clan)) {
            return "§c[pnClans] Ошибка: Нельзя распустить клан, пока в хранилище хранятся предметы! Заберите все вещи перед удалением."
        }

        val disbandEvent = ClanDisbandedEvent(clan)
        Bukkit.getPluginManager().callEvent(disbandEvent)
        if (disbandEvent.isCancelled) return "§c[pnClans] Распуск клана был отменён событием."

        // 2. Safety check for treasury bank balance
        if (clan.bankBalance > 0.0) {
            if (settings.disbandRequireEmptyBank) {
                val formattedBalance = clan.bankBalance.toBigDecimal().stripTrailingZeros().toPlainString()
                return "§c[pnClans] Ошибка: Нельзя распустить клан, пока в казне находятся средства ($formattedBalance ⛁)! Снимите все деньги из казны перед удалением."
            } else if (settings.disbandAutoRefundBank) {
                val refundAmount = clan.bankBalance
                val leaderUser = clan.users.find { clan.getUserRole(it) == ClanRole.LEADER }
                val leader = leaderPlayer ?: (leaderUser?.let { Bukkit.getPlayer(it.uuid) })

                if (leader == null || !economy.depositPlayer(leader, refundAmount)) {
                    return "§c[pnClans] Ошибка: не удалось вернуть средства из казны лидеру. Клан не был распущен."
                }
                leader.sendMessage("§a[pnClans] Средства из казны (§e${refundAmount.toBigDecimal().stripTrailingZeros().toPlainString()} ⛁§a) переведены на ваш баланс!")
            }
        }

        plugin.clanBattleService.prepareClanRemoval(clan)

        val memberUuids = clan.users.map { it.uuid }
        memberUuids.forEach(playtimeTracker::clearSession)
        memberUuids.forEach { _uuidToClan.remove(it) }
        _clans.remove(clan)
        invalidateClanChest(clan.id)
        storage.deleteClan(clan)
        notifyClanDisbanded(memberUuids)

        memberUuids.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { member ->
                configService.send(member, configService.messages.clan.disbanded, mapOf("clan" to clan.name))
            }
        }

        return null
    }

    private val activeChestGuis = ConcurrentHashMap<String, ClanChestUX>()

    /**
     * Opens the shared clan chest GUI ([ClanChestUX]) for the given player.
     *
     * If another member of the same clan has the chest open, reuses the active GUI instance
     * enabling real-time multiplayer item synchronization across all viewers.
     *
     * @param player The player to open the chest for.
     * @param clan The clan whose chest contents to display.
     */
    fun openClanChest(player: Player, clan: Clan): ClanOperationResult {
        val member = clan.getMember(player.uniqueId)
            ?: return ClanOperationResult.Rejected(ClanOperationRejection.NOT_A_CLAN_MEMBER)
        if (!clan.hasPermission(member, ClanPerms.Action.OPEN_CHEST)) {
            return ClanOperationResult.Rejected(ClanOperationRejection.NO_PERMISSION)
        }
        if (!clan.isSettingEnabled(ClanSetting.CHEST)) {
            return ClanOperationResult.Rejected(ClanOperationRejection.SETTING_DISABLED)
        }
        val event = ClanChestOpenEvent(clan, player)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return ClanOperationResult.Rejected(ClanOperationRejection.CANCELLED_BY_EVENT)
        val chestGui = activeChestGuis.computeIfAbsent(clan.id) {
            ClanChestUX(this, clan)
        }
        chestGui.open(player)
        return ClanOperationResult.Success
    }

    /**
     * Removes an active shared chest GUI session from memory when all viewers have closed it.
     */
    fun closeClanChest(clanId: String) {
        activeChestGuis.remove(clanId)
    }

    fun invalidateClanChest(clanId: String) {
        activeChestGuis.remove(clanId)?.invalidate()
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
     * Adds a user to a clan and keeps the [_uuidToClan] index in sync.
     *
     * @return True if the user was successfully added.
     */
    fun addUserToClan(clan: Clan, user: ua.inventorytype.pnclans.api.User, role: ua.inventorytype.pnclans.api.clan.ClanRole): Boolean {
        val event = ClanMemberJoinEvent(clan, user, role)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        val added = clan.addUser(user, role)
        if (added) {
            _uuidToClan[user.uuid] = clan
            if (!saveClan(clan)) {
                clan.removeUser(user.uuid)
                _uuidToClan.remove(user.uuid, clan)
                return false
            }
            Bukkit.getPlayer(user.uuid)?.takeIf(Player::isOnline)?.let { player ->
                playtimeTracker.markOnline(user.uuid, clan.id)
                plugin.clanQuestService.recordMemberJoined(clan, player)
            }
        }
        return added
    }

    /**
     * Removes a user from a clan and keeps the [_uuidToClan] index in sync.
     *
     * @return True if the user was removed.
     */
    fun removeUserFromClan(clan: Clan, uuid: UUID, kicked: Boolean = false): Boolean {
        val user = clan.getMember(uuid) ?: return false
        val role = clan.getUserRole(user)
        val event = ClanMemberLeaveEvent(clan, user, kicked)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        playtimeTracker.flushSession(uuid, this)
        val removed = clan.removeUser(uuid)
        if (removed) {
            if (!saveClan(clan)) {
                clan.addUser(user, role)
                Bukkit.getPlayer(uuid)?.takeIf(Player::isOnline)?.let { playtimeTracker.markOnline(uuid, clan.id) }
                return false
            }
            _uuidToClan.remove(uuid)
            plugin.clanHighlightService.removeMember(clan, uuid)
            plugin.clanBattleService.handleMemberRemoved(clan, uuid)
        } else {
            Bukkit.getPlayer(uuid)?.takeIf(Player::isOnline)?.let { playtimeTracker.markOnline(uuid, clan.id) }
        }
        return removed
    }

    /** Applies a role change only after add-ons approve the cancellable event. */
    fun changeMemberRole(clan: Clan, user: ua.inventorytype.pnclans.api.User, newRole: ClanRole): ClanOperationResult {
        if (clan.getMember(user.uuid) == null) {
            return ClanOperationResult.Rejected(ClanOperationRejection.MEMBER_NOT_FOUND)
        }
        val oldRole = clan.getUserRole(user)
        if (oldRole == newRole) return ClanOperationResult.Rejected(ClanOperationRejection.ALREADY_IN_REQUESTED_STATE)
        val event = ClanMemberRoleChangeEvent(clan, user, oldRole, newRole)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return ClanOperationResult.Rejected(ClanOperationRejection.CANCELLED_BY_EVENT)
        if (event.newRole == ClanRole.LEADER && clan.getLeader()?.uuid != user.uuid) {
            return ClanOperationResult.Rejected(ClanOperationRejection.INVALID_ROLE_TRANSITION)
        }
        if (!clan.setUserRole(user, event.newRole)) return ClanOperationResult.Rejected(ClanOperationRejection.MEMBER_NOT_FOUND)
        if (!saveClan(clan)) {
            clan.setUserRole(user, oldRole)
            return ClanOperationResult.Rejected(ClanOperationRejection.PERSISTENCE_FAILED)
        }
        return ClanOperationResult.Success
    }

    /** Transfers leadership atomically after both role changes have been approved. */
    fun transferLeadership(clan: Clan, currentLeader: ua.inventorytype.pnclans.api.User, newLeader: ua.inventorytype.pnclans.api.User): ClanOperationResult {
        if (currentLeader.uuid == newLeader.uuid || clan.getMember(currentLeader.uuid) == null || clan.getMember(newLeader.uuid) == null) {
            return ClanOperationResult.Rejected(ClanOperationRejection.MEMBER_NOT_FOUND)
        }
        if (clan.getUserRole(currentLeader) != ClanRole.LEADER || clan.getUserRole(newLeader) == ClanRole.LEADER) {
            return ClanOperationResult.Rejected(ClanOperationRejection.INVALID_ROLE_TRANSITION)
        }
        val previousNewLeaderRole = clan.getUserRole(newLeader)
        val demote = ClanMemberRoleChangeEvent(clan, currentLeader, clan.getUserRole(currentLeader), ClanRole.DEPUTY)
        val promote = ClanMemberRoleChangeEvent(clan, newLeader, previousNewLeaderRole, ClanRole.LEADER)
        Bukkit.getPluginManager().callEvent(demote)
        Bukkit.getPluginManager().callEvent(promote)
        if (demote.isCancelled || promote.isCancelled) return ClanOperationResult.Rejected(ClanOperationRejection.CANCELLED_BY_EVENT)
        if (demote.newRole != ClanRole.DEPUTY || promote.newRole != ClanRole.LEADER) return ClanOperationResult.Rejected(ClanOperationRejection.INVALID_ROLE_TRANSITION)
        if (!clan.setUserRole(currentLeader, ClanRole.DEPUTY)) return ClanOperationResult.Rejected(ClanOperationRejection.MEMBER_NOT_FOUND)
        if (!clan.setUserRole(newLeader, ClanRole.LEADER)) {
            clan.setUserRole(currentLeader, ClanRole.LEADER)
            return ClanOperationResult.Rejected(ClanOperationRejection.MEMBER_NOT_FOUND)
        }
        if (!saveClan(clan)) {
            clan.setUserRole(newLeader, previousNewLeaderRole)
            clan.setUserRole(currentLeader, ClanRole.LEADER)
            return ClanOperationResult.Rejected(ClanOperationRejection.PERSISTENCE_FAILED)
        }
        return ClanOperationResult.Success
    }

    /** Applies a setting value only after add-ons approve the cancellable event. */
    fun changeSetting(clan: Clan, setting: ClanSetting, newValue: Boolean): ClanOperationResult {
        val oldValue = clan.isSettingEnabled(setting)
        if (oldValue == newValue) return ClanOperationResult.Rejected(ClanOperationRejection.ALREADY_IN_REQUESTED_STATE)
        val event = ClanSettingChangeEvent(clan, setting, oldValue, newValue)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return ClanOperationResult.Rejected(ClanOperationRejection.CANCELLED_BY_EVENT)
        clan.setSetting(setting, event.newValue)
        if (!saveClan(clan)) {
            clan.setSetting(setting, oldValue)
            return ClanOperationResult.Rejected(ClanOperationRejection.PERSISTENCE_FAILED)
        }
        return ClanOperationResult.Success
    }

    fun setClanHome(clan: Clan, actor: Player, homeId: String, location: Location): ClanOperationResult {
        val normalizedHomeId = homeId.lowercase()
        val previousLocation = clan.homes[normalizedHomeId]
        val isNewHome = previousLocation == null
        val event = ClanHomeSetEvent(clan, actor, normalizedHomeId, location)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return ClanOperationResult.Rejected(ClanOperationRejection.CANCELLED_BY_EVENT)
        clan.setHome(normalizedHomeId, event.location)
        if (!saveClan(clan)) {
            if (previousLocation == null) clan.deleteHome(normalizedHomeId) else clan.setHome(normalizedHomeId, previousLocation)
            return ClanOperationResult.Rejected(ClanOperationRejection.PERSISTENCE_FAILED)
        }
        if (isNewHome) plugin.clanQuestService.recordHomeSet(clan, actor)
        return ClanOperationResult.Success
    }

    fun deleteClanHome(clan: Clan, actor: Player, homeId: String): ClanOperationResult {
        val normalizedHomeId = homeId.lowercase()
        val location = clan.homes[normalizedHomeId] ?: return ClanOperationResult.Rejected(ClanOperationRejection.HOME_NOT_FOUND)
        val event = ClanHomeDeleteEvent(clan, actor, normalizedHomeId, location)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return ClanOperationResult.Rejected(ClanOperationRejection.CANCELLED_BY_EVENT)
        if (!clan.deleteHome(normalizedHomeId)) return ClanOperationResult.Rejected(ClanOperationRejection.HOME_NOT_FOUND)
        if (!saveClan(clan)) {
            clan.setHome(normalizedHomeId, location)
            return ClanOperationResult.Rejected(ClanOperationRejection.PERSISTENCE_FAILED)
        }
        return ClanOperationResult.Success
    }

    /**
     * Persists a single [Clan] to the storage backend.
     * Should be called after any mutation to clan data (role changes, balance updates, etc.).
     *
     * @param clan The [Clan] instance to persist.
     */
    fun saveClan(clan: Clan): Boolean {
        return if (storage.saveClan(clan)) {
            Bukkit.getPluginManager().callEvent(ClanSavedEvent(clan))
            true
        } else {
            plugin.logger.warning("[pnClans] Клан «${clan.name}» не был сохранён; событие ClanSavedEvent не отправлено.")
            false
        }
    }

    private companion object {
        val NAME_REGEX = Regex("^[a-zA-Z0-9_а-яА-Я]+$")
    }
}
