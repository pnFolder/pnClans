package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action

@Serializable
enum class ClanQuestObjective { PLAYER_KILL, MOB_KILL, ACTIVITY_INTERVAL }

@Serializable
data class ClanQuestConfig(
    val slot: Int = 0,
    val material: String = "BOOK",
    val name: String = "&eКлановый квест",
    val lore: List<String> = emptyList(),
    val objective: ClanQuestObjective,
    val target: Long,
    @YamlComment("Optional EntityType names for MOB_KILL, such as ZOMBIE or WITHER. Empty means every mob.")
    val entityTypes: Set<String> = emptySet(),
    @YamlComment("Completed quest IDs required before this quest may progress.")
    val prerequisites: Set<String> = emptySet(),
    val rewards: List<Action> = emptyList()
)

/** Flexible clan quest definition stored in `quests.yml`. */
@Serializable
data class ClanQuestsConfig(
    val enabled: Boolean = true,
    val title: String = "&#5EA9FD« Клановые квесты »",
    val rows: Int = 6,
    val quests: Map<String, ClanQuestConfig> = mapOf(
        "hunter" to ClanQuestConfig(20, "IRON_SWORD", "&#FC7D37⚔ Охота на соперников", listOf("&7Побеждайте игроков вместе с кланом.", "", "&7Цель: &#FFD700{target} &7убийств", "&7Награда: &#5EFD7Dочки клана"), ClanQuestObjective.PLAYER_KILL, 25),
        "slayers" to ClanQuestConfig(22, "ROTTEN_FLESH", "&#5EFD7D✦ Чистка территории", listOf("&7Очистите мир от опасных существ.", "", "&7Цель: &#FFD700{target} &7мобов", "&7Награда: &#5EFD7Dочки клана"), ClanQuestObjective.MOB_KILL, 250),
        "presence" to ClanQuestConfig(24, "CLOCK", "&#5EA9FD⌚ Активный клан", listOf("&7Будьте активны вместе.", "", "&7Цель: &#FFD700{target} &7интервалов", "&7Награда: &#5EFD7Dочки клана"), ClanQuestObjective.ACTIVITY_INTERVAL, 50)
    )
)
