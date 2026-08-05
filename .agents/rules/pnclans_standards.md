# Project Standards & Architectural Rules — pnClans

## 1. 100% Config-Driven UI Guarantee
- NEVER hardcode user-facing lore lines, status indicators, or click action descriptions (e.g. `ЛКМ: Повысить`, `СКМ: Права`) inside Kotlin GUI classes.
- ALWAYS define item templates in `MenusConfig.kt` / `menus.yml` using configurable placeholder tokens (`{player}`, `{role}`, `{status}`, `{action_promote}`).

## 2. Dynamic Live GUI Reload Standard (`/clan reload`)
- Reload commands MUST reload disk configurations (`ConfigService.loadAll()`, `ClanService.loadClans()`) AND iterate over all online players viewing `BaseGui` instances.
- Re-open/refresh open GUIs live so new `menus.yml` layout slots, materials, titles, and lores apply instantly without requiring players to re-open menus manually.

## 3. Atomic Hierarchy & Role Operations
- Promoting any member to `LEADER` MUST automatically demote the current leader to `DEPUTY`, save storage state, and trigger live GUI updates across viewers.

## 4. Build Safety & ShadowJar Binding
- `runServer` MUST always bind to `shadowJar.flatMap { it.archiveFile }`.
- Ensure no locked JAR instances prevent clean overwrites during test server execution.
