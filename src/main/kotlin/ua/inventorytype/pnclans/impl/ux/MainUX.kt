package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.api.permission.isTrue
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

class MainUX(val _clanService: ClanService) : BaseGui(_clanService) {

    init {
        title("Мой Клан")
        rows(5)
        border(Material.GRAY_STAINED_GLASS_PANE)

        // =========================================================
        // ВЕРХНИЙ И СРЕДНИЙ РЯД (Основная информация и доступ)
        // =========================================================

        // [Слот 20] УЧАСТНИКИ
        slot(20) {
            dynamicItem(Material.PLAYER_HEAD) { player ->
                val clan = this@MainUX.clanService.getClanUser(player)!!
                val totalMembers = clan.users.size
                val onlineMembers = clan.onlineCount

                name("&#FC7D37Участники клана")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fВсего игроков: &b$totalMembers чел.",
                    " &7- &fСейчас в сети: &a$onlineMembers чел.",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fУправление составом клана.",
                    " &7- &fЗдесь можно повышать, понижать",
                    " &7- &fи исключать участников.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть список"
                )
            }
            onClick { player, _ ->
                // TODO: Открыть MembersUX(clanService).open(player)
                player.sendMessage("&aОткрытие списка участников...")
            }
        }

        // [Слот 22] СТАТИСТИКА КЛАНА (ЦЕНТР)
        slot(22) {
            dynamicItem(Material.NETHER_STAR) { player ->
                val clan = this@MainUX.clanService.getClanUser(player)!!

                // Заглушки для твоей будущей статистики
                val level = 1 // clan.level
                val mmr = 1050 // clan.mmr
                val kills = 120 // clan.kills
                val deaths = 45 // clan.deaths
                val kda = if (deaths == 0) "N/A" else String.format("%.2f", kills.toDouble() / deaths)

                name("&#FC7D37${clan.name}")
                lore(
                    "",
                    "&#9EFC65 «Основное»",
                    " &7- &fУровень клана: &#5EFD7D$level лвл.",
                    " &7- &fОчки рейтинга (MMR): &e$mmr",
                    "",
                    "&#FC65DF «Боевая сводка»",
                    " &7- &fУбийств: &a$kills",
                    " &7- &fСмертей: &c$deaths",
                    " &7- &fKDA: &e$kda",
                    ""
                )
            }
        }

