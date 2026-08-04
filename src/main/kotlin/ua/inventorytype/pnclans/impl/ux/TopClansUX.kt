package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

class TopClansUX(
    val _clanService: ClanService,
    var page: Int = 0 // Используем нашу охуенную фичу без пересоздания окон!
) : BaseGui(_clanService) {

    init {
        title("Зал Славы > Топ Кланов")
        rows(6)
        border(Material.GRAY_STAINED_GLASS_PANE)

        // Сетка для вывода топа (28 слотов)
        val topSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        for (i in topSlots.indices) {
            slot(topSlots[i]) {
                dynamicItem(Material.SHIELD) { viewer -> // Дефолтный материал, мы его перезапишем
                    // TODO: У тебя в сервисе должен быть метод получения всех кланов
                    // Заглушка: val allClans = this@TopClansUX.clanService.getAllClans().sortedByDescending { it.mmr }
                    val allClans = emptyList<Any>() // УБЕРИ ЭТО и подставь свой список кланов

                    val maxPages = ceil(allClans.size / topSlots.size.toDouble()).toInt()
                    val index = (page * topSlots.size) + i

                    if (index >= allClans.size) return@dynamicItem null

                    val clan = allClans[index]
                    val rank = index + 1 // Место в топе

                    // --- ВЫТЯГИВАЕМ ВСЮ ВОЗМОЖНУЮ СТАТИСТИКУ (Заглушки, поменяй на свои методы) ---
                    val clanName = "ТестовыйКлан" // clan.name
                    val leaderName = "SuperNagibator" // clan.getLeader().name
                    val level = 5
                    val mmr = 3500 - (index * 150) // Типа MMR падает в зависимости от места
                    val kills = 1450
                    val deaths = 320
                    val kda = String.format("%.2f", kills.toDouble() / deaths.coerceAtLeast(1))
                    val bank = 150000
                    val members = 15

                    // --- ДЕЛАЕМ ПИЗДАТОЕ ВЫДЕЛЕНИЕ ТОП-3 ---
                    val icon = when (rank) {
                        1 -> Material.DRAGON_EGG      // Абсолютный чемпион
                        2 -> Material.NETHER_STAR     // Второе место
                        3 -> Material.DIAMOND         // Третье место
                        else -> Material.SHIELD       // Все остальные смертные
                    }

                    // Переопределяем материал на лету
                    type(icon)

                    // Цвет места зависит от того, в топ-3 ли он
                    val rankColor = when (rank) {
                        1 -> "&#FFD700" // Золотой
                        2 -> "&#C0C0C0" // Серебряный
                        3 -> "&#CD7F32" // Бронзовый
                        else -> "&#A9A9A9" // Серый
                    }

                    name("$rankColor#$rank &8| &#FC7D37$clanName")
                    lore(
                        "",
                        "&#9EFC65 «Обзор»",
                        " &7- &fВладелец: &e$leaderName",
                        " &7- &fУровень: &#5EFD7D$level лвл.",
                        "",
                        "&#FC65DF «Боевая Мощь»",
                        " &7- &fРейтинг (MMR): &6$mmr ⚔",
                        " &7- &fУбийства / Смерти: &a$kills &7/ &c$deaths",
                        " &7- &fОбщий KDA: &e$kda",
                        "",
                        "&#5EA9FD «Экономика и Состав»",
                        " &7- &fКазна клана: &#FDD05E$bank ⛁",
                        " &7- &fУчастников: &b$members чел."
                    )

                    // Можно добавить свечение (enchantment glow) для ТОП-1
                    // if (rank == 1) addUnsafeEnchantment(Enchantment.DURABILITY, 1) // Если в ItemBuilder есть такой метод
                }
            }
        }

        // =========================================================
        // НАВИГАЦИЯ (Нижний ряд: 45 - 53)
        // =========================================================

        slot(48) {
            dynamicItem(Material.ARROW) {
                if (page > 0) {
                    name("&a← Выше по рейтингу")
                    lore("&7Нажмите, чтобы вернуться к лидерам.")
                } else null
            }
            onClick { player, _ ->
                if (page > 0) {
                    this@TopClansUX.page--
                    this@TopClansUX.update(player)
                }
            }
        }

        slot(49) {
            item(Material.OAK_DOOR) {
                name("&cВернуться в меню")
                lore("&7Нажмите, чтобы закрыть Зал Славы.")
            }
            onClick { player, _ ->
                MainUX(this@TopClansUX.clanService).open(player)
            }
        }

        slot(50) {
            dynamicItem(Material.ARROW) { viewer ->
                // Заглушка, подставь получение всех кланов
                val allClans = emptyList<Any>()
                val maxPages = ceil(allClans.size / 28.0).toInt()

                if (page + 1 < maxPages) {
                    name("&aНиже по рейтингу →")
                    lore("&7Нажмите, чтобы листать дальше.")
                } else null
            }
            onClick { player, _ ->
                val allClans = emptyList<Any>() // Заглушка
                val maxPages = ceil(allClans.size / 28.0).toInt()

                if (page + 1 < maxPages) {
                    this@TopClansUX.page++
                    this@TopClansUX.update(player)
                }
            }
        }
    }
}