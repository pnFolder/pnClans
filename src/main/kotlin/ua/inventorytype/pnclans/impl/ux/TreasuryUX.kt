package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionEvent
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionPreEvent
import ua.inventorytype.pnclans.impl.clan.ClanImpl
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt
import java.text.SimpleDateFormat
import java.util.Date

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

    private fun openDepositPrompt(player: Player) {
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

    private fun openWithdrawPrompt(player: Player) {
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

    private fun openAmountPrompt(player: Player, withdraw: Boolean) {
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

    private fun performDeposit(player: Player, amount: Double, reopen: Boolean) {
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

    private fun performWithdraw(player: Player, amount: Double, reopen: Boolean) {
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

    private fun reopenTreasury(player: Player) {
        TreasuryUX(clanService).open(player)
    }

    private fun refreshBalance(player: Player) {
        val centerSlot = clanService.plugin.configService.menus.treasuryMenu.items["center"]?.slot ?: return
        updateSlot(centerSlot, player)
    }

    private fun formatAmount(value: Double): String =
        value.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun sendPersistenceFailure(player: Player) {
        val cfg = clanService.plugin.configService
        cfg.send(player, cfg.messages.treasury.persistenceFailed)
    }

    private fun placeholders(
        player: Player,
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
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(clanService.plugin.configService.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { line -> clanService.plugin.configService.formatMessage(player, line, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
}

/** Paginated transaction history rendered entirely from treasuryHistoryMenu in menus.yml. */
class HistoryUX(
    clanService: ClanService,
    var page: Int = 0
) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.treasuryHistoryMenu
        val currentPage = page.coerceAtLeast(0)
        val entrySlots = menuCfg.entrySlots
            .distinct()
            .filter { it in 0 until menuCfg.rows.coerceIn(1, 6) * 9 }
        val pageSize = entrySlots.size.coerceAtLeast(1)

        title(menuCfg.title.replace("{page}", (currentPage + 1).toString()).replace("{pages}", "?"))
        rows(menuCfg.rows.coerceIn(1, 6))
        hotWorldDecor(true)

        entrySlots.forEachIndexed { position, slotIndex ->
            slot(slotIndex) {
                dynamicItemNullable(Material.PAPER) { player ->
                    val allLogs = this@HistoryUX.clanService.getClanUser(player)?.treasuryLogs
                        ?.sortedByDescending { it.timestamp }
                        ?: emptyList()
                    val index = currentPage * pageSize + position
                    val log = allLogs.getOrNull(index) ?: return@dynamicItemNullable null
                    val itemCfg = this@HistoryUX.entryConfig(log.type) ?: return@dynamicItemNullable null
                    val date = this@HistoryUX.dateFormatter().format(Date(log.timestamp))
                    val time = this@HistoryUX.timeFormatter().format(Date(log.timestamp))
                    val amount = log.amount.toBigDecimal().stripTrailingZeros().toPlainString()
                    val prefix = if (log.type == TreasuryTransactionType.DEPOSIT) "+" else "-"
                    val placeholders = mapOf(
                        "operation" to itemCfg.name,
                        "player" to log.playerName,
                        "amount" to amount,
                        "signed_amount" to "$prefix$amount",
                        "date" to date,
                        "time" to time
                    )
                    this@HistoryUX.render(this, player, itemCfg, placeholders)
                    build()
                }
            }
        }

        configurePrevious(currentPage, pageSize)
        configureBack()
        configureNext(currentPage, pageSize)
    }

    private fun configurePrevious(currentPage: Int, pageSize: Int) {
        val menuCfg = clanService.plugin.configService.menus.treasuryHistoryMenu
        val enabledCfg = menuCfg.items["previous"] ?: return
        val disabledCfg = menuCfg.items["previousDisabled"] ?: enabledCfg
        slot(enabledCfg.slot) {
            dynamicItem(this@HistoryUX.parseMaterial(enabledCfg.material, Material.ARROW)) { player ->
                val pages = this@HistoryUX.maxPages(player, pageSize)
                val active = currentPage > 0
                val itemCfg = if (active) enabledCfg else disabledCfg
                this.type = this@HistoryUX.parseMaterial(itemCfg.material, if (active) Material.ARROW else Material.BLACK_STAINED_GLASS_PANE)
                this@HistoryUX.render(
                    this,
                    player,
                    itemCfg,
                    mapOf("page" to (currentPage + 1).toString(), "target_page" to currentPage.toString(), "pages" to pages.toString())
                )
                null
            }
            onClick { player, _ ->
                if (currentPage > 0) HistoryUX(this@HistoryUX.clanService, currentPage - 1).open(player)
            }
        }
    }

    private fun configureBack() {
        val itemCfg = clanService.plugin.configService.menus.treasuryHistoryMenu.items["back"] ?: return
        slot(itemCfg.slot) {
            dynamicItem(this@HistoryUX.parseMaterial(itemCfg.material, Material.OAK_DOOR)) { player ->
                this@HistoryUX.render(this, player, itemCfg, emptyMap())
                null
            }
            onClick { player, _ -> TreasuryUX(this@HistoryUX.clanService).open(player) }
        }
    }

    private fun configureNext(currentPage: Int, pageSize: Int) {
        val menuCfg = clanService.plugin.configService.menus.treasuryHistoryMenu
        val enabledCfg = menuCfg.items["next"] ?: return
        val disabledCfg = menuCfg.items["nextDisabled"] ?: enabledCfg
        slot(enabledCfg.slot) {
            dynamicItem(this@HistoryUX.parseMaterial(enabledCfg.material, Material.ARROW)) { player ->
                val pages = this@HistoryUX.maxPages(player, pageSize)
                val active = currentPage + 1 < pages
                val itemCfg = if (active) enabledCfg else disabledCfg
                this.type = this@HistoryUX.parseMaterial(itemCfg.material, if (active) Material.ARROW else Material.BLACK_STAINED_GLASS_PANE)
                this@HistoryUX.render(
                    this,
                    player,
                    itemCfg,
                    mapOf("page" to (currentPage + 1).toString(), "target_page" to (currentPage + 2).toString(), "pages" to pages.toString())
                )
                null
            }
            onClick { player, _ ->
                val pages = this@HistoryUX.maxPages(player, pageSize)
                if (currentPage + 1 < pages) HistoryUX(this@HistoryUX.clanService, currentPage + 1).open(player)
            }
        }
    }

    private fun entryConfig(type: TreasuryTransactionType): GuiItemConfig? {
        val items = clanService.plugin.configService.menus.treasuryHistoryMenu.items
        return when (type) {
            TreasuryTransactionType.DEPOSIT -> items["depositEntry"]
            TreasuryTransactionType.WITHDRAW -> items["withdrawEntry"]
            TreasuryTransactionType.UPGRADE -> items["upgradeEntry"]
        }
    }

    private fun maxPages(player: Player, pageSize: Int): Int {
        val total = clanService.getClanUser(player)?.treasuryLogs?.size ?: 0
        return maxOf(1, (total + pageSize - 1) / pageSize)
    }

    private fun dateFormatter(): SimpleDateFormat = formatter(
        clanService.plugin.configService.menus.treasuryHistoryMenu.dateFormat,
        "dd.MM.yyyy"
    )

    private fun timeFormatter(): SimpleDateFormat = formatter(
        clanService.plugin.configService.menus.treasuryHistoryMenu.timeFormat,
        "HH:mm:ss"
    )

    private fun formatter(pattern: String, fallback: String): SimpleDateFormat =
        runCatching { SimpleDateFormat(pattern) }.getOrElse { SimpleDateFormat(fallback) }

    private fun render(
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        val cfg = clanService.plugin.configService
        builder.name(cfg.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { cfg.formatMessage(player, it, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)
}
