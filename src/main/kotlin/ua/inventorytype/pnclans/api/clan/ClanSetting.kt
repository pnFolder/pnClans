package ua.inventorytype.pnclans.api.clan

/**
 * Enumerates global toggleable operational settings for a clan.
 *
 * @property key The unique string key used for configuration and storage persistence.
 * @property defaultValue The default boolean state assigned to newly created clans.
 */
enum class ClanSetting(
    val key: String,
    val defaultValue: Boolean
) {
    /** Toggles friendly fire (PvP damage) between members of the same clan. */
    PVP("pvp", false),

    /** Toggles the availability of the private clan chat channel. */
    CHAT("chat", true),

    /** Toggles member access to the virtual clan chest storage. */
    CHEST("chest", true),

    /** Toggles chat notification broadcasts when members join or leave the server. */
    JOIN("join", true);

    companion object {
        /**
         * Resolves a [ClanSetting] enum instance from its string key.
         *
         * @param key The setting key to match (case-insensitive).
         * @return The matching [ClanSetting] instance, or null if not found.
         */
        fun fromKey(key: String): ClanSetting? = entries.find { it.key.equals(key, true) }
    }
}