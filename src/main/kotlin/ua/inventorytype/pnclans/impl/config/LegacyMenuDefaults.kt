package ua.inventorytype.pnclans.impl.config

import kotlinx.serialization.Serializable

/**
 * Defaults for legacy GUIs migrated into the normal MenusConfig model.
 * These are configuration constructors only; ConfigService remains the single YAML loader.
 */
internal fun defaultNoClanMenu(): GuiMenuConfig = GuiMenuConfig(
    title = "&#FC7D37« Кланы »",
    rows = 5,
    items = mapOf(
        "info" to GuiItemConfig(
            slot = 22,
            material = "BEACON",
            name = "&#FC7D37✦ Путь к величию",
            lore = listOf(
                "",
                "&#9EFC65 «Добро пожаловать»",
                " &7- &fСейчас вы не состоите в клане.",
                " &7- &fОснуйте свой или примите приглашение от другого лидера.",
                "",
                "&#5EA9FD «Ваше будущее»",
                " &7- &fРазвивайте клан, выполняйте задания",
                " &7- &fи поднимайтесь в рейтинге.",
                "",
                "&#FF8702➥ &fВыберите свой путь ниже"
            ),
            glow = true
        ),
        "create" to GuiItemConfig(
            slot = 31,
            material = "EMERALD",
            name = "&#5EFD7D✚ Основать свой клан",
            lore = listOf(
                "",
                "&#9EFC65 «Условия создания»",
                " &7- &fСтоимость: &e{cost} ⛁",
                " &7- &fВаша роль: &#5EFD7DЛидер клана",
                "",
                "&#FC65DF «После основания»",
                " &7- &fПриглашайте игроков и распределяйте роли.",
                " &7- &fРазвивайте клан и покоряйте рейтинг!",
                "",
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ввести название"
            ),
            glow = true
        ),
        "top" to GuiItemConfig(
            slot = 29,
            material = "GOLDEN_HELMET",
            name = "&#FC65DF♛ Топ кланов",
            lore = listOf(
                "",
                "&#9EFC65 «Рейтинг сервера»",
                " &7- &fУзнайте, кто удерживает вершину",
                " &7- &fи доминирует среди кланов.",
                "",
                "&#5EA9FD «Показатели»",
                " &7- &fПозиция, уровень, очки и MMR.",
                "",
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть рейтинг"
            )
        ),
        "help" to GuiItemConfig(
            slot = 33,
            material = "BOOK",
            name = "&#5EA9FD❖ Путеводитель по кланам",
            lore = listOf(
                "",
                "&#9EFC65 «Справочник»",
                " &7- &fВсё о развитии и эволюции клана.",
                " &7- &fПодсказки для быстрого старта.",
                "",
                "&#FC65DF «Что внутри?»",
                " &7- &fУровни, привилегии, очки, цели и награды.",
                "",
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть справочник"
            )
        )
    )
)

