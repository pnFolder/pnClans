package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionEvent
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Clan treasury GUI for depositing and withdrawing funds from the clan bank.
 *
 * Provides:
 * - A central bank balance display (visible only to members with [ClanPerms.Bank.SEE]).
 * - A free-form anvil prompt for entering any amount.
 * - Quick-deposit and quick-withdraw buttons configured in `config.yml` (treasuryDepositPresets/withdrawPresets).
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
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.treasuryMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        border(Material.BLACK_STAINED_GLASS_PANE)

        val goldSlots = listOf(3, 4, 5, 12, 14, 45, 46, 52, 53)
        for (i in goldSlots) {
            slot(i) { item(Material.YELLOW_STAINED_GLASS_PANE) { name(" ") } }
        }

        menuCfg.items["center"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.GOLD_BLOCK)) { player ->
                    val clan = this@TreasuryUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem null
                    val placeholders = this@TreasuryUX.placeholders(player, clan, user)
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
            }
        }

        menuCfg.items["deposit"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.EMERALD)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> this@TreasuryUX.openAnvilDeposit(player) }
            }
        }

        menuCfg.items["withdraw"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.REDSTONE)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> this@TreasuryUX.openAnvilWithdraw(player) }
            }
        }

        val depositSlots = listOf(29, 30)
        val depositPresets = cfg.settings.treasuryDepositPresets
        depositPresets.take(depositSlots.size).forEachIndexed { index, amount ->
            slot(depositSlots[index]) {
                item(Material.LIME_DYE) {
                    name("&#5EFD7D+${amount} ⛁")
                    lore(
                        "",
                        "&#9EFC65 «Быстрый внос»",
                        " &7- &fСумма: &e+$amount ⛁",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы пополнить"
                    )
                }
                onClick { player, _ -> this@TreasuryUX.performDeposit(player, amount.toDouble(), reopen = false) }
            }
        }

        val withdrawSlots = listOf(33, 34)
        val withdrawPresets = cfg.settings.treasuryWithdrawPresets
        withdrawPresets.take(withdrawSlots.size).forEachIndexed { index, amount ->
            slot(withdrawSlots[index]) {
                item(Material.ORANGE_DYE) {
                    name("&#FC3737-${amount} ⛁")
                    lore(
                        "",
                        "&#9EFC65 «Быстрое снятие»",
                        " &7- &fСумма: &c-$amount ⛁",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы снять"
                    )
                }
                onClick { player, _ -> this@TreasuryUX.performWithdraw(player, amount.toDouble(), reopen = false) }
            }
        }

        slot(40) {
            item(Material.WRITABLE_BOOK) {
                name("&#5EA9FDИстория операций")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fПросмотр истории пополнений",
                    " &7- &fи вывода средств из банка.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть лог"
                )
            }
            onClick { player, _ -> HistoryUX(this@TreasuryUX.clanService).open(player) }
        }

        slot(49) {
            item(Material.RED_CANDLE) {
                name("&#FC3737⏎ Вернуться в штаб")
                lore(
                    "",
                    "&#FC65DF «Переход»",
                    " &7- &fОткрывает главный штаб клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы вернуться"
                )
            }
            onClick { player, _ -> MainUX(this@TreasuryUX.clanService).open(player) }
        }
    }

    private fun openAnvilDeposit(player: org.bukkit.entity.Player) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player) ?: return
        val user = clan.users.find { it.uuid == player.uniqueId } ?: return

        if (!clan.hasPermission(user, ClanPerms.Bank.DEPOSIT)) {
            cfg.send(player, cfg.messages.treasury.noPermissionDeposit, mapOf("role" to cfg.getRoleDisplayName(clan.getUserRole(user))))
            return
        }

        AnvilInputUX(service, "Внести сумму в казну", "100",
            onSubmit = { p, amount -> performDeposit(p, amount, reopen = true) },
            onCancel = { p -> TreasuryUX(service).open(p) }
        ).open(player)
    }

    private fun openAnvilWithdraw(player: org.bukkit.entity.Player) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player) ?: return
        val user = clan.users.find { it.uuid == player.uniqueId } ?: return

        if (!clan.hasPermission(user, ClanPerms.Bank.WITHDRAW)) {
            cfg.send(player, cfg.messages.treasury.noPermissionWithdraw, mapOf("role" to cfg.getRoleDisplayName(clan.getUserRole(user))))
            return
        }

        AnvilInputUX(service, "Снять сумму с казны", "100",
            onSubmit = { p, amount -> performWithdraw(p, amount, reopen = true) },
            onCancel = { p -> TreasuryUX(service).open(p) }
        ).open(player)
    }

    private fun performDeposit(player: org.bukkit.entity.Player, amount: Double, reopen: Boolean) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player)
        if (clan == null || amount <= 0.0) {
            cfg.send(player, cfg.messages.general.invalidInput, mapOf("amount" to "0"))
            return
        }
        val transaction = TreasuryTransaction(TreasuryTransactionType.DEPOSIT, player.name, amount, System.currentTimeMillis())
        val transactionEvent = ClanTreasuryTransactionEvent(clan, transaction)
        org.bukkit.Bukkit.getPluginManager().callEvent(transactionEvent)
        if (transactionEvent.isCancelled) return

        if (service.economy.withdraw(player, amount)) {
            clan.depositBank(amount)
            clan.addTreasuryLog(transaction)
            service.saveClan(clan)
            service.notifyClanUpdated(player.uniqueId)
            val placeholders = mapOf(
                "amount" to formatAmount(amount),
                "balance" to formatAmount(clan.bankBalance),
                "clan" to clan.name
            )
            cfg.send(player, cfg.messages.treasury.deposited, placeholders)
            playFeedback(player, true)
        } else {
            cfg.send(player, cfg.messages.treasury.insufficientPersonalFunds, mapOf("amount" to formatAmount(amount)))
            playFeedback(player, false)
        }
        if (reopen) reopenTreasury(player) else refreshBalance(player)
    }

    private fun performWithdraw(player: org.bukkit.entity.Player, amount: Double, reopen: Boolean) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player)
        if (clan == null || amount <= 0.0) {
            cfg.send(player, cfg.messages.general.invalidInput, mapOf("amount" to "0"))
            return
        }
        val transaction = TreasuryTransaction(TreasuryTransactionType.WITHDRAW, player.name, amount, System.currentTimeMillis())
        val transactionEvent = ClanTreasuryTransactionEvent(clan, transaction)
        org.bukkit.Bukkit.getPluginManager().callEvent(transactionEvent)
        if (transactionEvent.isCancelled) return

        if (clan.withdrawBank(amount)) {
            service.economy.depositPlayer(player, amount)
            clan.addTreasuryLog(transaction)
            service.saveClan(clan)
            service.notifyClanUpdated(player.uniqueId)
            val placeholders = mapOf(
                "amount" to formatAmount(amount),
                "balance" to formatAmount(clan.bankBalance),
                "clan" to clan.name
            )
            cfg.send(player, cfg.messages.treasury.withdrawn, placeholders)
            playFeedback(player, true)
        } else {
            cfg.send(player, cfg.messages.treasury.insufficientClanFunds, mapOf("amount" to formatAmount(amount)))
            playFeedback(player, false)
        }
        if (reopen) reopenTreasury(player) else refreshBalance(player)
    }

    private fun reopenTreasury(player: org.bukkit.entity.Player) {
        TreasuryUX(clanService).open(player)
    }

    private fun refreshBalance(player: org.bukkit.entity.Player) {
        val holder = player.openInventory.topInventory.holder
        if (holder is TreasuryUX) {
            val centerSlot = clanService.plugin.configService.menus.treasuryMenu.items["center"]?.slot ?: 13
            holder.updateSlot(centerSlot, player)
        }
    }

    private fun formatAmount(value: Double): String =
        value.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun playFeedback(player: org.bukkit.entity.Player, success: Boolean) {
        val location = player.location
        player.world.playSound(location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, if (success) 1.2f else 0.6f)
    }

    private fun placeholders(
        player: org.bukkit.entity.Player,
        clan: ua.inventorytype.pnclans.api.clan.Clan,
        user: ua.inventorytype.pnclans.api.User
    ): Map<String, String> {
        val cfg = clanService.plugin.configService
        val canSee = clan.hasUserPermission(user, ClanPerms.Bank.SEE)
        val display = if (canSee) clan.bankBalance.toBigDecimal().stripTrailingZeros().toPlainString() else "Скрыто"
        return mapOf(
            "balance" to display,
            "balance_animated" to display,
            "player" to player.name,
            "clan" to clan.name
        )
    }

    private fun renderConfigItem(
        builder: ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder,
        player: org.bukkit.entity.Player,
        itemCfg: ua.inventorytype.pnclans.impl.config.GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(clanService.plugin.configService.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { line -> clanService.plugin.configService.formatMessage(player, line, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
}

/** Represents the type of a treasury transaction event. */
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
                    val allLogs = this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs
                        ?.sortedByDescending { it.timestamp }
                        ?: emptyList()

                    val index = (currentPage * logSlots.size) + i
                    if (index >= allLogs.size) return@dynamicItemNullable null

                    val log = allLogs[index]
                    val (icon, titleColor, operationText, amountColor, amountPrefix) = when (log.type) {
                        TreasuryTransactionType.DEPOSIT -> listOf(Material.EMERALD, "&#5EFD7D", "Пополнение казны", "&#5EFD7D", "+")
                        TreasuryTransactionType.WITHDRAW -> listOf(Material.REDSTONE, "&#FC3737", "Снятие из казны", "&#FC3737", "-")
                        TreasuryTransactionType.UPGRADE -> listOf(Material.NETHER_STAR, "&#FC65DF", "Оплата улучшения", "&#FC65DF", "-")
                    }

                    this.type = icon as Material
                    name("${titleColor}$operationText")
                    lore(
                        "",
                        "&#9EFC65 «Детали операции»",
                        " &7- &fТип: $titleColor$operationText",
                        " &7- &fИнициатор: &e${log.playerName}",
                        "",
                        "&#9EFC65 «Сумма»",
                        " &7- &fОперация: $amountColor$amountPrefix${log.amount.toBigDecimal().stripTrailingZeros().toPlainString()} ⛁",
                        "",
                        "&#5EA9FD «Время операции»",
                        " &7- &fДата: &b${java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date(log.timestamp))}",
                        " &7- &fВремя: &b${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(log.timestamp))}",
                        "",
                        "&#FF8702➥ &fСобытие записано в историю казны"
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
            dynamicItem(Material.ARROW) { player ->
                val maxPages = ((this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs?.size ?: 0) + 27) / 28
                if (currentPage + 1 < maxPages) {
                    name("&aСтарые записи →")
                    lore("&7Перейти к прошлым событиям.")
                }
                null
            }
            onClick { player, _ ->
                val maxPages = ((this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs?.size ?: 0) + 27) / 28
                if (this@HistoryUX.page + 1 < maxPages) {
                    this@HistoryUX.page++
                    this@HistoryUX.update(player)
                }
            }
        }
    }
}
