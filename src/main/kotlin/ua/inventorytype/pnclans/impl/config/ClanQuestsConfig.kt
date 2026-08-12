package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action

@Serializable
enum class ClanQuestObjective {
    PLAYER_KILL,
    MOB_KILL,
    ACTIVITY_INTERVAL,
    MEMBER_JOINED,
    HOME_SET,
    TREASURY_DEPOSIT,
    SHOP_PURCHASE,
    BATTLE_PARTICIPATION,
    BATTLE_WIN,
    BATTLE_KILL,
    BATTLE_DAMAGE
}

@Serializable
enum class ClanQuestReset { NONE, DAILY, WEEKLY }

@Serializable
enum class ClanQuestRewardRecipient { ACTOR, LEADER, ONLINE_MEMBERS }

@Serializable
data class ClanQuestConfig(
    val slot: Int = 0,
    val material: String = "BOOK",
    val name: String = "&#FFD700✦ Клановый квест",
    val lore: List<String> = emptyList(),
    val objective: ClanQuestObjective,
    val target: Long,
    @YamlComment("Optional EntityType names for MOB_KILL, such as ZOMBIE or WITHER. Empty means every mob.")
    val entityTypes: Set<String> = emptySet(),
    @YamlComment("Completed quest IDs required before this quest may progress.")
    val prerequisites: Set<String> = emptySet(),
    val rewards: List<Action> = emptyList(),
    @YamlComment("Clan points awarded once when this quest is completed.")
    val rewardPoints: Long = 0L,
    @YamlComment("Repeat this quest after its reset cycle. Use DAILY or WEEKLY.")
    val repeatable: Boolean = false,
    val reset: ClanQuestReset = ClanQuestReset.NONE,
    @YamlComment("Who receives configured item/command actions. Clan points always go to the clan.")
    val rewardRecipient: ClanQuestRewardRecipient = ClanQuestRewardRecipient.ACTOR
)

@Serializable
data class ClanQuestDisplayConfig(
    val headerSlot: Int = 4,
    val headerMaterial: String = "WRITABLE_BOOK",
    val headerName: String = "&#FC7D37✦ Клановые квесты",
    val headerLore: List<String> = listOf(
        "",
        "&#9EFC65 «Общая кампания клана»",
        " &7- &fКаждый участник двигает общий прогресс.",
        " &7- &fНаграды выдаются автоматически.",
        "",
        "&#FC65DF «Текущий раздел»",
        " &7- &fФильтр: {filter}",
        " &7- &fЗавершено: &#5EFD7D{completed_quests} &7/ &f{total_quests}",
        "",
        "&8Выберите карточку, чтобы увидеть состояние квеста."
    ),
    @YamlComment("Only interior slots are used so filtered quest cards never replace decorative glass frames.")
    val entrySlots: List<Int> = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33),
    val entryLore: List<String> = listOf(
        "",
        "&#9EFC65 «Цель квеста»",
        " &7- &f{objective_instruction}",
        " &7- &fТип цели: &#5EA9FD{objective}",
        "",
        "&#5EA9FD «Прогресс клана»",
        " &7- &fСтатус: {status_icon} {status}",
        " &7- &fПрогресс: &#5EA9FD{progress} &7/ &f{target} &8({percent}%)",
        " &7- {progress_bar}",
        "",
        "&#FFD700 «Награда»",
        " &7- &fКлановые очки: &#FFD700{reward_points}",
        "{reward_lines}",
        "",
        "&#FC65DF «Доступ и цикл»",
        "{prerequisite_lines}",
        " &7- &fЦикл: &#5EA9FD{cycle}",
        "",
        "{action}"
    ),
    val backSlot: Int = 45,
    val backMaterial: String = "OAK_DOOR",
    val backName: String = "&#FC3737⏎ Вернуться в штаб",
    val backLore: List<String> = listOf(
        "",
        "&#FC65DF «Переход»",
        " &7- &fВернуться в штаб клана.",
        "",
        "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться"
    ),
    val summarySlot: Int = 46,
    val summaryMaterial: String = "NETHER_STAR",
    val summaryName: String = "&#FFD700✦ Сводка квестов",
    val summaryLore: List<String> = listOf(
        "",
        "&#9EFC65 «Состояние кампании»",
        " &7- &fКлановые очки: &#FFD700{clan_points}",
        " &7- &fЗавершено: &#5EFD7D{completed_quests} &7/ &f{total_quests}",
        " &7- &fВ процессе: &#5EA9FD{in_progress_quests}",
        " &7- &fДоступно: &#FF8702{available_quests}",
        " &7- &fЗаблокировано: &#FC3737{locked_quests}",
        "",
        "&#FC65DF «Подсказка»",
        " &7- &fНажмите на карточку для краткого отчёта."
    ),
    val previousSlot: Int = 48,
    val pageSlot: Int = 49,
    val nextSlot: Int = 50,
    val filterSlot: Int = 53,
    val previousMaterial: String = "SPECTRAL_ARROW",
    val pageMaterial: String = "PAPER",
    val nextMaterial: String = "SPECTRAL_ARROW",
    val filterMaterial: String = "COMPASS",
    val previousName: String = "&#5EA9FD← Предыдущая страница",
    val previousLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fОткрыть предыдущую страницу раздела.", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
    val nextName: String = "&#5EA9FDСледующая страница →",
    val nextLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fОткрыть следующую страницу раздела.", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
    val disabledPreviousName: String = "&#FC3737← Предыдущая страница недоступна",
    val disabledPreviousLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &fВы уже на первой странице раздела."),
    val disabledNextName: String = "&#FC3737Следующая страница недоступна →",
    val disabledNextLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &fВы уже на последней странице раздела."),
    val pageName: String = "&#FFD700✦ Каталог квестов",
    val pageLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fРаздел: {filter}", " &7- &fСтраница: &#5EA9FD{page} &7/ &f{pages}", " &7- &fНайдено квестов: &#5EFD7D{visible_quests}", "", "&8Карточки остаются внутри рамки меню."),
    val filterName: String = "&#FC65DF⇅ Фильтр: {filter}",
    val filterLore: List<String> = listOf("", "&#9EFC65 «Текущий раздел»", " &7- &f{filter}", "", "&#FC65DF «Следующий раздел»", " &7- &f{next_filter}", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля переключения"),
    val emptyName: String = "&#FFD700⌕ В этом разделе нет квестов",
    val emptyLore: List<String> = listOf("", "&#9EFC65 «Состояние раздела»", " &7- &fПод выбранный статус пока нет квестов.", " &7- &fПереключите фильтр компасом справа.")
)

