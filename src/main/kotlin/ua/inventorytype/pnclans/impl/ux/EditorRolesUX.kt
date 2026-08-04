package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.api.permission.isTrue
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import java.util.EnumMap

class EditorRolesUX(val _clanService: ClanService) : BaseGui(_clanService) {

    init {
        title("Клан > Настройки")
        rows(3)

        var roles = ClanRole.entries
            .filter { it != ClanRole.LEADER }
            .sortedBy { it.weight }
            .toMutableList()

        var indexSlot = 10

        for (role in roles) {

            slot(indexSlot++) {
                dynamicItem(role.icon) { player ->
                    val clan = this@EditorRolesUX.clanService.getClanUser(player)!!
                    val isS = clan.isSettingEnabled(ClanSetting.PVP)

                    val permsCount = clan.rolePermissions[role]?.size ?: "Ошибка #331"

                    name("&#FC7D37Роль: ${this@EditorRolesUX.getDisplayNameRole(role)}")
                    lore(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fИерархия: &#5EFD7D#${role.weight}",
                        " &7- &fАктивных прав: &e$permsCount шт.",
                        "",
                        "&#FC65DF «Описание»",
                        " &7- &fНастройка доступа и возможностей",
                        " &7- &fучастников с рангом &e${this@EditorRolesUX.getDisplayNameRole(role)}&f.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы настроить права"
                    )
                }

                onClick { player, _ ->
                    RolePermissionsUX.get(this@EditorRolesUX.clanService, role, this@EditorRolesUX).open(player)
                }
            }
        }
    }


    fun getDisplayNameRole(role: ClanRole): String { //TODO Надо будет вытягивать название из конфигурации.
        return when (role) {
            ClanRole.MEMBER -> "MEMBER"
            ClanRole.ELDER -> "ELDER"
            ClanRole.DEPUTY -> "DEPUTY"
            ClanRole.LEADER -> "LEADER"
        }
    }
}

class RolePermissionsUX private constructor(
    clanService: ClanService,
    val targetRole: ClanRole,
    val editorRolesUX: EditorRolesUX
) : BaseGui(clanService) {

    companion object {
        private val cache = EnumMap<ClanRole, RolePermissionsUX>(ClanRole::class.java)

        /**
         * Возвращает кэшированный экземпляр GUI для конкретной роли.
         * Если его ещё нет — создаёт один раз и сохраняет.
         */
        fun get(clanService: ClanService, role: ClanRole, editorRolesUX: EditorRolesUX): RolePermissionsUX {
            return cache.getOrPut(role) { RolePermissionsUX(clanService, role, editorRolesUX) }
        }
    }

    init {
        title("Права роли > ${targetRole.name}")
        rows(5)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val allPermissions = ClanPerms.ALL_PERMISSIONS

        allPermissions.forEachIndexed { index, perm ->
            val slotIndex = 10 + (index / 7) * 9 + (index % 7)

            slot(slotIndex) {
                dynamicItem(perm.icon) { player ->
                    val clan = this@RolePermissionsUX.clanService.getClanUser(player)!!
                    val isEnabled = clan.hasRolePermission(this@RolePermissionsUX.targetRole, perm).isTrue

                    name("&6Право: &f${perm.displayName}")
                    lore(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fСтатус: ${if (isEnabled) "&aРазрешено" else "&cЗапрещено"}",
                        "",
                        "&#FC65DF «Описание»",
                        " &7- &f${perm.description}",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (isEnabled) "&cЗапретить" else "&aРазрешить"}"
                    )
                }

                onClick { player, event ->
                    val clan = this@RolePermissionsUX.clanService.getClanUser(player) ?: return@onClick
                    val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                    if (clan.getUserRole(user) != ClanRole.LEADER) {
                        player.sendMessage("&cУ вас нет прав на редактирование.")
                        return@onClick
                    }

                    val isEnabled = clan.hasRolePermission(this@RolePermissionsUX.targetRole, perm).isTrue

                    if (isEnabled) {
                        clan.revokeRolePermission(this@RolePermissionsUX.targetRole, perm)
                    } else {
                        clan.grantRolePermission(this@RolePermissionsUX.targetRole, Pair(perm, Permission.Flag.TRUE))
                    }

                    this@RolePermissionsUX.updateSlot(event.slot, player)
                }
            }
        }

        slot(36) {
            item(Material.ARROW) {
                name("&cНазад")
                lore("&7Вернуться к выбору ролей")
            }
            onClick { player, _ ->
                this@RolePermissionsUX.editorRolesUX.open(player) //TODO
            }
        }
    }
}