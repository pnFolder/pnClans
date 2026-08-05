package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionEvent
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt

/**
 * Clan treasury GUI for depositing, withdrawing, and inspecting financial transactions.
 *
 * **Design Features:**
 * - 6-row HotWorld glass border (`hotWorldDecor(true)`).
 * - Central gold block displaying live clan balance (slot 13).
 * - Instant quick-deposit (`+500`, `+1000`) and quick-withdraw (`-500`, `-1000`) buttons.
 * - **Zero GUI closing/flickering on quick buttons**: clicking deposit/withdraw presets
 *   updates ONLY the central balance slot at index 13 without reopening the GUI.
 * - 100% reliable chat input prompt for arbitrary amounts (e.g. 1000, 100000, 10).
 * - Back button with `OAK_DOOR` and full config-driven styling.
 *
 * @param clanService The clan service providing economy and bank data.
 */
class TreasuryUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.treasuryMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        hotWorldDecor(true)

        // ── Central Bank Balance Display (Slot 13) ────────────────────────────
        menuCfg.items["center"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.GOLD_BLOCK)) { player ->
                    val clan = this@TreasuryUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val user = clan.getMember(player.uniqueId) ?: return@dynamicItem null
                    val placeholders = this@TreasuryUX.placeholders(player, clan, user)
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, placeholders)
                    null
                }
            }
        }

        // ── Custom Deposit Button (Slot 20) ───────────────────────────────────
        menuCfg.items["deposit"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.EMERALD)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> this@TreasuryUX.openDepositPrompt(player) }
            }
        }

        // ── Custom Withdraw Button (Slot 24) ──────────────────────────────────
        menuCfg.items["withdraw"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.REDSTONE)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> this@TreasuryUX.openWithdrawPrompt(player) }
            }
        }

        // ── Quick Deposit Buttons (+500, +1000) (Slots 28, 29) ────────────────
        val depositSlots = listOf(28, 29)
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
                        "&#FF8702➥ &fНажмите &eЛКМ &fчтобы пополнить"
                    )
                }
                onClick { player, _ ->
                    this@TreasuryUX.performDeposit(player, amount.toDouble(), reopen = false)
                }
            }
        }

        // ── Quick Withdraw Buttons (-500, -1000) (Slots 33, 34) ───────────────
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
                        "&#FF8702➥ &fНажмите &eЛКМ &fчтобы снять"
                    )
                }
                onClick { player, _ ->
                    this@TreasuryUX.performWithdraw(player, amount.toDouble(), reopen = false)
                }
            }
        }

        // ── Transaction History Button (Slot 40) ──────────────────────────────
        menuCfg.items["history"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.WRITABLE_BOOK)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> HistoryUX(this@TreasuryUX.clanService).open(player) }
            }
        }

        // ── Back Button (Slot 49) — Door return to Main Menu ──────────────────
        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.OAK_DOOR)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> MainUX(this@TreasuryUX.clanService).open(player) }
            }
        }
    }

    private fun openDepositPrompt(player: org.bukkit.entity.Player) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return

        if (!clan.hasPermission(user, ClanPerms.Bank.DEPOSIT)) {
            cfg.send(player, cfg.messages.treasury.noPermissionDeposit, mapOf("role" to cfg.getRoleDisplayName(clan.getUserRole(user))))
            return
        }

        player.closeInventory()
        player.sendMessage("§a[pnClans] §fВведите сумму для пополнения казны в чат (например: §e1000§f, §e100000§f) или §c'cancel'§f для отмены:")

        ChatInputPrompt.prompt(
            plugin = service.plugin,
            player = player,
            timeoutTicks = 600L,
            onInput = { input ->
                if (input.equals("cancel", ignoreCase = true)) {
                    player.sendMessage("§c[pnClans] Ввод суммы отменён.")
                    TreasuryUX(service).open(player)
                    return@prompt
                }
                val amount = input.replace(" ", "").replace(",", ".").toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    player.sendMessage("§c[pnClans] Некорректная сумма: '$input'. Вводите только числа.")
                    TreasuryUX(service).open(player)
                    return@prompt
                }
                performDeposit(player, amount, reopen = true)
            },
            onTimeout = {
                player.sendMessage("§c[pnClans] Время на ввод суммы истекло.")
                TreasuryUX(service).open(player)
            }
        )
    }

    private fun openWithdrawPrompt(player: org.bukkit.entity.Player) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return

        if (!clan.hasPermission(user, ClanPerms.Bank.WITHDRAW)) {
            cfg.send(player, cfg.messages.treasury.noPermissionWithdraw, mapOf("role" to cfg.getRoleDisplayName(clan.getUserRole(user))))
            return
        }

        player.closeInventory()
        player.sendMessage("§c[pnClans] §fВведите сумму для снятия с казны в чат (например: §e1000§f, §e100000§f) или §c'cancel'§f для отмены:")

        ChatInputPrompt.prompt(
            plugin = service.plugin,
            player = player,
            timeoutTicks = 600L,
            onInput = { input ->
                if (input.equals("cancel", ignoreCase = true)) {
                    player.sendMessage("§c[pnClans] Ввод суммы отменён.")
                    TreasuryUX(service).open(player)
                    return@prompt
                }
                val amount = input.replace(" ", "").replace(",", ".").toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    player.sendMessage("§c[pnClans] Некорректная сумма: '$input'. Вводите только числа.")
                    TreasuryUX(service).open(player)
                    return@prompt
                }
                performWithdraw(player, amount, reopen = true)
            },
            onTimeout = {
                player.sendMessage("§c[pnClans] Время на ввод суммы истекло.")
                TreasuryUX(service).open(player)
            }
        )
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

        if (reopen) {
            reopenTreasury(player)
        } else {
            refreshBalance(player)
        }
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

        if (reopen) {
            reopenTreasury(player)
        } else {
            refreshBalance(player)
        }
    }

    private fun reopenTreasury(player: org.bukkit.entity.Player) {
        TreasuryUX(clanService).open(player)
    }

    private fun refreshBalance(player: org.bukkit.entity.Player) {
        val centerSlot = clanService.plugin.configService.menus.treasuryMenu.items["center"]?.slot ?: 13
        updateSlot(centerSlot, player)
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

/**
 * Paginated transaction history GUI for the clan treasury.
 *
 * Provides:
 * - 6-row HotWorld border.
 * - 28 transaction logs per page.
 * - Dynamic page switching across arbitrary number of log pages.
 * - Clean border blending when navigation arrows are inactive.
 * - Back button door (slot 49) returning to [TreasuryUX].
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
        title("« История Казны (Стр. ${currentPage + 1}) »")
        rows(6)
        hotWorldDecor(true)

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

        // ── Previous Page Arrow (Slot 48) ─────────────────────────────────────
        slot(48) {
            dynamicItem(Material.ARROW) { player ->
                val totalLogs = this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs?.size ?: 0
                val maxPages = maxOf(1, (totalLogs + 27) / 28)

                if (currentPage > 0) {
                    this.type = Material.ARROW
                    name("&#5EFD7D← Предыдущая страница")
                    lore(
                        "",
                        "&#9EFC65 «Навигация»",
                        " &7- &fПерейти на страницу &e${currentPage} &7/ &f$maxPages",
                        "",
                        "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"
                    )
                    glow(true)
                } else {
                    this.type = Material.BLACK_STAINED_GLASS_PANE
                    name(" ")
                }
                null
            }
            onClick { player, _ ->
                if (currentPage > 0) {
                    HistoryUX(this@HistoryUX.clanService, currentPage - 1).open(player)
                }
            }
        }

        // ── Back Button Door (Slot 49) ────────────────────────────────────────
        slot(49) {
            item(Material.OAK_DOOR) {
                name("&#FC3737⏎ Вернуться в банк")
                lore(
                    "",
                    "&#FC65DF «Переход»",
                    " &7- &fОткрывает главное меню банка.",
                    "",
                    "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться"
                )
            }
            onClick { player, _ ->
                TreasuryUX(this@HistoryUX.clanService).open(player)
            }
        }

        // ── Next Page Arrow (Slot 50) ─────────────────────────────────────────
        slot(50) {
            dynamicItem(Material.ARROW) { player ->
                val totalLogs = this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs?.size ?: 0
                val maxPages = maxOf(1, (totalLogs + 27) / 28)

                if (currentPage + 1 < maxPages) {
                    this.type = Material.ARROW
                    name("&#5EFD7DСледующая страница →")
                    lore(
                        "",
                        "&#9EFC65 «Навигация»",
                        " &7- &fПерейти на страницу &e${currentPage + 2} &7/ &f$maxPages",
                        "",
                        "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"
                    )
                    glow(true)
                } else {
                    this.type = Material.BLACK_STAINED_GLASS_PANE
                    name(" ")
                }
                null
            }
            onClick { player, _ ->
                val totalLogs = this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs?.size ?: 0
                val maxPages = maxOf(1, (totalLogs + 27) / 28)
                if (currentPage + 1 < maxPages) {
                    HistoryUX(this@HistoryUX.clanService, currentPage + 1).open(player)
                }
            }
        }
    }
}
