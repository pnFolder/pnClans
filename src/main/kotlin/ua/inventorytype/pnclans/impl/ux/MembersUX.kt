package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.clan.ClanStatsPeriod
import ua.inventorytype.pnclans.impl.clan.ClanUser
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

/** Paginated config-driven clan member list and management GUI. */
class MembersUX(
    clanService: ClanService,
    val page: Int = 0
) : BaseGui(clanService) {

    init {
        val currentPage = page.coerceAtLeast(0)
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.membersMenu
        val memberSlots = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)

        title(menuCfg.title.replace("{page}", (currentPage + 1).toString()).replace("{pages}", "?"))
        rows(menuCfg.rows.coerceIn(1, 6))
        hotWorldDecor(true)

        fun navPlaceholders(maxPages: Int) = mapOf(
            "page" to (currentPage + 1).toString(),
            "pages" to maxPages.coerceAtLeast(1).toString()
        )

        memberSlots.forEachIndexed { position, slotIndex ->
            slot(slotIndex) {
                dynamicItemNullable(Material.NAME_TAG) { viewer ->
                    val service = this@MembersUX.clanService
                    val clan = service.getClanUser(viewer) ?: return@dynamicItemNullable null
                    val allMembers = this@MembersUX.sortedMembers(clan)
                    val index = currentPage * memberSlots.size + position
                    val targetUser = allMembers.getOrNull(index) ?: return@dynamicItemNullable null
                    val targetRole = clan.getUserRole(targetUser)
                    val myUser = clan.getMember(viewer.uniqueId) ?: return@dynamicItemNullable null
                    val myRole = clan.getUserRole(myUser)
                    val isMe = targetUser.uuid == viewer.uniqueId
                    val canManage = myRole.weight > targetRole.weight && !isMe
                    val isLeader = myRole == ClanRole.LEADER
                    val combatMember = targetUser as? ClanUser
                    val dayStats = combatMember?.combatStats(ClanStatsPeriod.DAY)
                    val weekStats = combatMember?.combatStats(ClanStatsPeriod.WEEK)
                    val monthStats = combatMember?.combatStats(ClanStatsPeriod.MONTH)

                    val template = when {
                        isMe -> menuCfg.items["member_self"] ?: return@dynamicItemNullable null
                        canManage -> menuCfg.items["member"] ?: return@dynamicItemNullable null
                        else -> menuCfg.items["member_no_permission"] ?: menuCfg.items["member"] ?: return@dynamicItemNullable null
                    }
                    type(this@MembersUX.parseMaterial(template.material, targetRole.icon))

                    val placeholders = mapOf(
                        "player" to targetUser.playerName,
                        "role" to cfg.getRoleDisplayName(targetRole),
                        "status" to if (targetUser.isOnline) "&#5EFD7DОнлайн" else "&#FC3737Оффлайн",
                        "weight" to targetRole.weight.toString(),
                        "kills_today" to (dayStats?.kills ?: 0).toString(),
                        "deaths_today" to (dayStats?.deaths ?: 0).toString(),
                        "kills_week" to (weekStats?.kills ?: 0).toString(),
                        "deaths_week" to (weekStats?.deaths ?: 0).toString(),
                        "kills_month" to (monthStats?.kills ?: 0).toString(),
                        "deaths_month" to (monthStats?.deaths ?: 0).toString(),
                        "kills_total" to (combatMember?.kills ?: 0).toString(),
                        "deaths_total" to (combatMember?.deaths ?: 0).toString(),
                        "action_promote" to if (targetRole == ClanRole.DEPUTY && isLeader) "&#FFD700Передать лидерство" else "&#5EFD7DПовысить в должности"
                    )
                    name(cfg.formatMessage(viewer, template.name, placeholders))
                    lore(template.lore.map { cfg.formatMessage(viewer, it, placeholders) })
                    glow(template.glow || targetRole == ClanRole.LEADER || isMe)
                    build()
                }

                onClick { viewer, event ->
                    val service = this@MembersUX.clanService
                    val clan = service.getClanUser(viewer) ?: return@onClick
                    val targetUser = this@MembersUX.sortedMembers(clan)
                        .getOrNull(currentPage * memberSlots.size + position) ?: return@onClick
                    val myUser = clan.getMember(viewer.uniqueId) ?: return@onClick
                    val myRole = clan.getUserRole(myUser)
                    val targetRole = clan.getUserRole(targetUser)

                    if (targetUser.uuid == viewer.uniqueId || myRole.weight <= targetRole.weight) return@onClick

                    if (event.isShiftClick && event.isLeftClick && myRole == ClanRole.LEADER) {
                        UserPermissionsUX(service, targetUser, this@MembersUX).open(viewer)
                        return@onClick
                    }

                    if (event.isShiftClick && event.isRightClick) {
                        if (!clan.hasPermission(myUser, ClanPerms.Members.KICK)) {
                            cfg.send(viewer, cfg.messages.members.noPermissionKick)
                            return@onClick
                        }
                        if (!service.removeUserFromClan(clan, targetUser.uuid, kicked = true)) return@onClick
                        cfg.send(viewer, cfg.messages.members.kicked, mapOf("player" to targetUser.playerName))
                        this@MembersUX.update(viewer)
                        return@onClick
                    }

                    val rolesSorted = ClanRole.entries.sortedBy { it.weight }
                    val currentRoleIndex = rolesSorted.indexOf(targetRole)

                    if (event.isLeftClick && !event.isShiftClick && currentRoleIndex + 1 < rolesSorted.size) {
                        val nextRole = rolesSorted[currentRoleIndex + 1]
                        if (nextRole == ClanRole.LEADER) {
                            if (myRole != ClanRole.LEADER) {
                                cfg.send(viewer, cfg.messages.members.cannotPromote)
                                return@onClick
                            }
                            if (!service.transferLeadership(clan, myUser, targetUser).isSuccess) return@onClick
                            cfg.send(viewer, cfg.messages.members.leaderTransferred, mapOf("player" to targetUser.playerName))
                            this@MembersUX.update(viewer)
                            return@onClick
                        }
                        if (nextRole.weight >= myRole.weight) {
                            cfg.send(viewer, cfg.messages.members.cannotPromote)
                            return@onClick
                        }
                        if (!service.changeMemberRole(clan, targetUser, nextRole).isSuccess) return@onClick
                        cfg.send(viewer, cfg.messages.members.promoted, mapOf("player" to targetUser.playerName, "role" to cfg.getRoleDisplayName(nextRole)))
                        this@MembersUX.update(viewer)
                    }

                    if (event.isRightClick && !event.isShiftClick && currentRoleIndex > 0) {
                        val previousRole = rolesSorted[currentRoleIndex - 1]
                        if (!service.changeMemberRole(clan, targetUser, previousRole).isSuccess) return@onClick
                        cfg.send(viewer, cfg.messages.members.demoted, mapOf("player" to targetUser.playerName, "role" to cfg.getRoleDisplayName(previousRole)))
                        this@MembersUX.update(viewer)
                    }
                }
            }
        }

        menuCfg.items["previous"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@MembersUX.parseMaterial(itemCfg.material, Material.ARROW)) { viewer ->
                    val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@dynamicItem null
                    val maxPages = this@MembersUX.maxPages(clan.users.size, memberSlots.size)
                    if (currentPage <= 0) return@dynamicItem null
                    val placeholders = navPlaceholders(maxPages)
                    name(cfg.formatMessage(viewer, itemCfg.name, placeholders))
                    lore(itemCfg.lore.map { cfg.formatMessage(viewer, it, placeholders) })
                    glow(itemCfg.glow)
                    null
                }
                onClick { player, _ ->
                    if (currentPage > 0) MembersUX(this@MembersUX.clanService, currentPage - 1).open(player)
                }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@MembersUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) { viewer ->
                    name(cfg.formatMessage(viewer, itemCfg.name))
                    lore(itemCfg.lore.map { cfg.formatMessage(viewer, it) })
                    glow(itemCfg.glow)
                    null
                }
                onClick { player, _ -> MainUX(this@MembersUX.clanService).open(player) }
            }
        }

        menuCfg.items["next"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@MembersUX.parseMaterial(itemCfg.material, Material.SPECTRAL_ARROW)) { viewer ->
                    val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@dynamicItem null
                    val maxPages = this@MembersUX.maxPages(clan.users.size, memberSlots.size)
                    if (currentPage + 1 >= maxPages) return@dynamicItem null
                    val placeholders = navPlaceholders(maxPages)
                    name(cfg.formatMessage(viewer, itemCfg.name, placeholders))
                    lore(itemCfg.lore.map { cfg.formatMessage(viewer, it, placeholders) })
                    glow(itemCfg.glow)
                    null
                }
                onClick { viewer, _ ->
                    val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@onClick
                    val maxPages = this@MembersUX.maxPages(clan.users.size, memberSlots.size)
                    if (currentPage + 1 < maxPages) MembersUX(this@MembersUX.clanService, currentPage + 1).open(viewer)
                }
            }
        }
    }

    private fun maxPages(memberCount: Int, pageSize: Int): Int =
        ceil(memberCount / pageSize.toDouble()).toInt().coerceAtLeast(1)

    private fun sortedMembers(clan: ua.inventorytype.pnclans.api.clan.Clan): List<User> =
        clan.users.sortedWith(compareByDescending<User> { clan.getUserRole(it).weight }.thenBy { it.playerName })

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
}
