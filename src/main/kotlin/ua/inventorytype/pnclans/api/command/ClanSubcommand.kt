package ua.inventorytype.pnclans.api.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan

data class ClanCommandContext(
    val sender: CommandSender,
    val args: List<String>,
    val clan: Clan?,
    val player: Player? = sender as? Player
)

interface ClanSubcommand {
    val name: String
    val aliases: Set<String> get() = emptySet()
    val usage: String get() = "/clan $name"
    fun execute(context: ClanCommandContext): Boolean
    fun tabComplete(context: ClanCommandContext): List<String> = emptyList()
}

interface ClanSubcommandRegistry {
    fun register(owner: org.bukkit.plugin.Plugin, subcommand: ClanSubcommand): Boolean
    fun unregister(owner: org.bukkit.plugin.Plugin, name: String): Boolean
    fun all(): Collection<ClanSubcommand>
}
