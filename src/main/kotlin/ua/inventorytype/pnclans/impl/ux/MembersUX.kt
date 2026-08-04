package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.api.permission.isTrue
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

class MembersUX(
    val _clanService: ClanService,
    val page: Int = 0 // Текущая страница (по умолчанию 0)
) : BaseGui(_clanService) {

    init {
        title("Участники > Страница ${page + 1}")
        rows(6) // 54 слота для пагинации
        border(Material.GRAY_STAINED_GLASS_PANE)

        // Слоты под самих игроков (28 слотов в центре)
        val memberSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        // Динамически заполняем слоты участниками при открытии
        for (i in memberSlots.indices) {
            slot(memberSlots[i]) {
                dynamicItem(Material.PLAYER_HEAD) { viewer ->
                    val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@dynamicItem

                    // Сортируем список: сначала Лидер, потом по весу, потом по нику
                    val allMembers = clan.users.sortedWith(
                        compareByDescending<User> { clan.getUserRole(it).weight }
                            .thenBy { it.playerName }
                    )

                    val maxPages = ceil(allMembers.size / memberSlots.size.toDouble()).toInt()
                    val index = (page * memberSlots.size) + i

                    // Если на этот слот не хватило игрока — не рисуем предмет
                    if (index >= allMembers.size) return@dynamicItem null

                    val targetUser = allMembers[index]
                    val targetRole = clan.getUserRole(targetUser)
                    val myUser = clan.users.find { it.uuid == viewer.uniqueId }!!
                    val myRole = clan.getUserRole(myUser)

                    // Проверка иерархии: могу ли я управлять этим игроком?
                    // Я могу управлять, если мой вес СТРОГО больше его веса.
                    val isMe = targetUser.uuid == viewer.uniqueId
                    val canManage = myRole.weight > targetRole.weight && !isMe
                    val isLeader = myRole == ClanRole.LEADER

                    // TODO: Вытянуть реальную стату
                    val isOnline = true // Bukkit.getPlayer(targetUser.uuid) != null
                    val joinedDate = "12.08.2026"
                    val kda = "1.5"
                    val contribution = 5000

                    name("&#FC7D37${targetUser.playerName}")
                    val loreLines = mutableListOf(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fРоль: &#5EFD7D${targetRole.name}",
                        " &7- &fСтатус: ${if (isOnline) "&aОнлайн" else "&cОффлайн"}",
                        " &7- &fВ клане с: &e$joinedDate",
                        "",
                        "&#FC65DF «Статистика»",
                        " &7- &fKDA: &e$kda",
                        " &7- &fВзнос в казну: &e$contribution ⛁"
                    )

                    if (canManage) {
                        loreLines.add("")
                        loreLines.add("&#FF8702 «Управление»")
                        loreLines.add(" &7- &fЛКМ: &aПовысить")
                        loreLines.add(" &7- &fПКМ: &cПонизить")
                        loreLines.add(" &7- &fShift+ПКМ: &4Исключить")

                        if (isLeader) {
                            loreLines.add(" &7- &fShift+ЛКМ: &eПередать лидерство")
                        }
                    } else if (isMe) {
                        loreLines.add("")
                        loreLines.add(" &7- &fЭто ваш профиль.")
                    } else {
                        loreLines.add("")
                        loreLines.add(" &cУ вас недостаточно прав для управления.")
                    }

                    lore(*loreLines.toTypedArray())
                }

                onClick { viewer, event ->
                    val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@onClick
                    val allMembers = clan.users.sortedWith(
                        compareByDescending<User> { clan.getUserRole(it).weight }
                            .thenBy { it.playerName }
                    )

                    val index = (page * memberSlots.size) + i
                    if (index >= allMembers.size) return@onClick

                    val targetUser = allMembers[index]
                    val myUser = clan.users.find { it.uuid == viewer.uniqueId } ?: return@onClick

                    val myRole = clan.getUserRole(myUser)
                    val targetRole = clan.getUserRole(targetUser)

                    // Железобетонная защита
                    if (targetUser.uuid == viewer.uniqueId) return@onClick
                    if (myRole.weight <= targetRole.weight) {
                        viewer.sendMessage("&cВы не можете управлять игроком с таким же или более высоким рангом.")
                        return@onClick
                    }

                    // Логика кликов
                    if (event.isShiftClick && event.isRightClick) {
                        // КИК (Требуется пермишен)
                        if (clan.hasPermission(myUser, ClanPerms.Members.KICK) != Permission.Flag.TRUE) {
                            viewer.sendMessage("&cНет прав на исключение.")
                            return@onClick
                        }
                        clan.removeUser(targetUser.uuid)
                        viewer.sendMessage("&aВы исключили ${targetUser.name} из клана.")
                        this@MembersUX.update(viewer) // Обновляем весь GUI, так как список съехал
                        return@onClick
                    }

                    if (event.isShiftClick && event.isLeftClick && myRole == ClanRole.LEADER) {
                        // ПЕРЕДАЧА ЛИДЕРСТВА
                        clan.setUserRole(myUser.uuid, ClanRole.DEPUTY) // Себя делаем замом
                        clan.setUserRole(targetUser.uuid, ClanRole.LEADER) // Его лидером
                        viewer.sendMessage("&aВы передали права лидера игроку ${targetUser.name}.")
                        viewer.closeInventory()
                        return@onClick
                    }

                    val rolesSorted = ClanRole.entries.sortedBy { it.weight }
                    val currentRoleIndex = rolesSorted.indexOf(targetRole)

                    if (event.isLeftClick && !event.isShiftClick) {
                        // ПОВЫШЕНИЕ
                        if (currentRoleIndex + 1 < rolesSorted.size) {
                            val nextRole = rolesSorted[currentRoleIndex + 1]
                            // Проверка: мы не можем выдать роль, равную или выше нашей
                            if (nextRole.weight >= myRole.weight && myRole != ClanRole.LEADER) {
                                viewer.sendMessage("&cВы не можете повысить игрока до этого ранга.")
                                return@onClick
                            }
                            clan.setUserRole(targetUser.uuid, nextRole)
                            viewer.sendMessage("&aИгрок ${targetUser.name} повышен до ${nextRole.name}.")
                            this@MembersUX.updateSlot(event.slot, viewer)
                        }
                    }

                    if (event.isRightClick && !event.isShiftClick) {
                        // ПОНИЖЕНИЕ
                        if (currentRoleIndex > 0) {
                            val prevRole = rolesSorted[currentRoleIndex - 1]
                            clan.setUserRole(targetUser.uuid, prevRole)
                            viewer.sendMessage("&aИгрок ${targetUser.name} понижен до ${prevRole.name}.")
                            this@MembersUX.updateSlot(event.slot, viewer)
                        }
                    }
                }
            }
        }

        // =========================================================
        // НАВИГАЦИЯ (Нижний ряд: 45 - 53)
        // =========================================================

        // Назад на предыдущую страницу
        slot(48) {
            dynamicItem(Material.ARROW) {
                if (page > 0) {
                    name("&a← Предыдущая страница")
                    lore("&7Нажмите, чтобы вернуться назад.")
                } else null
            }
            onClick { player, _ ->
                if (page > 0) MembersUX(this@MembersUX.clanService, page - 1).open(player)
            }
        }

        // Возврат в Главное Меню
        slot(49) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в меню")
                lore("&7Нажмите, чтобы открыть настройки клана.")
            }
            onClick { player, _ ->
                MainUX(this@MembersUX.clanService).open(player)
            }
        }

        // Вперед на следующую страницу
        slot(50) {
            dynamicItem(Material.ARROW) { viewer ->
                val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@dynamicItem null
                val maxPages = ceil(clan.users.size / 28.0).toInt()

                if (page + 1 < maxPages) {
                    name("&aСледующая страница →")
                    lore("&7Нажмите, чтобы перейти дальше.")
                } else null
            }
            onClick { viewer, _ ->
                val clan = this@MembersUX.clanService.getClanUser(viewer) ?: return@onClick
                val maxPages = ceil(clan.users.size / 28.0).toInt()
                if (page + 1 < maxPages) MembersUX(this@MembersUX.clanService, page + 1).open(viewer)
            }
        }
    }


