package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.OpenGuiAction
import ua.inventorytype.pnclans.api.SoundAction

@Serializable
data class GuiItemConfig(
    val slot: Int = 0,
    val material: String = "STONE",
    val name: String = "",
    val lore: List<String> = emptyList(),
    val glow: Boolean = false,
    val permission: String? = null,
    val actions: List<Action> = emptyList()
)

@Serializable
data class GuiMenuConfig(
    val title: String = "Клан",
    val rows: Int = 6,
    val items: Map<String, GuiItemConfig> = emptyMap()
)

/**
 * 100% Config-Driven Menu Design.
 * Every single title, item slot, material, display name, lore line, glow effect,
 * decorative border glass pane, and click action is configured in menus.yml.
 */
@Serializable
class MenusConfig {

    @YamlComment("Главное меню клана (/clan menu)")
    val mainMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Управление Кланом »",
        rows = 6,
        items = mapOf(
            // --- Декоративная окантовка (Черные и Оранжевые панели) ---
            "decor_b0" to GuiItemConfig(slot = 0, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_o1" to GuiItemConfig(slot = 1, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_b2" to GuiItemConfig(slot = 2, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_b3" to GuiItemConfig(slot = 3, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_b4" to GuiItemConfig(slot = 4, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_b5" to GuiItemConfig(slot = 5, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_b6" to GuiItemConfig(slot = 6, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_o7" to GuiItemConfig(slot = 7, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_b8" to GuiItemConfig(slot = 8, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_o9" to GuiItemConfig(slot = 9, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_b10" to GuiItemConfig(slot = 10, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_o11" to GuiItemConfig(slot = 11, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_o12" to GuiItemConfig(slot = 12, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_o13" to GuiItemConfig(slot = 13, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_o14" to GuiItemConfig(slot = 14, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_o15" to GuiItemConfig(slot = 15, material = "ORANGE_STAINED_GLASS_PANE", name = " "),
            "decor_b16" to GuiItemConfig(slot = 16, material = "BLACK_STAINED_GLASS_PANE", name = " "),
            "decor_b17" to GuiItemConfig(slot = 17, material = "BLACK_STAINED_GLASS_PANE", name = " "),

            // --- Кнопки управления ---
            "members" to GuiItemConfig(
                slot = 20,
                material = "PLAYER_HEAD",
                name = "&#FC7D37Участники клана",
                lore = listOf(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fИгроков в клане: &b{clan_members}",
                    " &7- &fВ сети: &a{clan_online}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fУправление составом клана.",
                    " &7- &fПовышение, понижение и исключение.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fдля просмотра участников"
                ),
                glow = false,
                actions = listOf(OpenGuiAction("MEMBERS"), SoundAction("UI_BUTTON_CLICK"))
            ),
            "stats" to GuiItemConfig(
                slot = 22,
                material = "NETHER_STAR",
                name = "&#FC7D37Клан: {clan}",
                lore = listOf(
                    "",
                    "&#9EFC65 «Основное»",
                    " &7- &fУровень клана: &#5EFD7D{clan_level} лвл.",
                    " &7- &fОчки рейтинга (MMR): &e{clan_mmr}",
                    "",
                    "&#FC65DF «Боевая сводка»",
                    " &7- &fУбийств: &a{clan_kills}",
                    " &7- &fСмертей: &c{clan_deaths}"
                ),
                glow = true
            ),
            "chest" to GuiItemConfig(
                slot = 24,
                material = "CHEST",
                name = "&#FC7D37Клановый Сундук",
                lore = listOf(
                    "",
                    "&#9EFC65 «Общее Хранилище»",
                    " &7- &fХранилище предметов клана",
                    " &7- &fВместимость зависит от уровня",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть склад"
                ),
                glow = true,
                actions = listOf(OpenGuiAction("CHEST"), SoundAction("BLOCK_CHEST_OPEN"))
            ),
            "treasury" to GuiItemConfig(
                slot = 29,
                material = "GOLD_INGOT",
                name = "&#FC7D37Казна клана",
                lore = listOf(
                    "",
                    "&#9EFC65 «Баланс»",
                    " &7- &fБаланс: &#5EFD7D{clan_balance} ⛁",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fдля управления казной"
                ),
                glow = false,
                actions = listOf(OpenGuiAction("TREASURY"), SoundAction("UI_BUTTON_CLICK"))
            ),
            "homes" to GuiItemConfig(
                slot = 30,
                material = "RED_BED",
                name = "&#FC7D37Клановые Дома",
                lore = listOf(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fОбщие точки телепортации",
                    "",
                    "&#FF8702➥ &fИспользуйте &e/clan home &fдля телепорта"
                ),
                glow = false
            ),
            "top" to GuiItemConfig(
                slot = 31,
                material = "DRAGON_EGG",
                name = "&#FC7D37Топ Кланов",
                lore = listOf(
                    "",
                    "&#9EFC65 «Зал Славы»",
                    " &7- &fРейтинг лучших кланов",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы посмотреть ТОП"
                ),
                glow = true,
                actions = listOf(OpenGuiAction("TOP"), SoundAction("UI_BUTTON_CLICK"))
            ),
            "invite" to GuiItemConfig(
                slot = 32,
                material = "WRITABLE_BOOK",
                name = "&#FC7D37Приглашения в клан",
                lore = listOf(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fПриглашайте игроков через &e/clan invite <ник>",
                    "",
                    "&#FF8702➥ &fИспользуйте команду для отправки"
                ),
                glow = false
            ),
            "settings" to GuiItemConfig(
                slot = 33,
                material = "COMPARATOR",
                name = "&#FC7D37Настройки клана",
                lore = listOf(
                    "",
                    "&#9EFC65 «Параметры»",
                    " &7- &fУправление чатом, PvP и ролями",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть настройки"
                ),
                glow = false,
                actions = listOf(OpenGuiAction("SETTINGS"), SoundAction("UI_BUTTON_CLICK"))
            ),
            "leave" to GuiItemConfig(
                slot = 40,
                material = "BARRIER",
                name = "&#FC3737Выйти / Распустить",
                lore = listOf(
                    "",
                    "&#FC65DF «Внимание»",
                    " &7- &fВыход из состава или расформирование",
                    "",
                    "&#FF8702➥ &fНажмите для подтверждения действия"
                ),
                glow = false
            )
        )
    )

    @YamlComment("Меню участников клана")
    val membersMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Состав Клана »",
        rows = 6,
        items = mapOf(
            "back" to GuiItemConfig(
                slot = 49,
                material = "RED_CANDLE",
                name = "§x§F§F§0§0§0§0Вернуться",
                lore = listOf("&7Вернуться в главное меню"),
                actions = listOf(OpenGuiAction("MAIN"))
            )
        )
    )

    @YamlComment("Меню настроек клана")
    val settingsMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Настройки Клана »",
        rows = 3,
        items = mapOf(
            "pvp" to GuiItemConfig(
                slot = 10,
                material = "DIAMOND_SWORD",
                name = "&#FC7D37Режим ПвП",
                lore = listOf(
                    "",
                    "&#65D1FC «Боевой режим»",
                    " &7- &fПереключение урона между своими",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы переключить"
                )
            ),
            "chat" to GuiItemConfig(
                slot = 11,
                material = "WRITABLE_BOOK",
                name = "&#FC7D37Клановый Чат",
                lore = listOf(
                    "",
                    "&#65D1FC «Канал общения»",
                    " &7- &fПереключение кланового чата",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы переключить"
                )
            ),
            "chest" to GuiItemConfig(
                slot = 12,
                material = "CHEST",
                name = "&#FC7D37Доступ к Сундуку",
                lore = listOf(
                    "",
                    "&#65D1FC «Хранилище»",
                    " &7- &fРазрешить/запретить доступ к сундуку",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы переключить"
                )
            ),
            "join" to GuiItemConfig(
                slot = 13,
                material = "BELL",
                name = "&#FC7D37Уведомления о входе",
                lore = listOf(
                    "",
                    "&#65D1FC «Оповещения»",
                    " &7- &fСообщения при входе соклановцев",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы переключить"
                )
            ),
            "roles" to GuiItemConfig(
                slot = 14,
                material = "ARMOR_STAND",
                name = "&#FC7D37Управление ролями",
                lore = listOf(
                    "",
                    "&#65D1FC «Права доступа»",
                    " &7- &fНастройка прав для каждой должности",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть редактор"
                )
            )
        )
    )

    @YamlComment("Редактор ролей")
    val editorRolesMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Редактор Ролей »",
        rows = 3,
        items = mapOf(
            "back" to GuiItemConfig(
                slot = 22,
                material = "RED_CANDLE",
                name = "§x§F§F§0§0§0§0Вернуться",
                lore = listOf("&7Вернуться в настройки"),
                actions = listOf(OpenGuiAction("SETTINGS"))
            )
        )
    )

    @YamlComment("Персональные права участника")
    val userPermissionsMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Права Игрока »",
        rows = 5,
        items = mapOf(
            "back" to GuiItemConfig(
                slot = 36,
                material = "RED_CANDLE",
                name = "§x§F§F§0§0§0§0Вернуться",
                lore = listOf("&7Вернуться к участникам"),
                actions = listOf(OpenGuiAction("MEMBERS"))
            )
        )
    )

    @YamlComment("Меню банка и финансов клана")
    val treasuryMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Финансы Клана »",
        rows = 6,
        items = mapOf(
            "center" to GuiItemConfig(
                slot = 13,
                material = "GOLD_BLOCK",
                name = "&#FFD700Центральный Банк Клана",
                lore = listOf(
                    "",
                    "&#9EFC65 «Счет»",
                    " &7- &fТекущий баланс: &#5EFD7D{clan_balance} ⛁"
                ),
                glow = true
            ),
            "deposit" to GuiItemConfig(
                slot = 29,
                material = "EMERALD",
                name = "&#5EFD7DПополнить Казну",
                lore = listOf(
                    "",
                    "&#9EFC65 «Внос средств»",
                    " &7- &fВнести деньги на счет клана",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы пополнить"
                )
            ),
            "withdraw" to GuiItemConfig(
                slot = 33,
                material = "REDSTONE",
                name = "&#FC65DFСнять с Казны",
                lore = listOf(
                    "",
                    "&#9EFC65 «Снятие средств»",
                    " &7- &fСнять накопленные средства",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы снять"
                )
            ),
            "history" to GuiItemConfig(
                slot = 40,
                material = "WRITABLE_BOOK",
                name = "&#5EA9FDИстория операций",
                lore = listOf(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fПросмотр истории пополнений и снятий",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть лог"
                )
            ),
            "back" to GuiItemConfig(
                slot = 49,
                material = "RED_CANDLE",
                name = "§x§F§F§0§0§0§0Вернуться",
                lore = listOf("&7Вернуться в главное меню"),
                actions = listOf(OpenGuiAction("MAIN"))
            )
        )
    )

    @YamlComment("Меню эволюции и уровней клана")
    val upgradeMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Эволюция Клана »",
        rows = 6,
        items = mapOf(
            "upgrade" to GuiItemConfig(
                slot = 40,
                material = "BEACON",
                name = "&#FC7D37Провести Ритуал Возвышения",
                lore = listOf(
                    "",
                    "&#9EFC65 «Прогресс»",
                    " &7- &fТекущий уровень: &e{clan_level} лвл.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы улучшить клан"
                ),
                glow = true
            ),
            "back" to GuiItemConfig(
                slot = 49,
                material = "RED_CANDLE",
                name = "§x§F§F§0§0§0§0Вернуться",
                lore = listOf("&7Вернуться в главное меню"),
                actions = listOf(OpenGuiAction("MAIN"))
            )
        )
    )

    @YamlComment("Зал Славы / Топ Кланов")
    val topMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Зал Славы (Топ Кланов) »",
        rows = 6,
        items = mapOf(
            "back" to GuiItemConfig(
                slot = 49,
                material = "RED_CANDLE",
                name = "§x§F§F§0§0§0§0Вернуться",
                lore = listOf("&7Вернуться в главное меню"),
                actions = listOf(OpenGuiAction("MAIN"))
            )
        )
    )

