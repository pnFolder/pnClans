package ua.inventorytype.pnclans.impl.inventory

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import ua.inventorytype.pnclans.api.ActionContext
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiMenuConfig
import ua.inventorytype.pnclans.impl.inventory.annotation.GuiDsl
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import ua.inventorytype.pnclans.impl.inventory.builder.SlotBuilder
import ua.inventorytype.pnclans.impl.util.ColorUtil

/**
 * Base abstract GUI class supporting fluent DSL layout, YAML config deserialization,
 * dynamic updates, and signature HotWorld glass border decor.
 */
@GuiDsl
abstract class BaseGui(
    val clanService: ClanService
) : HolderGui {

    var title: String = "&8Инвентарь"
    var rows: Int = 3
    var type: InventoryType = InventoryType.CHEST

    private val slots = mutableMapOf<Int, SlotBuilder>()
    private var closeAction: ((InventoryCloseEvent) -> Unit)? = null
    private var borderMaterial: Material? = null
    private var hotWorldDecor: Boolean = false

    private val inv: Inventory by lazy {
        if (type == InventoryType.CHEST) {
            Bukkit.createInventory(this, rows.coerceIn(1, 6) * 9, ColorUtil.color(title))
        } else {
            Bukkit.createInventory(this, type, ColorUtil.color(title))
        }
    }

    override fun getInventory(): Inventory = inv

    fun title(value: String) {
        this.title = value
    }

    fun rows(value: Int) {
        this.rows = value
    }

    fun type(value: InventoryType) {
        this.type = value
    }

    fun onClose(block: (InventoryCloseEvent) -> Unit) {
        this.closeAction = block
    }

    fun slot(index: Int, block: SlotBuilder.() -> Unit) {
        val slotBuilder = SlotBuilder().apply(block)
        slots[index] = slotBuilder
    }

    fun updateSlot(index: Int, player: Player) {
        if (index !in 0 until inventory.size) return
        val slotBuilder = slots[index] ?: return
        val item = slotBuilder.buildItemFor(player)
        inventory.setItem(index, item)
    }

    /** Rebuilds only the requested cells, keeping the currently opened inventory intact. */
    fun updateSlots(indices: Iterable<Int>, player: Player) {
        indices.distinct().filter { it in 0 until inventory.size }.forEach { index ->
            inventory.setItem(index, slots[index]?.buildItemFor(player))
        }

        if (hotWorldDecor) {
            applyHotWorldDecor()
        } else {
            borderMaterial?.let { applyBorder(it) }
        }
    }

    fun update(player: Player) {
        inventory.clear()

        slots.forEach { (index, slotBuilder) ->
            if (index !in 0 until inventory.size) return@forEach
            val item = slotBuilder.buildItemFor(player)
            if (item != null) {
                inventory.setItem(index, item)
            }
        }

        if (hotWorldDecor) {
            applyHotWorldDecor()
        } else {
            borderMaterial?.let { applyBorder(it) }
        }
    }

    fun border(material: Material) {
        this.borderMaterial = material
    }

    /** Enables signature Orange & Black glass pattern decor */
    fun hotWorldDecor(enabled: Boolean = true) {
        this.hotWorldDecor = enabled
    }

    /**
     * Digitizes and populates GUI title, rows, item slots, materials, names, lores, glow, and actions
     * directly from a deserialized [GuiMenuConfig] object (`menus.yml`).
     */
    fun loadFromConfig(
        guiConfig: GuiMenuConfig,
        clickHandlers: Map<String, (Player, InventoryClickEvent) -> Unit> = emptyMap()
    ) {
        title(guiConfig.title)
        rows(guiConfig.rows)
        hotWorldDecor(true)

        guiConfig.items.forEach { (key, itemConfig) ->
            if (itemConfig.slot in 0 until (rows * 9)) {
                slot(itemConfig.slot) {
                    dynamicItem(runCatching { Material.valueOf(itemConfig.material.uppercase()) }.getOrDefault(Material.STONE)) { player ->
                        val service = this@BaseGui.clanService
                        val formattedName = service.plugin.configService.formatMessage(player, itemConfig.name)
                        val formattedLore = itemConfig.lore.map { line ->
                            service.plugin.configService.formatMessage(player, line)
                        }

                        name(formattedName)
                        lore(formattedLore)
                        glow(itemConfig.glow)
                        null
                    }
                    onClick { player, event ->
                        val service = this@BaseGui.clanService
                        val handler = clickHandlers[key]
                        handler?.invoke(player, event)

                        if (itemConfig.actions.isNotEmpty()) {
                            val context = ActionContext(
                                player = player,
                                placeholderRegistry = service.plugin.placeholderRegistry,
                                plugin = service.plugin
                            )
                            itemConfig.actions.forEach { action ->
                                action.execute(context)
                            }
                        }
                    }
                }
            }
        }
    }

    open fun open(player: Player) {
        update(player)
        player.openInventory(inventory)
    }

    private fun applyHotWorldDecor() {
        val blackItem = ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).apply { name(" ") }.build()
        val orangeItem = ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).apply { name(" ") }.build()

        val blackSlots = intArrayOf(0, 2, 3, 4, 5, 6, 8, 10, 16, 17, 18, 26, 27, 35, 37, 43, 45, 47, 48, 50, 51, 53)
        val orangeSlots = intArrayOf(1, 7, 9, 11, 12, 13, 14, 15, 19, 25, 28, 34, 36, 38, 39, 40, 41, 42, 44, 46, 52)

        for (s in blackSlots) {
            if (s < inventory.size && inventory.getItem(s) == null) inventory.setItem(s, blackItem)
        }
        for (s in orangeSlots) {
            if (s < inventory.size && inventory.getItem(s) == null) inventory.setItem(s, orangeItem)
        }
    }

    private fun applyBorder(material: Material) {
        val borderItem = ItemBuilder(material).apply { name(" ") }.build()
        val size = inventory.size

        for (i in 0 until size) {
            if (type == InventoryType.CHEST) {
                if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, borderItem)
                    }
                }
            } else {
                if (i == 0 || i == size - 1) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, borderItem)
                    }
                }
            }
        }
    }

    open fun handleClose(e: InventoryCloseEvent) {
        closeAction?.invoke(e)
    }

    open fun handleClick(e: InventoryClickEvent) {
        e.isCancelled = true
        val player = e.whoClicked as? Player ?: return
        if (e.rawSlot !in 0 until inventory.size) return
        slots[e.rawSlot]?.executeClick(player, e)
    }
}

interface HolderGui : InventoryHolder
