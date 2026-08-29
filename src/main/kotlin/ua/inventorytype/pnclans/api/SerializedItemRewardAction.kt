package ua.inventorytype.pnclans.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Material
import ua.inventorytype.pnclans.impl.storage.ItemStackSerializer

/**
 * Gives one or more exact Bukkit ItemStack snapshots, preserving custom item meta/NBT serialized by
 * [ItemStackSerializer]. Intended for in-game shop administration where `addhand` must reproduce
 * the exact held item rather than approximating it from material/name/lore/enchantments.
 *
 * YAML tag: `!serialized_item_reward`
 */
@Serializable
@SerialName("serialized_item_reward")
data class SerializedItemRewardAction(
    val itemStack: String,
    val copies: Int = 1
) : Action {
    override fun execute(context: ActionContext) {
        require(copies in 1..64) { "Serialized item reward copies must be between 1 and 64" }
        val templates = ItemStackSerializer.fromBase64(itemStack)
            .filterNotNull()
            .filter { it.type != Material.AIR }
        require(templates.isNotEmpty()) { "Serialized item reward is empty" }

        repeat(copies) {
            templates.forEach { template ->
                val stack = template.clone()
                context.player.inventory.addItem(stack).values.forEach { overflow ->
                    context.player.world.dropItemNaturally(context.player.location, overflow)
                }
            }
        }
    }
}