    @YamlComment("Виртуальное хранилище сундук клана")
    val chestMenu: GuiMenuConfig = GuiMenuConfig(
        title = "&#FC7D37« Клановый Сундук »",
        rows = 6,
        items = mapOf(
            "stats" to GuiItemConfig(
                slot = 45,
                material = "KNOWLEDGE_BOOK",
                name = "&#5EFD7D📊 СТАТИСТИКА СКЛАДА",
                lore = listOf(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fУровень клана: &e{clan_level} лвл.",
                    " &7- &fБаланс: &#5EFD7D{clan_balance} ⛁"
                )
            ),
            "back" to GuiItemConfig(
                slot = 48,
                material = "OAK_DOOR",
                name = "&c🚪 Вернуться в меню",
                lore = listOf("&7Нажмите для возврата"),
                actions = listOf(OpenGuiAction("MAIN"))
            ),
            "core" to GuiItemConfig(
                slot = 49,
                material = "BEACON",
                name = "&#FC7D37⚡ ЯДРО ХРАНИЛИЩА",
                lore = listOf(
                    "",
                    "&#9EFC65 «Статус»",
                    " &7- &fСохранение в БД: &aАКТИВНО"
                ),
                glow = true
            ),
            "upgrade" to GuiItemConfig(
                slot = 50,
                material = "NETHER_STAR",
                name = "&#FC65DF✨ ЭВОЛЮЦИЯ КЛАНА",
                lore = listOf(
                    "",
                    "&#9EFC65 «Прокачка»",
                    " &7- &fОткрыть больше слотов",
                    "",
                    "&#FF8702➥ &fНажмите для перехода"
                ),
                glow = true,
                actions = listOf(OpenGuiAction("UPGRADE"))
            ),
            "close" to GuiItemConfig(
                slot = 53,
                material = "BARRIER",
                name = "&c✖ Закрыть",
                lore = listOf("&7Закрыть склад")
            )
        )
    )

