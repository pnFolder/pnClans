package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.api.permission.isTrue
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

class TreasuryUX(val _clanService: ClanService) : BaseGui(_clanService) {

    init {
        title("Финансы Клана")
        rows(6)
        border(Material.BLACK_STAINED_GLASS_PANE)

        // Декор под "Золотое хранилище"
        val goldSlots = listOf(3, 4, 5, 12, 14, 45, 46, 52, 53)
        for (i in goldSlots) {
            slot(i) { item(Material.YELLOW_STAINED_GLASS_PANE) { name(" ") } }
        }

        // =========================================================
        // ЦЕНТР: ОБЩЕЕ ХРАНИЛИЩЕ (Слот 13)
        // =========================================================
        slot(13) {
            dynamicItem(Material.GOLD_BLOCK) { player ->
                val clan = this@TreasuryUX.clanService.getClanUser(player)!!
                val user = clan.users.find { it.uuid == player.uniqueId }!!

                // Проверка права на просмотр баланса (SEE)
                val canSee = clan.hasUserPermission(user, ClanPerms.Bank.SEE).isTrue

                val totalBalance = 150000.0 // TODO: clan.bankBalance
                val weeklyIncome = 24500.0  // TODO: Опционально, можно считать доход за неделю
                val taxRate = "5%"          // TODO: Налог клана, если есть такая механика

                if (canSee) {
                    name("&#FFD700Центральный Банк Клана")
                    lore(
                        "",
                        "&#9EFC65 «Счет»",
                        " &7- &fТекущий баланс: &#5EFD7D$totalBalance ⛁",
                        " &7- &fДоход за неделю: &a+$weeklyIncome ⛁",
                        "",
                        "&#FC65DF «Информация»",
                        " &7- &fНалог на пополнение: &e$taxRate",
                        " &7- &fДеньги используются для",
                        " &7- &fповышения уровня клана."
                    )
                } else {
                    name("&#FFD700Центральный Банк Клана")
                    lore(
                        "",
                        "&#FC3737 «Доступ закрыт»",
                        " &7- &fУ вас нет прав для просмотра",
                        " &7- &fсостояния счета.",
                        "",
                        " &7- &fБаланс: &cСкрыто"
                    )
                }
            }
        }

        // =========================================================
        // ЛЕВО: ПОПОЛНЕНИЕ (Слот 29)
        // =========================================================
        slot(29) {
            dynamicItem(Material.EMERALD) { player ->
                name("&#5EFD7DВнести средства")
                lore(
                    "",
                    "&#9EFC65 «Операция»",
                    " &7- &fПеревод личных денег",
                    " &7- &fна общий счет клана.",
                    "",
                    "&#FC65DF «Бонусы»",
                    " &7- &fПовышает ваш личный рейтинг",
                    " &7- &fвнутри клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ввести сумму"
                )
            }
            onClick { player, _ ->
                val clan = this@TreasuryUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Bank.DEPOSIT) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет прав на пополнение казны.")
                    return@onClick
                }

                // TODO: Здесь вызываешь AnvilGUI для ввода суммы пополнения
                player.sendMessage("&aОткрытие окна ввода суммы для пополнения...")
                player.closeInventory()
            }
        }

        // =========================================================
        // ПРАВО: СНЯТИЕ СРЕДСТВ (Слот 33)
        // =========================================================
        slot(33) {
            dynamicItem(Material.REDSTONE) { player ->
                name("&#FC3737Снять средства")
                lore(
                    "",
                    "&#FC3737 «Операция»",
                    " &7- &fВывод средств из казны",
                    " &7- &fна ваш личный счет.",
                    "",
                    "&#FC65DF «Требования»",
                    " &7- &fДоступно только Лидеру",
                    " &7- &fи доверенным Заместителям.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ввести сумму"
                )
            }
            onClick { player, _ ->
                val clan = this@TreasuryUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Bank.WITHDRAW) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет прав для вывода средств из казны.")
                    return@onClick
                }

                // TODO: Здесь вызываешь AnvilGUI для ввода суммы снятия
                player.sendMessage("&cОткрытие окна ввода суммы для снятия...")
                player.closeInventory()
            }
        }

        // =========================================================
        // ЦЕНТР (НИЗ): ЛИЧНАЯ СТАТИСТИКА ВКЛАДА (Слот 31)
        // =========================================================
        slot(31) {
            dynamicItem(Material.PLAYER_HEAD) { player ->
                val myContribution = 45000.0 // TODO: clan.getPlayerContribution(player.uniqueId)
                val rankInClan = 2 // TODO: Место игрока по вкладам среди соклановцев

                name("&#FC7D37Ваша статистика")
                lore(
                    "",
                    "&#9EFC65 «Личный вклад»",
                    " &7- &fВсего инвестировано: &e$myContribution ⛁",
                    " &7- &fМесто в клане: &a#$rankInClan",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fЧем больше вы вкладываете,",
                    " &7- &fтем быстрее клан достигает",
                    " &7- &fновых высот."
                )
            }
        }

        // =========================================================
        // НИЖНИЙ РЯД: ИСТОРИЯ ТРАНЗАКЦИЙ (Слот 40)
        // =========================================================
        slot(40) {
            dynamicItem(Material.WRITABLE_BOOK) { player ->
                name("&#5EA9FDИстория операций")
                lore(
                    "",
                    "&#9EFC65 «Последние действия»",
                    " &f1. &a+500 ⛁ &7(от xX_Nagibator_Xx)",
                    " &f2. &c-15000 ⛁ &7(от Lider_2010)",
                    " &f3. &a+1200 ⛁ &7(от Petya_Pro)",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fПрозрачная выписка из банка.",
                    " &7- &fЗдесь видно, кто пополнял,",
                    " &7- &fи кто воровал деньги.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть полный лог"
                )
            }
            onClick { player, _ ->
                // TODO: Открыть новое меню HistoryUX с логами
                player.sendMessage("&bОткрытие истории банка...")
            }
        }

        // =========================================================
        // НАВИГАЦИЯ (Возврат) (Слот 49)
        // =========================================================
        slot(49) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в меню")
                lore("&7Нажмите, чтобы вернуться к управлению.")
            }
            onClick { player, _ ->
                MainUX(this@TreasuryUX.clanService).open(player)
            }
        }
    }
}

