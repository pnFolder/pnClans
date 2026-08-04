package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Clan level upgrade GUI (Ritual of Elevation).
 *
 * Displays the full 5-level progression tree with:
 * - Current unlock status (unlocked / available / locked) per level
 * - Required clan bank balance, MMR, and completed quests for each level
 * - Upgradable perks and chest row expansion per level
 *
 * The central beacon button triggers the upgrade ritual when all requirements are met,
 * deducting the bank balance and advancing [ua.inventorytype.pnclans.api.clan.Clan.level].
 *
 * All feedback messages are dispatched through the [ua.inventorytype.pnclans.api.Action] system
 * configured in `messages.yml`.
 *
 * @param clanService The clan service providing level and balance data.
 */
class UpgradeUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        title("Эволюция Клана")
        rows(6)
        border(Material.BLACK_STAINED_GLASS_PANE)

        val decorSlots = listOf(1, 7, 9, 17, 36, 44, 46, 52)
        for (i in decorSlots) {
            slot(i) { item(Material.ORANGE_STAINED_GLASS_PANE) { name(" ") } }
        }

        val centerSlots = listOf(20, 21, 22, 23, 24)

        ClanLevels.LEVELS.values.forEachIndexed { index, levelData ->
            slot(centerSlots[index]) {
                dynamicItem(levelData.icon) { player ->
                    val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val currentLevel = clan.level

                    val isUnlocked = currentLevel >= levelData.level
                    val isNext = currentLevel + 1 == levelData.level

                    val statusColor = when {
                        isUnlocked -> "&#5EFD7D"
                        isNext -> "&#FF8702"
                        else -> "&#FC3737"
                    }
                    val statusText = when {
                        isUnlocked -> "&a[Разблокировано]"
                        isNext -> "&e[Доступно для прокачки]"
                        else -> "&c[Заблокировано]"
                    }

                    name("${statusColor}Уровень ${levelData.level}")
                    lore(
                        "",
                        "&#9EFC65 «Статус»",
                        " &7- $statusText",
                        "",
                        "&#FC65DF «Разблокируемые возможности»",
                        " &7- &fВместимость состава: &b${levelData.maxMembers} чел.",
                        " &7- &fРазмер сундука: &e${levelData.chestRows} строк(и)",
                        " &7- &fУникальный перк: &d${levelData.unlockedPerk}",
                        "",
                        "&#5EA9FD «Требования для достижения»",
                        " &7- &fКазна: &e${levelData.costMoney} ⛁",
                        " &7- &fРейтинг: &6${levelData.requiredMmr} MMR",
                        " &7- &fПройдено квестов: &3${levelData.requiredQuests} шт."
                    )
                    null
                }
            }

            slot(centerSlots[index] + 9) {
                dynamicItem(Material.WHITE_STAINED_GLASS_PANE) { player ->
                    val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    val currentLevel = clan.level

                    this.type = when {
                        currentLevel >= levelData.level -> Material.LIME_STAINED_GLASS_PANE
                        currentLevel + 1 == levelData.level -> Material.ORANGE_STAINED_GLASS_PANE
                        else -> Material.RED_STAINED_GLASS_PANE
                    }
                    name(" ")
                    null
                }
            }
        }

        // [Слот 40] МЕГА-КНОПКА ПРОКАЧКИ
        slot(40) {
            dynamicItem(Material.BEACON) { player ->
                val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val currentLevel = clan.level

                if (currentLevel >= ClanLevels.MAX_LEVEL) {
                    name("&#FC3737Абсолютное Величие")
                    lore(
                        "",
                        "&#FC65DF «Информация»",
                        " &7- &fВаш клан достиг финального уровня.",
                        " &7- &fВы — легенды этого сервера."
                    )
                    return@dynamicItem null
                }

                val nextData = ClanLevels.getNext(currentLevel) ?: return@dynamicItem null

                val currentMoney = clan.bankBalance
                val currentMMR = clan.mmr
                val completedQuests = 0

                val hasMoney = currentMoney >= nextData.costMoney
                val hasMMR = currentMMR >= nextData.requiredMmr
                val hasQuests = completedQuests >= nextData.requiredQuests

                name("&#FC7D37Провести Ритуал Возвышения")
                lore(
                    "",
                    "&#9EFC65 «Ваш текущий прогресс»",
                    " &7- &fКазна: ${if (hasMoney) "&a" else "&c"}$currentMoney &7/ &e${nextData.costMoney} ⛁",
                    " &7- &fРейтинг: ${if (hasMMR) "&a" else "&c"}$currentMMR &7/ &6${nextData.requiredMmr} MMR",
                    " &7- &fКвесты: ${if (hasQuests) "&a" else "&c"}$completedQuests &7/ &3${nextData.requiredQuests} шт.",
                    "",
                    "&#FC65DF «Условия»",
                    " &7- &fТолько Лидер и Заместители.",
                    " &7- &fПри улучшении средства спишутся.",
                    "",
                    if (hasMoney && hasMMR && hasQuests) "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы улучшить клан до ${nextData.level} ур!"
                    else "&cУ клана недостаточно ресурсов для повышения."
                )
                null
            }

            onClick { player, _ ->
                val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                val currentLevel = clan.level
                if (currentLevel >= ClanLevels.MAX_LEVEL) return@onClick

                val cfg = this@UpgradeUX.clanService.plugin.configService

                if (!clan.hasPermission(user, ClanPerms.Action.UPGRADE_LEVEL)) {
                    cfg.send(player, cfg.messages.upgrade.noPermission)
                    return@onClick
                }

                val nextData = ClanLevels.getNext(currentLevel) ?: return@onClick

                if (clan.bankBalance < nextData.costMoney) {
                    cfg.send(player, cfg.messages.upgrade.insufficientFunds)
                    return@onClick
                }
                if (clan.mmr < nextData.requiredMmr) {
                    cfg.send(player, cfg.messages.upgrade.insufficientMmr)
                    return@onClick
                }

                clan.withdrawBank(nextData.costMoney)
                clan.level = nextData.level
                this@UpgradeUX.clanService.saveClan(clan)

                cfg.send(player, cfg.messages.upgrade.levelUp, mapOf("level" to nextData.level.toString()))
                this@UpgradeUX.update(player)
            }
        }

        slot(49) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в меню")
            }
            onClick { player, _ ->
                MainUX(this@UpgradeUX.clanService).open(player)
            }
        }
    }
}

