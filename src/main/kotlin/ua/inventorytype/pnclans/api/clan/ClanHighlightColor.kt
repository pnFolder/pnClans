package ua.inventorytype.pnclans.api.clan

import org.bukkit.ChatColor

enum class ClanHighlightColor(
    val key: String,
    val displayName: String,
    val chatColor: ChatColor
) {
    AQUA("aqua", "Бирюзовый", ChatColor.AQUA),
    BLUE("blue", "Синий", ChatColor.BLUE),
    DARK_AQUA("dark_aqua", "Тёмно-бирюзовый", ChatColor.DARK_AQUA),
    GREEN("green", "Зелёный", ChatColor.GREEN),
    RED("red", "Красный", ChatColor.RED),
    GOLD("gold", "Золотой", ChatColor.GOLD),
    YELLOW("yellow", "Жёлтый", ChatColor.YELLOW),
    LIGHT_PURPLE("light_purple", "Лиловый", ChatColor.LIGHT_PURPLE),
    WHITE("white", "Белый", ChatColor.WHITE);

    companion object {
        fun fromKey(key: String): ClanHighlightColor? = entries.find { it.key.equals(key, true) || it.name.equals(key, true) }
    }
}
