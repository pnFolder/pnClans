package ua.inventorytype.pnclans.api.clan

/**
 * Visual style used to highlight clan teammates.
 *
 * - [ARMOR]: per-viewer virtual dyed leather armor (server inventory is never changed).
 * - [GLOW]: per-viewer team-colored glow outline.
 */
enum class ClanHighlightType(
    val key: String,
    val displayName: String
) {
    ARMOR("armor", "Броня"),
    GLOW("glow", "Подсветка");

    companion object {
        fun fromKey(key: String): ClanHighlightType? =
            entries.find { it.key.equals(key, true) || it.name.equals(key, true) }
    }
}
