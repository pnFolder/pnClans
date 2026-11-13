package ua.inventorytype.pnclans.impl.storage

import org.bukkit.inventory.ItemStack
import org.pnfolder.pnlibrary.bukkit.item.ItemService

/**
 * Хранение предметов клана (сундук, товары магазина, награды) через [ItemService] pnLibrary.
 *
 * Новый вид: `items:` и закодированные библиотекой предметы через запятую, пустой слот пустой строкой.
 * Данные, сохранённые старыми версиями pnClans, читаются через [LegacyItemArray] и при следующем сохранении
 * записываются уже в новом виде.
 */
object ClanItems {
    private const val PREFIX = "items:"
    private const val MAX_SLOTS = 54
    private lateinit var items: ItemService

    fun connect(service: ItemService) {
        items = service
    }

    fun encode(slots: Array<ItemStack?>): String {
        require(slots.size <= MAX_SLOTS) { "A clan chest cannot contain more than $MAX_SLOTS slots" }
        if (slots.all { it == null }) return ""
        return PREFIX + slots.joinToString(",") { item -> item?.takeIf(items::isReal)?.let(items::encode).orEmpty() }
    }

    fun encode(item: ItemStack): String = encode(arrayOf(item))

    fun decode(data: String): Array<ItemStack?> {
        if (data.isEmpty()) return arrayOfNulls(MAX_SLOTS)
        if (!data.startsWith(PREFIX)) return LegacyItemArray.decode(data)
        val parts = data.substring(PREFIX.length).split(',')
        require(parts.size <= MAX_SLOTS) { "Invalid item storage size: ${parts.size}" }
        return Array(parts.size) { index -> parts[index].takeIf(String::isNotEmpty)?.let(items::decode) }
    }

    fun decodeFirst(data: String): ItemStack? = decode(data).firstOrNull { it != null }
}
