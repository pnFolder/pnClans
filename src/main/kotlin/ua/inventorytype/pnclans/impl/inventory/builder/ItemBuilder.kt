package ua.inventorytype.pnclans.impl.inventory.builder

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
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
            val previousMeta = itemMeta
            val replacement = ItemStack(value)
            itemMeta = previousMeta?.let { Bukkit.getItemFactory().asMetaFor(it, replacement) } ?: replacement.itemMeta
            itemStack = replacement
        }

    private var itemStack = ItemStack(initialMaterial)
    private var itemMeta = itemStack.itemMeta

    fun type(material: Material) {
        this.type = material
    }

    fun amount(value: Int) {
        itemStack.amount = value.coerceIn(1, itemStack.maxStackSize)
    }

    @Suppress("DEPRECATION")
    fun name(displayName: String) {
        if (displayName.isEmpty()) return
        itemMeta?.setDisplayName(ColorUtil.color(displayName))
    }

    @Suppress("DEPRECATION")
    fun lore(vararg lines: String) {
        if (lines.isEmpty()) return
        itemMeta?.lore = lines.map { ColorUtil.color(it) }
    }

    @Suppress("DEPRECATION")
    fun lore(lines: List<String>) {
        if (lines.isEmpty()) return
        itemMeta?.lore = lines.map { ColorUtil.color(it) }
    }

    fun glow(enabled: Boolean = true) {
        if (enabled) {
            val unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking")) ?: return
            itemMeta?.addEnchant(unbreaking, 1, true)
            itemMeta?.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
    }

    fun hideAttributes(enabled: Boolean = true) {
        if (enabled) {
            itemMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        }
    }

    fun build(): ItemStack {
        itemMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
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

    /**
     * Dynamic item provider that may intentionally leave a slot empty.
     *
     * Unlike [dynamicItem], a `null` result is preserved instead of being replaced with the
     * initial material. Use [ItemBuilder.build] explicitly for visible items.
     */
    fun dynamicItemNullable(material: Material, block: ItemBuilder.(Player) -> ItemStack?) {
        this.itemProvider = { player ->
            val builder = ItemBuilder(material)
            builder.block(player)
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
