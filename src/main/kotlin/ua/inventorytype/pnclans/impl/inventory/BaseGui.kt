package ua.inventorytype.pnclans.impl.inventory

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.annotation.GuiDsl
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import ua.inventorytype.pnclans.impl.inventory.builder.SlotBuilder
import ua.inventorytype.pnclans.impl.util.ColorUtil

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

    // Bukkit Inventory создаётся с итоговыми параметрами title/rows/type
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

    /** Настройка действия при закрытии */
    fun onClose(block: (InventoryCloseEvent) -> Unit) {
        this.closeAction = block
    }

    /** Регистрация слота */
    fun slot(index: Int, block: SlotBuilder.() -> Unit) {
        val slotBuilder = SlotBuilder().apply(block)
        slots[index] = slotBuilder
    }

    /** Мгновенно обновляет только один конкретный слот */
    fun updateSlot(index: Int, player: Player) {
        val slotBuilder = slots[index] ?: return
        val item = slotBuilder.buildItemFor(player)

        if (item != null) {
            inventory.setItem(index, item)
        }
    }

    /** Задать материал рамки */
    fun border(material: Material) {
        this.borderMaterial = material
    }

    /** Открыть инвентарь игроку и отрисовать все слоты */
    fun open(player: Player) {
        // 1. Отрисовываем зарегистированные слоты
        slots.forEach { (index, slotBuilder) ->
            val item = slotBuilder.buildItemFor(player)
            if (item != null) {
                inventory.setItem(index, item)
            }
        }

        // 2. Если задана рамка — заполняем пустые слоты
        borderMaterial?.let { applyBorder(it) }

        player.openInventory(inventory)
    }

    /** Заполнение границы по краям */
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

    /** Обработка закрытия для GuiListener */
    open fun handleClose(e: InventoryCloseEvent) {
        closeAction?.invoke(e)
    }

    /** Обработка клика для GuiListener */
    open fun handleClick(e: InventoryClickEvent) {
        e.isCancelled = true
        val player = e.whoClicked as? Player ?: return

        //TODO
        // if (clanService.getClanUser(player) == null) {}
        // Возможно, надо будет здесь проверять.
        // at ua.inventorytype.pnclans.impl.inventory.listener.GuiListener.init<>(GuiListener.kt:21)
        // GuiListener Ну это реализовано здесь и оно должно закрыть. Клики здесь не пойдут, но эта пометка так мне, строка.

        slots[e.slot]?.executeClick(player, e)
    }
}

interface HolderGui : InventoryHolder