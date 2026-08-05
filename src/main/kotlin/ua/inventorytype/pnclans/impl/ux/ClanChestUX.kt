package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/**
 * Virtual clan chest GUI providing shared item storage across all clan members.
 *
 * Slot availability scales with clan level (9 slots per level, up to 45 unlocked slots).
 * Locked slots display a styled red glass pane with unlock level requirements.
 * Items are persisted to the storage backend automatically on inventory close and on
 * manual navigation via the back/close buttons.
 *
 * The navigation bar (slots 45–53) contains analytics, a return button, the storage core,
 * a shortcut to [UpgradeUX], and a close button.
 *
 * @param clanService The clan service providing chest persistence and plugin access.
 * @param clan The owning clan whose chest contents are displayed and managed.
 */
class ClanChestUX(
    clanService: ClanService,
    val clan: Clan
) : BaseGui(clanService) {

    val unlockedSlotsCount: Int = when (clan.level) {
        1 -> 9
        2 -> 18
        3 -> 27
        4 -> 36
        else -> 45
    }

    private val controlSlots = setOf(45, 46, 47, 48, 49, 50, 51, 52, 53)

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.chestMenu

        title(menuCfg.title)
        rows(menuCfg.rows)

        // Load persisted storage items before rendering control slots.
        val savedItems = clanService.getChestItems(clan.id)
        for (slotIndex in 0 until unlockedSlotsCount) {
            val item = savedItems.getOrNull(slotIndex)
            if (item != null && item.type != Material.AIR) {
                inventory.setItem(slotIndex, item.clone())
            }
        }

        // Render locked storage slots that are unlocked by future clan levels.
        for (slotIndex in unlockedSlotsCount until 45) {
            val requiredLevel = when {
                slotIndex < 18 -> 2
                slotIndex < 27 -> 3
                slotIndex < 36 -> 4
                else -> 5
            }

            slot(slotIndex) {
                item(Material.RED_STAINED_GLASS_PANE) {
                    name("&#FF3B3B🔒 СЛОТ ЗАБЛОКИРОВАН")
                    lore(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fСтатус: &#FC3737Закрыт для хранения",
                        " &7- &fТребуется уровень клана: &e$requiredLevel лвл.",
                        "",
                        "&#FC65DF «Как разблокировать?»",
                        " &7- &fКаждый уровень клана открывает",
                        " &7- &fдополнительно &e9 новых слотов&f!",
                        "",
                        "&#FF8702➥ &fНажмите &eЭволюция Клана &fдля прокачки!"
                    )
                }
                onClick { player, event ->
                    event.isCancelled = true
                    val cfg = this@ClanChestUX.clanService.plugin.configService
                    cfg.send(player, cfg.messages.chest.slotLocked, mapOf("level" to requiredLevel.toString()))
                }
            }
        }

        val controlDecor = listOf(46, 47, 51, 52)
        for (i in controlDecor) {
            slot(i) { item(Material.BLACK_STAINED_GLASS_PANE) { name(" ") } }
        }

        menuCfg.items["stats"]?.let { itemCfg -> slot(itemCfg.slot) {
            dynamicItem(Material.KNOWLEDGE_BOOK) { _ ->
                val maxSlots = this@ClanChestUX.unlockedSlotsCount
                val itemsStored = (0 until maxSlots).count { slotIdx ->
                    val item = this@ClanChestUX.inventory.getItem(slotIdx)
                    item != null && item.type != Material.AIR
                }
                val percent = if (maxSlots > 0) (itemsStored * 100) / maxSlots else 0
                val progressBar = this@ClanChestUX.buildProgressBar(percent)
                val bankBal = this@ClanChestUX.clan.bankBalance
                val placeholders = mapOf(
                    "stored" to itemsStored.toString(),
                    "slots" to maxSlots.toString(),
                    "percent" to percent.toString(),
                    "progress" to progressBar,
                    "balance" to bankBal.toString(),
                    "level" to this@ClanChestUX.clan.level.toString(),
                    "rows" to (maxSlots / 9).toString()
                )

                this@ClanChestUX.renderConfigItem(this, itemCfg, placeholders)
                null
            }
            onClick { _, event -> event.isCancelled = true }
        } }

        menuCfg.items["back"]?.let { itemCfg -> slot(itemCfg.slot) {
            dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) {
                this@ClanChestUX.renderConfigItem(this, itemCfg, emptyMap())
                null
            }
            onClick { player, _ ->
                this@ClanChestUX.saveChestContents()
                MainUX(this@ClanChestUX.clanService).open(player)
            }
        } }

        menuCfg.items["core"]?.let { itemCfg -> slot(itemCfg.slot) {
            dynamicItem(Material.BEACON) { _ ->
                val lvl = this@ClanChestUX.clan.level
                val count = this@ClanChestUX.unlockedSlotsCount
                this@ClanChestUX.renderConfigItem(
                    this,
                    itemCfg,
                    mapOf("level" to lvl.toString(), "rows" to (count / 9).toString(), "slots" to count.toString())
                )
                null
            }
            onClick { _, event -> event.isCancelled = true }
        } }

        menuCfg.items["upgrade"]?.let { itemCfg -> slot(itemCfg.slot) {
            dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.NETHER_STAR)) {
                this@ClanChestUX.renderConfigItem(
                    this,
                    itemCfg,
                    mapOf("level" to this@ClanChestUX.clan.level.toString(), "slots" to this@ClanChestUX.unlockedSlotsCount.toString())
                )
                null
            }
            onClick { player, _ ->
                this@ClanChestUX.saveChestContents()
                UpgradeUX(this@ClanChestUX.clanService).open(player)
            }
        } }

        menuCfg.items["close"]?.let { itemCfg -> slot(itemCfg.slot) {
            dynamicItem(this@ClanChestUX.parseMaterial(itemCfg.material, Material.RED_DYE)) {
                this@ClanChestUX.renderConfigItem(this, itemCfg, emptyMap())
                null
            }
            onClick { player, _ ->
                this@ClanChestUX.saveChestContents()
                player.closeInventory()
            }
        } }
    }

    override fun open(player: Player) {
        updateControlSlots(player)
        player.openInventory(inventory)
    }

    private fun updateControlSlots(player: Player) {
        controlSlots.forEach { index ->
            updateSlot(index, player)
        }
        for (slotIndex in unlockedSlotsCount until 45) {
            updateSlot(slotIndex, player)
        }
    }

    override fun handleClick(e: InventoryClickEvent) {
        val rawSlot = e.rawSlot
        val topSize = inventory.size // 54
        val player = e.whoClicked as? Player

        if (rawSlot in 0 until topSize) {
            // Click is inside top inventory (Clan Chest)
            if (rawSlot in controlSlots || rawSlot >= unlockedSlotsCount) {
                e.isCancelled = true
                super.handleClick(e)
            } else {
                // Unlocked storage slot -> allow placing, taking, moving items freely
                e.isCancelled = false
                scheduleStatsUpdate(player)
            }
        } else {
            // Click is in player inventory (bottom inventory)
            if (e.isShiftClick) {
                val clickedItem = e.currentItem
                if (clickedItem != null && clickedItem.type != Material.AIR) {
                    val hasUnlockedSpace = (0 until unlockedSlotsCount).any { idx ->
                        val existing = inventory.getItem(idx)
                        existing == null || existing.type == Material.AIR || (existing.isSimilar(clickedItem) && existing.amount < existing.maxStackSize)
                    }
                    if (!hasUnlockedSpace) {
                        e.isCancelled = true
                        return
                    }
                }
                scheduleStatsUpdate(player)
            } else {
                scheduleStatsUpdate(player)
            }
            // Allow player to move items in their hand / player inventory freely
            e.isCancelled = false
        }
    }

    private fun scheduleStatsUpdate(player: Player?) {
        if (player == null) return
        clanService.plugin.server.scheduler.runTaskLater(clanService.plugin, Runnable {
            val statsSlot = clanService.plugin.configService.menus.chestMenu.items["stats"]?.slot ?: 45
            inventory.viewers.filterIsInstance<Player>().forEach { viewer ->
                if (viewer.isOnline && viewer.openInventory.topInventory.holder == this) {
                    updateSlot(statsSlot, viewer)
                }
            }
        }, 1L)
    }

    override fun handleClose(e: InventoryCloseEvent) {
        saveChestContents()
        if (inventory.viewers.size <= 1) {
            clanService.closeClanChest(clan.id)
        }
        super.handleClose(e)
    }

    fun saveChestContents() {
        val items = arrayOfNulls<ItemStack>(54)
        for (i in 0 until unlockedSlotsCount) {
            val item = inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                items[i] = item.clone()
            }
        }
        clanService.saveChestItems(clan.id, items)
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
}
