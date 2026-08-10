package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.ConsoleCommandAction
import ua.inventorytype.pnclans.api.GiveItemAction
import ua.inventorytype.pnclans.api.ItemRewardAction
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency

@Serializable
data class ClanShopPaymentOption(
    val currency: ClanShopCurrency,
    val amount: Long,
    @YamlComment("Player-facing currency name, for example 'Coins'. Uses the currency default when omitted.")
    val displayName: String? = null,
    @YamlComment("Optional permission required to use this payment option.")
    val permission: String? = null
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
    @YamlComment("Optional full ItemStack from the in-game editor. Preserves all NBT, names, lore, and enchantments.")
    val itemStack: String? = null,
    val payments: List<ClanShopPaymentOption>,
    val conditions: ClanShopConditions = ClanShopConditions(),
    @YamlComment("Actions after payment. Supports !item_give, !item_reward with enchantments, and !console_command with {player}, {product}, {quantity}, {clan}.")
    val rewards: List<Action> = emptyList(),
    @YamlComment("Category ID from the categories section. Category names are fully configurable.")
    val category: String = "general",
    @YamlComment("Stable catalogue order used by the DEFAULT sort mode.")
    val sortOrder: Int = 0,
    @YamlComment("Rarity ID from the rarities section. IDs and display names are fully configurable.")
    val rarity: String = "common"
)

@Serializable
data class ClanShopCategoryConfig(
    val slot: Int,
    val material: String,
    val name: String,
    val lore: List<String> = emptyList()
)

@Serializable
data class ClanShopRarityConfig(
    @YamlComment("Player-facing rarity name. Administrators may add and rename rarity IDs freely.")
    val name: String,
    @YamlComment("Higher values are shown first when sorting by rarity.")
    val weight: Int = 0
)

@Serializable
data class ClanShopDisplayConfig(
    val headerName: String = "&#FC7D37✦ Клановый магазин",
    val headerLore: List<String> = listOf("", "&#9EFC65 «Выбранный раздел»", " &7- &f{category}", "", "&#FC65DF «Как пользоваться»", " &7- &fВыберите категорию ниже.", " &7- &fНажмите на товар для выбора оплаты."),
    val backName: String = "&#FC3737⏎ Вернуться в штаб",
    val backLore: List<String> = listOf("", "&#FC65DF «Переход»", " &7- &fОткрывает главное меню клана.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться"),
    val balanceName: String = "&#FFD700⛁ Доступные средства",
    val balanceLore: List<String> = listOf("", "&#9EFC65 «Казна клана»", " &7- &fКлановые очки: &#FFD700{clan_points}", "", "&#FC65DF «Личный счёт игрока»", " &7- &fМонеты: &#5EFD7D{vault_balance}", " &7- &fБонусные очки: &#FC65DF{player_points_balance}", "", "&8Источник списания выбирается при покупке."),
    val productSlots: List<Int> = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43),
    val fallbackProductName: String = "&#FFD700✦ &f{product}",
    val fallbackProductLore: List<String> = listOf("", "&#9EFC65 «Товар»", " &7- &fРаздел: {category}", " &7- &fРедкость: {rarity}", " &7- &fКоличество: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Способы оплаты»", "{payment_lines}", "", "&#FFD700 «Требования»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы выбрать оплату"),
    val previousPageName: String = "&#5EFD7D← Предыдущая страница",
    val nextPageName: String = "&#5EFD7DСледующая страница →",
    val disabledPreviousPageName: String = "&8← Предыдущая страница",
    val disabledNextPageName: String = "&8Следующая страница →",
    val pageName: String = "&#FFD700✦ Каталог товаров",
    val pageLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fРаздел: {category}", " &7- &fСтраница: &#5EA9FD{page} &7/ &f{pages}", " &7- &fТовары: &#5EFD7D{range} &7из &f{products}"),
    val singlePageName: String = "&#5EFD7D✦ Все товары показаны",
    val singlePageLore: List<String> = listOf("", "&#9EFC65 «Каталог»", " &7- &fРаздел: {category}", " &7- &fДоступно товаров: &#5EA9FD{products}", "", "&8Для этого раздела переключение страниц не требуется."),
    val unavailablePageLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &f{reason}", "", "&8Выберите доступное направление."),
    val emptyCategoryName: String = "&8Каталог пока пуст",
    val emptyCategoryLore: List<String> = listOf("", "&7В разделе {category} пока нет товаров.", "&7Вернитесь сюда позже."),
    val sortName: String = "&#FC65DF⇅ Сортировка товаров",
    val sortLore: List<String> = listOf("", "&#9EFC65 «Текущий порядок»", " &7- &f{sort}", "", "&#FC65DF «Следующий вариант»", " &7- &f{next_sort}", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы сменить"),
    val sortDefaultName: String = "Как настроил администратор",
    val sortNameName: String = "По названию",
    val sortPriceName: String = "Сначала дешевле",
    val sortRarityName: String = "Сначала редкие",
    val sortLevelName: String = "По уровню клана",
    val paymentTitle: String = "&#FFD700« Выбор оплаты »",
    val paymentHeaderName: String = "&#FFD700✦ {product_name}",
    val paymentHeaderLore: List<String> = listOf("", "&#9EFC65 «Ваш выбор»", " &7- &fРаздел: {category}", " &7- &fРедкость: {rarity}", " &7- &fКоличество: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Выберите источник списания»", " &7- &fКазна клана или личный счёт игрока."),
    val paymentOptionName: String = "{state_icon} &fОплатить: {currency}",
    val paymentOptionLore: List<String> = listOf("", "&#9EFC65 «Источник списания»", " &7- &f{source}", "", "&#FC65DF «Стоимость покупки»", " &7- &fК оплате: &#FFD700{price}", " &7- &fНа счету: &#5EA9FD{balance}", " &7- &fОстанется: &#5EFD7D{remaining}", "", "&#FFD700 «Статус»", " &7- &f{state}", "", "{action}"),
    val paymentBackName: String = "&#FC3737⏎ Вернуться к товарам",
    val paymentBackLore: List<String> = listOf("", "&#FC65DF «Переход»", " &7- &fВернуться в каталог без сброса", " &7- &fкатегории, страницы и сортировки.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться"),
    val unavailableText: String = "&#FC3737Недоступно",
    val availableText: String = "&#5EFD7DДоступно",
    val insufficientText: String = "&#FFD700Недостаточно средств",
    val noPermissionText: String = "&#FC3737Нет разрешения",
    val notRequiredText: String = "Не требуется",
    val unlimitedText: String = "Без лимита"
)

