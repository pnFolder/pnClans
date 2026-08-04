package ua.inventorytype.pnclans.impl.inventory.builder

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.impl.inventory.annotation.GuiDsl
import ua.inventorytype.pnclans.impl.util.ColorUtil

@GuiDsl
class ItemBuilder(initialMaterial: Material) {
    var type: Material = initialMaterial
        set(value) {
            field = value
            val newMeta = itemMeta
            itemStack = ItemStack(value)
            if (newMeta != null) {
                itemStack.itemMeta = newMeta
            }
        }

    private var itemStack = ItemStack(initialMaterial)
    private var itemMeta = itemStack.itemMeta

    fun type(material: Material) {
        this.type = material
    }

    fun name(displayName: String) {
        itemMeta?.setDisplayName(ColorUtil.color(displayName))
    }

    fun lore(vararg lines: String) {
        itemMeta?.lore = lines.map { ColorUtil.color(it) }
    }

    fun lore(lines: List<String>) {
        itemMeta?.lore = lines.map { ColorUtil.color(it) }
    }

    fun glow(enabled: Boolean = true) {
        if (enabled) {
            itemMeta?.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
            itemMeta?.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
    }

    fun hideAttributes(enabled: Boolean = true) {
        if (enabled) {
            itemMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        }
    }

    fun build(): ItemStack {
        itemStack.itemMeta = itemMeta
        return itemStack
    }
}

@GuiDsl
class SlotBuilder {
    private var itemStack: ItemStack? = null
    private var itemProvider: ((Player) -> ItemStack?)? = null
    private var action: ((Player, InventoryClickEvent) -> Unit)? = null

    /** 1. ОБЫЧНЫЙ (Статический) предмет */
    fun item(material: Material, block: ItemBuilder.() -> Unit) {
        val builder = ItemBuilder(material)
        builder.block()
        this.itemStack = builder.build()
        this.itemProvider = null
    }

    /** 2. ДИНАМИЧЕСКИЙ предмет (получает игрока) */
    fun dynamicItem(material: Material, block: ItemBuilder.(Player) -> ItemStack?) {
        this.itemProvider = { player ->
            val builder = ItemBuilder(material)
            val customItem = builder.block(player)
            customItem ?: builder.build()
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