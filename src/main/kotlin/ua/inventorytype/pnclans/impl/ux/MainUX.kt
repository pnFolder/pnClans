package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.util.ChatInputPrompt

/**
 * Main clan control panel GUI — the primary entry point for all clan management features.
 *
 * Renders the clan dashboard with navigation slots for:
 * - Member list ([MembersUX])
 * - Clan statistics
 * - Clan chest ([ClanChestUX])
 * - Treasury ([TreasuryUX])
 * - Clan homes ([HomesUX])
 * - Top clans leaderboard ([TopClansUX])
 * - Invitation flow (BossBar + chat prompt via [ChatInputPrompt])
 * - Settings panel ([SettingsUX])
 * - Disband/leave action
 *
 * All feedback messages are dispatched through the [ua.inventorytype.pnclans.api.Action] system
 * configured in `messages.yml`.
 *
 * @param clanService The clan service providing all required data access.
 */
class MainUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        title("Мой Клан")
        rows(5)
        border(Material.GRAY_STAINED_GLASS_PANE)

        // [Слот 20] УЧАСТНИКИ
        slot(20) {
            dynamicItem(Material.PLAYER_HEAD) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val totalMembers = clan.users.size
                val onlineMembers = clan.onlineCount

                name("&#FC7D37Участники клана")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fВсего игроков: &b$totalMembers чел.",
                    " &7- &fСейчас в сети: &a$onlineMembers чел.",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fУправление составом клана.",
                    " &7- &fЗдесь можно повышать, понижать",
                    " &7- &fи исключать участников.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть список"
                )
                null
            }
            onClick { player, _ ->
                MembersUX(this@MainUX.clanService).open(player)
            }
        }

        // [Слот 22] СТАТИСТИКА КЛАНА
        slot(22) {
            dynamicItem(Material.NETHER_STAR) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null

                val level = clan.level
                val mmr = clan.mmr
                val kills = clan.kills
                val deaths = clan.deaths
                val kda = if (deaths == 0) "N/A" else String.format("%.2f", kills.toDouble() / deaths)

                name("&#FC7D37${clan.name}")
                lore(
                    "",
                    "&#9EFC65 «Основное»",
                    " &7- &fУровень клана: &#5EFD7D$level лвл.",
                    " &7- &fОчки рейтинга (MMR): &e$mmr",
                    "",
                    "&#FC65DF «Боевая сводка»",
                    " &7- &fУбийств: &a$kills",
                    " &7- &fСмертей: &c$deaths",
                    " &7- &fKDA: &e$kda",
                    ""
                )
                null
            }
        }

        // [Слот 24] КЛАНОВЫЙ СУНДУК
        slot(24) {
            dynamicItem(Material.CHEST) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val isOpen = clan.isSettingEnabled(ClanSetting.CHEST)

                name("&#FC7D37Клановый Сундук")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fДоступ: ${if (isOpen) "&aОткрыт" else "&cЗакрыт"}",
                    " &7- &fВместимость: &e${clan.level * 9} слотов",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fОбщее хранилище ресурсов клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть склад"
                )
                null
            }
            onClick { player, _ ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick
                val cfg = this@MainUX.clanService.plugin.configService

                if (!clan.hasPermission(user, ClanPerms.Action.OPEN_CHEST)) {
                    cfg.send(player, cfg.messages.chest.noPermission)
                    return@onClick
                }

                if (!clan.isSettingEnabled(ClanSetting.CHEST)) {
                    cfg.send(player, cfg.messages.chest.chestDisabled)
                    return@onClick
                }

                this@MainUX.clanService.openClanChest(player, clan)
            }
        }

        // [Слот 29] КАЗНА
        slot(29) {
            dynamicItem(Material.GOLD_INGOT) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem null
                val balance = clan.bankBalance

                name("&#FC7D37Казна клана")
                lore(
                    "",
                    "&#9EFC65 «Баланс»",
                    " &7- &fСчет: &#5EFD7D${if (clan.hasUserPermission(user, ClanPerms.Bank.SEE)) "$balance" else "*****"} ⛁",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fБанк клана для покупки улучшений",
                    " &7- &fи расширения вместимости.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fдля управления средствами"
                )
                null
            }
            onClick { player, _ ->
                TreasuryUX(this@MainUX.clanService).open(player)
            }
        }

        // [Слот 30] КЛАНОВЫЕ ДОМА
        slot(30) {
            dynamicItem(Material.RED_BED) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val homesCount = clan.homes.size
                name("&#FC7D37Клановые Дома")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fУстановлено: &e$homesCount&7/&e3 точек",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fОбщие точки телепортации для",
                    " &7- &fбыстрого сбора участников.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть список домов"
                )
                null
            }
            onClick { player, _ ->
                HomesUX(this@MainUX.clanService).open(player)
            }
        }

        // [Слот 31] ТОП КЛАНОВ
        slot(31) {
            dynamicItem(Material.DRAGON_EGG) { player ->
                name("&#FC7D37Топ Кланов")
                lore(
                    "",
                    "&#9EFC65 «Зал Славы»",
                    " &7- &fРейтинг лучших кланов сервера",
                    " &7- &fпо MMR и убийствам.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы посмотреть таблицу"
                )
                null
            }
            onClick { player, _ ->
                TopClansUX(this@MainUX.clanService).open(player)
            }
        }

        // [Слот 32] ПРИГЛАШЕНИЯ С BOSSBAR И ВАЛИДАЦИЕЙ
        slot(32) {
            dynamicItem(Material.WRITABLE_BOOK) { player ->
                name("&#FC7D37Приглашения в клан")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fОтправка приглашения игроку",
                    " &7- &fчерез ввод никнейма в чат",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы отправить инвайт"
                )
                null
            }
            onClick { player, _ ->
                val service = this@MainUX.clanService
                val cfg = service.plugin.configService
                val clan = service.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (!clan.hasPermission(user, ClanPerms.Members.INVITE)) {
                    cfg.send(player, cfg.messages.invite.noPermission)
                    return@onClick
                }

                val bossBar = Bukkit.createBossBar(
                    "§e💬 Введите никнейм игрока в чат (или 'отмена')",
                    BarColor.YELLOW,
                    BarStyle.SOLID
                )
                bossBar.addPlayer(player)

                ChatInputPrompt.prompt(
                    plugin = service.plugin,
                    player = player,
                    titleMessage = "§e💬 Напишите никнейм игрока в чат для отправки приглашения:"
                ) { input ->
                    bossBar.removeAll()
                    if (input.equals("отмена", ignoreCase = true) || input.equals("cancel", ignoreCase = true)) {
                        cfg.send(player, cfg.messages.invite.cancelled)
                        MainUX(service).open(player)
                        return@prompt
                    }

                    val targetPlayer = Bukkit.getPlayer(input)
                    if (targetPlayer == null) {
                        cfg.send(player, cfg.messages.invite.targetNotFound, mapOf("player" to input))
                        MainUX(service).open(player)
                        return@prompt
                    }

                    val targetClan = service.getClanUser(targetPlayer)
                    if (targetClan != null) {
                        if (targetClan.id == clan.id) {
                            cfg.send(player, cfg.messages.invite.targetAlreadyInYourClan, mapOf("player" to targetPlayer.name))
                        } else {
                            cfg.send(player, cfg.messages.invite.targetAlreadyInOtherClan, mapOf(
                                "player" to targetPlayer.name,
                                "clan" to targetClan.name
                            ))
                        }
                        MainUX(service).open(player)
                        return@prompt
                    }

                    cfg.send(targetPlayer, cfg.messages.invite.inviteReceived, mapOf("clan" to clan.name))
                    cfg.send(targetPlayer, cfg.messages.invite.inviteInstructions, mapOf("clan" to clan.name))
                    cfg.send(player, cfg.messages.invite.inviteSent, mapOf("player" to targetPlayer.name))
                    MainUX(service).open(player)
                }
            }
        }

        // [Слот 33] НАСТРОЙКИ
        slot(33) {
            dynamicItem(Material.COMPARATOR) { player ->
                name("&#FC7D37Настройки клана")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fУправление ролями, чатом,",
                    " &7- &fрежимом PvP и уведомлениями.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть настройки"
                )
                null
            }
            onClick { player, _ ->
                SettingsUX(this@MainUX.clanService).open(player)
            }
        }

        // [Слот 40] ВЫХОД / РАСФОРМ
        slot(40) {
            dynamicItem(Material.BARRIER) { player ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@dynamicItem null
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem null
                val isLeader = clan.getUserRole(user) == ClanRole.LEADER

                if (isLeader) {
                    name("&#FC3737Распустить клан")
                    lore(
                        "",
                        "&#FC65DF «Внимание»",
                        " &7- &fВы являетесь Лидером.",
                        " &7- &fЭто действие навсегда удалит клан.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы распустить клан"
                    )
                } else {
                    name("&#FC3737Покинуть клан")
                    lore(
                        "",
                        "&#FC65DF «Внимание»",
                        " &7- &fВы потеряете доступ к клану.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы выйти"
                    )
                }
                null
            }
            onClick { player, _ ->
                val clan = this@MainUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick
                val isLeader = clan.getUserRole(user) == ClanRole.LEADER
                val cfg = this@MainUX.clanService.plugin.configService

                if (isLeader) {
                    this@MainUX.clanService.disbandClan(clan)
                    cfg.send(player, cfg.messages.clan.disbandedLeader, mapOf("clan" to clan.name))
                    player.closeInventory()
                } else {
                    clan.removeUser(player.uniqueId)
                    cfg.send(player, cfg.messages.clan.left, mapOf("clan" to clan.name))
                    player.closeInventory()
                }
            }
        }
    }
}