internal fun defaultClanColorMenu(): GuiMenuConfig = GuiMenuConfig(
    title = "&#FC7D37« Метка Соклановцев »",
    rows = 6,
    items = mapOf(
        "overview" to GuiItemConfig(
            slot = 4,
            material = "BEACON",
            name = "&#FC7D37✦ Метка соклановцев",
            lore = listOf(
                "",
                "&#9EFC65 «Сводка»",
                " &7- &fТип: &e{type}",
                " &7- &fСтатус: {status}",
                " &7- &fЦвет: &e{color}",
                "",
                "&#FC65DF «Назначение»",
                " &7- &fВизуальная метка для соклановцев.",
                " &7- &fНастоящий инвентарь игрока не меняется.",
                "",
                "&#FF8702➥ &fВыберите тип, статус и цвет ниже"
            ),
            glow = true
        ),
        "typeInfo" to GuiItemConfig(
            slot = 9,
            material = "NAME_TAG",
            name = "&#FFD700✦ Тип метки",
            lore = listOf("", "&#9EFC65 «Как показывать»", " &7- &fБроня — виртуальная цветная броня.", " &7- &fПодсветка — светящийся контур.", "", "&#FF8702➥ &fВыберите вариант ниже")
        ),
        "armor" to GuiItemConfig(
            slot = 12,
            material = "LEATHER_CHESTPLATE",
            name = "&#5EA9FD✦ Броня",
            lore = listOf("", "&#9EFC65 «Состояние»", " &7- &fВыбран: {selected}", "", "&#FC65DF «Описание»", " &7- &fВиртуальная кожаная броня", " &7- &fв выбранном цвете клана.", "", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы применить")
        ),
        "glow" to GuiItemConfig(
            slot = 14,
            material = "GLOWSTONE_DUST",
            name = "&#5EA9FD✦ Подсветка",
            lore = listOf("", "&#9EFC65 «Состояние»", " &7- &fВыбран: {selected}", "", "&#FC65DF «Описание»", " &7- &fСветящийся контур вокруг игрока.", " &7- &fЦвет контура = цвет клана.", "", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы применить")
        ),
        "statusInfo" to GuiItemConfig(
            slot = 18,
            material = "REPEATER",
            name = "&#FFD700✦ Статус метки",
            lore = listOf("", "&#9EFC65 «Когда показывать»", " &7- &fВключена — метка отображается.", " &7- &fВыключена — метка полностью скрыта.", "", "&#FF8702➥ &fВыберите состояние ниже")
        ),
        "enabled" to GuiItemConfig(
            slot = 21,
            material = "BEACON",
            name = "&#5EFD7D✦ Включена",
            lore = listOf("", "&#9EFC65 «Состояние»", " &7- &fСтатус: {status}", "", "&#FC65DF «Описание»", " &7- &fМетка активна для соклановцев.", "", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы включить")
        ),
        "disabled" to GuiItemConfig(
            slot = 23,
            material = "BARRIER",
            name = "&#FC3737✦ Выключена",
            lore = listOf("", "&#9EFC65 «Состояние»", " &7- &fСтатус: {status}", "", "&#FC65DF «Описание»", " &7- &fМетка полностью отключена.", " &7- &fВыбранные настройки сохраняются.", "", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы выключить")
        ),
        "color_aqua" to colorItem(27, "CYAN_DYE"),
        "color_blue" to colorItem(28, "BLUE_DYE"),
        "color_dark_aqua" to colorItem(29, "PRISMARINE_CRYSTALS"),
        "color_green" to colorItem(30, "LIME_DYE"),
        "color_red" to colorItem(31, "RED_DYE"),
        "color_gold" to colorItem(32, "GOLD_INGOT"),
        "color_yellow" to colorItem(33, "YELLOW_DYE"),
        "color_light_purple" to colorItem(34, "MAGENTA_DYE"),
        "color_white" to colorItem(35, "WHITE_DYE"),
        "reset" to GuiItemConfig(
            slot = 40,
            material = "LAVA_BUCKET",
            name = "&#FC65DF✦ Сбросить метку",
            lore = listOf("", "&#9EFC65 «Действие»", " &7- &fВернуть стандартный статус,", " &7- &fтип и цвет метки.", "", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы сбросить")
        ),
        "back" to GuiItemConfig(
            slot = 49,
            material = "RED_CANDLE",
            name = "&#FC3737⏎ Вернуться назад",
            lore = listOf("", "&#FC65DF «Переход»", " &7- &fОткрывает настройки клана.", "", "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы вернуться")
        )
    )
)

private fun colorItem(slot: Int, material: String): GuiItemConfig = GuiItemConfig(
    slot = slot,
    material = material,
    name = "{color_code}✦ {color_name}",
    lore = listOf(
        "",
        "&#9EFC65 «Цвет метки»",
        " &7- &fТип: &e{type}",
        " &7- &fСтатус: {status}",
        "",
        "&#FC65DF «Описание»",
        " &7- &fЭтот цвет увидят соклановцы.",
        "",
        "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы применить"
    )
)

@Serializable
data class TreasuryHistoryMenuConfig(
    val title: String = "&#FC7D37« История Казны » &7({page}/{pages})",
    val rows: Int = 6,
    val entrySlots: List<Int> = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43),
    val dateFormat: String = "dd.MM.yyyy",
    val timeFormat: String = "HH:mm:ss",
    val items: Map<String, GuiItemConfig> = mapOf(
        "depositEntry" to historyEntry("EMERALD", "&#5EFD7DПополнение казны"),
        "withdrawEntry" to historyEntry("REDSTONE", "&#FC3737Снятие из казны"),
        "upgradeEntry" to historyEntry("NETHER_STAR", "&#FC65DFОплата улучшения"),
        "previous" to GuiItemConfig(slot = 48, material = "ARROW", name = "&#5EFD7D← Предыдущая страница", lore = listOf("", "&#9EFC65 «Навигация»", " &7- &fПерейти на страницу &e{target_page} &7/ &f{pages}", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"), glow = true),
        "previousDisabled" to GuiItemConfig(slot = 48, material = "BLACK_STAINED_GLASS_PANE", name = " "),
        "back" to GuiItemConfig(slot = 49, material = "OAK_DOOR", name = "&#FC3737⏎ Вернуться в банк", lore = listOf("", "&#FC65DF «Переход»", " &7- &fОткрывает главное меню банка.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться")),
        "next" to GuiItemConfig(slot = 50, material = "ARROW", name = "&#5EFD7DСледующая страница →", lore = listOf("", "&#9EFC65 «Навигация»", " &7- &fПерейти на страницу &e{target_page} &7/ &f{pages}", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"), glow = true),
        "nextDisabled" to GuiItemConfig(slot = 50, material = "BLACK_STAINED_GLASS_PANE", name = " ")
    )
)

private fun historyEntry(material: String, name: String): GuiItemConfig = GuiItemConfig(
    material = material,
    name = name,
    lore = listOf(
        "",
        "&#9EFC65 «Детали операции»",
        " &7- &fТип: {operation}",
        " &7- &fИнициатор: &e{player}",
        "",
        "&#9EFC65 «Сумма»",
        " &7- &fОперация: {signed_amount} ⛁",
        "",
        "&#5EA9FD «Время операции»",
        " &7- &fДата: &b{date}",
        " &7- &fВремя: &b{time}"
    )
)
