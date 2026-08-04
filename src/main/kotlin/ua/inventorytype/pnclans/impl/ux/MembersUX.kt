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
 * Displays role icons instead of cosmetic player heads, sorted by role weight then by name.
 * Each icon shows role display name, online status, and available management actions:
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
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.membersMenu
        val memberSlots = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)

        title(menuCfg.title.replace("{page}", (currentPage + 1).toString()).replace("{pages}", "?"))
        rows(menuCfg.rows)
        hotWorldDecor(true)

        fun navPlaceholders(maxPages: Int) = mapOf(
            "page" to (currentPage + 1).toString(),
            "pages" to maxPages.coerceAtLeast(1).toString()
        )

        for (i in memberSlots.indices) {
            slot(memberSlots[i]) {
                dynamicItemNullable(Material.NAME_TAG) { viewer ->
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
                    type(targetRole.icon)
                    val myUser = clan.users.find { it.uuid == viewer.uniqueId } ?: return@dynamicItem null
                    val myRole = clan.getUserRole(myUser)

                    val isMe = targetUser.uuid == viewer.uniqueId
                    val canManage = myRole.weight > targetRole.weight && !isMe
                    val isLeader = myRole == ClanRole.LEADER

                    val isOnline = targetUser.isOnline
                    val roleDisplayName = service.plugin.configService.getRoleDisplayName(targetRole)

                    name("&#FC7D37${targetUser.playerName} &7• $roleDisplayName")
                    val loreLines = mutableListOf(
                        "",
                        "&#9EFC65 «Профиль»",
                        " &7- &fДолжность: &#5EA9FD$roleDisplayName",
                        " &7- &fСтатус: ${if (isOnline) "&#5EFD7DОнлайн" else "&#FC3737Оффлайн"}",
                        " &7- &fИерархия: &e${targetRole.weight}",
                        ""
                    )

                    if (canManage) {
                        loreLines.add("&#FC65DF «Управление»")
                        loreLines.add(" &7- &fЛКМ: &#5EFD7DПовысить")
                        loreLines.add(" &7- &fПКМ: &#FC3737Понизить")
                        loreLines.add(" &7- &fShift+ПКМ: &#FC3737Исключить")

                        if (isLeader) {
                            loreLines.add(" &7- &fShift+ЛКМ: &#FFD700Передать лидерство")
                        }
                        loreLines.add("")
                        loreLines.add("&#FF8702➥ &fИспользуйте клики для управления")
                    } else if (isMe) {
                        loreLines.add("&#FC65DF «Личный профиль»")
                        loreLines.add(" &7- &fЭто ваш профиль в составе клана.")
                    } else {
                        loreLines.add("&c➥ У вас недостаточно прав для управления")
                    }

                    glow(targetRole == ClanRole.LEADER || isMe)
                    lore(*loreLines.toTypedArray())
                    build()
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

        val previousCfg = menuCfg.items["previous"]
        slot(previousCfg?.slot ?: 47) {
            dynamicItem(Material.ARROW) { viewer ->
                val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@dynamicItem null
                val maxPages = ceil(clan.users.size / memberSlots.size.toDouble()).toInt()
                if (currentPage > 0) {
                    name(previousCfg?.name ?: "&#5EA9FD◀ Предыдущая страница")
                    lore(previousCfg?.lore?.map { line -> replace(line, navPlaceholders(maxPages)) } ?: listOf("&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть"))
                    glow(previousCfg?.glow == true)
                } else {
                    type(Material.GRAY_DYE)
                    name("&#FC3737◀ Предыдущая страница")
                    lore("", "&#FC3737 «Недоступно»", " &7- &fВы уже на первой странице.", "", "&c➥ Листать назад нельзя")
                }
                null
            }
            onClick { player, _ ->
                if (currentPage > 0) MembersUX(this@MembersUX.clanService, currentPage - 1).open(player)
            }
        }

        val backCfg = menuCfg.items["back"]
        slot(backCfg?.slot ?: 49) {
            item(parseMaterial(backCfg?.material, Material.RED_CANDLE)) {
                name(backCfg?.name ?: "&#FC3737⏎ Вернуться в меню")
                lore(backCfg?.lore ?: listOf("", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы вернуться"))
            }
            onClick { player, _ ->
                MainUX(this@MembersUX.clanService).open(player)
            }
        }

        val nextCfg = menuCfg.items["next"]
        slot(nextCfg?.slot ?: 51) {
            dynamicItem(Material.ARROW) { viewer ->
                val service = this@MembersUX.clanService
                val clan = service.getClanUser(viewer) ?: return@dynamicItem null
                val maxPages = ceil(clan.users.size / memberSlots.size.toDouble()).toInt()

                if (currentPage + 1 < maxPages) {
                    type(parseMaterial(nextCfg?.material, Material.SPECTRAL_ARROW))
                    name(nextCfg?.name ?: "&#5EA9FDСледующая страница ▶")
                    lore(nextCfg?.lore?.map { line -> replace(line, navPlaceholders(maxPages)) } ?: listOf("&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть"))
                    glow(nextCfg?.glow != false)
                } else {
                    type(Material.GRAY_DYE)
                    name("&#FC3737Следующая страница ▶")
                    lore("", "&#FC3737 «Недоступно»", " &7- &fВы уже на последней странице.", "", "&c➥ Листать вперёд нельзя")
                }
                null
            }
            onClick { viewer, _ ->
                val service = this@MembersUX.clanService
                val clan = service.getClanUser(viewer) ?: return@onClick
                val maxPages = ceil(clan.users.size / memberSlots.size.toDouble()).toInt()
                if (currentPage + 1 < maxPages) MembersUX(service, currentPage + 1).open(viewer)
            }
        }
    }

    companion object {
        private fun parseMaterial(name: String?, fallback: Material): Material =
            runCatching { Material.valueOf(name.orEmpty().uppercase()) }.getOrDefault(fallback)

        private fun replace(template: String, placeholders: Map<String, String>): String =
            placeholders.entries.fold(template) { result, (key, value) -> result.replace("{$key}", value) }
    }
}
