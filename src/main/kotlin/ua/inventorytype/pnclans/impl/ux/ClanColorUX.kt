package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.ClanHighlightColor
import ua.inventorytype.pnclans.api.clan.ClanHighlightType
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

class ClanColorUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        title("&#FC7D37« Метка Соклановцев »")
        rows(6)
        hotWorldDecor(true)

        val colors = cfg.settings.clanHighlightColors.mapNotNull { ClanHighlightColor.fromKey(it) }

        slot(4) {
            dynamicItem(Material.BEACON) { player ->
                val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null

                name("&#FC7D37✦ Метка соклановцев")
                lore(
                    "",
                    "&#9EFC65 «Сводка»",
                    " &7- &fТип: &e${clan.highlightType.displayName}",
                    " &7- &fСтатус: ${if (clan.highlightEnabled) "&#5EFD7DВключена" else "&#FC3737Выключена"}",
                    " &7- &fЦвет: &e${clan.highlightColor.displayName}",
                    "",
                    "&#FC65DF «Назначение»",
                    " &7- &fВизуальная метка: соклановцы",
                    " &7- &fвидят друг друга в цветной броне",
                    " &7- &fили со светящимся контуром.",
                    " &7- &fМетку видят только соклановцы.",
                    "",
                    "&#FF8702➥ &fВыберите тип, статус и цвет ниже"
                )
                glow(true)
                null
            }
        }

        slot(9) {
            item(Material.NAME_TAG) {
                name("&#FFD700✦ Тип метки")
                lore(
                    "",
                    "&#9EFC65 «Как показывать»",
                    " &7- &fБроня — виртуальная кожаная",
                    " &7- &fброня в цвете клана.",
                    " &7- &fПодсветка — светящийся контур",
                    " &7- &fвокруг игрока.",
                    "",
                    "&#FF8702➥ &fВыберите один вариант ниже"
                )
            }
        }

        slot(12) {
            dynamicItem(Material.LEATHER_CHESTPLATE) { player ->
                val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null

                name("&#5EA9FD✦ Броня")
                lore(
                    "",
                    "&#9EFC65 «Состояние»",
                    " &7- &fВыбран: ${if (clan.highlightType == ClanHighlightType.ARMOR) "&#5EFD7DДа" else "&#FC3737Нет"}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fСоклановцы увидят цветную",
                    " &7- &fкожаную броню поверх",
                    " &7- &fнастоящей. Инвентарь",
                    " &7- &fигрока не меняется.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы применить"
                )
                glow(clan.highlightType == ClanHighlightType.ARMOR)
                null
            }
            onClick { player, _ -> this@ClanColorUX.applyType(player, ClanHighlightType.ARMOR) }
        }

        slot(14) {
            dynamicItem(Material.GLOWSTONE_DUST) { player ->
                val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null

                name("&#5EA9FD✦ Подсветка")
                lore(
                    "",
                    "&#9EFC65 «Состояние»",
                    " &7- &fВыбран: ${if (clan.highlightType == ClanHighlightType.GLOW) "&#5EFD7DДа" else "&#FC3737Нет"}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fСоклановцы увидят светящийся",
                    " &7- &fконтур вокруг игрока.",
                    " &7- &fЦвет контура = цвет клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы применить"
                )
                glow(clan.highlightType == ClanHighlightType.GLOW)
                null
            }
            onClick { player, _ -> this@ClanColorUX.applyType(player, ClanHighlightType.GLOW) }
        }

        slot(18) {
            item(Material.REPEATER) {
                name("&#FFD700✦ Статус метки")
                lore(
                    "",
                    "&#9EFC65 «Когда показывать»",
                    " &7- &fВключена — метка активна,",
                    " &7- &fсоклановцы видят друг друга.",
                    " &7- &fВыключена — метка отключена",
                    " &7- &fполностью (и броня, и контур).",
                    "",
                    "&#FF8702➥ &fВыберите состояние ниже"
                )
            }
        }

        slot(21) {
            dynamicItem(Material.BEACON) { player ->
                val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null

                name("&#5EFD7D✦ Включена")
                lore(
                    "",
                    "&#9EFC65 «Состояние»",
                    " &7- &fСтатус: ${if (clan.highlightEnabled) "&#5EFD7DВключена" else "&7Выключена"}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fМетка активна: соклановцы",
                    " &7- &fвидят выбранный тип и цвет.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы включить"
                )
                glow(clan.highlightEnabled)
                null
            }
            onClick { player, _ -> this@ClanColorUX.applyStatus(player, true) }
        }

        slot(23) {
            dynamicItem(Material.BARRIER) { player ->
                val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null

                name("&#FC3737✦ Выключена")
                lore(
                    "",
                    "&#9EFC65 «Состояние»",
                    " &7- &fСтатус: ${if (clan.highlightEnabled) "&7Включена" else "&#FC3737Выключена"}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fМетка отключена: никто",
                    " &7- &fне видит ни броню, ни контур.",
                    " &7- &fНастройки сохранятся.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы выключить"
                )
                glow(!clan.highlightEnabled)
                null
            }
            onClick { player, _ -> this@ClanColorUX.applyStatus(player, false) }
        }

        colors.take(9).forEachIndexed { index, color ->
            this@ClanColorUX.slot(27 + index) {
                dynamicItem(this@ClanColorUX.materialFor(color)) { player ->
                    val clan = this@ClanColorUX.clanService.getClanUser(player) ?: return@dynamicItem null

                    name("${color.chatColor}✦ ${color.displayName}")
                    lore(
                        "",
                        "&#9EFC65 «Цвет метки»",
                        " &7- &fТип: &e${clan.highlightType.displayName}",
                        " &7- &fСтатус: ${if (clan.highlightEnabled) "&#5EFD7DВключена" else "&#FC3737Выключена"}",
                        "",
                        "&#FC65DF «Описание»",
                        " &7- &fМетку этого цвета увидят",
                        " &7- &fвсе соклановцы.",
                        "",
                        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы применить"
                    )
                    glow(clan.highlightColor == color)
                    null
                }
                onClick { player, _ -> this@ClanColorUX.applyColor(player, color) }
            }
        }

        slot(40) {
            item(Material.LAVA_BUCKET) {
                name("&#FC65DF✦ Сбросить метку")
                lore(
                    "",
                    "&#9EFC65 «Действие»",
                    " &7- &fВернуть стандартные настройки:",
                    " &7- &fСтатус: &#5EFD7DВключена",
                    " &7- &fТип: &eБроня",
                    " &7- &fЦвет: &eБирюзовый",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы сбросить"
                )
            }
            onClick { player, _ -> this@ClanColorUX.applyReset(player) }
        }

        slot(49) {
            item(Material.RED_CANDLE) {
                name("&#FC3737⏎ Вернуться назад")
                lore(
                    "",
                    "&#FC65DF «Переход»",
                    " &7- &fОткрывает настройки клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы вернуться"
                )
            }
            onClick { player, _ -> SettingsUX(this@ClanColorUX.clanService).open(player) }
        }
    }

    private fun applyColor(player: Player, color: ClanHighlightColor) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_COLOR)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }

        clan.highlightColor = color
        clanService.saveClan(clan)
        clanService.plugin.clanHighlightService.syncClan(clan)
        clanService.plugin.clanHighlightService.resyncClanLater(clan)
        player.sendMessage(clanService.plugin.configService.formatMessage(
            player,
            "&#5EFD7D✔ &fЦвет метки изменён на &e${color.displayName}&f.",
            emptyMap()
        ))
        update(player)
    }

    private fun applyType(player: Player, type: ClanHighlightType) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_HIGHLIGHT_MODE)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }

        clan.highlightType = type
        clanService.saveClan(clan)
        clanService.plugin.clanHighlightService.syncClan(clan)
        clanService.plugin.clanHighlightService.resyncClanLater(clan)
        player.sendMessage(clanService.plugin.configService.formatMessage(
            player,
            "&#5EFD7D✔ &fТип метки изменён на &e${type.displayName}&f.",
            emptyMap()
        ))
        update(player)
    }

    private fun applyStatus(player: Player, enabled: Boolean) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_HIGHLIGHT_MODE)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }

        clan.highlightEnabled = enabled
        clanService.saveClan(clan)
        clanService.plugin.clanHighlightService.syncClan(clan)
        clanService.plugin.clanHighlightService.resyncClanLater(clan)
        player.sendMessage(clanService.plugin.configService.formatMessage(
            player,
            if (enabled) "&#5EFD7D✔ &fМетка соклановцев включена." else "&#FC3737✖ &fМетка соклановцев выключена.",
            emptyMap()
        ))
        update(player)
    }

    private fun applyReset(player: Player) {
        val cfg = clanService.plugin.configService
        val clan = clanService.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return
        if (!clan.hasPermission(user, ClanPerms.Settings.TOGGLE_HIGHLIGHT_MODE)) {
            cfg.send(player, cfg.messages.settings.noPermission)
            return
        }

        clan.highlightEnabled = true
        clan.highlightType = ClanHighlightType.ARMOR
        clan.highlightColor = ClanHighlightColor.AQUA
        clanService.saveClan(clan)
        clanService.plugin.clanHighlightService.syncClan(clan)
        clanService.plugin.clanHighlightService.resyncClanLater(clan)
        player.sendMessage(clanService.plugin.configService.formatMessage(
            player,
            "&#FFD700↺ &fНастройки метки сброшены к стандартным.",
            emptyMap()
        ))
        update(player)
    }

    private fun materialFor(color: ClanHighlightColor): Material = when (color) {
        ClanHighlightColor.AQUA -> Material.CYAN_DYE
        ClanHighlightColor.BLUE -> Material.BLUE_DYE
        ClanHighlightColor.DARK_AQUA -> Material.PRISMARINE_CRYSTALS
        ClanHighlightColor.GREEN -> Material.LIME_DYE
        ClanHighlightColor.RED -> Material.RED_DYE
        ClanHighlightColor.GOLD -> Material.GOLD_INGOT
        ClanHighlightColor.YELLOW -> Material.YELLOW_DYE
        ClanHighlightColor.LIGHT_PURPLE -> Material.MAGENTA_DYE
        ClanHighlightColor.WHITE -> Material.WHITE_DYE
    }
}
