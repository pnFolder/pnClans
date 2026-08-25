package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/**
 * Virtual clan chest GUI providing shared item storage across all clan members.
 *
 * Slot availability scales with clan level (9 slots per level, up to 45 unlocked slots).
 * Items are persisted to the storage backend automatically on inventory close and on
 * manual navigation via the back/close buttons.
 */
class ClanChestUX(
    clanService: ClanService,
    val clan: Clan
) : BaseGui(clanService) {
    private var invalidated = false

    val unlockedSlotsCount: Int = when (clan.level) {
        1 -> 9
        2 -> 18
        3 -> 27
        4 -> 36
        else -> 45
    }

    private val controlSlots: Set<Int>
        get() {
            val items = clanService.plugin.configService.menus.chestMenu.items
            val fixedKeys = setOf("stats", "back", "core", "upgrade", "close")
            return items
                .filter { (key, _) -> key in fixedKeys || key.startsWith("decor_") }
                .values
                .map(GuiItemConfig::slot)
                .filter { it in 0 until inventory.size }
                .toSet()
        }

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.chestMenu

        title(menuCfg.title)
        rows(menuCfg.rows)

        val savedItems = clanService.getChestItems(clan.id)
        for (slotIndex in 0 until unlockedSlotsCount) {
            val item = savedItems.getOrNull(slotIndex)
            if (item != null && item.type != Material.AIR) {
                inventory.setItem(slotIndex, item.clone())
            }
        }

        menuCfg.items["lockedSlot"]?.let { lockedCfg ->
            for (slotIndex in unlockedSlotsCount until MAX_STORAGE_SLOTS) {
                val requiredLevel = requiredLevelForSlot(slotIndex)
                slot(slotIndex) {
                    dynamicItem(this@ClanChestUX.parseMaterial(lockedCfg.material, Material.RED_STAINED_GLASS_PANE)) {
                        this@ClanChestUX.renderConfigItem(
                            this,
                            lockedCfg,
                            mapOf("level" to requiredLevel.toString(), "slot" to slotIndex.toString())
                        )
                        null
                    }
                    onClick { player, event ->
                        event.isCancelled = true
                        cfg.send(player, cfg.messages.chest.slotLocked, mapOf("level" to requiredLevel.toString()))
                    }
                }
            }
        }

        menuCfg.items
            .filterKeys { it.startsWith("decor_") }
            .values
            .forEach { itemCfg ->
                if (itemCfg.slot !in 0 until inventory.size) return@forEach
                slot(itemCfg.slot) {
                    dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.BLACK_STAINED_GLASS_PANE)) {
                        this@ClanChestUX.renderConfigItem(this, itemCfg, emptyMap())
                        null
                    }
                    onClick { _, event -> event.isCancelled = true }
                }
            }

        menuCfg.items["stats"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.KNOWLEDGE_BOOK)) { _ ->
                    val maxSlots = this@ClanChestUX.unlockedSlotsCount
                    val itemsStored = (0 until maxSlots).count { slotIdx ->
                        val item = this@ClanChestUX.inventory.getItem(slotIdx)
                        item != null && item.type != Material.AIR
                    }
                    val percent = if (maxSlots > 0) (itemsStored * 100) / maxSlots else 0
                    val placeholders = mapOf(
                        "stored" to itemsStored.toString(),
                        "slots" to maxSlots.toString(),
                        "percent" to percent.toString(),
                        "progress" to this@ClanChestUX.buildProgressBar(percent),
                        "balance" to this@ClanChestUX.clan.bankBalance.toString(),
                        "level" to this@ClanChestUX.clan.level.toString(),
                        "rows" to (maxSlots / 9).toString()
                    )
                    this@ClanChestUX.renderConfigItem(this, itemCfg, placeholders)
                    null
                }
                onClick { _, event -> event.isCancelled = true }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) {
                    this@ClanChestUX.renderConfigItem(this, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ ->
                    this@ClanChestUX.saveChestContents()
                    MainUX(this@ClanChestUX.clanService).open(player)
                }
            }
        }

        menuCfg.items["core"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.BEACON)) { _ ->
                    val count = this@ClanChestUX.unlockedSlotsCount
                    this@ClanChestUX.renderConfigItem(
                        this,
                        itemCfg,
                        mapOf(
                            "level" to this@ClanChestUX.clan.level.toString(),
                            "rows" to (count / 9).toString(),
                            "slots" to count.toString()
                        )
                    )
                    null
                }
                onClick { _, event -> event.isCancelled = true }
            }
        }

        menuCfg.items["upgrade"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.NETHER_STAR)) {
                    this@ClanChestUX.renderConfigItem(
                        this,
                        itemCfg,
                        mapOf(
                            "level" to this@ClanChestUX.clan.level.toString(),
                            "slots" to this@ClanChestUX.unlockedSlotsCount.toString()
                        )
                    )
                    null
                }
                onClick { player, _ ->
                    this@ClanChestUX.saveChestContents()
                    UpgradeUX(this@ClanChestUX.clanService).open(player)
                }
            }
        }

        menuCfg.items["close"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.RED_DYE)) {
                    this@ClanChestUX.renderConfigItem(this, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ ->
                    this@ClanChestUX.saveChestContents()
                    player.closeInventory()
                }
            }
        }
    }

    override fun open(player: Player) {
        updateControlSlots(player)
        player.openInventory(inventory)
    }

    private fun updateControlSlots(player: Player) {
        controlSlots.forEach { index -> updateSlot(index, player) }
        for (slotIndex in unlockedSlotsCount until MAX_STORAGE_SLOTS) {
            updateSlot(slotIndex, player)
        }
    }

    override fun handleClick(e: InventoryClickEvent) {
        if (e.isCancelled) return
        val rawSlot = e.rawSlot
        val topSize = inventory.size
        val player = e.whoClicked as? Player
        if (player == null || !canAccess(player)) {
            e.isCancelled = true
            return
        }

        if (rawSlot in 0 until topSize) {
            if (rawSlot in controlSlots || rawSlot in unlockedSlotsCount until MAX_STORAGE_SLOTS) {
                e.isCancelled = true
                super.handleClick(e)
            } else {
                scheduleStatsUpdate(player)
            }
        } else {
            if (e.isShiftClick) {
                val clickedItem = e.currentItem
                if (clickedItem != null && clickedItem.type != Material.AIR) {
                    val hasUnlockedSpace = (0 until unlockedSlotsCount).any { idx ->
                        val existing = inventory.getItem(idx)
                        existing == null || existing.type == Material.AIR ||
                            (existing.isSimilar(clickedItem) && existing.amount < existing.maxStackSize)
                    }
                    if (!hasUnlockedSpace) {
                        e.isCancelled = true
                        return
                    }
                }
            }
            scheduleStatsUpdate(player)
        }
    }

    private fun scheduleStatsUpdate(player: Player?) {
        if (player == null) return
        clanService.plugin.server.scheduler.runTaskLater(clanService.plugin, Runnable {
            val statsSlot = clanService.plugin.configService.menus.chestMenu.items["stats"]?.slot ?: return@Runnable
            inventory.viewers.filterIsInstance<Player>().forEach { viewer ->
                if (viewer.isOnline && viewer.openInventory.topInventory.holder == this) {
                    updateSlot(statsSlot, viewer)
                }
            }
        }, 1L)
    }

    override fun handleClose(e: InventoryCloseEvent) {
        val player = e.player as? Player
        if (!invalidated && player != null && canAccess(player)) saveChestContents()
        if (inventory.viewers.size <= 1) {
            clanService.closeClanChest(clan.id)
        }
        super.handleClose(e)
    }

    fun saveChestContents() {
        if (invalidated) return
        val items = arrayOfNulls<ItemStack>(inventory.size)
        for (i in 0 until unlockedSlotsCount) {
            if (i in controlSlots) continue
            val item = inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                items[i] = item.clone()
            }
        }
        clanService.saveChestItems(clan.id, items)
    }

    fun invalidate() {
        invalidated = true
        inventory.viewers.toList().forEach { it.closeInventory() }
    }

    override fun handleDrag(e: InventoryDragEvent) {
        if (e.isCancelled) return
        val player = e.whoClicked as? Player
        if (player == null || !canAccess(player)) {
            e.isCancelled = true
            return
        }
        if (e.rawSlots.any { it in controlSlots || it in unlockedSlotsCount until MAX_STORAGE_SLOTS }) {
            e.isCancelled = true
        }
    }

    private fun canAccess(player: Player): Boolean {
        val member = clan.getMember(player.uniqueId) ?: return false
        return clan.isSettingEnabled(ClanSetting.CHEST) && clan.hasPermission(member, ClanPerms.Action.OPEN_CHEST)
    }

    private fun requiredLevelForSlot(slot: Int): Int = when {
        slot < 18 -> 2
        slot < 27 -> 3
        slot < 36 -> 4
        else -> 5
    }

    private fun buildProgressBar(percent: Int): String {
        val filled = (percent / 10).coerceIn(0, 10)
        val empty = 10 - filled
        return "&a" + "■".repeat(filled) + "&7" + "□".repeat(empty)
    }

    private fun renderConfigItem(
        builder: ItemBuilder,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(format(itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { format(it, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun format(template: String, placeholders: Map<String, String>): String =
        placeholders.entries.fold(template) { result, (key, value) -> result.replace("{$key}", value) }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)

    private companion object {
        const val MAX_STORAGE_SLOTS = 45
    }
}
