package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

class UpgradeUX(val _clanService: ClanService) : BaseGui(_clanService) {

    init {
        title("Эволюция Клана")
        rows(6) // Увеличил до 6, чтобы впихнуть больше красоты
        border(Material.BLACK_STAINED_GLASS_PANE)

        // Декор оранжевыми панельками по углам (как ты любишь)
        val decorSlots = listOf(1, 7, 9, 17, 36, 44, 46, 52)
        for (i in decorSlots) {
            slot(i) { item(Material.ORANGE_STAINED_GLASS_PANE) { name(" ") } }
        }

        // =========================================================
        // ВЕТКА ПРОКАЧКИ (Слоты 20, 21, 22, 23, 24)
        // =========================================================
        val centerSlots = listOf(20, 21, 22, 23, 24)

        // Гуляем по нашему реестру через forEachIndexed
        ClanLevels.LEVELS.values.forEachIndexed { index, levelData ->
            slot(centerSlots[index]) {
                dynamicItem(levelData.icon) { player ->
                    val clan = this@UpgradeUX.clanService.getClanUser(player)!!
                    val currentLevel = 2 // TODO: clan.level

                    val isUnlocked = currentLevel >= levelData.level
                    val isNext = currentLevel + 1 == levelData.level

                    // Свечение для текущего/открытого уровня
                    if (isUnlocked) {
                        // Тут можно добавить зачарование через твой билдер, чтобы предмет блестел
                    }

                    val statusColor = when {
                        isUnlocked -> "&#5EFD7D" // Зеленый
                        isNext -> "&#FF8702"     // Оранжевый
                        else -> "&#FC3737"       // Красный
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
                }
            }

            // Красивая соединительная линия под веткой прокачки (Слоты 29-33)
            slot(centerSlots[index] + 9) {
                dynamicItem(Material.WHITE_STAINED_GLASS_PANE) { player ->
                    val clan = this@UpgradeUX.clanService.getClanUser(player)!!
                    val currentLevel = 2 // TODO: clan.level

                    this.type = when {
                        currentLevel >= levelData.level -> Material.LIME_STAINED_GLASS_PANE
                        currentLevel + 1 == levelData.level -> Material.ORANGE_STAINED_GLASS_PANE
                        else -> Material.RED_STAINED_GLASS_PANE
                    }
                    name(" ") // Пустое имя для декора
                }
            }
        }

        // =========================================================
        // МЕГА-КНОПКА ПРОКАЧКИ (Слот 40)
        // =========================================================
        slot(40) {
            dynamicItem(Material.BEACON) { player ->
                val clan = this@UpgradeUX.clanService.getClanUser(player)!!
                val currentLevel = 2 // TODO: clan.level

                if (currentLevel >= ClanLevels.MAX_LEVEL) {
                    name("&#FC3737Абсолютное Величие")
                    lore(
                        "",
                        "&#FC65DF «Информация»",
                        " &7- &fВаш клан достиг финального уровня.",
                        " &7- &fВы — легенды этого сервера."
                    )
                    return@dynamicItem
                }

                val nextData = ClanLevels.getNext(currentLevel)!!

                // Заглушки текущей статы клана
                val currentMoney = 15000.0 // clan.bankBalance
                val currentMMR = 1050 // clan.mmr
                val completedQuests = 3 // clan.completedQuestsCount

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
            }

            onClick { player, _ ->
                val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                val currentLevel = 2
                if (currentLevel >= ClanLevels.MAX_LEVEL) return@onClick

                if (clan.hasPermission(user, ClanPerms.Action.UPGRADE_LEVEL) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет полномочий для проведения ритуала.")
                    return@onClick
                }

                val nextData = ClanLevels.getNext(currentLevel)!!
                val currentMoney = 15000.0
                val currentMMR = 1050
                val completedQuests = 3

                if (currentMoney < nextData.costMoney) {
                    player.sendMessage("&cНе хватает монет в казне!")
                    return@onClick
                }
                if (currentMMR < nextData.requiredMmr) {
                    player.sendMessage("&cКлан недостаточно силён (Мало MMR)!")
                    return@onClick
                }
                if (completedQuests < nextData.requiredQuests) {
                    player.sendMessage("&cНеобходимо выполнить больше клановых квестов!")
                    return@onClick
                }

                // TODO: Логика списания и выдачи лвла
                // clan.withdrawBank(nextData.costMoney)
                // clan.level = nextData.level

                player.sendMessage("&a⚡ Клан возвысился до ${nextData.level} уровня! Открыты новые возможности!")

                // Моментально обновляем интерфейс
                this@UpgradeUX.update(player)
            }
        }

        // =========================================================
        // НАВИГАЦИЯ
        // =========================================================
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
 * Структура одного уровня клана.
 */
data class ClanLevelData(
    val level: Int,
    val costMoney: Double,     // Цена в монетах
    val requiredMmr: Int,      // Требуемый MMR для перехода
    val requiredQuests: Int,   // Сколько клановых квестов надо выполнить
    val maxMembers: Int,       // Лимит участников
    val chestRows: Int,        // Строки сундука (1-6)
    val unlockedPerk: String,  // Текстовое описание главной фишки уровня
    val icon: Material         // Иконка для отображения в GUI
)

/**
 * Реестр всех уровней. Тот самый список, по которому мы будем гулять.
 */
object ClanLevels {
    val MAX_LEVEL = 5

    // Жестко заданные границы. Позже можешь парсить это из конфига.
    val LEVELS = mapOf(
        1 to ClanLevelData(1, 0.0, 0, 0, 10, 1, "Создание клана", Material.COAL),
        2 to ClanLevelData(2, 50000.0, 1200, 5, 15, 3, "Доступ к базовому магазину", Material.IRON_INGOT),
        3 to ClanLevelData(3, 150000.0, 1800, 15, 20, 4, "Символ клана над головой", Material.GOLD_INGOT),
        4 to ClanLevelData(4, 500000.0, 2500, 35, 25, 5, "Вечные баффы клана (2ч)", Material.DIAMOND),
        5 to ClanLevelData(5, 1500000.0, 4000, 75, 30, 6, "Кастомные титулы и частицы", Material.NETHER_STAR)
    )

    fun get(level: Int): ClanLevelData = LEVELS[level] ?: LEVELS[1]!!
    fun getNext(currentLevel: Int): ClanLevelData? = LEVELS[currentLevel + 1]
}