    @YamlComment("Меню клановых домов — точки телепортации")
    val homesMenu: HomesMenuConfig = HomesMenuConfig()
}

// ──────────────────────────────────────────────────────────────────────────────
// HomesMenuConfig — полностью конфигурируемое меню клановых домов
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Full configuration for the clan homes GUI ([ua.inventorytype.pnclans.impl.ux.HomesUX]).
 *
 * Controls the inventory title, size, all item texts (names, lore lines), materials,
 * and slot positions for each home entry. Supports three home waypoints by default.
 *
 * All lore list supports `{world}`, `{x}`, `{y}`, `{z}` and `{home}` placeholder tokens.
 */
@Serializable
data class HomesMenuConfig(

    @YamlComment("Название инвентаря клановых домов")
    val title: String = "&#FC7D37« Клановые Дома »",

    @YamlComment("Количество рядов (1-6). По умолчанию 6 (54 слота)")
    val rows: Int = 6,

    @YamlComment(
        "Список точек домов. Каждая точка — отдельная запись с ключом, названием,",
        "цветом, эмодзи, слотом и предметами для установленного / незаполненного состояния."
    )
    val homes: List<HomesEntryConfig> = listOf(
        HomesEntryConfig(
            key            = "main",
            label          = "Главная",
            colorCode      = "&#5EFD7D",
            emoji          = "🏠",
            slot           = 20,
            lockedMaterial = "RED_STAINED_GLASS_PANE",
            unlockedMaterial = "RED_BED"
        ),
        HomesEntryConfig(
            key            = "base",
            label          = "База",
            colorCode      = "&#5EA9FD",
            emoji          = "🏰",
            slot           = 22,
            lockedMaterial = "BLUE_STAINED_GLASS_PANE",
            unlockedMaterial = "BLUE_BED"
        ),
        HomesEntryConfig(
            key            = "pvp",
            label          = "PvP",
            colorCode      = "&#FC65DF",
            emoji          = "⚔",
            slot           = 24,
            lockedMaterial = "PURPLE_STAINED_GLASS_PANE",
            unlockedMaterial = "PURPLE_BED"
        )
    ),

    @YamlComment(
        "Лор предмета для УСТАНОВЛЕННОГО дома.",
        "Переменные: {world} — мир, {x} {y} {z} — координаты, {home} — ключ точки"
    )
    val setLore: List<String> = listOf(
        "",
        "&#9EFC65 «Статус»",
        " &7- &fСостояние: &aУстановлена ✔",
        " &7- &fМир: &b{world}",
        " &7- &fX: &e{x}  &fY: &e{y}  &fZ: &e{z}",
        "",
        "&#FC65DF «Управление»",
        " &7- &fЛКМ: &aТелепортироваться",
        " &7- &fПКМ: &eПереставить сюда",
        " &7- &fShift+ПКМ: &cУдалить",
        "",
        "&#FF8702➥ &fНажмите &eЛКМ &fдля телепорта"
    ),

    @YamlComment(
        "Лор предмета для НЕ УСТАНОВЛЕННОГО дома.",
        "Переменная: {home} — ключ точки"
    )
    val unsetLore: List<String> = listOf(
        "",
        "&#FC3737 «Не настроена»",
        " &7- &fТочка ещё не установлена.",
        " &7- &fВстаньте в нужном месте",
        " &7- &fи нажмите &eПКМ &fдля установки.",
        "",
        "&#FF8702➥ &fНажмите &eПКМ &fчтобы установить"
    ),

    @YamlComment("Слот информационного предмета (компас) в центре меню")
    val infoSlot: Int = 31,

    @YamlComment("Название информационного предмета")
    val infoName: String = "&#FC7D37Клановые Дома",

    @YamlComment(
        "Лор информационного предмета.",
        "Переменная: {set} — кол-во установленных, {max} — максимум"
    )
    val infoLore: List<String> = listOf(
        "",
        "&#9EFC65 «Информация»",
        " &7- &fУстановлено: &e{set} &7/ &f{max} точек",
        " &7- &fМакс. точек: &b{max}",
        "",
        "&#FC65DF «Управление»",
        " &7- &fЛКМ: &aТелепортироваться",
        " &7- &fПКМ: &eУстановить / Переставить",
        " &7- &fShift+ПКМ: &cУдалить точку"
    ),

    @YamlComment("Слот кнопки «Назад»")
    val backSlot: Int = 49,

    @YamlComment("Название кнопки «Назад»")
    val backName: String = "&cВернуться в меню",

    @YamlComment("Лор кнопки «Назад»")
    val backLore: List<String> = listOf("&7Нажмите, чтобы открыть главное меню.")
)

