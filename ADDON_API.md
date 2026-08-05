# pnClans Add-on API

pnClans exposes a stable Bukkit service for other plugins. Depend on `pnClans` in your `plugin.yml`; do not import `ua.inventorytype.pnclans.impl.*`.

```yaml
depend: [pnClans]
```

```kotlin
import ua.inventorytype.pnclans.api.PnClansProvider

override fun onEnable() {
    val api = PnClansProvider.require()
    require(api.apiVersion == PnClansProvider.API_VERSION)

    val clan = api.clans.findByMember(player.uniqueId)
    api.clans.save(clan)
}
```

Available contracts:

- `PnClansApi`: service entry point, API version and placeholders.
- `ClanRepository`: clan lookup, immutable collection snapshot and explicit persistence.
- `Clan`: public clan model, member, permission, home and treasury operations.
- `TreasuryTransaction`: persisted treasury log entry.
- Bukkit events: lifecycle, treasury, persistence, main-menu and subcommand hooks.
- `PnClansAddon`: optional lifecycle abstraction for a feature module owned by another Bukkit plugin.
- `ClanSubcommand`: registration point for `/clan <your-command>` without modifying pnClans.

`ClanCreatedEvent`, `ClanDisbandedEvent`, and `ClanTreasuryTransactionEvent` implement Bukkit `Cancellable` and are emitted before pnClans applies an operation. Set `event.isCancelled = true` to prevent a clan from being created or disbanded, or to stop a treasury transaction before money or data changes. Mutating a `Clan` from an add-on requires `api.clans.save(clan)` afterwards. API calls and Bukkit events must be used from the server thread.

For a binary-compatible add-on, compile against the same major pnClans API version and guard new functionality with `api.apiVersion`.

## Subcommand example

An addon remains a normal separate Bukkit jar. In its `onEnable`, obtain the API and register its command:

```kotlin
class MissionsAddon : JavaPlugin() {
    override fun onEnable() {
        val api = PnClansProvider.require()
        api.subcommands.register(this, object : ClanSubcommand {
            override val name = "missions"
            override val aliases = setOf("quests")

            override fun execute(context: ClanCommandContext): Boolean {
                val player = context.player ?: return true
                val clan = context.clan ?: return true
                player.sendMessage("Your clan: ${clan.name}")
                return true
            }
        })
    }
}
```

This command is then available as `/clan missions` and `/clan quests`. The core keeps ownership and tab completion isolated from the addon jar.

## Addon metadata and management

Describe the addon with `id`, `addonVersion`, `author`, `summary`, optional `website`, and `requiredApiVersion`. The registry exposes the current state and source JAR in `AddonDescriptor`.

```kotlin
class MissionsAddon : JavaPlugin(), PnClansAddon {
    override val id = "missions"
    override val addonVersion = "1.0.0"
    override val author = "ExampleDeveloper"
    override val summary = "Clan missions"

    override fun onEnable() {
        PnClansProvider.require().addons.register(this, this)
    }

    override fun onEnable(context: AddonContext) { /* register features */ }
    override fun onDisable() { /* unregister tasks/listeners owned by this addon */ }
}
```

pnClans creates `plugins/pnClans/addons/` on startup and attempts to load every JAR in it. The JAR must be a regular Bukkit plugin and have `depend: [pnClans]` in `plugin.yml`. A server administration plugin may call `api.addons.load(file)`, `enable(id)`, `disable(id)`, inspect `find(id)`, or inspect `all()`.

`disable(id)` controls the pnClans addon lifecycle; it does not unload the Bukkit classloader. Addons must stop their own tasks and listeners in `PnClansAddon.onDisable()`.

## Main menu extension

Addons can render a button into any main-menu slot. A button registered after the core layout intentionally replaces that slot, so an addon can extend or replace a standard entry without importing GUI internals.

```kotlin
api.menus.registerMainButton(this, object : ClanMainMenuButton {
    override val id = "missions-button"
    override val slot = 22

    override fun createItem(context: ClanMainMenuContext) = ItemStack(Material.BOOK).apply {
        itemMeta = itemMeta.apply { setDisplayName("§eМиссии клана") }
    }

    override fun onClick(context: ClanMainMenuContext) {
        context.player.sendMessage("Открываем миссии ${context.clan.name}")
    }
})
```

`ClanMainMenuItemRenderEvent` can hide a core item for a player, while `ClanMainMenuItemClickEvent` can cancel its default action. `ClanSavedEvent` is useful for external persistence or scoreboards. `ClanSubcommandExecuteEvent` runs before an addon subcommand and can be cancelled.
