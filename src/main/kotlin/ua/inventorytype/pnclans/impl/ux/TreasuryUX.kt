package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionEvent
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionPreEvent
import ua.inventorytype.pnclans.impl.clan.ClanImpl
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt

/** Clan treasury GUI for deposits, withdrawals, presets and transaction history. */
class TreasuryUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.treasuryMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        hotWorldDecor(true)

        menuCfg.items["center"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.GOLD_BLOCK)) { player ->
                    val clan = this@TreasuryUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val user = clan.getMember(player.uniqueId) ?: return@dynamicItem null
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, this@TreasuryUX.placeholders(player, clan, user))
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
                onClick { player, _ -> this@TreasuryUX.openDepositPrompt(player) }
            }
        }

        menuCfg.items["withdraw"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.REDSTONE)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> this@TreasuryUX.openWithdrawPrompt(player) }
            }
        }

        configurePresetButtons(
            templateKey = "depositPresets",
            amounts = cfg.settings.treasuryDepositPresets,
            slots = cfg.settings.treasuryDepositPresetSlots,
            fallbackMaterial = Material.LIME_DYE,
            operation = "deposit"
        )
        configurePresetButtons(
            templateKey = "withdrawPresets",
            amounts = cfg.settings.treasuryWithdrawPresets,
            slots = cfg.settings.treasuryWithdrawPresetSlots,
            fallbackMaterial = Material.ORANGE_DYE,
            operation = "withdraw"
        )

        menuCfg.items["history"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@TreasuryUX.parseMaterial(itemCfg.material, Material.WRITABLE_BOOK)) { player ->
                    this@TreasuryUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> HistoryUX(this@TreasuryUX.clanService).open(player) }
            }
        }

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

    private fun configurePresetButtons(
        templateKey: String,
        amounts: List<Int>,
        slots: List<Int>,
        fallbackMaterial: Material,
        operation: String
    ) {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.treasuryMenu
        val template = menuCfg.items[templateKey] ?: return
        val maximumSlot = (menuCfg.rows.coerceIn(1, 6) * 9) - 1

        if (amounts.size != slots.size) {
            clanService.plugin.logger.warning(
                "[pnClans] Treasury preset '$templateKey' has ${amounts.size} amounts but ${slots.size} slots; only matching pairs will be used."
            )
        }

        amounts.zip(slots).forEach { (amount, slotIndex) ->
            if (amount <= 0 || slotIndex !in 0..maximumSlot) {
                clanService.plugin.logger.warning(
                    "[pnClans] Ignoring invalid treasury preset: operation=$operation amount=$amount slot=$slotIndex."
                )
                return@forEach
            }

            slot(slotIndex) {
                dynamicItem(this@TreasuryUX.parseMaterial(template.material, fallbackMaterial)) { player ->
                    val sign = if (operation == "deposit") "+" else "-"
                    this@TreasuryUX.renderConfigItem(
                        this,
                        player,
                        template,
                        mapOf(
                            "amount" to amount.toString(),
                            "signed_amount" to "$sign$amount",
                            "operation" to operation
                        )
                    )
                    null
                }
                onClick { player, _ ->
                    if (operation == "deposit") {
                        this@TreasuryUX.performDeposit(player, amount.toDouble(), reopen = false)
                    } else {
                        this@TreasuryUX.performWithdraw(player, amount.toDouble(), reopen = false)
                    }
                }
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

        openAmountPrompt(player, withdraw = false)
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

        openAmountPrompt(player, withdraw = true)
    }

    private fun openAmountPrompt(player: org.bukkit.entity.Player, withdraw: Boolean) {
        val service = clanService
        val cfg = service.plugin.configService
        val timeoutSeconds = cfg.settings.treasuryPromptTimeoutSeconds.coerceAtLeast(1)
        val cancelInputs = cfg.settings.treasuryPromptCancelInputs
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf("cancel") }
        val promptPlaceholders = mapOf(
            "seconds" to timeoutSeconds.toString(),
            "cancel" to cancelInputs.first()
        )

        player.closeInventory()
        cfg.send(
            player,
            if (withdraw) cfg.messages.treasury.withdrawPromptStarted else cfg.messages.treasury.depositPromptStarted,
            promptPlaceholders
        )

        ChatInputPrompt.prompt(
            plugin = service.plugin,
            player = player,
            timeoutTicks = timeoutSeconds.toLong() * 20L,
            onInput = { rawInput ->
                val input = rawInput.trim()
                if (cancelInputs.any { it.equals(input, ignoreCase = true) }) {
                    cfg.send(player, cfg.messages.treasury.promptCancelled)
                    TreasuryUX(service).open(player)
                    return@prompt
                }

                val amount = input.replace(" ", "").replace(",", ".").toDoubleOrNull()
                if (amount == null || !amount.isFinite() || amount <= 0.0) {
                    cfg.send(player, cfg.messages.treasury.promptInvalidAmount, mapOf("input" to input))
                    TreasuryUX(service).open(player)
                    return@prompt
                }

                if (withdraw) {
                    performWithdraw(player, amount, reopen = true)
                } else {
                    performDeposit(player, amount, reopen = true)
                }
            },
            onTimeout = {
                cfg.send(player, cfg.messages.treasury.promptTimedOut)
                TreasuryUX(service).open(player)
            }
        )
    }

    private fun performDeposit(player: org.bukkit.entity.Player, amount: Double, reopen: Boolean) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player)
        if (clan == null || !amount.isFinite() || amount <= 0.0) {
            cfg.send(player, cfg.messages.general.invalidInput, mapOf("amount" to "0"))
            return
        }
        val transaction = TreasuryTransaction(TreasuryTransactionType.DEPOSIT, player.name, amount, System.currentTimeMillis())
        val transactionEvent = ClanTreasuryTransactionPreEvent(clan, transaction, player)
        org.bukkit.Bukkit.getPluginManager().callEvent(transactionEvent)
        if (transactionEvent.isCancelled) {
            cfg.send(player, cfg.messages.treasury.cancelledByPlugin)
            return
        }

        if (service.economy.withdraw(player, amount)) {
            val previousBalance = clan.bankBalance
            val previousLogs = clan.treasuryLogs
            clan.depositBank(amount)
            clan.addTreasuryLog(transaction)
            if (!service.saveClan(clan)) {
                if (service.economy.depositPlayer(player, amount)) {
                    clan.bankBalance = previousBalance
                    (clan as? ClanImpl)?.restoreTreasuryLogs(previousLogs)
                    sendPersistenceFailure(player)
                    if (reopen) reopenTreasury(player) else refreshBalance(player)
                    return
                }
                if (!service.saveClan(clan)) {
                    service.plugin.logger.severe("[pnClans] Не удалось сохранить пополнение казны и вернуть ${formatAmount(amount)} игроку ${player.name}.")
                    sendPersistenceFailure(player)
                    if (reopen) reopenTreasury(player) else refreshBalance(player)
                    return
                }
            }
            service.plugin.clanQuestService.recordTreasuryDeposit(clan, player, amount)
            org.bukkit.Bukkit.getPluginManager().callEvent(ClanTreasuryTransactionEvent(clan, transaction, player))
            service.notifyClanUpdated(player.uniqueId)
            cfg.send(
                player,
                cfg.messages.treasury.deposited,
                mapOf(
                    "amount" to formatAmount(amount),
                    "balance" to formatAmount(clan.bankBalance),
                    "clan" to clan.name
                )
            )
        } else {
            cfg.send(player, cfg.messages.treasury.insufficientPersonalFunds, mapOf("amount" to formatAmount(amount)))
        }

        if (reopen) reopenTreasury(player) else refreshBalance(player)
    }

    private fun performWithdraw(player: org.bukkit.entity.Player, amount: Double, reopen: Boolean) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player)
        if (clan == null || !amount.isFinite() || amount <= 0.0) {
            cfg.send(player, cfg.messages.general.invalidInput, mapOf("amount" to "0"))
            return
        }
        val transaction = TreasuryTransaction(TreasuryTransactionType.WITHDRAW, player.name, amount, System.currentTimeMillis())
        val transactionEvent = ClanTreasuryTransactionPreEvent(clan, transaction, player)
        org.bukkit.Bukkit.getPluginManager().callEvent(transactionEvent)
        if (transactionEvent.isCancelled) {
            cfg.send(player, cfg.messages.treasury.cancelledByPlugin)
            return
        }

        val previousBalance = clan.bankBalance
        val previousLogs = clan.treasuryLogs
        if (clan.withdrawBank(amount)) {
            if (!service.economy.depositPlayer(player, amount)) {
                clan.depositBank(amount)
                cfg.send(player, cfg.messages.treasury.insufficientClanFunds, mapOf("amount" to formatAmount(amount)))
                if (reopen) reopenTreasury(player) else refreshBalance(player)
                return
            }
            clan.addTreasuryLog(transaction)
            if (!service.saveClan(clan)) {
                if (service.economy.withdraw(player, amount)) {
                    clan.bankBalance = previousBalance
                    (clan as? ClanImpl)?.restoreTreasuryLogs(previousLogs)
                    sendPersistenceFailure(player)
                    if (reopen) reopenTreasury(player) else refreshBalance(player)
                    return
                }
                if (!service.saveClan(clan)) {
                    service.plugin.logger.severe("[pnClans] Не удалось сохранить снятие из казны и вернуть ${formatAmount(amount)} с баланса игрока ${player.name}.")
                    sendPersistenceFailure(player)
                    if (reopen) reopenTreasury(player) else refreshBalance(player)
                    return
                }
            }
            org.bukkit.Bukkit.getPluginManager().callEvent(ClanTreasuryTransactionEvent(clan, transaction, player))
            service.notifyClanUpdated(player.uniqueId)
            cfg.send(
                player,
                cfg.messages.treasury.withdrawn,
                mapOf(
                    "amount" to formatAmount(amount),
                    "balance" to formatAmount(clan.bankBalance),
                    "clan" to clan.name
                )
            )
        } else {
            cfg.send(player, cfg.messages.treasury.insufficientClanFunds, mapOf("amount" to formatAmount(amount)))
        }

        if (reopen) reopenTreasury(player) else refreshBalance(player)
    }

    private fun reopenTreasury(player: org.bukkit.entity.Player) {
        TreasuryUX(clanService).open(player)
    }

    private fun refreshBalance(player: org.bukkit.entity.Player) {
        val centerSlot = clanService.plugin.configService.menus.treasuryMenu.items["center"]?.slot ?: return
        updateSlot(centerSlot, player)
    }

    private fun formatAmount(value: Double): String =
        value.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun sendPersistenceFailure(player: org.bukkit.entity.Player) {
        val cfg = clanService.plugin.configService
        cfg.send(player, cfg.messages.treasury.persistenceFailed)
    }

    private fun placeholders(
        player: org.bukkit.entity.Player,
        clan: ua.inventorytype.pnclans.api.clan.Clan,
        user: ua.inventorytype.pnclans.api.User
    ): Map<String, String> {
        val cfg = clanService.plugin.configService
        val canSee = clan.hasUserPermission(user, ClanPerms.Bank.SEE)
        val display = if (canSee) {
            clan.bankBalance.toBigDecimal().stripTrailingZeros().toPlainString()
        } else {
            cfg.menus.mainMenu.display.hiddenBalance
        }
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
 * Legacy history rendering remains scheduled for the broader menu config migration.
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
                        " &7- &fДата: &b${DATE_FORMAT.format(java.util.Date(log.timestamp))}",
                        " &7- &fВремя: &b${TIME_FORMAT.format(java.util.Date(log.timestamp))}",
                        "",
                        "&#FF8702➥ &fСобытие записано в историю казны"
                    )
                    build()
                }
            }
        }

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
                if (currentPage > 0) HistoryUX(this@HistoryUX.clanService, currentPage - 1).open(player)
            }
        }

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
            onClick { player, _ -> TreasuryUX(this@HistoryUX.clanService).open(player) }
        }

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
                if (currentPage + 1 < maxPages) HistoryUX(this@HistoryUX.clanService, currentPage + 1).open(player)
            }
        }
    }

    private companion object {
        private val DATE_FORMAT = java.text.SimpleDateFormat("dd.MM.yyyy")
        private val TIME_FORMAT = java.text.SimpleDateFormat("HH:mm:ss")
    }
}