        // [Слот 24] КЛАНОВЫЙ СУНДУК
        slot(24) {
            dynamicItem(Material.CHEST) { player ->
                val clan = this@MainUX.clanService.getClanUser(player)!!
                val isS = clan.isSettingEnabled(ClanSetting.CHEST)

                name("&#FC7D37Клановый Сундук")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fДоступ: ${if (isS) "&aОткрыт" else "&cЗакрыт"}",
                    " &7- &fВместимость: &e27 слотов", // TODO: брать из уровня
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fОбщее хранилище ресурсов клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть склад"
                )
            }
            onClick { player, _ ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Action.OPEN_CHEST) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет прав для доступа к сундуку.")
                    return@onClick
                }

                if (!clan.isSettingEnabled(ClanSetting.CHEST)) {
                    player.sendMessage("&cСундук клана временно закрыт лидером.")
                    return@onClick
                }

                // TODO: Открыть инвентарь сундука
                player.sendMessage("&aОткрытие сундука...")
            }
        }


        // =========================================================
        // НИЖНИЙ РЯД (Функциональные кнопки - 29, 30, 31, 32, 33)
        // =========================================================

        // [Слот 29] КАЗНА
        slot(29) {
            dynamicItem(Material.GOLD_INGOT) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem
                val balance = 15000 // TODO: clan.bankBalance

                name("&#FC7D37Казна клана")
                lore(
                    "",
                    "&#9EFC65 «Баланс»",
                    " &7- &fСчет: &#5EFD7D${if (clan.hasUserPermission(user, ClanPerms.Bank.SEE).isTrue) "$balance" else "*****"} ⛁",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fБанк клана для покупки улучшений",
                    " &7- &fи расширения вместимости.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fдля управления средствами"
                )
            }
            onClick { player, _ ->
                player.sendMessage("&aОткрытие меню банка...")
            }
        }

        // [Слот 30] КЛАНОВЫЕ ДОМА
        slot(30) {
            dynamicItem(Material.RED_BED) { player ->
                name("&#FC7D37Клановые Дома")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fУстановлено: &e1&7/&e3 точек", // TODO: брать из клана
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fОбщие точки телепортации для",
                    " &7- &fбыстрого сбора участников.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть список домов"
                )
            }
            onClick { player, _ ->
                player.sendMessage("&aОткрытие меню домов...")
            }
        }

        // [Слот 31] ТОП КЛАНОВ (Центр нижнего ряда)
        slot(31) {
            dynamicItem(Material.DRAGON_EGG) { player ->
                name("&#FC7D37Топ Кланов")
                lore(
                    "",
                    "&#9EFC65 «Позиция»",
                    " &7- &fВаше место: &e#14", // TODO: считать место в топе
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fРейтинг лучших кланов сервера",
                    " &7- &fпо количеству MMR и убийствам.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы посмотреть таблицу"
                )
            }
            onClick { player, _ ->
                player.sendMessage("&aОткрытие зала славы...")
            }
        }

        // [Слот 32] ПРИГЛАШЕНИЯ
        slot(32) {
            dynamicItem(Material.WRITABLE_BOOK) { player ->
                name("&#FC7D37Приглашения в клан")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fВы можете приглашать игроков, ",
                    " &7- &fи они смогут к вам присоединиться.",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fУправление входящими запросами",
                    " &7- &fи рассылка инвайтов.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы управлять приглашениями"
                )
            }
            onClick { player, _ ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Members.INVITE) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет прав приглашать игроков.")
                    return@onClick
                }

                player.sendMessage("&aОткрытие меню приглашений...")
            }
        }

        // [Слот 33] НАСТРОЙКИ (переход в SettingsUX)
        slot(33) {
            dynamicItem(Material.COMPARATOR) { player ->
                name("&#FC7D37Настройки клана")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fУправление ролями, чатом,",
                    " &7- &fрежимом PvP и уведомлениями.",
                    "",
                    "&#FC65DF «Требования»",
                    " &7- &fДоступно только Лидеру и Офицерам.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть настройки"
                )
            }
            onClick { player, _ ->
                // Открываем готовое меню настроек
                SettingsUX(this@MainUX.clanService).open(player)
            }
        }

        // =========================================================
        // САМЫЙ НИЗ (Слот 40 - Выход / Расформ)
        // =========================================================
        slot(40) {
            dynamicItem(Material.BARRIER) { player ->
                val clan = this@MainUX.clanService.getClanUser(player)!!
                val user = clan.users.find { it.uuid == player.uniqueId }!!
                val isLeader = clan.getUserRole(user) == ClanRole.LEADER

                if (isLeader) {
                    name("&#FC3737Распустить клан") //TODO Ну просто так тоже удалить нельзя. Не должно остаться там ресурсов.
                    lore(
                        "",
                        "&#FC65DF «Внимание»",
                        " &7- &fВы являетесь Лидером.",
                        " &7- &fЭто действие навсегда удалит клан,",
                        " &7- &fказну и клановые дома.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы удалить клан"
                    )
                } else {
                    name("&#FC3737Покинуть клан")
                    lore(
                        "",
                        "&#FC65DF «Внимание»",
                        " &7- &fВы потеряете доступ к казне,",
                        " &7- &fсундуку и клановым домам.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы выйти"
                    )
                }
            }
            onClick { player, _ ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick
                val isLeader = clan.getUserRole(user) == ClanRole.LEADER

                if (isLeader) {
                    // TODO: Открыть меню подтверждения расформа (чтобы случайно не снес)
                    player.sendMessage("&cОткрыто подтверждение расформа...")
                } else {
                    // TODO: Открыть меню подтверждения выхода
                    player.sendMessage("&cОткрыто подтверждение выхода...")
                }
            }
        }
    }
}