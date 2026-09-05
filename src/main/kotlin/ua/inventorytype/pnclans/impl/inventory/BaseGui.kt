package ua.inventorytype.pnclans.impl.inventory

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import ua.inventorytype.pnclans.api.ActionContext
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiMenuConfig
import ua.inventorytype.pnclans.impl.config.GuiBackgroundConfig
import ua.inventorytype.pnclans.impl.inventory.annotation.GuiDsl
import ua.inventorytype.pnclans.impl.inventory.builder.SlotBuilder
import ua.inventorytype.pnclans.impl.util.ColorUtil

/**
 * Base abstract GUI class supporting fluent DSL layout, YAML config deserialization,
 * dynamic updates, and explicitly configured decoration.
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

    }

    fun border(material: Material) {
        val size = if (type == InventoryType.CHEST) rows.coerceIn(1, 6) * 9 else type.defaultSize
        for (index in 0 until size) {
            val isBorder = if (type == InventoryType.CHEST) {
                index < 9 || index >= size - 9 || index % 9 == 0 || index % 9 == 8
            } else {
                index == 0 || index == size - 1
            }
            if (isBorder) decorativeSlot(index, material)
        }
    }

    /** Adds the configured background to this GUI. Functional slots declared later override it. */
    fun background(config: GuiBackgroundConfig) {
        if (!config.enabled) return
        val primary = parseMaterial(config.primaryMaterial, Material.BLACK_STAINED_GLASS_PANE)
        val secondary = parseMaterial(config.secondaryMaterial, Material.ORANGE_STAINED_GLASS_PANE)
        config.primarySlots.forEach { decorativeSlot(it, primary) }
        config.secondarySlots.forEach { decorativeSlot(it, secondary) }
    }

    private fun decorativeSlot(index: Int, material: Material) {
        val size = if (type == InventoryType.CHEST) rows.coerceIn(1, 6) * 9 else type.defaultSize
        if (index !in 0 until size) return
        slot(index) { item(material) { name(" ") } }
    }

    private fun parseMaterial(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

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
        guiConfig.items.forEach { (key, itemConfig) ->
            if (itemConfig.slot in 0 until (rows * 9)) {
                slot(itemConfig.slot) {
                    dynamicItemNullable(runCatching { Material.valueOf(itemConfig.material.uppercase()) }.getOrDefault(Material.STONE)) { player ->
                        if (!itemConfig.permission.isNullOrBlank() && !player.hasPermission(itemConfig.permission)) {
                            return@dynamicItemNullable null
                        }
                        val service = this@BaseGui.clanService
                        val formattedName = service.plugin.configService.formatMessage(player, itemConfig.name)
                        val formattedLore = itemConfig.lore.map { line ->
                            service.plugin.configService.formatMessage(player, line)
                        }

                        name(formattedName)
                        lore(formattedLore)
                        glow(itemConfig.glow)
                        build()
                    }
                    onClick { player, event ->
                        if (!itemConfig.permission.isNullOrBlank() && !player.hasPermission(itemConfig.permission)) {
                            event.isCancelled = true
                            return@onClick
                        }
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

    open fun handleClose(e: InventoryCloseEvent) {
        closeAction?.invoke(e)
    }

    open fun handleClick(e: InventoryClickEvent) {
        e.isCancelled = true
        val player = e.whoClicked as? Player ?: return
        if (e.rawSlot !in 0 until inventory.size) return
        slots[e.rawSlot]?.executeClick(player, e)
    }

    open fun handleDrag(e: InventoryDragEvent) {
        if (e.rawSlots.any { it in 0 until inventory.size }) {
            e.isCancelled = true
        }
    }
}

interface HolderGui : InventoryHolder
