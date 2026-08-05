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

        title("« pnClans — Выберите действие »")
        rows(3)
        hotWorldDecor(false)
        border(Material.BLACK_STAINED_GLASS_PANE)

        // ── Central Info Display (Slot 13) ────────────────────────────────────
        slot(13) {
            item(Material.BEACON) {
                name("&#5EFD7D❖ У вас нет клана")
                lore(
                    "",
                    "&#9EFC65 «Добро пожаловать»",
                    " &7- &fСоздайте собственный клан или дождитесь",
                    " &7- &fприглашения от лидеров других кланов!",
                    "",
                    "&#5EA9FD «Возможности клана»",
                    " &7- &fОбщий клановый склад и хранилище",
                    " &7- &fСовместная казна и финансовый банк",
                    " &7- &fКлановые точки дома и точки спавна",
                    " &7- &fЭволюция и прокачка уровня клана",
                    "",
                    "&#FF8702➥ &fВыберите вариант создания ниже"
                )
                glow(true)
            }
        }

        // ── Create Clan Button (Slot 11) ──────────────────────────────────────
        slot(11) {
            item(Material.EMERALD) {
                name("&#5EFD7D➕ Создать свой клан")
                lore(
                    "",
                    "&#9EFC65 «Параметры»",
                    " &7- &fСтоимость: &e$formattedCost ⛁",
                    " &7- &fВаш статус: &aСтанете Лидером клана",
                    "",
                    "&#FF8702➥ &fНажмите &eЛКМ &fчтобы ввести название"
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

        // ── Top Clans Button (Slot 15) ────────────────────────────────────────
        slot(15) {
            item(Material.COMPASS) {
                name("&#FC65DF🏆 Топ Кланов Сервера")
                lore(
                    "",
                    "&#9EFC65 «Рейтинг»",
                    " &7- &fПосмотреть сильнейшие кланы",
                    " &7- &fПозиции, уровни и статистика",
                    "",
                    "&#FF8702➥ &fНажмите &eЛКМ &fдля просмотра"
                )
            }
            onClick { player, _ ->
                TopClansUX(this@NoClanUX.clanService).open(player)
            }
        }
    }
}
