# pnClans Add-on API

pnClans is a Bukkit service that other plugins can use after pnClans has enabled. An add-on is a normal Bukkit plugin JAR: it can be installed in the server `plugins/` directory, or placed in `plugins/pnClans/addons/` for pnClans to load on startup.

Only import packages under `ua.inventorytype.pnclans.api.*`. Classes in `impl.*` are internal and may change without notice.

## Quick Start

1. Add pnClans as a hard dependency.
2. Compile against the same pnClans release as the server.
3. Obtain `PnClansApi` in `onEnable`.
4. Register your add-on features from `PnClansAddon.onEnable`.

```yaml
# src/main/resources/plugin.yml
name: ExampleAddon
version: '1.0.0'
main: com.example.addon.ExampleAddon
api-version: '26.2'
depend: [pnClans]
```

```kotlin
class ExampleAddon : JavaPlugin(), PnClansAddon {
    override val id = "example-addon"
    override val addonVersion = "1.0.0"
    override val author = "ExampleDeveloper"
    override val summary = "A pnClans extension"

    private lateinit var api: PnClansApi

    override fun onEnable() {
        api = PnClansProvider.require()
        check(api.addons.register(this, this)) { "Could not register pnClans addon" }
    }

    override fun onEnable(context: AddonContext) {
        api = context.api
        // Register commands, menu buttons, listeners, and tasks here.
    }

    override fun onDisable() {
        // Cancel tasks and unregister features owned by this addon here.
    }
}
```

`PnClansProvider.require()` produces a clear error when pnClans is unavailable. `plugin.yml` must therefore use `depend`, not `softdepend`.

## Build Setup

The included `examples/clan-missions-addon` project compiles against a locally built pnClans JAR. Build pnClans first:

```powershell
.\gradlew.bat build
```

Then build the example from its directory. Pass `-PpnClansVersion=<version>` when the pnClans version differs from `1.0.6`.

```powershell
.\gradlew.bat build "-PpnClansVersion=1.0.6"
```

## Public Services

| API member | Purpose |
| --- | --- |
| `api.clans` | Find clans and persist mutations. |
| `api.points` | Award or spend typed clan reward points. |
| `api.operations` | Event-aware role, setting, home, and chest mutations. |
| `api.gui` | Config-bound add-on GUI item providers and click actions. |
| `api.placeholders` | Register `{key}` placeholders for pnClans messages and GUI text. |
| `api.subcommands` | Add `/clan <command>` subcommands. |
| `api.menus` | Add buttons to the main clan menu. |
| `api.addons` | Register and inspect pnClans add-ons. |

All API calls, mutations, and Bukkit event handlers must run on the Bukkit server thread. After changing a `Clan` directly, call `api.clans.save(clan)`.

### Clan Points

Points are the shared clan reward currency. Every successful operation is persisted, written to the clan points history, and fires a cancellable `ClanPointsTransactionEvent` before the balance changes.

```kotlin
import ua.inventorytype.pnclans.api.clan.ClanPointsSource

api.points.award(clan, 25, ClanPointsSource.QUEST)
val purchased = api.points.spend(clan, 100, ClanPointsSource.SHOP)
```

Available sources: `PLAYER_KILL`, `MOB_KILL`, `ACTIVITY`, `QUEST`, `SHOP`, and `ADMIN`.

## Events

Events follow one rule:

- `*PreEvent` is cancellable and runs before a mutation.
- An event without `Pre` is a notification after a successful mutation and is not cancellable.

Important events:

| Event | When it runs |
| --- | --- |
| `ClanCreatedEvent`, `ClanDisbandedEvent` | Before clan creation or disbanding; cancellable. |
| `ClanTreasuryTransactionPreEvent` | Before Vault and treasury changes; cancellable. |
| `ClanTreasuryTransactionEvent` | After a treasury transaction was persisted. |
| `ClanPointsTransactionEvent` | Before reward-point balance changes; cancellable and the amount is mutable. |
| `ClanMemberJoinEvent`, `ClanMemberLeaveEvent` | Before a member joins or leaves; cancellable. |
| `ClanMemberRoleChangeEvent` | Before a role changes; cancellable and the target role is mutable. |
| `ClanSettingChangeEvent` | Before a clan setting changes; cancellable and the target value is mutable. |
| `ClanHomeSetEvent`, `ClanHomeDeleteEvent` | Before a clan home is set, moved, or deleted; cancellable. |
| `ClanChestOpenEvent` | Before the clan chest GUI opens; cancellable. |
| `ClanSavedEvent` | After a clan was persisted. |
| `ClanSubcommandExecuteEvent` | Before an add-on subcommand runs; cancellable. |

```kotlin
@EventHandler
fun onPoints(event: ClanPointsTransactionEvent) {
    if (event.source == ClanPointsSource.MOB_KILL) {
        event.amount = event.amount.coerceAtMost(5)
    }
}
```

## Subcommands

```kotlin
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
```

The command becomes available as `/clan missions` and `/clan quests`. Unregister it in `PnClansAddon.onDisable` with `api.subcommands.unregister(this, "missions")`.

## Event-Aware Operations

Use `api.operations` for state-changing clan actions. These operations invoke the matching cancellable event, persist successful changes, and return a typed result instead of a plain boolean.

```kotlin
when (val result = api.operations.changeSetting(clan, ClanSetting.PVP, false)) {
    ClanOperationResult.Success -> player.sendMessage("Clan PvP disabled")
    is ClanOperationResult.Rejected -> player.sendMessage("Change rejected: ${result.reason}")
}
```

## Main Menu Buttons

```kotlin
api.menus.registerMainButton(this, object : ClanMainMenuButton {
    override val id = "missions-button"
    override val slot = 22

    override fun createItem(context: ClanMainMenuContext): ItemStack =
        ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta.apply { setDisplayName("§eClan missions") }
        }

    override fun onClick(context: ClanMainMenuContext) {
        context.player.sendMessage("Open missions for ${context.clan.name}")
    }
})
```

Registration fails when another button already owns the same slot. Use a stable and globally unique button ID, then unregister it in `onDisable`.

## Config-Bound GUI Items

Use `api.gui` when an add-on owns the item behavior but the server administrator should control its position in the existing pnClans main menu. The add-on registers an item provider and optional click action; `menus.yml` connects their IDs to a slot. pnClans never loads arbitrary classes or executes code from YAML.

```kotlin
api.gui.registerItem(this, "clan-missions:daily-quest") { context ->
    ItemStack(Material.BOOK).apply {
        itemMeta = itemMeta.apply { setDisplayName("§eDaily clan quest") }
    }
}

api.gui.registerAction(this, "clan-missions:open-daily-quest") { context ->
    context.player.sendMessage("Open the daily quest for ${context.clan.name}")
}
```

The server configuration then places that registered feature inside the normal `mainMenu` structure:

```yaml
mainMenu:
  addons:
    daily-quest:
      slot: 22
      item: "clan-missions:daily-quest"
      action: "clan-missions:open-daily-quest"
```

`daily-quest` is a local configuration ID. `item` and `action` must be globally unique IDs in the `addon-id:item-id` format. If an add-on is disabled or an ID is not registered, pnClans hides the configured slot safely. Add-on GUI registrations are removed automatically when the add-on is disabled.

## Add-on Lifecycle

`PnClansAddon` is optional for a plugin that only consumes the API. Implement it when your plugin registers pnClans-owned features such as subcommands or menu buttons. pnClans calls `onEnable(context)` only after successful registration and calls `onDisable()` when the add-on is disabled.

`api.addons.disable(id)` stops the pnClans add-on lifecycle; Bukkit does not unload a plugin classloader at runtime. An add-on must therefore cancel its own tasks, unregister listeners, commands, and menu buttons in `onDisable`.

## API Compatibility

The current public API version is `4`. Compare `api.apiVersion` only when using features introduced after the minimum version your add-on supports. Do not compare the plugin release version for API compatibility.
