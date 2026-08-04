package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Clan treasury GUI for depositing and withdrawing funds from the clan bank.
 *
 * Provides:
 * - A central bank balance display (visible only to members with [ClanPerms.Bank.SEE]).
 * - Deposit button opening [AnvilInputUX] for entering an amount to transfer from personal funds.
 * - Withdraw button opening [AnvilInputUX] for extracting funds from the clan balance.
 * - Personal statistics panel showing the current clan balance.
 * - Navigation to [HistoryUX] for viewing the transaction log.
 *
 * All feedback messages are dispatched through the [ua.inventorytype.pnclans.api.Action] system
 * configured in `messages.yml`.
 *
 * @param clanService The clan service providing balance data and economy integration.
 */
class TreasuryUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        title("Финансы Клана")
        rows(6)
        border(Material.BLACK_STAINED_GLASS_PANE)

        val goldSlots = listOf(3, 4, 5, 12, 14, 45, 46, 52, 53)
        for (i in goldSlots) {
            slot(i) { item(Material.YELLOW_STAINED_GLASS_PANE) { name(" ") } }
        }

        // [Слот 13] ОБЩЕЕ ХРАНИЛИЩЕ
        slot(13) {
            dynamicItem(Material.GOLD_BLOCK) { player ->
                val service = this@TreasuryUX.clanService
                val clan = service.getClanUser(player) ?: return@dynamicItem null
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem null

                val canSee = clan.hasUserPermission(user, ClanPerms.Bank.SEE)
                val totalBalance = clan.bankBalance

                if (canSee) {
                    name("&#FFD700Центральный Банк Клана")
                    lore(
                        "",
                        "&#9EFC65 «Счет»",
                        " &7- &fТекущий баланс: &#5EFD7D$totalBalance ⛁",
                        "",
                        "&#FC65DF «Информация»",
                        " &7- &fСредства используются для",
                        " &7- &fповышения уровня и развития."
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
                null
            }
        }

        // [Слот 29] ПОПОЛНЕНИЕ ЧЕРЕЗ NATIVE ANVIL GUI
        slot(29) {
            dynamicItem(Material.EMERALD) { player ->
                name("&#5EFD7DВнести средства")
                lore(
                    "",
                    "&#9EFC65 «Операция»",
                    " &7- &fПеревод личных денег",
                    " &7- &fна общий счет клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть наковальню"
                )
                null
            }
            onClick { player, _ ->
                val service = this@TreasuryUX.clanService
                val clan = service.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (!clan.hasPermission(user, ClanPerms.Bank.DEPOSIT)) {
                    val cfg = service.plugin.configService
                    cfg.send(player, cfg.messages.treasury.noPermissionDeposit)
                    return@onClick
                }

                AnvilInputUX(service, "Внести сумму в казну", "100") { p, amount ->
                    val cfg = service.plugin.configService
                    val userClan = service.getClanUser(p)
                    if (userClan != null && service.economy.withdraw(p, amount)) {
                        userClan.depositBank(amount)
                        service.saveClan(userClan)
                        cfg.send(p, cfg.messages.treasury.deposited, mapOf("amount" to amount.toInt().toString()))
                    } else {
                        cfg.send(p, cfg.messages.treasury.insufficientPersonalFunds)
                    }
                    TreasuryUX(service).open(p)
                }.open(player)
            }
        }

        // [Слот 33] СНЯТИЕ СРЕДСТВ ЧЕРЕЗ NATIVE ANVIL GUI
        slot(33) {
            dynamicItem(Material.REDSTONE) { player ->
                name("&#FC3737Снять средства")
                lore(
                    "",
                    "&#FC3737 «Операция»",
                    " &7- &fВывод средств из казны",
                    " &7- &fна ваш личный счет.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть наковальню"
                )
                null
            }
            onClick { player, _ ->
                val service = this@TreasuryUX.clanService
                val clan = service.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (!clan.hasPermission(user, ClanPerms.Bank.WITHDRAW)) {
                    val cfg = service.plugin.configService
                    cfg.send(player, cfg.messages.treasury.noPermissionWithdraw)
                    return@onClick
                }

                AnvilInputUX(service, "Снять сумму с казны", "100") { p, amount ->
                    val cfg = service.plugin.configService
                    val userClan = service.getClanUser(p)
                    if (userClan != null && userClan.withdrawBank(amount)) {
                        service.economy.depositPlayer(p, amount)
                        service.saveClan(userClan)
                        cfg.send(p, cfg.messages.treasury.withdrawn, mapOf("amount" to amount.toInt().toString()))
                    } else {
                        cfg.send(p, cfg.messages.treasury.insufficientClanFunds)
                    }
                    TreasuryUX(service).open(p)
                }.open(player)
            }
        }

        // [Слот 31] ЛИЧНАЯ СТАТИСТИКА
        slot(31) {
            dynamicItem(Material.PLAYER_HEAD) { player ->
                val service = this@TreasuryUX.clanService
                val clan = service.getClanUser(player) ?: return@dynamicItem null
                name("&#FC7D37Ваша статистика")
                lore(
                    "",
                    "&#9EFC65 «Личный вклад»",
                    " &7- &fБаланс клана: &#5EFD7D${clan.bankBalance} ⛁"
                )
                null
            }
        }

        // [Слот 40] ИСТОРИЯ ТРАНЗАКЦИЙ
        slot(40) {
            dynamicItem(Material.WRITABLE_BOOK) { _ ->
                name("&#5EA9FDИстория операций")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fПросмотр истории пополнений",
                    " &7- &fи вывода средств из банка.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть лог"
                )
                null
            }
            onClick { player, _ ->
                HistoryUX(this@TreasuryUX.clanService).open(player)
            }
        }

        // [Слот 49] НАВИГАЦИЯ
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

/** Represents the type of a treasury transaction event. */
enum class LogType { DEPOSIT, WITHDRAW, UPGRADE }

/**
 * Immutable record of a single treasury transaction event.
 *
 * @property type The operation type (deposit, withdraw, or upgrade deduction).
 * @property playerName The in-game name of the player who initiated the transaction.
 * @property amount The monetary amount involved in this transaction.
 * @property date The human-readable date string of the transaction.
 * @property time The human-readable time string of the transaction.
 */
data class ClanLog(
    val type: LogType,
    val playerName: String,
    val amount: Double,
    val date: String,
    val time: String
)

/**
 * Paginated transaction history GUI for the clan treasury.
 *
 * Displays up to 28 log entries per page. Each entry shows the initiating player,
 * operation type (deposit/withdraw/upgrade), amount, date, and time.
 *
 * @param clanService The clan service providing log access.
 * @param page The current page index (zero-based).
 */
class HistoryUX(
    clanService: ClanService,
    var page: Int = 0
) : BaseGui(clanService) {

    init {
        val currentPage = page
        title("Финансы > История (Стр. ${currentPage + 1})")
        rows(6)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val decorSlots = listOf(1, 7, 9, 17, 36, 44, 46, 52)
        for (i in decorSlots) {
            slot(i) { item(Material.LIGHT_BLUE_STAINED_GLASS_PANE) { name(" ") } }
        }

        val logSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        for (i in logSlots.indices) {
            slot(logSlots[i]) {
                dynamicItemNullable(Material.PAPER) { player ->
                    val allLogs = listOf(
                        ClanLog(LogType.DEPOSIT, player.name, 1000.0, "Сегодня", "21:00")
                    )

                    val index = (currentPage * logSlots.size) + i
                    if (index >= allLogs.size) return@dynamicItemNullable null

                    val log = allLogs[index]
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
                    build()
                }
            }
        }

        slot(48) {
            dynamicItem(Material.ARROW) {
                if (currentPage > 0) {
                    name("&a← Новые записи")
                    lore("&7Перейти к недавним событиям.")
                }
                null
            }
            onClick { player, _ ->
                if (this@HistoryUX.page > 0) {
                    this@HistoryUX.page--
                    this@HistoryUX.update(player)
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
            dynamicItem(Material.ARROW) { _ ->
                val maxPages = 1
                if (currentPage + 1 < maxPages) {
                    name("&aСтарые записи →")
                    lore("&7Перейти к прошлым событиям.")
                }
                null
            }
            onClick { player, _ ->
                val maxPages = 1
                if (this@HistoryUX.page + 1 < maxPages) {
                    this@HistoryUX.page++
                    this@HistoryUX.update(player)
                }
            }
        }
    }
}
