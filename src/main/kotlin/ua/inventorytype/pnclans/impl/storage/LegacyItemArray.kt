package ua.inventorytype.pnclans.impl.storage

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import java.io.ByteArrayInputStream
import java.util.Base64

/** Читает предметы, сохранённые pnClans до перехода на pnLibrary. Записывает только [ClanItems]. */
internal object LegacyItemArray {
    private const val MAX_SLOTS = 54
    private const val MAX_SERIALIZED_BYTES = 8 * 1024 * 1024

    @Suppress("DEPRECATION")
    fun decode(data: String): Array<ItemStack?> = runCatching {
        val decoded = Base64.getDecoder().decode(data)
        require(decoded.size <= MAX_SERIALIZED_BYTES) { "Serialized item storage is too large" }
        BukkitObjectInputStream(ByteArrayInputStream(decoded)).use { input ->
            val size = input.readInt()
            require(size in 0..MAX_SLOTS) { "Invalid item storage size: $size" }
            Array(size) { input.readObject() as? ItemStack }
        }
    }.getOrElse { throw IllegalStateException("Unable to deserialize item storage", it) }
}