/**
 * Immutable snapshot of requirements and rewards for a single clan level.
 *
 * @property level The level number (1–5).
 * @property costMoney The clan bank balance deducted when upgrading to this level.
 * @property requiredMmr The minimum clan MMR required to perform the upgrade ritual.
 * @property requiredQuests The number of completed clan quests required.
 * @property maxMembers The maximum clan roster size unlocked at this level.
 * @property chestRows The number of shared chest rows available at this level.
 * @property unlockedPerk A short description of the unique perk unlocked at this level.
 * @property icon The [Material] used as the upgrade display icon in the GUI.
 */
data class ClanLevelData(
    val level: Int,
    val costMoney: Double,
    val requiredMmr: Int,
    val requiredQuests: Int,
    val maxMembers: Int,
    val chestRows: Int,
    val unlockedPerk: String,
    val icon: Material
)

/**
 * Static registry of all clan level definitions.
 *
 * Provides access to level data by number and convenience methods
 * for resolving the next available upgrade target.
 */
object ClanLevels {
    /** The highest achievable clan level. */
    val MAX_LEVEL = 5

    /** Map of level number → [ClanLevelData] for all 5 clan progression stages. */
    val LEVELS = mapOf(
        1 to ClanLevelData(1, 0.0, 0, 0, 10, 1, "Создание клана", Material.COAL),
        2 to ClanLevelData(2, 50000.0, 1200, 5, 15, 3, "Доступ к расширению сундука", Material.IRON_INGOT),
        3 to ClanLevelData(3, 150000.0, 1800, 15, 20, 4, "Символ клана над головой", Material.GOLD_INGOT),
        4 to ClanLevelData(4, 500000.0, 2500, 35, 25, 5, "Вечные баффы клана", Material.DIAMOND),
        5 to ClanLevelData(5, 1500000.0, 4000, 75, 30, 6, "Кастомные титулы и частицы", Material.NETHER_STAR)
    )

    /**
     * Returns [ClanLevelData] for the given [level], or level 1 data if not found.
     *
     * @param level The clan level to query.
     */
    fun get(level: Int): ClanLevelData = LEVELS[level] ?: LEVELS[1]!!

    /**
     * Returns [ClanLevelData] for the level directly above [currentLevel], or null if already at max.
     *
     * @param currentLevel The clan's current level.
     */
    fun getNext(currentLevel: Int): ClanLevelData? = LEVELS[currentLevel + 1]
}