package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.OpenGuiAction
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.AnimationKey
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.config.MainMenuConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt
import java.util.Locale

/**
 * Config-driven clan headquarters GUI.
 *
 * `mainMenu` in `menus.yml` owns all visual design, titles, materials, lore, and click effects.
 * This class supplies live clan placeholders and enforces permission-sensitive transitions.
 *
 * @param clanService The clan service providing state, persistence, and navigation dependencies.
 */
class MainUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.mainMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        hotWorldDecor(true)

        addMenuItem(menuCfg, "stats")

        addMenuItem(menuCfg, "members") { player, itemCfg ->
            clickEffects(player, itemCfg)
            MembersUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "chest") { player, itemCfg ->
            val clan = this@MainUX.clanService.getClanUser(player) ?: return@addMenuItem
            val user = clan.users.find { it.uuid == player.uniqueId } ?: return@addMenuItem

            if (!clan.hasPermission(user, ClanPerms.Action.OPEN_CHEST)) {
                cfg.send(player, cfg.messages.chest.noPermission)
                return@addMenuItem
            }
            if (!clan.isSettingEnabled(ClanSetting.CHEST)) {
                cfg.send(player, cfg.messages.chest.chestDisabled)
                return@addMenuItem
            }

            clickEffects(player, itemCfg, mainPlaceholders(player, clan))
            this@MainUX.clanService.openClanChest(player, clan)
        }

        addMenuItem(menuCfg, "treasury") { player, itemCfg ->
            clickEffects(player, itemCfg)
            TreasuryUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "homes") { player, itemCfg ->
            clickEffects(player, itemCfg)
            HomesUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "invite") { player, itemCfg ->
            startInvitePrompt(player, itemCfg)
        }

        addMenuItem(menuCfg, "top") { player, itemCfg ->
            clickEffects(player, itemCfg)
            TopClansUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "upgrade") { player, itemCfg ->
            clickEffects(player, itemCfg, mainPlaceholders(player, this@MainUX.clanService.getClanUser(player) ?: return@addMenuItem))
            UpgradeUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "settings") { player, itemCfg ->
            clickEffects(player, itemCfg)
            SettingsUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "help") { player, itemCfg ->
            clickEffects(player, itemCfg, mainPlaceholders(player, this@MainUX.clanService.getClanUser(player) ?: return@addMenuItem))
            HelpUX(this@MainUX.clanService).open(player)
        }

        addMenuItem(menuCfg, "leave") { player, itemCfg ->
            clickEffects(player, itemCfg)
            ClanLeaveConfirmUX(this@MainUX.clanService).open(player)
        }
    }

    private fun addMenuItem(
        menuCfg: MainMenuConfig,
        key: String,
        onClick: ((Player, GuiItemConfig) -> Unit)? = null
    ) {
        val itemCfg = menuCfg.items[key] ?: return

        slot(itemCfg.slot) {
            dynamicItem(this@MainUX.parseMaterial(itemCfg.material, Material.STONE)) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null
                this@MainUX.renderConfigItem(this, player, itemCfg, this@MainUX.mainPlaceholders(player, clan))
                null
            }
            if (onClick != null) {
                onClick { player, _ -> onClick(player, itemCfg) }
            }
        }
    }

    private fun startInvitePrompt(player: Player, itemCfg: GuiItemConfig) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player) ?: return
        val user = clan.users.find { it.uuid == player.uniqueId } ?: return

        if (!clan.hasPermission(user, ClanPerms.Members.INVITE)) {
            cfg.send(player, cfg.messages.invite.noPermission)
            return
        }

        val promptCfg = cfg.messages.invite.prompt
        val promptSeconds = cfg.settings.invitePromptTimeoutSeconds.coerceAtLeast(MIN_PROMPT_SECONDS)
        val promptPlaceholders = mainPlaceholders(player, clan) + mapOf("seconds" to promptSeconds.toString())

        service.plugin.timedBossBarService.remove(player)
        ChatInputPrompt.cancel(player)
        clickEffects(player, itemCfg, promptPlaceholders)
        cfg.send(player, promptCfg.started, promptPlaceholders, promptSeconds)

        ChatInputPrompt.prompt(
            plugin = service.plugin,
            player = player,
            timeoutTicks = promptSeconds.toLong() * TICKS_PER_SECOND,
            onInput = { input ->
                service.plugin.timedBossBarService.remove(player)

                if (promptCfg.cancelInputs.any { it.equals(input, ignoreCase = true) }) {
                    cfg.send(player, cfg.messages.invite.cancelled)
                } else {
                    val target = Bukkit.getPlayer(input)
                    if (target == null) {
                        cfg.send(player, cfg.messages.invite.targetNotFound, mapOf("player" to input))
                    } else {
                        service.plugin.inviteService.sendInvite(player, target)
                    }
                }
                reopenMain(player)
            },
            onTimeout = {
                service.plugin.timedBossBarService.remove(player)
                cfg.send(player, promptCfg.timedOut)
                reopenMain(player)
            }
        )
    }

    private fun reopenMain(player: Player) {
        if (clanService.getClanUser(player) != null) {
            MainUX(clanService).open(player)
        } else {
            player.closeInventory()
        }
    }

    private fun clickEffects(
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String> = emptyMap()
    ) {
        val effects = itemCfg.actions.filterNot { it is OpenGuiAction }
        if (effects.isNotEmpty()) {
            clanService.plugin.configService.send(player, effects, placeholders)
        }
    }

    private fun mainPlaceholders(player: Player, clan: Clan): Map<String, String> {
        val cfg = clanService.plugin.configService
        val display = cfg.menus.mainMenu.display
        val user = clan.users.find { it.uuid == player.uniqueId }
        val homes = cfg.menus.homesMenu.homes
        val unlockedHomes = homes.filter { clan.level >= it.requiredLevel }
        val isLeader = user != null && clan.getUserRole(user) == ClanRole.LEADER
        val kda = if (clan.deaths == 0) {
            ZERO_KDA
        } else {
            String.format(Locale.US, KDA_FORMAT, clan.kills.toDouble() / clan.deaths)
        }
        val canSeeBalance = user != null && clan.hasUserPermission(user, ClanPerms.Bank.SEE)

        val hiddenStars = if (canSeeBalance) "" else cfg.animatedFrame(cfg.animationFrames(AnimationKey.HIDDEN_BALANCE))
        return mapOf(
            "clan" to clan.name,
            "clan_level" to clan.level.toString(),
            "next_level" to (clan.level + 1).coerceAtMost(MAX_CLAN_LEVEL).toString(),
            "clan_mmr" to clan.mmr.toString(),
            "clan_kills" to clan.kills.toString(),
            "clan_deaths" to clan.deaths.toString(),
            "clan_kda" to kda,
            "clan_members" to clan.users.size.toString(),
            "clan_online" to clan.onlineCount.toString(),
            "clan_balance" to if (canSeeBalance) clan.bankBalance.toString() else display.hiddenBalance,
            "clan_balance_animated" to if (canSeeBalance) clan.bankBalance.toString() else hiddenStars,
            "chest_state" to if (clan.isSettingEnabled(ClanSetting.CHEST)) display.chestOpen else display.chestClosed,
            "chest_slots" to (clan.level.coerceIn(MIN_CLAN_LEVEL, MAX_CLAN_LEVEL) * SLOTS_PER_LEVEL).toString(),
            "homes_set" to unlockedHomes.count { clan.homes.containsKey(it.key) }.toString(),
            "homes_unlocked" to unlockedHomes.size.toString(),
            "homes_total" to homes.size.toString(),
            "clan_slots" to (clan.level * MEMBERS_PER_LEVEL).toString(),
            "clan_homes" to unlockedHomes.size.toString(),
            "prompt_seconds" to cfg.settings.invitePromptTimeoutSeconds.coerceAtLeast(MIN_PROMPT_SECONDS).toString(),
            "leave_name" to if (isLeader) display.leaderLeaveName else display.memberLeaveName,
            "leave_warning" to if (isLeader) display.leaderLeaveWarning else display.memberLeaveWarning
        )
    }

    private fun animatedStars(): String {
        val cfg = clanService.plugin.configService
        return cfg.animatedFrame(cfg.animationFrames(AnimationKey.HIDDEN_BALANCE))
    }

    private fun renderConfigItem(
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(clanService.plugin.configService.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { line -> clanService.plugin.configService.formatMessage(player, line, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)

    private companion object {
        const val MIN_PROMPT_SECONDS = 1
        const val TICKS_PER_SECOND = 20L
        const val MIN_CLAN_LEVEL = 1
        const val MAX_CLAN_LEVEL = 5
        const val SLOTS_PER_LEVEL = 9
        const val MEMBERS_PER_LEVEL = 5
        const val ZERO_KDA = "0.00"
        const val KDA_FORMAT = "%.2f"
    }
}
