package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Interactive personal permission override editor for an individual clan member.
 *
 * Allows leaders and authorized managers to toggle individual permission overrides
 * for a specific member, taking precedence over their assigned role permissions.
 *
 * @param clanService The clan service providing member state and persistence.
 * @param targetUser The specific clan member whose permissions are being edited.
 * @param parentGui The parent [MembersUX] list GUI for back navigation.
 */
class UserPermissionsUX(
    clanService: ClanService,
    val targetUser: User,
    val parentGui: MembersUX
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.editorRolesMenu
        val permissionSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        val targetMember = this.targetUser
        val parent = this.parentGui

        title("&#FC7D37« Права Игрока » &7• ${targetMember.playerName}")
        rows(6)
        hotWorldDecor(true)

        ClanPerms.ALL_PERMISSIONS.take(permissionSlots.size).forEachIndexed { index, perm ->
            val slotIndex = permissionSlots[index]

            slot(slotIndex) {
                dynamicItem(perm.icon) { player ->
                    val clan = this@UserPermissionsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val targetRole = clan.getUserRole(targetMember)
                    val roleName = cfg.getRoleDisplayName(targetRole)

                    val hasRolePerm = clan.hasRolePermission(targetRole, perm)
                    val hasPersonalOverride = clan.hasUserPermission(targetMember, perm)
                    val effectiveState = clan.hasPermission(targetMember, perm)

                    val overrideStatus = if (hasPersonalOverride) {
                        "&#5EFD7DПерсонально разрешено"
                    } else if (hasRolePerm) {
                        "&#5EA9FDОт роли ($roleName)"
                    } else {
                        "&#FC3737Запрещено"
                    }

                    name("&#FC7D37${perm.displayName} &7• $overrideStatus")
                    lore(
                        "",
                        "&#9EFC65 «Описание»",
                        " &7- &f${perm.description}",
                        "",
                        "&#5EA9FD «Статус прав»",
                        " &7- &fОт роли (&e$roleName&f): ${if (hasRolePerm) "&#5EFD7DРазрешено" else "&#FC3737Запрещено"}",
                        " &7- &fПерсонально: ${if (hasPersonalOverride) "&#5EFD7DРазрешено" else "&7По умолчанию (от роли)"}",
                        " &7- &fИтоговый доступ: ${if (effectiveState) "&#5EFD7DДОСТУПНО" else "&#FC3737ЗАБЛОКИРОВАНО"}",
                        "",
                        "&#FF8702➥ &fЛКМ: ${if (hasPersonalOverride) "&#FC3737Сбросить к роли" else "&#5EFD7DВыдать персонально"}",
                        "&#FF8702➥ &fПКМ: &#FC3737Принудительно запретить"
                    )
                    glow(effectiveState)
                    null
                }

                onClick { player, event ->
                    val clan = this@UserPermissionsUX.clanService.getClanUser(player) ?: return@onClick
                    val myUser = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                    if (!clan.hasPermission(myUser, ClanPerms.Members.INVITE) && clan.getUserRole(myUser) != ClanRole.LEADER) {
                        cfg.send(player, cfg.messages.members.cannotManageHigherRank)
                        return@onClick
                    }

                    val hasPersonalOverride = clan.hasUserPermission(targetMember, perm)

                    if (event.isLeftClick) {
                        if (hasPersonalOverride) {
                            clan.revokeUserPermission(targetMember, perm)
                        } else {
                            clan.grantUserPermission(targetMember, perm to true)
                        }
                    } else if (event.isRightClick) {
                        clan.grantUserPermission(targetMember, perm to false)
                    }

                    this@UserPermissionsUX.clanService.saveClan(clan)
                    this@UserPermissionsUX.updateSlot(event.slot, player)
                }
            }
        }

        // Back Button (Slot 49)
        slot(49) {
            item(Material.RED_CANDLE) {
                name("&#FC3737⏎ Вернуться к списку участников")
                lore("", "&#FF8702➥ &fНажмите &eЛКМ &fдля возврата")
            }
            onClick { player, _ -> parent.open(player) }
        }
    }
}
