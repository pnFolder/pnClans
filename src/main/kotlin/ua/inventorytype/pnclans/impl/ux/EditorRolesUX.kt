package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import java.util.EnumMap

/**
 * Role selector GUI for choosing which clan role to configure permissions for.
 *
 * Displays one clickable icon per non-LEADER clan role. Selecting a role opens
 * [RolePermissionsUX] for fine-grained per-permission configuration.
 * Access is restricted to the clan LEADER only.
 *
 * @param clanService The clan service providing clan and role data.
 */
class EditorRolesUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        title("Клан > Редактор Ролей")
        rows(3)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val roles = ClanRole.entries
            .filter { it != ClanRole.LEADER }
            .sortedBy { it.weight }

        var indexSlot = 11

        for (role in roles) {
            slot(indexSlot) {
                dynamicItem(role.icon) { player ->
                    val clan = this@EditorRolesUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val roleDisplayName = this@EditorRolesUX.clanService.plugin.configService.getRoleDisplayName(role)

                    name("&#FC7D37Роль: $roleDisplayName")
                    lore(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fИерархия: &#5EFD7D#${role.weight}",
                        "",
                        "&#FC65DF «Описание»",
                        " &7- &fНастройка прав для ранга &e$roleDisplayName&f.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы настроить права"
                    )
                    null
                }

                onClick { player, _ ->
                    RolePermissionsUX.get(this@EditorRolesUX.clanService, role, this@EditorRolesUX).open(player)
                }
            }
            indexSlot += 2
        }

        // Back button to SettingsUX
        slot(22) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в настройки")
                lore("&7Нажмите, чтобы вернуться в меню настроек.")
            }
            onClick { player, _ ->
                SettingsUX(this@EditorRolesUX.clanService).open(player)
            }
        }
    }
}

/**
 * Permission grid editor GUI for a specific clan role.
 *
 * Renders a toggle grid for every entry in [ua.inventorytype.pnclans.api.permission.ClanPerms.ALL_PERMISSIONS].
 * Clicking a permission icon grants or revokes it for [targetRole].
 * Only the clan LEADER can modify permissions; others receive a config-driven error response.
 * All changes are persisted immediately via [ua.inventorytype.pnclans.impl.clan.ClanService.saveClan].
 *
 * Instances are cached per [ClanRole] in [RolePermissionsUX.cache] to avoid redundant construction.
 *
 * @param clanService The clan service providing permission storage.
 * @param targetRole The role whose permissions are displayed and editable.
 * @param editorRolesUX The parent role selector GUI used for back navigation.
 */
class RolePermissionsUX private constructor(
    clanService: ClanService,
    val targetRole: ClanRole,
    val editorRolesUX: EditorRolesUX
) : BaseGui(clanService) {

    companion object {
        private val cache = EnumMap<ClanRole, RolePermissionsUX>(ClanRole::class.java)

        fun get(clanService: ClanService, role: ClanRole, editorRolesUX: EditorRolesUX): RolePermissionsUX {
            return cache.getOrPut(role) { RolePermissionsUX(clanService, role, editorRolesUX) }
        }
    }

    init {
        val roleName = clanService.plugin.configService.getRoleDisplayName(targetRole)
        title("Права роли > $roleName")
        rows(5)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val allPermissions = ClanPerms.ALL_PERMISSIONS

        allPermissions.forEachIndexed { index, perm ->
            val slotIndex = 10 + (index / 7) * 9 + (index % 7)

            slot(slotIndex) {
                dynamicItem(perm.icon) { player ->
                    val clan = this@RolePermissionsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val isEnabled = clan.hasRolePermission(this@RolePermissionsUX.targetRole, perm)

                    name("&6Право: &f${perm.displayName}")
                    val descLines = perm.description.chunked(28).map { " &7- &f$it" }
                    val loreList = mutableListOf(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fСтатус: ${if (isEnabled) "&aРазрешено" else "&cЗапрещено"}",
                        "",
                        "&#FC65DF «Описание»"
                    )
                    loreList.addAll(descLines)
                    loreList.add("")
                    loreList.add("&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (isEnabled) "&cЗапретить" else "&aРазрешить"}")

                    lore(*loreList.toTypedArray())
                    null
                }

                onClick { player, event ->
                    val clan = this@RolePermissionsUX.clanService.getClanUser(player) ?: return@onClick
                    val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                    if (clan.getUserRole(user) != ClanRole.LEADER) {
                        val cfg = this@RolePermissionsUX.clanService.plugin.configService
                        cfg.send(player, cfg.messages.settings.noPermissionRoles)
                        return@onClick
                    }

                    val isEnabled = clan.hasRolePermission(this@RolePermissionsUX.targetRole, perm)

                    if (isEnabled) {
                        clan.revokeRolePermission(this@RolePermissionsUX.targetRole, perm)
                    } else {
                        clan.grantRolePermission(this@RolePermissionsUX.targetRole, Pair(perm, true))
                    }
                    this@RolePermissionsUX.clanService.saveClan(clan)
                    this@RolePermissionsUX.updateSlot(event.slot, player)
                }
            }
        }

        // Back button to EditorRolesUX
        slot(36) {
            item(Material.OAK_DOOR) {
                name("&cВернуться к выбору ролей")
                lore("&7Нажмите, чтобы вернуться в редактор ролей.")
            }
            onClick { player, _ ->
                this@RolePermissionsUX.editorRolesUX.open(player)
            }
        }
    }
}