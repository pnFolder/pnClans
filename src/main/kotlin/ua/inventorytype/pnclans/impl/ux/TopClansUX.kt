package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import kotlin.math.ceil

class TopClansUX(
    clanService: ClanService,
    var page: Int = 0
) : BaseGui(clanService) {

    init {
        val currentPage = page
        title("Зал Славы > Топ Кланов")
        rows(6)
        border(Material.GRAY_STAINED_GLASS_PANE)

        val topSlots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        )

        for (i in topSlots.indices) {
            slot(topSlots[i]) {
                dynamicItem(Material.SHIELD) { _ ->
                    val service = this@TopClansUX.clanService
                    val allClans = service.getAllClans().sortedByDescending { it.mmr }
                    val index = (currentPage * topSlots.size) + i

                    if (index >= allClans.size) return@dynamicItem null

                    val clan = allClans[index]
                    val rank = index + 1

                    val leaderUser = clan.users.find { clan.getUserRole(it) == ClanRole.LEADER }
                    val leaderName = leaderUser?.playerName ?: "Неизвестен"
                    val level = clan.level
                    val mmr = clan.mmr
                    val kills = clan.kills
                    val deaths = clan.deaths
                    val kda = if (deaths == 0) "N/A" else String.format("%.2f", kills.toDouble() / deaths)
                    val bank = clan.bankBalance
                    val members = clan.users.size

                    val icon = when (rank) {
                        1 -> Material.DRAGON_EGG
                        2 -> Material.NETHER_STAR
                        3 -> Material.DIAMOND
                        else -> Material.SHIELD
                    }

                    this.type = icon

                    val rankColor = when (rank) {
                        1 -> "&#FFD700"
                        2 -> "&#C0C0C0"
                        3 -> "&#CD7F32"
                        else -> "&#A9A9A9"
                    }

                    name("$rankColor#$rank &8| &#FC7D37${clan.name}")
                    lore(
                        "",
                        "&#9EFC65 «Обзор»",
                        " &7- &fЛидер: &e$leaderName",
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
                    null
                }
            }
        }

        slot(48) {
            dynamicItem(Material.ARROW) {
                if (currentPage > 0) {
                    name("&a← Выше по рейтингу")
                    lore("&7Нажмите, чтобы вернуться к лидерам.")
                }
                null
            }
            onClick { player, _ ->
                if (this@TopClansUX.page > 0) {
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
            dynamicItem(Material.ARROW) { _ ->
                val service = this@TopClansUX.clanService
                val allClans = service.getAllClans()
                val maxPages = ceil(allClans.size / 28.0).toInt()

                if (currentPage + 1 < maxPages) {
                    name("&aНиже по рейтингу →")
                    lore("&7Нажмите, чтобы листать дальше.")
                }
                null
            }
            onClick { player, _ ->
                val service = this@TopClansUX.clanService
                val allClans = service.getAllClans()
                val maxPages = ceil(allClans.size / 28.0).toInt()

                if (this@TopClansUX.page + 1 < maxPages) {
                    this@TopClansUX.page++
                    this@TopClansUX.update(player)
                }
            }
        }
    }
}