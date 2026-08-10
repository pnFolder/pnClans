package ua.inventorytype.pnclans.impl.storage

import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.api.clan.Clan

/**
 * Universal storage abstraction supporting both JSON flat-file and SQLite relational storage backends.
 */
interface IClanStorage {

    /**
     * Loads all existing clan records from storage into memory.
     *
     * @return List of deserialized [Clan] instances.
     */
    fun loadAllClans(): List<Clan>

    /**
     * Persists the state of a single clan to storage.
     *
     * @param clan The clan instance to save.
     */
    fun saveClan(clan: Clan): Boolean

    /**
     * Permanently deletes a clan and its virtual chest data from storage.
     *
     * @param clan The clan instance to delete.
     */
    fun deleteClan(clan: Clan)

    /**
     * Saves the array of item stacks stored in a clan's virtual chest.
     *
     * @param clanId The unique string ID of the clan.
     * @param items The 54-element array of [ItemStack] objects.
     */
    fun saveChest(clanId: String, items: Array<ItemStack?>)

    /**
     * Loads the array of item stacks stored in a clan's virtual chest.
     *
     * @param clanId The unique string ID of the clan.
     * @return A 54-element array of deserialized [ItemStack] objects.
     */
    fun loadChest(clanId: String): Array<ItemStack?>

    /** Releases backend resources during plugin shutdown. */
    fun close() {}
}