class UserPermissionsUX(
    clanService: ClanService,
    val targetUser: User,   // Игрок, права которого мы меняем
    val returnPage: Int = 0 // Страница, с которой мы сюда перешли
) : BaseGui(clanService) {

    init {
        title("Права > ${targetUser.playerName}")
        rows(5)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val allPermissions = ClanPerms.ALL_PERMISSIONS

        allPermissions.forEachIndexed { index, perm ->
            val slotIndex = 10 + (index / 7) * 9 + (index % 7)

            slot(slotIndex) {
                dynamicItem(perm.icon) { player ->
                    val clan = this@UserPermissionsUX.clanService.getClanUser(player)!!

                    // Проверяем именно личные права пользователя
                    val hasPerm = clan.hasUserPermission(this@UserPermissionsUX.targetUser, perm).isTrue

                    name("&6Право: &f${perm.displayName}")
                    lore(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fИгрок: &#5EFD7D${this@UserPermissionsUX.targetUser.playerName}",
                        " &7- &fСтатус: ${if (hasPerm) "&aРазрешено" else "&cЗапрещено"}",
                        "",
                        "&#FC65DF «Описание»",
                        " &7- &fИндивидуальная настройка прав",
                        " &7- &fв обход стандартной роли.",
                        " &7- &f${perm.description}",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (hasPerm) "&cЗапретить" else "&aРазрешить"}"
                    )
                }

                onClick { player, event ->
                    val clan = this@UserPermissionsUX.clanService.getClanUser(player) ?: return@onClick
                    val myUser = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                    // Защита от дурака: редактировать права может только тот, у кого есть пермишен
                    if (clan.hasPermission(myUser, ClanPerms.Settings.EDIT_ROLES) != Permission.Flag.TRUE) {
                        player.sendMessage("&cУ вас нет прав на редактирование разрешений.")
                        return@onClick
                    }

                    val hasPerm = clan.hasUserPermission(this@UserPermissionsUX.targetUser, perm).isTrue

                    // TODO: Реализуй методы выдачи/забора персональных прав в классе Clan
                    if (hasPerm) {
                        clan.revokeUserPermission(this@UserPermissionsUX.targetUser, perm)
                    } else {
                        clan.grantUserPermission(this@UserPermissionsUX.targetUser, Pair(perm, Permission.Flag.TRUE))
                    }

                    this@UserPermissionsUX.updateSlot(event.slot, player)
                }
            }
        }

        // Кнопка «Назад» возвращает в список участников на ТУ ЖЕ страницу
        slot(36) {
            item(Material.ARROW) {
                name("&cНазад")
                lore("&7Вернуться к списку участников")
            }
            onClick { player, _ ->
                MembersUX(this@UserPermissionsUX.clanService, this@UserPermissionsUX.returnPage).open(player)
            }
        }
    }
}
}