/**
 * Configuration for a single clan home waypoint slot inside [HomesMenuConfig].
 *
 * @property key Internal storage key in [ua.inventorytype.pnclans.api.clan.Clan.homes].
 * @property label The Russian display name of this waypoint (shown in the item name).
 * @property colorCode Hex color prefix applied to the label when the home is set.
 * @property emoji Single emoji shown in the locked-state item name.
 * @property slot GUI slot index (0–53) where this home item is rendered.
 * @property lockedMaterial [Material] name shown when the waypoint is not yet configured.
 * @property unlockedMaterial [Material] name shown when the waypoint is active.
 */
@Serializable
data class HomesEntryConfig(
    @YamlComment("Внутренний ключ точки (используется в хранилище клана, не менять)")
    val key: String = "main",

    @YamlComment("Отображаемое название точки в инвентаре")
    val label: String = "Главная",

    @YamlComment("Цвет названия в формате &#RRGGBB (когда точка установлена)")
    val colorCode: String = "&#5EFD7D",

    @YamlComment("Эмодзи для незаполненного состояния")
    val emoji: String = "🏠",

    @YamlComment("Слот инвентаря (0-53)")
    val slot: Int = 20,

    @YamlComment("Предмет, когда точка НЕ установлена (стёкла, барьеры и т.д.)")
    val lockedMaterial: String = "RED_STAINED_GLASS_PANE",

    @YamlComment("Предмет, когда точка УСТАНОВЛЕНА (светится)")
    val unlockedMaterial: String = "RED_BED"
)

