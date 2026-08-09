package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action

@Serializable
enum class ClanShopCurrency { CLAN_POINTS, VAULT, PLAYER_POINTS }

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
    val payments: List<ClanShopPaymentOption>,
    val conditions: ClanShopConditions = ClanShopConditions(),
    @YamlComment("Actions executed for the purchasing player after payment succeeds.")
    val rewards: List<Action> = emptyList()
)

/** Flexible clan shop definition stored in `shop.yml`. */
@Serializable
data class ClanShopConfig(
    val enabled: Boolean = true,
    val title: String = "&8Clan shop",
    val rows: Int = 6,
    var products: Map<String, ClanShopProductConfig> = mapOf(
        "resource-cache" to ClanShopProductConfig(20, "DIAMOND", "&#5EFD7D✦ Набор ресурсов", listOf("&7Полезный запас для развития клана.", "", "&7Стоимость: &#FFD700{price}", "&7Требуется уровень: &#5EA9FD{required_level}", "", "&#FF8702➥ &fНажмите, чтобы выбрать оплату"), listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 100))),
        "combat-cache" to ClanShopProductConfig(22, "GOLDEN_APPLE", "&#FC7D37⚔ Боевой запас", listOf("&7Подготовьтесь к важной битве.", "", "&7Стоимость: &#FFD700{price}", "&7Доступно после клановых заданий", "", "&#FF8702➥ &fНажмите, чтобы выбрать оплату"), listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 250))),
        "legend-cache" to ClanShopProductConfig(24, "NETHER_STAR", "&#FC65DF✦ Легендарная награда", listOf("&7Эксклюзивная награда для развитого клана.", "", "&7Стоимость: &#FFD700{price}", "&7Требуется уровень: &#5EA9FD3", "", "&#FF8702➥ &fНажмите, чтобы выбрать оплату"), listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, 1000)), ClanShopConditions(minimumClanLevel = 3))
    )
)
