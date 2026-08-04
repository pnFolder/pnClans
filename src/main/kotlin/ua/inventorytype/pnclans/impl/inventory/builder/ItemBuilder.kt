package ua.inventorytype.pnclans.impl.inventory.builder

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.impl.inventory.annotation.GuiDsl
import ua.inventorytype.pnclans.impl.util.ColorUtil

@GuiDsl
class ItemBuilder(material: Material) {
    private var item = ItemStack(material)
    private val meta = item.itemMeta

    fun type(material: Material) {
        item = ItemStack(material)
    }

    fun name(displayName: String) {
        meta?.setDisplayName(ColorUtil.color(displayName))
    }

    fun lore(vararg lines: String) {
        meta?.lore = lines.map { ColorUtil.color(it) }
    }

    fun build(): ItemStack {
        item.itemMeta = meta
        return item
    }
}

@GuiDsl
class SlotBuilder {
    private var itemStack: ItemStack? = null
    private var itemProvider: ((Player) -> ItemStack)? = null
    private var action: ((Player, InventoryClickEvent) -> Unit)? = null

    /** 1. ОБЫЧНЫЙ (Статический) предмет */
    fun item(material: Material, block: ItemBuilder.() -> Unit) {
        val builder = ItemBuilder(material)
        builder.block()
        this.itemStack = builder.build()
        this.itemProvider = null
    }

    /** 2. ДИНАМИЧЕСКИЙ предмет (получает игрока) */
    fun dynamicItem(material: Material, block: ItemBuilder.(Player) -> Unit) {
        this.itemProvider = { player ->
            val builder = ItemBuilder(material)
            builder.block(player)
            builder.build()
        }
    }

    fun buildItemFor(player: Player): ItemStack? {
        return itemProvider?.invoke(player) ?: itemStack
    }

    fun onClick(block: (player: Player, event: InventoryClickEvent) -> Unit) {
        this.action = block
    }

    fun executeClick(player: Player, event: InventoryClickEvent) {
        action?.invoke(player, event)
    }
}