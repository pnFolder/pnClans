package ua.inventorytype.pnclans.impl.storage

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Utility for serializing and deserializing arrays of Bukkit [ItemStack] objects into Base64 strings.
 * Preserves complete NBT tags, lore, display names, enchants, and Spigot/Paper item meta attributes.
 */
object ItemStackSerializer {
    private const val MAX_SLOTS = 54
    private const val MAX_SERIALIZED_BYTES = 8 * 1024 * 1024

    /**
     * Serializes an array of [ItemStack] objects into a Base64 encoded string.
     *
     * @param items The array of items to serialize.
     * @return Base64 encoded string representation.
     */
    @Suppress("DEPRECATION")
    fun toBase64(items: Array<ItemStack?>): String {
        require(items.size <= MAX_SLOTS) { "A clan chest cannot contain more than $MAX_SLOTS slots" }
        if (items.all { it == null }) return ""
        return runCatching {
            val outputStream = ByteArrayOutputStream()
            BukkitObjectOutputStream(outputStream).use { dataOutput ->
                dataOutput.writeInt(items.size)
                for (item in items) {
                    dataOutput.writeObject(item)
                }
            }
            Base64.getEncoder().encodeToString(outputStream.toByteArray())
        }.getOrElse { throw IllegalStateException("Unable to serialize item storage", it) }
    }

    /**
     * Deserializes a Base64 encoded string back into an array of [ItemStack] objects.
     *
     * @param data The Base64 string to decode.
     * @return 54-element array of deserialized [ItemStack] objects.
     */
    @Suppress("DEPRECATION")
    fun fromBase64(data: String): Array<ItemStack?> {
        if (data.isEmpty()) return arrayOfNulls(54)
        return runCatching {
            val decoded = Base64.getDecoder().decode(data)
            require(decoded.size <= MAX_SERIALIZED_BYTES) { "Serialized item storage is too large" }
            val inputStream = ByteArrayInputStream(decoded)
            BukkitObjectInputStream(inputStream).use { dataInput ->
                val size = dataInput.readInt()
                require(size in 0..MAX_SLOTS) { "Invalid item storage size: $size" }
                val items = arrayOfNulls<ItemStack>(size)
                for (i in 0 until size) {
                    items[i] = dataInput.readObject() as? ItemStack
                }
                items
            }
        }.getOrElse { throw IllegalStateException("Unable to deserialize item storage", it) }
    }
}
