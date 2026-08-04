package ua.inventorytype.pnclans.api.clan

enum class ClanSetting(
    val key: String,
    val defaultValue: Boolean
) {
    PVP("pvp", false),            // По умолчанию PvP в клане выключено
    CHAT("chat", true),           // Клановый чат включен
    CHEST("chest", true),          // Открытый ли клан (вход без приглашения)
    JOIN("join", true),          // Открытый ли клан (вход без приглашения)

    ;

    companion object {
        fun fromKey(key: String): ClanSetting? = entries.find { it.key.equals(key, true) }
    }
}