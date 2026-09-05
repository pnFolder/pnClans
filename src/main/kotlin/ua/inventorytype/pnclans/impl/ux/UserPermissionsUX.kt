package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/** Personal permission override editor for one clan member. */
class UserPermissionsUX(
    clanService: ClanService,
    val targetUser: User,
    val parentGui: MembersUX
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.userPermissionsMenu
        val targetMember = targetUser
        val parent = parentGui

        title(menuCfg.title.replace("{player}", targetMember.playerName))
        rows(menuCfg.rows.coerceIn(1, 6))
        background(clanService.plugin.configService.menus.background)

        menuCfg.items["permission"]?.let { permissionTemplate ->
            permissionSlots(menuCfg.rows).zip(ClanPerms.ALL_PERMISSIONS).forEach { (slotIndex, perm) ->
                slot(slotIndex) {
                    dynamicItem(this@UserPermissionsUX.parseMaterial(permissionTemplate.material, perm.icon)) { player ->
                        val clan = this@UserPermissionsUX.clanService.getClanUser(player) ?: return@dynamicItem null
                        val targetRole = clan.getUserRole(targetMember)
                        val hasRolePerm = clan.hasRolePermission(targetRole, perm)
                        val hasPersonalOverride = clan.hasUserPermissionOverride(targetMember, perm)
                        val effectiveState = clan.hasPermission(targetMember, perm)

                        val placeholders = mapOf(
                            "player" to targetMember.playerName,
                            "permission" to perm.displayName,
                            "description" to perm.description,
                            "role" to cfg.getRoleDisplayName(targetRole),
                            "state" to if (effectiveState) "&#5EFD7DДоступно" else "&#FC3737Заблокировано",
                            "role_state" to if (hasRolePerm) "&#5EFD7DРазрешено" else "&#FC3737Запрещено",
                            "personal_state" to if (hasPersonalOverride) "&#5EFD7DПереопределено" else "&7От роли",
                            "action" to if (hasPersonalOverride) "&#FC3737сбросить к роли" else "&#5EFD7Dвыдать персонально"
                        )
                        this@UserPermissionsUX.render(this, player, permissionTemplate, placeholders, effectiveState)
                        null
                    }

                    onClick { player, event ->
                        val clan = this@UserPermissionsUX.clanService.getClanUser(player) ?: return@onClick
                        val myUser = clan.getMember(player.uniqueId) ?: return@onClick
                        if (clan.getUserRole(myUser) != ClanRole.LEADER && !clan.hasPermission(myUser, ClanPerms.Members.INVITE)) {
                            cfg.send(player, cfg.messages.members.cannotManageHigherRank)
                            return@onClick
                        }

                        val hasPersonalOverride = clan.hasUserPermissionOverride(targetMember, perm)
                        when {
                            event.isLeftClick && hasPersonalOverride -> clan.revokeUserPermission(targetMember, perm)
                            event.isLeftClick -> clan.grantUserPermission(targetMember, perm to true)
                            event.isRightClick -> clan.grantUserPermission(targetMember, perm to false)
                        }
                        if (this@UserPermissionsUX.clanService.saveClan(clan)) {
                            this@UserPermissionsUX.updateSlot(event.slot, player)
                        }
                    }
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@UserPermissionsUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    this@UserPermissionsUX.render(this, player, itemCfg, mapOf("player" to targetMember.playerName), itemCfg.glow)
                    null
                }
                onClick { player, _ -> parent.open(player) }
            }
        }
    }

    private fun permissionSlots(rows: Int): List<Int> {
        val maxSlot = rows.coerceIn(1, 6) * 9
        return listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        ).filter { it < maxSlot }
    }

    private fun render(
        builder: ItemBuilder,
        player: org.bukkit.entity.Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>,
        glow: Boolean
    ) {
        val cfg = clanService.plugin.configService
        builder.name(cfg.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { cfg.formatMessage(player, it, placeholders) })
        builder.glow(glow)
    }

    private fun parseMaterial(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)
}
