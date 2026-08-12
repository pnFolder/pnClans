package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

@Serializable
data class ClanBattleSpawnConfig(
    val x: Double = 0.0,
    val y: Double = 100.0,
    val z: Double = 0.0,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f
)

@Serializable
data class ClanBattleArenaConfig(
    @YamlComment("Exact loaded world name. Empty or unknown worlds make this arena unavailable.")
    val world: String = "world",
    @YamlComment("Spawn for the challenging clan.")
    val challenger: ClanBattleSpawnConfig = ClanBattleSpawnConfig(x = -20.0),
    @YamlComment("Spawn for the defending clan.")
    val defender: ClanBattleSpawnConfig = ClanBattleSpawnConfig(x = 20.0, yaw = 180.0f),
    @YamlComment("Maximum distance from the arena centre. Participants cannot leave this radius during a battle.")
    val radius: Double = 64.0
)

@Serializable
data class ClanBattleDisplayConfig(
    val headerSlot: Int = 4,
    val headerMaterial: String = "CROSSBOW",
    val headerName: String = "&#FC3737⚔ Клановые битвы",
    val headerLore: List<String> = listOf(
        "",
        "&#9EFC65 «Боевой протокол»",
        " &7- &fВыберите клан и отправьте вызов.",
        " &7- &fПосле принятия составы переместятся на арену.",
        "",
        "&#FC65DF «Боевой профиль клана»",
        " &7- &fMMR: &#FFD700{clan_mmr}",
        " &7- &fПобеды: &#5EFD7D{wins}",
        " &7- &fПоражения: &#FC3737{losses}",
        "",
        "&#FFD700 «Условие победы»",
        " &7- &fПервым набрать &#FC3737{score_to_win} убийств&f.",
        " &7- &fЕсли время истечёт, победит лидер по счёту.",
        "",
        "&8Соперники и вызовы обновляются в этом меню."
    ),
    val ownSlot: Int = 13,
    val ownMaterial: String = "SHIELD",
    val ownName: String = "&#FC7D37✦ Боевая готовность",
    val ownLore: List<String> = listOf(
        "",
        "&#9EFC65 «Текущее состояние»",
        " &7- &fСтатус: {battle_state}",
        " &7- &fСоперник: &#5EA9FD{opponent}",
        " &7- &fСчёт: &#FFD700{score}",
        "",
        "&#FC65DF «Следующее действие»",
        "{battle_action}"
    ),
    val incomingSlot: Int = 15,
    val incomingMaterial: String = "IRON_SWORD",
    val incomingName: String = "&#FC3737⚔ Входящий вызов",
    val incomingLore: List<String> = listOf(
        "",
        "&#9EFC65 «Соперник»",
        " &7- &fКлан: &#FC7D37{challenger}",
        " &7- &fMMR: &#FFD700{challenger_mmr}",
        "",
        "&#FFD700 «Решение»",
        " &7- &fЛКМ: &#5EFD7DПринять и начать бой",
        " &7- &fПКМ: &#FC3737Отклонить вызов",
        "",
        "&8Требуется право «Организация клановых битв».",
        "",
        "&#FF8702➥ &fВыберите действие кнопкой мыши"
    ),
    @YamlComment("Only interior slots are used; opponent cards never replace decorative glass frames.")
    val opponentSlots: List<Int> = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33),
    val opponentMaterial: String = "IRON_SWORD",
    val opponentName: String = "&#FC7D37⚔ Вызвать &8• &#5EA9FD{clan}",
    val opponentLore: List<String> = listOf(
        "",
        "&#9EFC65 «Сравнение составов»",
        " &7- &fMMR соперника: &#FFD700{opponent_mmr}",
        " &7- &fРазница с вами: {mmr_difference}",
        " &7- &fУчастников онлайн: &#5EA9FD{online}",
        " &7- &fМаксимум участников: &#5EA9FD{max_participants}",
        "",
        "&#FFD700 «Награды и потери»",
        " &7- &fПобеда: &#5EFD7D+{win_mmr} MMR &8• &#FFD700+{win_points} очк.",
        " &7- &fПоражение: &#FC3737-{loss_mmr} MMR",
        " &7- &fДо победы: &#FC3737{score_to_win} убийств",
        "",
        "&#FF8702➥ &fНажмите &eЛКМ &fчтобы отправить вызов"
    ),
    val emptyMaterial: String = "SPYGLASS",
    val emptyName: String = "&#FFD700⌕ Соперники недоступны",
    val emptyLore: List<String> = listOf("", "&#9EFC65 «Состояние списка»", " &7- &f{empty_reason}", "", "&8Обновите разведданные немного позже."),
    val backSlot: Int = 45,
    val backMaterial: String = "RED_CANDLE",
    val backName: String = "&#FC3737⏎ Вернуться в штаб",
    val backLore: List<String> = listOf("", "&#FC65DF «Переход»", " &7- &fВернуться к управлению кланом.", " &7- &fНовый вызов отправлен не будет.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться"),
    val previousSlot: Int = 47,
    val previousMaterial: String = "SPECTRAL_ARROW",
    val previousName: String = "&#5EA9FD← Предыдущая страница",
    val previousLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fПоказать предыдущую группу соперников.", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
    val disabledPreviousName: String = "&#FC3737← Предыдущая страница недоступна",
    val disabledPreviousLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &fВы уже на первой странице списка."),
    val pageSlot: Int = 49,
    val pageMaterial: String = "PAPER",
    val pageName: String = "&#FFD700✦ Список соперников",
    val pageLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fСтраница: &#5EA9FD{page} &7/ &f{pages}", " &7- &fДоступно кланов: &#5EFD7D{opponents}", "", "&8Запись 1/1 означает одну страницу, а не счёт боя."),
    val nextSlot: Int = 51,
    val nextMaterial: String = "SPECTRAL_ARROW",
    val nextName: String = "&#5EA9FDСледующая страница →",
    val nextLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fПоказать следующую группу соперников.", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
    val disabledNextName: String = "&#FC3737Следующая страница недоступна →",
    val disabledNextLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &fВы уже на последней странице списка."),
    val refreshSlot: Int = 53,
    val refreshMaterial: String = "COMPASS",
    val refreshName: String = "&#5EA9FD⟳ Обновить разведданные",
    val refreshLore: List<String> = listOf("", "&#9EFC65 «Что обновится»", " &7- &fОнлайн и MMR доступных кланов.", " &7- &fВызовы и состояние активного боя.", "", "&#FC65DF «Когда использовать»", " &7- &fПосле входа соперника или ответа", " &7- &fна отправленный вызов.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы обновить")
)

@Serializable
data class ClanBattlesConfig(
    val schemaVersion: Int = 3,
    @YamlComment("Keep disabled until every arena world, spawn and radius has been configured and tested.")
    val enabled: Boolean = false,
    val title: String = "&#FC3737« Клановые битвы »",
    val rows: Int = 6,
    @YamlComment("Challenge lifetime in seconds.")
    val challengeTimeoutSeconds: Long = 120L,
    @YamlComment("Battle duration in seconds.")
    val battleDurationSeconds: Long = 600L,
    @YamlComment("Minimum online participants required on each side.")
    val minimumOnlineMembers: Int = 1,
    @YamlComment("Maximum participants from each clan. First online members are selected.")
    val maximumParticipants: Int = 5,
    @YamlComment("Kills needed to win before the timer ends.")
    val scoreToWin: Int = 10,
    val ratingWin: Int = 30,
    val ratingLoss: Int = 20,
    val pointsWin: Long = 1000L,
    val pointsPerKill: Long = 25L,
    val display: ClanBattleDisplayConfig = ClanBattleDisplayConfig(),
    @YamlComment("At least one valid arena is required to start a battle.")
    val arenas: Map<String, ClanBattleArenaConfig> = mapOf("default" to ClanBattleArenaConfig())
)
