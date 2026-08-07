package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Settings panel GUI for toggling clan operational modes.
 *
 * Layout, item materials, names, and lore templates are loaded from `settingsMenu`
 * in `menus.yml`; this class only supplies runtime state placeholders and click logic.
 *
 * @param clanService The clan service providing data and persistence.
 * @param editorRolesUX Optional pre-constructed role editor instance.
 */
class SettingsUX(
    clanService: ClanService,
    val editorRolesUX: EditorRolesUX = EditorRolesUX(clanService)
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.settingsMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        hotWorldDecor(true)

        val modules = cfg.settings.modules

        if (modules.pvp) {
            settingToggle("pvp", ClanSetting.PVP, ClanPerms.Settings.TOGGLE_PVP) { enabled ->
                mapOf(
                    "state" to if (enabled) "&#5EFD7DВключён" else "&#FC3737Выключен",
                    "pvp_damage" to if (enabled) "&#5EFD7DРазрешён" else "&#FC3737Заблокирован",
                    "action" to if (enabled) "&#FC3737выключить" else "&#5EFD7Dвключить"
                )
            }
        }

        clanChatToggle()

        if (modules.chest) {
            settingToggle("chest", ClanSetting.CHEST, ClanPerms.Action.OPEN_CHEST) { enabled ->
                mapOf(
                    "state" to if (enabled) "&#5EFD7DОткрыто" else "&#FC3737Закрыто",
                    "action" to if (enabled) "&#FC3737закрыть" else "&#5EFD7Dоткрыть"
                )
            }
        }

        settingToggle("join", ClanSetting.JOIN, ClanPerms.Settings.TOGGLE_JOIN) { enabled ->
            mapOf(
                "state" to if (enabled) "&#5EFD7DВключены" else "&#FC3737Выключены",
                "action" to if (enabled) "&#FC3737выключить" else "&#5EFD7Dвключить"
            )
        }

        renderInfoItem("overview")
        renderInfoItem("hint")

        menuCfg.items["roles"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.NETHER_STAR)) { player ->
                    val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val user = clan.getMember(player.uniqueId) ?: return@dynamicItem null
                    val placeholders = this@SettingsUX.commonPlaceholders(player, clan) + mapOf(
                        "roles" to ClanRole.entries.size.toString(),
                        "role" to cfg.getRoleDisplayName(clan.getUserRole(user))
                    )

                    name(this@SettingsUX.format(player, itemCfg.name, placeholders))
                    lore(this@SettingsUX.format(player, itemCfg.lore, placeholders))
                    glow(itemCfg.glow)
                    null
                }
                onClick { player, _ ->
                    val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                    val user = clan.getMember(player.uniqueId) ?: return@onClick

                    if (clan.getUserRole(user) != ClanRole.LEADER) {
                        cfg.send(player, cfg.messages.settings.noPermissionRoles)
                        return@onClick
                    }

                    EditorRolesUX(this@SettingsUX.clanService).open(player)
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    name(this@SettingsUX.format(player, itemCfg.name, emptyMap()))
                    lore(this@SettingsUX.format(player, itemCfg.lore, emptyMap()))
                    glow(itemCfg.glow)
                    null
                }
                onClick { player, _ -> MainUX(this@SettingsUX.clanService).open(player) }
            }
        }
    }

    private fun settingToggle(
        key: String,
        setting: ClanSetting,
        permission: ua.inventorytype.pnclans.api.permission.Permission,
        placeholders: (Boolean) -> Map<String, String>
    ) {
        val cfg = clanService.plugin.configService
        val itemCfg = cfg.menus.settingsMenu.items[key] ?: return

        slot(itemCfg.slot) {
            dynamicItem(parseMaterial(itemCfg.material, Material.STONE)) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val enabled = clan.isSettingEnabled(setting)
                val resolved = this@SettingsUX.commonPlaceholders(player, clan) + placeholders(enabled)

                name(this@SettingsUX.format(player, itemCfg.name, resolved))
                lore(this@SettingsUX.format(player, itemCfg.lore, resolved))
                glow(itemCfg.glow || enabled)
                null
            }

            onClick { player, event ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.getMember(player.uniqueId) ?: return@onClick

                if (!clan.hasPermission(user, permission)) {
                    cfg.send(player, cfg.messages.settings.noPermission)
                    return@onClick
                }

                clan.toggleSetting(setting)
                this@SettingsUX.clanService.saveClan(clan)
                this@SettingsUX.updateSlot(event.slot, player)
            }
        }
    }

    /** Renders the clan chat toggle with the name and lore selected by its configured activation mode. */
    private fun clanChatToggle() {
        val cfg = clanService.plugin.configService
        val baseItemCfg = cfg.menus.settingsMenu.items["chat"] ?: return

        slot(baseItemCfg.slot) {
            dynamicItem(parseMaterial(baseItemCfg.material, Material.WRITABLE_BOOK)) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val enabled = clan.isSettingEnabled(ClanSetting.CHAT)
                val chatConfig = cfg.settings.clanChat
                val itemText = when (chatConfig.mode) {
                    ua.inventorytype.pnclans.impl.config.ClanChatMode.COMMAND -> chatConfig.commandMenuItem
                    ua.inventorytype.pnclans.impl.config.ClanChatMode.PREFIX -> chatConfig.prefixMenuItem
                }
                val placeholders = this@SettingsUX.commonPlaceholders(player, clan) + mapOf(
                    "state" to if (enabled) chatConfig.enabledState else chatConfig.disabledState,
                    "action" to if (enabled) chatConfig.disableAction else chatConfig.enableAction,
                    "command" to chatConfig.command.trim().removePrefix("/"),
                    "prefix" to chatConfig.prefix
                )

                name(this@SettingsUX.format(player, itemText.name, placeholders))
                lore(this@SettingsUX.format(player, itemText.lore, placeholders))
                glow(baseItemCfg.glow || enabled)
                null
            }

            onClick { player, event ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.getMember(player.uniqueId) ?: return@onClick

                if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_CHAT)) {
                    cfg.send(player, cfg.messages.settings.noPermission)
                    return@onClick
                }

                clan.toggleSetting(ClanSetting.CHAT)
                this@SettingsUX.clanService.saveClan(clan)
                this@SettingsUX.updateSlot(event.slot, player)
            }
        }
    }

    private fun renderInfoItem(key: String) {
        val cfg = clanService.plugin.configService
        val itemCfg = cfg.menus.settingsMenu.items[key] ?: return

        slot(itemCfg.slot) {
            dynamicItem(parseMaterial(itemCfg.material, Material.PAPER)) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val user = clan.getMember(player.uniqueId)
                val placeholders = this@SettingsUX.commonPlaceholders(player, clan) + mapOf(
                    "role" to if (user != null) cfg.getRoleDisplayName(clan.getUserRole(user)) else "-"
                )

                name(this@SettingsUX.format(player, itemCfg.name, placeholders))
                lore(this@SettingsUX.format(player, itemCfg.lore, placeholders))
                glow(itemCfg.glow)
                null
            }
        }
    }

    private fun commonPlaceholders(player: Player, clan: Clan): Map<String, String> =
        mapOf(
            "members" to clan.users.size.toString(),
            "online" to clan.onlineCount.toString(),
            "player" to player.name
        )

    private fun format(player: Player, template: String, placeholders: Map<String, String>): String =
        clanService.plugin.configService.formatMessage(player, template, placeholders)

    private fun format(player: Player, templates: List<String>, placeholders: Map<String, String>): List<String> =
        templates.map { format(player, it, placeholders) }

    companion object {
        private fun parseMaterial(name: String, fallback: Material): Material =
            runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
    }
}
