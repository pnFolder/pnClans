package ua.inventorytype.pnclans.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry

@Serializable
sealed interface Action {
    fun execute(context: ActionContext)
}

// !message
@Serializable
@SerialName("message")
data class MessageAction(
    val text: String
) : Action {
    override fun execute(context: ActionContext) {
        val resultText = context.placeholderRegistry.process(context.player, text)
        context.player.sendMessage(resultText)
    }
}

data class ActionContext(
    val player: Player,
    val placeholderRegistry: PlaceholderRegistry,
    val placeholders: Map<String, String> = emptyMap()
)