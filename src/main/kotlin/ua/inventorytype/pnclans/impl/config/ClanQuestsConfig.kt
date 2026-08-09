package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action

@Serializable
enum class ClanQuestObjective { PLAYER_KILL, MOB_KILL, ACTIVITY_INTERVAL }

@Serializable
data class ClanQuestConfig(
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
    val quests: Map<String, ClanQuestConfig> = emptyMap()
)