@Serializable
data class ClanShopMessagesConfig(
    val success: String = "&#5EFD7D✔ &fТовар {product_name} успешно приобретён за &e{price} {currency}&f.",
    val insufficientFunds: String = "&#FC3737✘ &fНедостаточно средств. Нужно: &e{price} {currency}&f.",
    val currencyUnavailable: String = "&#FC3737✘ &fЭта валюта сейчас недоступна на сервере.",
    val noPermission: String = "&#FC3737✘ &fУ вас нет разрешения использовать этот способ оплаты.",
    val shopChanged: String = "&#FFD700! &fМагазин был обновлён. Откройте товар ещё раз.",
    val requirementsNotMet: String = "&#FC3737✘ &fКлан не выполняет требования этого товара.",
    val clanLimitReached: String = "&#FC3737✘ &fКлан достиг дневного лимита покупок этого товара.",
    val globalLimitReached: String = "&#FC3737✘ &fСерверный дневной лимит этого товара исчерпан.",
    val cancelled: String = "&#FC3737✘ &fПокупка отменена другим плагином."
)

/** Flexible clan shop definition stored in `shop.yml`. */
@Serializable
data class ClanShopConfig(
    val schemaVersion: Int = 8,
    val enabled: Boolean = true,
    val title: String = "&#5EFD7D« Клановый магазин »",
    @YamlComment("The catalogue uses six rows to fit 21 products, categories, and navigation.")
    val rows: Int = 6,
    val categories: Map<String, ClanShopCategoryConfig> = mapOf(
        "all" to ClanShopCategoryConfig(11, "COMPASS", "&#5EA9FD⌕ Все товары", listOf("", "&#9EFC65 «Информация»", " &7- &fПолный каталог кланового магазина.", "", "&#5EA9FD «Действие»", " &7- &fПоказать все доступные товары.")),
        "food" to ClanShopCategoryConfig(12, "GOLDEN_CARROT", "&#FFD700✦ Еда", listOf("", "&#9EFC65 «Информация»", " &7- &fПрипасы и восстановление для боя.", "", "&#5EA9FD «Действие»", " &7- &fОткрыть раздел с едой.")),
        "weapons" to ClanShopCategoryConfig(13, "IRON_SWORD", "&#FC3737⚔ Оружие", listOf("", "&#9EFC65 «Информация»", " &7- &fСнаряжение для клановых сражений.", "", "&#5EA9FD «Действие»", " &7- &fОткрыть оружейный раздел.")),
        "resources" to ClanShopCategoryConfig(14, "DIAMOND", "&#5EFD7D✦ Ресурсы", listOf("", "&#9EFC65 «Информация»", " &7- &fМатериалы для развития и строительства.", "", "&#5EA9FD «Действие»", " &7- &fОткрыть раздел ресурсов.")),
        "rare" to ClanShopCategoryConfig(15, "NETHER_STAR", "&#FC65DF✦ Редкое", listOf("", "&#9EFC65 «Информация»", " &7- &fОсобые награды для развитого клана.", "", "&#5EA9FD «Действие»", " &7- &fОткрыть редкие товары."))
    ),
    @YamlComment("Rarity IDs are arbitrary. Rename existing entries or add your own and reference them from products.")
    val rarities: Map<String, ClanShopRarityConfig> = mapOf(
        "common" to ClanShopRarityConfig("&fОбычный", 10),
        "valuable" to ClanShopRarityConfig("&#5EFD7DЦенный", 20),
        "rare" to ClanShopRarityConfig("&#C77DFFРедкий", 30),
        "legendary" to ClanShopRarityConfig("&#FFD166Легендарный", 40)
    ),
    var products: Map<String, ClanShopProductConfig> = mapOf(
        "resource-cache" to ClanShopProductConfig(29, "DIAMOND", "&#5EFD7D✦ Алмазный запас", listOf("", "&#9EFC65 «Информация»", " &7- &fРаздел: {category}", " &7- &fРедкость: {rarity}", "", "&#5EA9FD «Награда»", " &7- &fАлмазы: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы выбрать оплату"), 3, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 100), ClanShopPaymentOption(ClanShopCurrency.VAULT, 1500, "Монеты"), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 15, "Бонусные очки")), rewards = listOf(GiveItemAction("DIAMOND", 3)), category = "resources", sortOrder = 20, rarity = "valuable"),
        "combat-cache" to ClanShopProductConfig(31, "GOLDEN_APPLE", "&#FFD700✦ Боевой паёк", listOf("", "&#9EFC65 «Информация»", " &7- &fРаздел: {category}", " &7- &fРедкость: {rarity}", "", "&#5EA9FD «Награда»", " &7- &fЗолотые яблоки: &#FFD700{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы выбрать оплату"), 8, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 250), ClanShopPaymentOption(ClanShopCurrency.VAULT, 3500, "Монеты"), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 35, "Бонусные очки")), rewards = listOf(ConsoleCommandAction("minecraft:give {player} golden_apple {quantity}")), category = "food", sortOrder = 10, rarity = "common"),
        "weapon-cache" to ClanShopProductConfig(30, "DIAMOND_SWORD", "&#FC3737⚔ Алмазный меч", listOf("", "&#9EFC65 «Информация»", " &7- &fРаздел: {category}", " &7- &fРедкость: {rarity}", "", "&#5EA9FD «Награда»", " &7- &fКлинок клана: &#5EA9FD{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы выбрать оплату"), 1, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 400), ClanShopPaymentOption(ClanShopCurrency.VAULT, 6000, "Монеты"), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 60, "Бонусные очки")), conditions = ClanShopConditions(minimumClanLevel = 2), rewards = listOf(ItemRewardAction("DIAMOND_SWORD", 1, "&#5EA9FDКлинок клана", listOf("&7Выдан игроку &f{player}", "&8Награда из кланового магазина"), mapOf("DAMAGE_ALL" to 5, "DURABILITY" to 3), true)), category = "weapons", sortOrder = 30, rarity = "rare"),
        "legend-cache" to ClanShopProductConfig(33, "NETHER_STAR", "&#FC65DF✦ Звезда Незера", listOf("", "&#9EFC65 «Информация»", " &7- &fРаздел: {category}", " &7- &fРедкость: {rarity}", "", "&#5EA9FD «Награда»", " &7- &fЗвезда Незера: &#FC65DF{quantity} шт.", "", "&#FC65DF «Оплата»", "{payment_lines}", "", "&#FFD700 «Ограничения»", " &7- &fУровень клана: {required_level}", " &7- &fЛимит клана: {clan_limit}", " &7- &fЛимит сервера: {global_limit}", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы выбрать оплату"), 1, payments = listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 1000), ClanShopPaymentOption(ClanShopCurrency.VAULT, 15000, "Монеты"), ClanShopPaymentOption(ClanShopCurrency.PLAYER_POINTS, 150, "Бонусные очки")), conditions = ClanShopConditions(minimumClanLevel = 3, dailyClanLimit = 1, dailyGlobalLimit = 25), rewards = listOf(GiveItemAction("NETHER_STAR", 1)), category = "rare", sortOrder = 40, rarity = "legendary")
    ),
    val display: ClanShopDisplayConfig = ClanShopDisplayConfig(),
    val messages: ClanShopMessagesConfig = ClanShopMessagesConfig()
)
