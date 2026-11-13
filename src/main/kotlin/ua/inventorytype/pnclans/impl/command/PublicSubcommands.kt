package ua.inventorytype.pnclans.impl.command

import ua.inventorytype.pnclans.api.PnClansApi
import ua.inventorytype.pnclans.api.command.ClanSubcommand

internal fun PnClansApi.findSubcommand(name: String): ClanSubcommand? =
    subcommands.all().firstOrNull { it.name.equals(name, true) || it.aliases.any { alias -> alias.equals(name, true) } }

internal fun PnClansApi.subcommandNames(): List<String> =
    subcommands.all().flatMap { listOf(it.name) + it.aliases }
