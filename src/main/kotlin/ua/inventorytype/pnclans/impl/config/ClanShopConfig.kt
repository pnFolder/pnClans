package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.GiveItemAction
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency

@Serializable
data class ClanShopPaymentOption(
    val currency: ClanShopCurrency,
    val amount: Long
)

@Serializable
data class ClanShopConditions(
    @YamlComment("Minimum clan level. Set 0 to disable this condition.")
    val minimumClanLevel: Int = 0,
    @YamlComment("Minimum number of clan members. Set 0 to disable this condition.")
    val minimumMembers: Int = 0,
    @YamlComment("Quest IDs that must be completed before purchase.")
    val requiredQuests: Set<String> = emptySet(),
    @YamlComment("Maximum purchases by one clan per UTC day. Set 0 for unlimited.")
    val dailyClanLimit: Int = 0,
    @YamlComment("Maximum purchases by all clans per UTC day. Set 0 for unlimited.")
    val dailyGlobalLimit: Int = 0
)

@Serializable
data class ClanShopProductConfig(
    val slot: Int,
    val material: String = "CHEST",
    val name: String = "&eShop product",
    val lore: List<String> = emptyList(),
    @YamlComment("Amount shown in the product card and used by item-based rewards.")
    val itemAmount: Int = 1,
    @YamlComment("Optional full ItemStack serialized by the in-game editor.")
    val itemStack: String? = null,
    val payments: List<ClanShopPaymentOption>,
    val conditions: ClanShopConditions = ClanShopConditions(),
    @YamlComment("Actions executed for the purchasing player after payment succeeds.")
    val rewards: List<Action> = emptyList(),
    val category: String = "general"
)

@Serializable
data class ClanShopCategoryConfig(
    val slot: Int,
    val material: String,
    val name: String,
    val lore: List<String> = emptyList()
)

@Serializable
data class ClanShopDisplayConfig(
    val headerName: String = "&#5EFD7D✦ Выберите категорию",
    val headerLore: List<String> = listOf("&7Категории расположены рядом в верхнем ряду.", "&7Ниже показаны доступные товары."),
    val backName: String = "&#FC3737← Вернуться в штаб",
    val backLore: List<String> = listOf("&7Вернуться в главное меню клана."),
    val balanceName: String = "&#FFD700⛁ Балансы",
    val balanceLore: List<String> = listOf("&7Очки клана: &#FFD700{clan_points}", "&7Vault: &#5EFD7D{vault_balance}", "&7PlayerPoints: &#FC65DF{player_points_balance}"),
    val paymentTitle: String = "&#FFD700« Выбор валюты »",
    val paymentHeaderName: String = "&#FFD700✦ Выберите способ оплаты",
    val paymentHeaderLore: List<String> = listOf("&7Товар: &f{product_name}", "&7Количество: &#5EA9FD{quantity}", "", "&7Нажмите на доступную валюту ниже."),
    val unavailableText: String = "&#FC3737Недоступно",
    val availableText: String = "&#5EFD7DДоступно",
    val unlimitedText: String = "Без лимита"
)

@Serializable
data class ClanShopMessagesConfig(
    val success: String = "&#5EFD7D✔ &fТовар {product_name} успешно приобретён за &e{price} {currency}&f.",
    val insufficientFunds: String = "&#FC3737✘ &fНедостаточно средств. Нужно: &e{price} {currency}&f.",
    val currencyUnavailable: String = "&#FC3737✘ &fЭта валюта сейчас недоступна на сервере.",
    val requirementsNotMet: String = "&#FC3737✘ &fКлан не выполняет требования этого товара.",
    val clanLimitReached: String = "&#FC3737✘ &fКлан достиг дневного лимита покупок этого товара.",
    val globalLimitReached: String = "&#FC3737✘ &fСерверный дневной лимит этого товара исчерпан.",
    val cancelled: String = "&#FC3737✘ &fПокупка отменена другим плагином."
)

/** Flexible clan shop definition stored in `shop.yml`. */
@Serializable
data class ClanShopConfig(
    val schemaVersion: Int = 2,
    val enabled: Boolean = true,
    val title: String = "&#5EFD7D« Клановый магазин »",
    val rows: Int = 6,
    val categories: Map<String, ClanShopCategoryConfig> = mapOf(
        "all" to ClanShopCategoryConfig(10, "COMPASS", "&#5EA9FD⌕ Все", listOf("&7Показать все товары магазина.", "", "&#FF8702➥ &fНажмите, чтобы открыть")),
        "food" to ClanShopCategoryConfig(11, "GOLDEN_CARROT", "&#FFD700✦ Еда", listOf("&7Боевые припасы и восстановление.", "", "&#FF8702➥ &fНажмите, чтобы открыть")),
        "weapons" to ClanShopCategoryConfig(12, "IRON_SWORD", "&#FC3737⚔ Оружие", listOf("&7Снаряжение для сражений клана.", "", "&#FF8702➥ &fНажмите, чтобы открыть")),
        "resources" to ClanShopCategoryConfig(13, "DIAMOND", "&#5EFD7D✦ Ресурсы", listOf("&7Материалы для развития и строительства.", "", "&#FF8702➥ &fНажмите, чтобы открыть")),
        "rare" to ClanShopCategoryConfig(14, "NETHER_STAR", "&#FC65DF✦ Редкое", listOf("&7Ценные и эксклюзивные награды.", "", "&#FF8702➥ &fНажмите, чтобы открыть"))
    ),
    var products: Map<String, ClanShopProductConfig> = mapOf(
        "resource-cache" to ClanShopProductConfig(29, "DIAMOND", "&#5EFD7D✦ Набор ресурсов", listOf("", "&#9EFC65 «Содержимое»", " &7- &fАлмазы: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", " &7- &fОбщий лимит: {global_limit}", "", "&#FF8702➥ &fНажмите, чтобы выбрать валюту"), 3, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 100), ClanShopPaymentOption(ClanShopCurrency.VAULT, 1500), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 15)), rewards = listOf(GiveItemAction("DIAMOND", 3)), category = "resources"),
        "combat-cache" to ClanShopProductConfig(31, "GOLDEN_APPLE", "&#FC7D37⚔ Боевой запас", listOf("", "&#9EFC65 «Содержимое»", " &7- &fЗолотые яблоки: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", " &7- &fОбщий лимит: {global_limit}", "", "&#FF8702➥ &fНажмите, чтобы выбрать валюту"), 8, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 250), ClanShopPaymentOption(ClanShopCurrency.VAULT, 3500), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 35)), rewards = listOf(GiveItemAction("GOLDEN_APPLE", 8)), category = "food"),
        "legend-cache" to ClanShopProductConfig(33, "NETHER_STAR", "&#FC65DF✦ Легендарная награда", listOf("", "&#9EFC65 «Содержимое»", " &7- &fЗвезда Незера: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", " &7- &fОбщий лимит: {global_limit}", "", "&#FF8702➥ &fНажмите, чтобы выбрать валюту"), 1, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 1000), ClanShopPaymentOption(ClanShopCurrency.VAULT, 15000), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 150)), conditions = ClanShopConditions(minimumClanLevel = 3, dailyClanLimit = 1, dailyGlobalLimit = 25), rewards = listOf(GiveItemAction("NETHER_STAR", 1)), category = "rare")
    ),
    val display: ClanShopDisplayConfig = ClanShopDisplayConfig(),
    val messages: ClanShopMessagesConfig = ClanShopMessagesConfig()
)