// Временный дата-класс для примера (перенеси его в API или используй свой)
enum class LogType { DEPOSIT, WITHDRAW, UPGRADE }
data class ClanLog(
    val type: LogType,
    val playerName: String,
    val amount: Double,
    val date: String,
    val time: String
)

class HistoryUX(
    val _clanService: ClanService,
    var page: Int = 0 // Наша фирменная пагинация на лету
) : BaseGui(_clanService) {

    init {
        title("Финансы > История (Стр. ${page + 1})")
        rows(6)
        border(Material.GRAY_STAINED_GLASS_PANE)

        // Декор в стиле "Серверный Лог" (Синие панели по углам)
        val decorSlots = listOf(1, 7, 9, 17, 36, 44, 46, 52)
        for (i in decorSlots) {
            slot(i) { item(Material.LIGHT_BLUE_STAINED_GLASS_PANE) { name(" ") } }
        }

        // Центральная сетка под логи (28 слотов)
        val logSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        for (i in logSlots.indices) {
            slot(logSlots[i]) {
                dynamicItem(Material.PAPER) { player ->
                    val clan = this@HistoryUX.clanService.getClanUser(player) ?: return@dynamicItem

                    // TODO: Получай реальные логи из базы (отсортированные от новых к старым!)
                    // val allLogs = clan.getLogs().sortedByDescending { it.timestamp }

                    // --- ЗАГЛУШКА ДЛЯ ДЕМОНСТРАЦИИ ФОРМАТА ---
                    val allLogs = listOf(
                        ClanLog(LogType.DEPOSIT, "xX_Nagibator_Xx", 5000.0, "04.08.2026", "21:14"),
                        ClanLog(LogType.WITHDRAW, "Lider_2010", 15000.0, "03.08.2026", "14:30"),
                        ClanLog(LogType.UPGRADE, "Lider_2010", 50000.0, "01.08.2026", "09:00"),
                        ClanLog(LogType.DEPOSIT, "Petya_Pro", 1200.0, "30.07.2026", "18:45")
                    )

                    val maxPages = ceil(allLogs.size / logSlots.size.toDouble()).toInt()
                    val index = (page * logSlots.size) + i

                    if (index >= allLogs.size) return@dynamicItem null

                    val log = allLogs[index]

                    // Полностью динамическое оформление в зависимости от типа лога
                    val (icon, titleColor, operationText, amountColor) = when (log.type) {
                        LogType.DEPOSIT -> listOf(Material.EMERALD, "&#5EFD7D", "Пополнение казны", "&a+")
                        LogType.WITHDRAW -> listOf(Material.REDSTONE, "&#FC3737", "Снятие средств", "&c-")
                        LogType.UPGRADE -> listOf(Material.NETHER_STAR, "&#FC65DF", "Улучшение клана", "&c-")
                    }

                    this.type = icon as Material

                    name("${titleColor}Операция: $operationText")
                    lore(
                        "",
                        "&#9EFC65 «Детали транзакции»",
                        " &7- &fИнициатор: &e${log.playerName}",
                        " &7- &fСумма: $amountColor${log.amount} ⛁",
                        "",
                        "&#5EA9FD «Время»",
                        " &7- &fДата: &b${log.date}",
                        " &7- &fВремя: &b${log.time}"
                    )
                }
            }
        }

        // =========================================================
        // ПРАВИЛЬНАЯ НАВИГАЦИЯ (БЕЗ УТЕЧЕК ПАМЯТИ)
        // =========================================================
        slot(48) {
            dynamicItem(Material.ARROW) {
                if (page > 0) {
                    name("&a← Новые записи")
                    lore("&7Перейти к недавним событиям.")
                } else null
            }
            onClick { player, _ ->
                if (page > 0) {
                    this@HistoryUX.page--
                    this@HistoryUX.update(player) // Перерисовываем на лету!
                }
            }
        }

        slot(49) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в банк")
                lore("&7Нажмите, чтобы закрыть выписку.")
            }
            onClick { player, _ ->
                TreasuryUX(this@HistoryUX.clanService).open(player)
            }
        }

        slot(50) {
            dynamicItem(Material.ARROW) { viewer ->
                val clan = this@HistoryUX.clanService.getClanUser(viewer) ?: return@dynamicItem null

                // Заглушка, подставь свой clan.getLogs().size
                val totalLogs = 4
                val maxPages = ceil(totalLogs / 28.0).toInt()

                if (page + 1 < maxPages) {
                    name("&aСтарые записи →")
                    lore("&7Перейти к прошлым событиям.")
                } else null
            }
            onClick { viewer, _ ->
                // Заглушка, подставь свой clan.getLogs().size
                val totalLogs = 4
                val maxPages = ceil(totalLogs / 28.0).toInt()

                if (page + 1 < maxPages) {
                    this@HistoryUX.page++
                    this@HistoryUX.update(viewer) // Перерисовываем на лету!
                }
            }
        }
    }
}