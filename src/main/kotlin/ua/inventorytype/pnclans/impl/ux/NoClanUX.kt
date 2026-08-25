package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt

/**
 * Welcome GUI presented to players who are not currently members of any clan.
 *
 * Provides instant single-click access to:
 * - **Clan Creation**: Prompts the player in chat to enter a clean clan name.
 * - **Top Clans Leaderboard**: Opens [TopClansUX].
 * - **Help & Information**: Explains how invitations and clan perks work.
 *
 * @param clanService The clan service providing creation economy and state access.
 */
class NoClanUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val cost = cfg.settings.createClanCost
        val formattedCost = cost.toBigDecimal().stripTrailingZeros().toPlainString()

        title("&#FC7D37« Кланы »")
        rows(5)
        hotWorldDecor(true)

        slot(22) {
            item(Material.BEACON) {
                name("&#FC7D37✦ Путь к величию")
                lore(
                    "",
                    "&#9EFC65 «Добро пожаловать»",
                    " &7- &fСейчас вы не состоите в клане.",
                    " &7- &fОснуйте свой или примите",
                    " &7- &fприглашение от другого лидера.",
                    "",
                    "&#5EA9FD «Ваше будущее»",
                    " &7- &fРазвивайте клан и открывайте перки.",
                    " &7- &fПополняйте казну, выполняйте задания",
                    " &7- &fи ведите союзников к вершине рейтинга!",
                    "",
                    "&#FF8702➥ &fВыберите свой путь ниже"
                )
                glow(true)
            }
        }

        slot(31) {
            item(Material.EMERALD) {
                name("&#5EFD7D✚ Основать свой клан")
                lore(
                    "",
                    "&#9EFC65 «Условия создания»",
                    " &7- &fСтоимость: &e$formattedCost ⛁",
                    " &7- &fВаша роль: &#5EFD7DЛидер клана",
                    "",
                    "&#FC65DF «После основания»",
                    " &7- &fПриглашайте игроков и распределяйте роли.",
                    " &7- &fРазвивайте клан и покоряйте рейтинг!",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ввести название"
                )
                glow(true)
            }
            onClick { player, _ ->
                player.closeInventory()
                player.sendMessage("§a[pnClans] §fВведите желаемое название для вашего нового клана в чат (или §c'cancel'§f для отмены):")

                ChatInputPrompt.prompt(
                    plugin = this@NoClanUX.clanService.plugin,
                    player = player,
                    timeoutTicks = 600L,
                    onInput = { input ->
                        if (input.equals("cancel", ignoreCase = true)) {
                            player.sendMessage("§c[pnClans] Создание клана отменено.")
                            NoClanUX(this@NoClanUX.clanService).open(player)
                            return@prompt
                        }

                        val createdClan = this@NoClanUX.clanService.createClan(input, player)
                        if (createdClan != null) {
                            MainUX(this@NoClanUX.clanService).open(player)
                        } else {
                            NoClanUX(this@NoClanUX.clanService).open(player)
                        }
                    },
                    onTimeout = {
                        player.sendMessage("§c[pnClans] Время на ввод названия клана истекло.")
                        NoClanUX(this@NoClanUX.clanService).open(player)
                    }
                )
            }
        }

        slot(29) {
            item(Material.GOLDEN_HELMET) {
                name("&#FC65DF♛ Топ кланов")
                lore(
                    "",
                    "&#9EFC65 «Рейтинг сервера»",
                    " &7- &fУзнайте, кто удерживает вершину",
                    " &7- &fи доминирует среди кланов.",
                    "",
                    "&#5EA9FD «Показатели»",
                    " &7- &fПозиция в топе и уровень клана.",
                    " &7- &fОчки, MMR и ключевая статистика.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть рейтинг"
                )
            }
            onClick { player, _ -> TopClansUX(this@NoClanUX.clanService).open(player) }
        }

        slot(33) {
            item(Material.BOOK) {
                name("&#5EA9FD❖ Путеводитель по кланам")
                lore(
                    "",
                    "&#9EFC65 «Справочник»",
                    " &7- &fВсё о развитии и эволюции клана.",
                    " &7- &fПодсказки для быстрого старта.",
                    "",
                    "&#FC65DF «Что внутри?»",
                    " &7- &fУровни, привилегии и клановые очки.",
                    " &7- &fИсточники дохода, цели и награды.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть справочник"
                )
            }
            onClick { player, _ -> HelpUX(this@NoClanUX.clanService).open(player) }
        }
    }
}
