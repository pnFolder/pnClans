package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Premium role selector GUI for choosing which clan role to configure.
 *
 * The visual shell and reusable item templates are loaded from `editorRolesMenu`
 * in `menus.yml`; this class only injects runtime role data and navigation logic.
 *
 * @param clanService The clan service providing clan and role data.
 */
class EditorRolesUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.editorRolesMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        hotWorldDecor(true)

        menuCfg.items["overview"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.LECTERN)) { player ->
                    val clan = this@EditorRolesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val placeholders = mapOf(
                        "roles" to ClanRole.entries.count { it != ClanRole.LEADER }.toString(),
                        "members" to clan.users.size.toString()
                    )

                    this@EditorRolesUX.renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
            }
        }

        val roles = ClanRole.entries
            .filter { it != ClanRole.LEADER }
            .sortedBy { it.weight }
        val roleSlots = listOf(20, 22, 24)

        menuCfg.items["role"]?.let { roleTemplate ->
            roles.forEachIndexed { index, role ->
                val slotIndex = roleSlots.getOrNull(index) ?: return@forEachIndexed
                slot(slotIndex) {
                    dynamicItem(parseMaterial(roleTemplate.material, role.icon)) { player ->
                        val roleDisplayName = this@EditorRolesUX.clanService.plugin.configService.getRoleDisplayName(role)
                        val placeholders = mapOf(
                            "role" to roleDisplayName,
                            "weight" to role.weight.toString(),
                            "permissions" to role.defaultPermissions.size.toString()
                        )

                        this@EditorRolesUX.renderConfigItem(this, player, roleTemplate, placeholders)
                        null
                    }

                    onClick { player, _ ->
                        RolePermissionsUX(this@EditorRolesUX.clanService, role, this@EditorRolesUX).open(player)
                    }
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    this@EditorRolesUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> SettingsUX(this@EditorRolesUX.clanService).open(player) }
            }
        }
    }

    private fun renderConfigItem(
        builder: ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(format(player, itemCfg.name, placeholders))
        builder.lore(format(player, itemCfg.lore, placeholders))
        builder.glow(itemCfg.glow)
    }

    private fun format(player: Player, template: String, placeholders: Map<String, String>): String =
        clanService.plugin.configService.formatMessage(player, template, placeholders)

    private fun format(player: Player, templates: List<String>, placeholders: Map<String, String>): List<String> =
        templates.map { format(player, it, placeholders) }

    companion object {
        private fun parseMaterial(name: String, fallback: Material): Material =
            runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
    }
}

/**
 * Permission matrix editor GUI for a specific clan role.
 *
 * Permissions are rendered as a dense HotWorld grid with clear enabled/disabled states,
 * config-driven text templates, and non-door premium navigation.
 *
 * @param clanService The clan service providing permission storage.
 * @param targetRole The role whose permissions are displayed and editable.
 * @param editorRolesUX The parent role selector GUI used for back navigation.
 */
class RolePermissionsUX(
    clanService: ClanService,
    val targetRole: ClanRole,
    val editorRolesUX: EditorRolesUX
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.editorRolesMenu
        val roleName = cfg.getRoleDisplayName(targetRole)
        val permissionSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        title("&#FC7D37« Права Роли » &7• $roleName")
        rows(6)
        hotWorldDecor(true)

        menuCfg.items["permission"]?.let { permissionTemplate ->
            ClanPerms.ALL_PERMISSIONS.take(permissionSlots.size).forEachIndexed { index, perm ->
                val slotIndex = permissionSlots[index]

                slot(slotIndex) {
                    dynamicItem(this@RolePermissionsUX.parseMaterial(permissionTemplate.material, perm.icon)) { player ->
                        val clan = this@RolePermissionsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                        val isEnabled = clan.hasRolePermission(this@RolePermissionsUX.targetRole, perm)
                        val placeholders = this@RolePermissionsUX.permissionPlaceholders(roleName, perm, isEnabled)

                        name(this@RolePermissionsUX.format(player, permissionTemplate.name, placeholders))
                        lore(this@RolePermissionsUX.format(player, permissionTemplate.lore, placeholders))
                        glow(permissionTemplate.glow || isEnabled)
                        null
                    }

                    onClick { player, event ->
                        val clan = this@RolePermissionsUX.clanService.getClanUser(player) ?: return@onClick
                        val user = clan.getMember(player.uniqueId) ?: return@onClick

                        if (clan.getUserRole(user) != ClanRole.LEADER) {
                            cfg.send(player, cfg.messages.settings.noPermissionRoles)
                            return@onClick
                        }

                        val isEnabled = clan.hasRolePermission(this@RolePermissionsUX.targetRole, perm)

                        if (isEnabled) {
                            clan.revokeRolePermission(this@RolePermissionsUX.targetRole, perm)
                        } else {
                            clan.grantRolePermission(this@RolePermissionsUX.targetRole, Pair(perm, true))
                        }
                        if (this@RolePermissionsUX.clanService.saveClan(clan)) {
                            this@RolePermissionsUX.updateSlot(event.slot, player)
                        }
                    }
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@RolePermissionsUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    val placeholders = mapOf("role" to roleName)
                    name(this@RolePermissionsUX.format(player, itemCfg.name, placeholders))
                    lore(this@RolePermissionsUX.format(player, itemCfg.lore, placeholders))
                    glow(itemCfg.glow)
                    null
                }
                onClick { player, _ -> this@RolePermissionsUX.editorRolesUX.open(player) }
            }
        }
    }

    private fun permissionPlaceholders(roleName: String, perm: Permission, isEnabled: Boolean): Map<String, String> =
        mapOf(
            "permission" to perm.displayName,
            "role" to roleName,
            "state" to if (isEnabled) "&#5EFD7DРазрешено" else "&#FC3737Запрещено",
            "action" to if (isEnabled) "&#FC3737запретить" else "&#5EFD7Dразрешить",
            "description" to perm.description.chunked(30).joinToString("\n") { " &7- &f$it" }
        )

    private fun format(player: Player, template: String, placeholders: Map<String, String>): String =
        clanService.plugin.configService.formatMessage(player, template, placeholders)

    private fun format(player: Player, templates: List<String>, placeholders: Map<String, String>): List<String> =
        templates.flatMap { format(player, it, placeholders).split("\n") }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
}
