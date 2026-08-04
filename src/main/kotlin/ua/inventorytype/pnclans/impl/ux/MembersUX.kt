package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

/**
 * Paginated clan member list GUI supporting comprehensive member management operations.
 *
 * Displays up to 28 member heads per page, sorted by role weight (descending) then by name.
 * Each head shows role display name, online status, and available management actions:
 * - **LMB**: Promote the member one rank up.
 * - **RMB**: Demote the member one rank down.
 * - **Shift+RMB**: Kick the member (requires [ClanPerms.Members.KICK]).
 * - **Shift+LMB**: Transfer clan leadership to this member (LEADER only).
 *
 * All feedback messages are dispatched through the [ua.inventorytype.pnclans.api.Action] system
 * configured in `messages.yml`.
 *
 * @param clanService The clan service providing member data and persistence.
 * @param page The current page index (zero-based).
 */
class MembersUX(
    clanService: ClanService,
    val page: Int = 0
) : BaseGui(clanService) {

    init {
        val currentPage = page
        title("Участники > Страница ${currentPage + 1}")
        rows(6)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val memberSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        for (i in memberSlots.indices) {
            slot(memberSlots[i]) {
                dynamicItem(Material.PLAYER_HEAD) { viewer ->
                    val service = this@MembersUX.clanService
                    val clan = service.getClanUser(viewer) ?: return@dynamicItem null

                    val allMembers = clan.users.sortedWith(
                        compareByDescending<User> { clan.getUserRole(it).weight }
                            .thenBy { it.playerName }
                    )

                    val index = (currentPage * memberSlots.size) + i
                    if (index >= allMembers.size) return@dynamicItem null

                    val targetUser = allMembers[index]
                    val targetRole = clan.getUserRole(targetUser)
                    val myUser = clan.users.find { it.uuid == viewer.uniqueId } ?: return@dynamicItem null
                    val myRole = clan.getUserRole(myUser)

                    val isMe = targetUser.uuid == viewer.uniqueId
                    val canManage = myRole.weight > targetRole.weight && !isMe
                    val isLeader = myRole == ClanRole.LEADER

                    val isOnline = targetUser.isOnline
                    val roleDisplayName = service.plugin.configService.getRoleDisplayName(targetRole)

                    name("&#FC7D37${targetUser.playerName}")
                    val loreLines = mutableListOf(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fДолжность: &b$roleDisplayName",
                        " &7- &fСтатус: ${if (isOnline) "&aОнлайн" else "&cОффлайн"}",
                        ""
                    )

                    if (canManage) {
                        loreLines.add("&#FF8702 «Управление»")
                        loreLines.add(" &7- &fЛКМ: &aПовысить")
                        loreLines.add(" &7- &fПКМ: &cПонизить")
                        loreLines.add(" &7- &fShift+ПКМ: &4Исключить")

                        if (isLeader) {
                            loreLines.add(" &7- &fShift+ЛКМ: &eПередать лидерство")
                        }
                    } else if (isMe) {
                        loreLines.add(" &7- &fЭто ваш профиль.")
                    } else {
                        loreLines.add(" &cУ вас недостаточно прав для управления.")
                    }

                    lore(*loreLines.toTypedArray())
                    null
                }

                onClick { viewer, event ->
                    val service = this@MembersUX.clanService
                    val cfg = service.plugin.configService
                    val clan = service.getClanUser(viewer) ?: return@onClick
                    val allMembers = clan.users.sortedWith(
                        compareByDescending<User> { clan.getUserRole(it).weight }
                            .thenBy { it.playerName }
                    )

                    val index = (currentPage * memberSlots.size) + i
                    if (index >= allMembers.size) return@onClick

                    val targetUser = allMembers[index]
                    val myUser = clan.users.find { it.uuid == viewer.uniqueId } ?: return@onClick

                    val myRole = clan.getUserRole(myUser)
                    val targetRole = clan.getUserRole(targetUser)

                    if (targetUser.uuid == viewer.uniqueId) return@onClick
                    if (myRole.weight <= targetRole.weight) {
                        cfg.send(viewer, cfg.messages.members.cannotManageHigherRank)
                        return@onClick
                    }

                    if (event.isShiftClick && event.isRightClick) {
                        if (!clan.hasPermission(myUser, ClanPerms.Members.KICK)) {
                            cfg.send(viewer, cfg.messages.members.noPermissionKick)
                            return@onClick
                        }
                        clan.removeUser(targetUser.uuid)
                        service.saveClan(clan)
                        cfg.send(viewer, cfg.messages.members.kicked, mapOf("player" to targetUser.playerName))
                        this@MembersUX.update(viewer)
                        return@onClick
                    }

                    if (event.isShiftClick && event.isLeftClick && myRole == ClanRole.LEADER) {
                        clan.setUserRole(myUser, ClanRole.DEPUTY)
                        clan.setUserRole(targetUser, ClanRole.LEADER)
                        service.saveClan(clan)
                        cfg.send(viewer, cfg.messages.members.leaderTransferred, mapOf("player" to targetUser.playerName))
                        viewer.closeInventory()
                        return@onClick
                    }

                    val rolesSorted = ClanRole.entries.sortedBy { it.weight }
                    val currentRoleIndex = rolesSorted.indexOf(targetRole)

                    if (event.isLeftClick && !event.isShiftClick) {
                        if (currentRoleIndex + 1 < rolesSorted.size) {
                            val nextRole = rolesSorted[currentRoleIndex + 1]
                            if (nextRole.weight >= myRole.weight && myRole != ClanRole.LEADER) {
                                cfg.send(viewer, cfg.messages.members.cannotPromote)
                                return@onClick
                            }
                            clan.setUserRole(targetUser, nextRole)
                            service.saveClan(clan)
                            cfg.send(viewer, cfg.messages.members.promoted, mapOf(
                                "player" to targetUser.playerName,
                                "role" to cfg.getRoleDisplayName(nextRole)
                            ))
                            this@MembersUX.updateSlot(event.slot, viewer)
                        }
                    }

                    if (event.isRightClick && !event.isShiftClick) {
                        if (currentRoleIndex > 0) {
                            val prevRole = rolesSorted[currentRoleIndex - 1]
                            clan.setUserRole(targetUser, prevRole)
                            service.saveClan(clan)
                            cfg.send(viewer, cfg.messages.members.demoted, mapOf(
                                "player" to targetUser.playerName,
                                "role" to cfg.getRoleDisplayName(prevRole)
                            ))
                            this@MembersUX.updateSlot(event.slot, viewer)
                        }
                    }
                }
            }
        }

        slot(48) {
            dynamicItem(Material.ARROW) {
                if (currentPage > 0) {
                    name("&a← Предыдущая страница")
                    lore("&7Нажмите, чтобы вернуться назад.")
                }
                null
            }
            onClick { player, _ ->
                if (currentPage > 0) MembersUX(this@MembersUX.clanService, currentPage - 1).open(player)
            }
        }

        slot(49) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в меню")
                lore("&7Нажмите, чтобы открыть главное меню.")
            }
            onClick { player, _ ->
                MainUX(this@MembersUX.clanService).open(player)
            }
        }

        slot(50) {
            dynamicItem(Material.ARROW) { viewer ->
                val service = this@MembersUX.clanService
                val clan = service.getClanUser(viewer) ?: return@dynamicItem null
                val maxPages = ceil(clan.users.size / 28.0).toInt()

                if (currentPage + 1 < maxPages) {
                    name("&aСледующая страница →")
                    lore("&7Нажмите, чтобы перейти дальше.")
                }
                null
            }
            onClick { viewer, _ ->
                val service = this@MembersUX.clanService
                val clan = service.getClanUser(viewer) ?: return@onClick
                val maxPages = ceil(clan.users.size / 28.0).toInt()
                if (currentPage + 1 < maxPages) MembersUX(service, currentPage + 1).open(viewer)
            }
        }
    }
}