/** Flexible clan quest definition stored in `quests.yml`. */
@Serializable
data class ClanQuestsConfig(
    val schemaVersion: Int = 6,
    val enabled: Boolean = true,
    val title: String = "&#5EA9FD« Клановые квесты »",
    val rows: Int = 6,
    val display: ClanQuestDisplayConfig = ClanQuestDisplayConfig(),
    val quests: Map<String, ClanQuestConfig> = mapOf(
        "hunter" to ClanQuestConfig(
            slot = 19,
            material = "IRON_SWORD",
            name = "&#FC7D37⚔ Охота на соперников",
            lore = listOf("", "&#FC7D37 «Боевой контракт»", " &7- &fЗасчитывается любое PvP-убийство участника клана."),
            objective = ClanQuestObjective.PLAYER_KILL,
            target = 25,
            rewardPoints = 250
        ),
        "slayers" to ClanQuestConfig(
            slot = 20,
            material = "ROTTEN_FLESH",
            name = "&#5EFD7D✦ Чистка территории",
            lore = listOf("", "&#5EFD7D «Общая зачистка»", " &7- &fЗасчитывается убийство любого моба участником клана."),
            objective = ClanQuestObjective.MOB_KILL,
            target = 250,
            rewardPoints = 500
        ),
        "presence" to ClanQuestConfig(
            slot = 21,
            material = "CLOCK",
            name = "&#5EA9FD⌚ Активный клан",
            lore = listOf("", "&#5EA9FD «Активность клана»", " &7- &fИнтервал засчитывается, пока активен хотя бы один участник."),
            objective = ClanQuestObjective.ACTIVITY_INTERVAL,
            target = 50,
            rewardPoints = 350
        ),
        "recruitment" to ClanQuestConfig(
            slot = 22,
            material = "NAME_TAG",
            name = "&#5EA9FD✉ Сильнее вместе",
            lore = listOf("", "&#5EA9FD «Усиление состава»", " &7- &fЗасчитывается успешное вступление нового участника."),
            objective = ClanQuestObjective.MEMBER_JOINED,
            target = 3,
            rewardPoints = 300,
            rewardRecipient = ClanQuestRewardRecipient.LEADER
        ),
        "reserve" to ClanQuestConfig(
            slot = 23,
            material = "GOLD_BLOCK",
            name = "&#FFD700⛁ Полная казна",
            lore = listOf("", "&#FFD700 «Фонд развития»", " &7- &fСуммируются реальные пополнения общей казны."),
            objective = ClanQuestObjective.TREASURY_DEPOSIT,
            target = 10000,
            rewardPoints = 400
        ),
        "merchant" to ClanQuestConfig(
            slot = 24,
            material = "EMERALD",
            name = "&#5EFD7D✦ Клановый снабженец",
            lore = listOf("", "&#5EFD7D «Снабжение отряда»", " &7- &fЗасчитываются только успешно оплаченные покупки."),
            objective = ClanQuestObjective.SHOP_PURCHASE,
            target = 3,
            rewardPoints = 600
        ),
        "settlers" to ClanQuestConfig(
            slot = 25,
            material = "RED_BED",
            name = "&#FC7D37⌂ Новая опора",
            lore = listOf("", "&#FC7D37 «Клановые дома»", " &7- &fЗасчитывается создание нового, а не перенос старого дома."),
            objective = ClanQuestObjective.HOME_SET,
            target = 3,
            rewardPoints = 350
        ),
        "undead-purge" to ClanQuestConfig(
            slot = 26,
            material = "BONE",
            name = "&#5EFD7D✦ Зачистка нежити",
            lore = listOf("", "&#5EFD7D «Охота на нежить»", " &7- &fЦели: зомби, скелеты, кадавры и утопленники."),
            objective = ClanQuestObjective.MOB_KILL,
            target = 100,
            entityTypes = setOf("ZOMBIE", "SKELETON", "HUSK", "DROWNED"),
            rewardPoints = 450
        ),
        "nether-front" to ClanQuestConfig(
            slot = 27,
            material = "BLAZE_ROD",
            name = "&#FC3737✦ Рубеж Незера",
            lore = listOf("", "&#FC3737 «Незерский фронт»", " &7- &fЦели: ифриты, гасты и брутальные пиглины."),
            objective = ClanQuestObjective.MOB_KILL,
            target = 75,
            entityTypes = setOf("BLAZE", "GHAST", "PIGLIN_BRUTE"),
            rewardPoints = 650
        ),
        "first-battle" to ClanQuestConfig(
            slot = 28,
            material = "SHIELD",
            name = "&#FC7D37⚔ Первый выход",
            lore = listOf("", "&#FC7D37 «Боевое крещение»", " &7- &fЗасчитывается начало принятой клановой битвы."),
            objective = ClanQuestObjective.BATTLE_PARTICIPATION,
            target = 1,
            rewardPoints = 500
        ),
        "vanguard" to ClanQuestConfig(
            slot = 29,
            material = "DIAMOND_SWORD",
            name = "&#FC3737⚔ Передовая",
            lore = listOf("", "&#FC3737 «Передовая линия»", " &7- &fЗасчитываются только убийства внутри активной арены."),
            objective = ClanQuestObjective.BATTLE_KILL,
            target = 10,
            prerequisites = setOf("first-battle"),
            rewardPoints = 900
        ),
        "siege-engineer" to ClanQuestConfig(
            slot = 30,
            material = "TNT",
            name = "&#FC65DF✦ Осадная мощь",
            lore = listOf("", "&#FC65DF «Давление на врага»", " &7- &fУрон суммируется по всем участникам боевого состава."),
            objective = ClanQuestObjective.BATTLE_DAMAGE,
            target = 5000,
            prerequisites = setOf("first-battle"),
            rewardPoints = 1000
        ),
        "victory" to ClanQuestConfig(
            slot = 31,
            material = "NETHER_STAR",
            name = "&#FFD700♛ Первая победа",
            lore = listOf("", "&#FFD700 «Первый трофей»", " &7- &fПобедите по лимиту убийств, таймеру или из-за технического поражения соперника."),
            objective = ClanQuestObjective.BATTLE_WIN,
            target = 1,
            prerequisites = setOf("first-battle"),
            rewardPoints = 750
        ),
        "weekly-war" to ClanQuestConfig(
            slot = 32,
            material = "NETHERITE_SWORD",
            name = "&#FC3737⚔ Военная неделя",
            lore = listOf("", "&#FC3737 «Неделя доминирования»", " &7- &fОдержите три победы до следующего понедельника по UTC."),
            objective = ClanQuestObjective.BATTLE_WIN,
            target = 3,
            prerequisites = setOf("victory"),
            rewardPoints = 1500,
            repeatable = true,
            reset = ClanQuestReset.WEEKLY
        ),
        "duelist" to ClanQuestConfig(
            slot = 33,
            material = "IRON_AXE",
            name = "&#FC7D37⚔ Клинки клана",
            lore = listOf("", "&#FC7D37 «Открытое PvP»", " &7- &fАрена не обязательна: учитываются обычные PvP-убийства."),
            objective = ClanQuestObjective.PLAYER_KILL,
            target = 10,
            rewardPoints = 300
        ),
        "quartermaster" to ClanQuestConfig(
            slot = 34,
            material = "CHEST",
            name = "&#FFD700✦ Надёжный снабженец",
            lore = listOf("", "&#FFD700 «Крупное снабжение»", " &7- &fПродолжайте покупать товары после первого контракта."),
            objective = ClanQuestObjective.SHOP_PURCHASE,
            target = 10,
            prerequisites = setOf("merchant"),
            rewardPoints = 900
        ),
        "home-network" to ClanQuestConfig(
            slot = 35,
            material = "COMPASS",
            name = "&#5EA9FD⌂ Сеть опорных точек",
            lore = listOf("", "&#5EA9FD «Сеть клановых домов»", " &7- &fСоздавайте новые доступные клановые дома."),
            objective = ClanQuestObjective.HOME_SET,
            target = 6,
            prerequisites = setOf("settlers"),
            rewardPoints = 800
        )
    )
)
