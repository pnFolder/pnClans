package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.ClanShopPaymentOption
import ua.inventorytype.pnclans.impl.config.ClanShopProductConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

/** Config-driven clan shop catalogue with in-place filters, sorting, and pagination. */
class ClanShopUX(
    clanService: ClanService,
    selectedCategory: String? = null,
    selectedPage: Int = 0,
    selectedSort: String? = null
) : BaseGui(clanService) {
    private val config = clanService.plugin.configService.shop
    private val guiRows = 6
    private val bottomRow = (guiRows - 1) * 9
    private val backSlot = bottomRow
    private val balanceSlot = bottomRow + 1
    private val previousPageSlot = bottomRow + 3
    private val pageSlot = bottomRow + 4
    private val nextPageSlot = bottomRow + 5
    private val sortSlot = bottomRow + 8
    private val controlSlots = setOf(HEADER_SLOT, backSlot, balanceSlot, previousPageSlot, pageSlot, nextPageSlot, sortSlot)
    private val visibleCategories = config.categories.entries
        .filter { it.value.slot in 0 until guiRows * 9 && it.value.slot !in controlSlots }
        .distinctBy { it.value.slot }
    private val configuredProductSlots = config.display.productSlots
        .filter { slot ->
            slot in 0 until guiRows * 9 &&
                slot !in controlSlots &&
                visibleCategories.none { it.value.slot == slot }
        }
        .distinct()
    private val productSlots = configuredProductSlots.ifEmpty {
        DEFAULT_PRODUCT_SLOTS.filter { slot ->
            slot in 0 until guiRows * 9 &&
                slot !in controlSlots &&
                visibleCategories.none { it.value.slot == slot }
        }
    }
    private var activeCategory = selectedCategory
        ?.takeIf { selected -> visibleCategories.any { it.key == selected } }
        ?: "all"
    private var currentPage = selectedPage.coerceAtLeast(0)
    private var sortMode = ShopSortMode.fromConfig(selectedSort)

    init {
        currentPage = currentPage.coerceAtMost(pageCount() - 1)
        title(config.title)
        rows(guiRows)
        background(clanService.plugin.configService.menus.background)

        addHeader()
        addCategories()
        addProducts()
        addControls()
    }

    private fun addHeader() {
        slot(HEADER_SLOT) {
            dynamicItem(Material.EMERALD) { player ->
                val placeholders = this@ClanShopUX.pagePlaceholders()
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.headerName, placeholders))
                lore(this@ClanShopUX.config.display.headerLore.map { this@ClanShopUX.format(player, it, placeholders) })
                glow(true)
                null
            }
        }
    }

    private fun addCategories() {
        visibleCategories.forEach { (id, category) ->
            slot(category.slot) {
                dynamicItem(this@ClanShopUX.material(category.material, Material.CHEST)) { player ->
                    val selected = id == this@ClanShopUX.activeCategory
                    name(this@ClanShopUX.format(player, "${if (selected) "&#5EFD7D● " else "&8○ "}${category.name}"))
                    val lines = category.lore
                        .filterNot { line -> line.contains("Нажмите, чтобы открыть") }
                        .dropLastWhile(String::isBlank)
                        .toMutableList()
                    lines += if (selected) {
                        listOf("", "&#5EFD7D● &fСейчас открыто")
                    } else {
                        listOf("", "&#FF9F1CЛКМ &8— &fоткрыть раздел")
                    }
                    lore(lines.map { this@ClanShopUX.format(player, it) })
                    glow(selected)
                    null
                }
                onClick { player, _ -> this@ClanShopUX.selectCategory(player, id) }
            }
        }
    }

    private fun addProducts() {
        productSlots.forEachIndexed { index, slotIndex ->
            slot(slotIndex) {
                dynamicItemNullable(Material.CHEST) { player ->
                    val listedProduct = this@ClanShopUX.productAt(index)
                    if (listedProduct == null) {
                        if (index != 0 || this@ClanShopUX.listedProducts().isNotEmpty()) return@dynamicItemNullable null
                        val placeholders = this@ClanShopUX.pagePlaceholders()
                        type(Material.MINECART)
                        name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.emptyCategoryName, placeholders))
                        lore(this@ClanShopUX.config.display.emptyCategoryLore.map { this@ClanShopUX.format(player, it, placeholders) })
                        return@dynamicItemNullable build()
                    }
                    val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@dynamicItemNullable null
                    val placeholders = this@ClanShopUX.productPlaceholders(player, clan, listedProduct.id, listedProduct.product)
                    type(this@ClanShopUX.material(listedProduct.product.material, Material.CHEST))
                    amount(listedProduct.product.itemAmount.coerceAtLeast(1))
                    val productName = listedProduct.product.name.ifBlank { this@ClanShopUX.config.display.fallbackProductName }
                    val productLore = listedProduct.product.lore.ifEmpty { this@ClanShopUX.config.display.fallbackProductLore }
                    name(this@ClanShopUX.format(player, productName, placeholders))
                    val configuredLore = productLore.flatMap { line ->
                        when (line) {
                            "{payment_lines}" -> this@ClanShopUX.paymentLines(player, clan, listedProduct.product)
                            "{required_quest_lines}" -> this@ClanShopUX.requiredQuestLines(player, clan, listedProduct.product)
                            else -> listOf(this@ClanShopUX.format(player, line, placeholders))
                        }
                    }
                    val categoryLore = if (productLore.any { it.contains("{category}") }) emptyList() else listOf(
                        this@ClanShopUX.format(player, ""),
                        this@ClanShopUX.format(player, "&#9EFC65 «Категория»"),
                        this@ClanShopUX.format(player, " &7- &f{category}", placeholders)
                    )
                    val questLore = if (
                        listedProduct.product.conditions.requiredQuests.isNotEmpty() &&
                        productLore.none { it.contains("{required_quests}") || it.contains("{required_quest_lines}") }
                    ) {
                        listOf(
                            this@ClanShopUX.format(player, ""),
                            this@ClanShopUX.format(player, "&#FFD700 «Квестовые условия»")
                        ) + this@ClanShopUX.requiredQuestLines(player, clan, listedProduct.product)
                    } else {
                        emptyList()
                    }
                    val actionIndex = configuredLore.indexOfLast { it.contains("➥") }
                    val productDetails = if (questLore.isNotEmpty() && actionIndex >= 0) {
                        configuredLore.take(actionIndex) + questLore + configuredLore.drop(actionIndex)
                    } else {
                        configuredLore + questLore
                    }
                    lore(categoryLore + productDetails)
                    glow(this@ClanShopUX.meetsBasicRequirements(clan, listedProduct.product))
                    build()
                }
                onClick { player, _ -> this@ClanShopUX.openProduct(player, index) }
            }
        }
    }

    private fun addControls() {
        slot(backSlot) {
            dynamicItem(Material.OAK_DOOR) { player ->
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.backName))
                lore(this@ClanShopUX.config.display.backLore.map { this@ClanShopUX.format(player, it) })
                glow(true)
                null
            }
            onClick { player, _ -> MainUX(this@ClanShopUX.clanService).open(player) }
        }

        slot(balanceSlot) {
            dynamicItem(Material.SUNFLOWER) { player ->
                val clan = this@ClanShopUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val placeholders = this@ClanShopUX.balancePlaceholders(player, clan)
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.balanceName, placeholders))
                lore(this@ClanShopUX.config.display.balanceLore.map { this@ClanShopUX.format(player, it, placeholders) })
                glow(true)
                null
            }
        }

        slot(previousPageSlot) {
            dynamicItem(Material.ARROW) { player ->
                val enabled = this@ClanShopUX.currentPage > 0
                if (enabled) {
                    name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.previousPageName))
                    lore(this@ClanShopUX.pageButtonLore(player, true, -1))
                    glow(true)
                } else {
                    name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.disabledPreviousPageName))
                    lore(this@ClanShopUX.pageButtonLore(player, false, -1))
                }
                null
            }
            onClick { player, _ -> this@ClanShopUX.changePage(player, -1) }
        }

        slot(pageSlot) {
            dynamicItem(Material.MAP) { player ->
                val placeholders = this@ClanShopUX.pagePlaceholders()
                val display = this@ClanShopUX.config.display
                if (this@ClanShopUX.pageCount() == 1) {
                    name(this@ClanShopUX.format(player, display.singlePageName, placeholders))
                    lore(display.singlePageLore.map { this@ClanShopUX.format(player, it, placeholders) })
                } else {
                    name(this@ClanShopUX.format(player, display.pageName, placeholders))
                    lore(display.pageLore.map { this@ClanShopUX.format(player, it, placeholders) })
                }
                glow(true)
                null
            }
        }

        slot(nextPageSlot) {
            dynamicItem(Material.ARROW) { player ->
                val enabled = this@ClanShopUX.currentPage + 1 < this@ClanShopUX.pageCount()
                if (enabled) {
                    name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.nextPageName))
                    lore(this@ClanShopUX.pageButtonLore(player, true, 1))
                    glow(true)
                } else {
                    name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.disabledNextPageName))
                    lore(this@ClanShopUX.pageButtonLore(player, false, 1))
                }
                null
            }
            onClick { player, _ -> this@ClanShopUX.changePage(player, 1) }
        }

        slot(sortSlot) {
            dynamicItem(Material.HOPPER) { player ->
                val placeholders = mapOf(
                    "sort" to this@ClanShopUX.sortName(this@ClanShopUX.sortMode),
                    "next_sort" to this@ClanShopUX.sortName(this@ClanShopUX.sortMode.next())
                )
                name(this@ClanShopUX.format(player, this@ClanShopUX.config.display.sortName, placeholders))
                lore(this@ClanShopUX.config.display.sortLore.map { this@ClanShopUX.format(player, it, placeholders) })
                glow(true)
                null
            }
            onClick { player, _ -> this@ClanShopUX.changeSort(player) }
        }
    }

    private fun selectCategory(player: Player, categoryId: String) {
        if (categoryId == activeCategory) return
        val previousCategorySlot = config.categories[activeCategory]?.slot
        activeCategory = categoryId
        currentPage = 0
        val selectedCategorySlot = config.categories[activeCategory]?.slot
        refreshCatalogue(player, listOfNotNull(previousCategorySlot, selectedCategorySlot))
    }

    private fun changePage(player: Player, direction: Int) {
        val nextPage = (currentPage + direction).coerceIn(0, pageCount() - 1)
        if (nextPage == currentPage) return
        currentPage = nextPage
        refreshCatalogue(player)
    }

    private fun changeSort(player: Player) {
        sortMode = sortMode.next()
        currentPage = 0
        refreshCatalogue(player)
    }

    private fun refreshCatalogue(player: Player, categorySlots: List<Int> = emptyList()) {
        updateSlots(
            categorySlots + productSlots +
                listOf(HEADER_SLOT, previousPageSlot, pageSlot, nextPageSlot, sortSlot),
            player
        )
    }

    private fun openProduct(player: Player, index: Int) {
        val listedProduct = productAt(index) ?: return
        val clan = clanService.getClanUser(player) ?: return
        if (!meetsBasicRequirements(clan, listedProduct.product)) {
            send(player, config.messages.requirementsNotMet)
            return
        }
        ClanShopPaymentUX(clanService, listedProduct.id, activeCategory, currentPage, sortMode.name).open(player)
    }

    private fun productAt(pageIndex: Int): ListedProduct? =
        listedProducts().getOrNull(currentPage * productSlots.size + pageIndex)

    private fun listedProducts(): List<ListedProduct> {
        val products = config.products
            .filter { activeCategory == "all" || it.value.category == activeCategory }
            .map { ListedProduct(it.key, it.value) }
        return when (sortMode) {
            ShopSortMode.DEFAULT -> products.sortedBy { it.product.sortOrder }
            ShopSortMode.NAME -> products.sortedBy { stripColours(it.product.name).lowercase() }
            ShopSortMode.PRICE -> products.sortedBy { item -> item.product.payments.minOfOrNull { it.amount } ?: Long.MAX_VALUE }
            ShopSortMode.RARITY -> products.sortedByDescending { config.rarities[it.product.rarity]?.weight ?: 0 }
            ShopSortMode.LEVEL -> products.sortedBy { it.product.conditions.minimumClanLevel }
        }
    }

    private fun pageCount(): Int {
        if (productSlots.isEmpty()) return 1
        return ceil(listedProducts().size.toDouble() / productSlots.size).toInt().coerceAtLeast(1)
    }

    private fun pagePlaceholders(): Map<String, String> {
        val products = listedProducts()
        val categoryName = config.categories[activeCategory]?.name ?: activeCategory
        return mapOf(
            "page" to (currentPage + 1).toString(),
            "pages" to pageCount().toString(),
            "products" to products.size.toString(),
            "range" to if (products.isEmpty()) {
                "0"
            } else {
                val first = currentPage * productSlots.size + 1
                val last = minOf(first + productSlots.size - 1, products.size)
                "$first-$last"
            },
            "category" to categoryName
        )
    }

    private fun pageButtonLore(player: Player, enabled: Boolean, direction: Int): List<String> = if (enabled) {
        listOf(
            format(player, ""),
            format(player, "&7Открыть лист &#5EA9FD${currentPage + direction + 1}"),
            format(player, "&7Раздел: &f${pagePlaceholders().getValue("category")}"),
            format(player, ""),
            format(player, "&#FF9F1CЛКМ &8— &fпереключить")
        )
    } else {
        val reason = if (pageCount() == 1) {
            "Все товары этого раздела уже показаны."
        } else if (direction < 0) {
            "Вы уже на первой странице."
        } else {
            "Вы уже на последней странице."
        }
        config.display.unavailablePageLore.map { format(player, it, mapOf("reason" to reason)) }
    }

    private fun productPlaceholders(player: Player, clan: Clan, id: String, product: ClanShopProductConfig): Map<String, String> {
        val shop = clanService.plugin.clanShopService
        val clanLimit = product.conditions.dailyClanLimit
        val globalLimit = product.conditions.dailyGlobalLimit
        val categoryName = config.categories[product.category]?.name ?: product.category
        val rarityName = config.rarities[product.rarity]?.name ?: product.rarity
        return balancePlaceholders(player, clan) + mapOf(
            "product" to id,
            "category" to categoryName,
            "rarity" to rarityName,
            "quantity" to product.itemAmount.coerceAtLeast(1).toString(),
            "required_level" to product.conditions.minimumClanLevel.takeIf { it > 0 }?.toString().orEmpty().ifEmpty { config.display.notRequiredText },
            "required_members" to product.conditions.minimumMembers.takeIf { it > 0 }?.toString().orEmpty().ifEmpty { config.display.notRequiredText },
            "required_quests" to product.conditions.requiredQuests.sorted().joinToString(", ") { questId ->
                val quest = clanService.plugin.configService.quests.quests[questId]
                val completed = clanService.plugin.clanQuestService.hasCompletedAtLeastOnce(clan, questId)
                "${if (completed) "&#5EFD7D✔" else "&#FC3737✖"} ${quest?.name ?: questId}"
            }.ifEmpty { config.display.notRequiredText },
            "clan_limit" to if (clanLimit <= 0) config.display.unlimitedText else "${shop.clanPurchasesToday(clan, id)}/$clanLimit",
            "global_limit" to if (globalLimit <= 0) config.display.unlimitedText else "${shop.globalPurchasesToday(id)}/$globalLimit"
        )
    }

    private fun requiredQuestLines(player: Player, clan: Clan, product: ClanShopProductConfig): List<String> =
        product.conditions.requiredQuests.sorted().map { questId ->
            val quest = clanService.plugin.configService.quests.quests[questId]
            val completed = clanService.plugin.clanQuestService.hasCompletedAtLeastOnce(clan, questId)
            val icon = if (completed) "&#5EFD7D✔" else "&#FC3737✖"
            format(player, " &7- $icon &f${quest?.name ?: questId}")
        }

    private fun paymentLines(player: Player, clan: Clan, product: ClanShopProductConfig): List<String> =
        product.payments.map { payment ->
            val shop = clanService.plugin.clanShopService
            val balance = shop.balance(payment.currency, player, clan)
            val permitted = hasPaymentPermission(player, payment)
            val enough = permitted && balance != null && balance >= payment.amount
            val state = when {
                !permitted -> "&#FC3737✘"
                !shop.isCurrencyAvailable(payment.currency) -> "&#FC3737✘"
                enough -> "&#5EFD7D✔"
                else -> "&#FFD700!"
            }
            format(
                player,
                " $state &f${currencyName(payment)}: &#FFD166${payment.amount} &8(${paymentSource(payment.currency)}: ${formatNumber(balance)})"
            )
        }

    private fun balancePlaceholders(player: Player, clan: Clan): Map<String, String> {
        val shop = clanService.plugin.clanShopService
        return mapOf(
            "clan_points" to formatNumber(shop.balance(ClanShopCurrency.CLAN_POINTS, player, clan)),
            "vault_balance" to formatNumber(shop.balance(ClanShopCurrency.VAULT, player, clan)),
            "player_points_balance" to formatNumber(shop.balance(ClanShopCurrency.PLAYER_POINTS, player, clan))
        )
    }

    private fun meetsBasicRequirements(clan: Clan, product: ClanShopProductConfig): Boolean =
        clan.level >= product.conditions.minimumClanLevel &&
            clan.users.size >= product.conditions.minimumMembers &&
            clanService.plugin.clanQuestService.requiredQuestsMet(clan, product.conditions.requiredQuests)

    private fun material(value: String, fallback: Material): Material =
        runCatching { Material.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun format(player: Player, text: String, placeholders: Map<String, String> = emptyMap()): String =
        clanService.plugin.configService.formatMessage(player, text, placeholders)

    private fun send(player: Player, text: String, placeholders: Map<String, String> = emptyMap()) {
        player.sendMessage(format(player, text, placeholders))
    }

    private fun currencyName(payment: ClanShopPaymentOption): String = payment.displayName?.takeIf(String::isNotBlank) ?: when (payment.currency) {
        ClanShopCurrency.CLAN_POINTS -> "Клановые очки"
        ClanShopCurrency.VAULT -> "Монеты"
        ClanShopCurrency.PLAYER_POINTS -> "Бонусные очки"
    }

    private fun paymentSource(currency: ClanShopCurrency): String = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> "Казна клана (клановые очки)"
        ClanShopCurrency.VAULT -> "Личный счёт игрока (монеты)"
        ClanShopCurrency.PLAYER_POINTS -> "Личный счёт игрока (бонусные очки)"
    }

    private fun hasPaymentPermission(player: Player, payment: ClanShopPaymentOption): Boolean =
        payment.permission.isNullOrBlank() || player.hasPermission(payment.permission)

    private fun sortName(mode: ShopSortMode): String = when (mode) {
        ShopSortMode.DEFAULT -> config.display.sortDefaultName
        ShopSortMode.NAME -> config.display.sortNameName
        ShopSortMode.PRICE -> config.display.sortPriceName
        ShopSortMode.RARITY -> config.display.sortRarityName
        ShopSortMode.LEVEL -> config.display.sortLevelName
    }

    private fun stripColours(value: String): String =
        value.replace(Regex("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]"), "")

    private fun formatNumber(value: Double?): String = value?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it)
    } ?: config.display.unavailableText

    private data class ListedProduct(val id: String, val product: ClanShopProductConfig)

    private enum class ShopSortMode {
        DEFAULT,
        NAME,
        PRICE,
        RARITY,
        LEVEL;

        fun next(): ShopSortMode = entries[(ordinal + 1) % entries.size]

        companion object {
            fun fromConfig(value: String?): ShopSortMode =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }

    private companion object {
        const val HEADER_SLOT = 4
        val DEFAULT_PRODUCT_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43)
    }
}
