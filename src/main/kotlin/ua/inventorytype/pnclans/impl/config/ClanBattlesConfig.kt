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
    @YamlComment("Maximum distance from the arena centre. Participants cannot leave this radius when arena boundary enforcement is enabled.")
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
        " &7- &fПосле принятия соберите состав и подтвердите READY.",
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
        "&8Соперники, lobby и активный бой обновляются в этом меню."
    ),
    val ownSlot: Int = 13,
    val ownMaterial: String = "SHIELD",
    val activeBattleMaterial: String = "DIAMOND_SWORD",
    val lobbyMaterial: String = "SHIELD",
    val countdownMaterial: String = "CLOCK",
    val ownName: String = "&#FC7D37✦ Боевая готовность",
    val ownLore: List<String> = listOf(
        "",
        "&#9EFC65 «Текущее состояние»",
        " &7- &fСтатус: {battle_state}",
        " &7- &fСоперник: &#5EA9FD{opponent}",
        " &7- &fСчёт / состав: &#FFD700{score}",
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
        " &7- &fЛКМ: &#5EFD7DПринять и открыть lobby",
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
    val mmrDifferencePositive: String = "&#FC3737+{difference}",
    val mmrDifferenceNegative: String = "&#5EFD7D{difference}",
    val mmrDifferenceEqual: String = "&#FFD7000",
    val emptyMaterial: String = "SPYGLASS",
    val emptyName: String = "&#FFD700⌕ Соперники недоступны",
    val emptyLore: List<String> = listOf("", "&#9EFC65 «Состояние списка»", " &7- &f{empty_reason}", "", "&8Обновите разведданные немного позже."),
    val emptyReasonBattle: String = "Сначала завершите текущую битву.",
    val emptyReasonLobby: String = "Сначала завершите сбор боевого состава.",
    val emptyReasonIncoming: String = "Сначала примите или отклоните входящий вызов.",
    val emptyReasonOutgoing: String = "Сначала дождитесь ответа на отправленный вызов.",
    val emptyReasonNoOpponents: String = "Нет кланов с достаточным онлайном.",
    val noOpponentText: String = "Нет",
    val drawWinnerText: String = "Ничья",
    val endReasonScoreLimit: String = "Лимит убийств",
    val endReasonTimeLimit: String = "Время истекло",
    val endReasonForfeit: String = "Техническое поражение",
    val endReasonAdminStop: String = "Остановлено администратором",
    val endReasonServerShutdown: String = "Остановка сервера",
    val stateBattle: String = "&#FC3737Битва идёт",
    val stateCountdown: String = "&#FC3737Старт через {seconds} сек.",
    val stateLobby: String = "&#5EA9FDСбор состава",
    val stateIncoming: String = "&#FFD700Есть входящий вызов",
    val stateOutgoing: String = "&#5EA9FDВызов отправлен",
    val stateIdle: String = "&#5EFD7DГотов к вызову",
    val activeScoreFormat: String = "{own_score} : {enemy_score}",
    val lobbyScoreFormat: String = "{own_selected}/{max_participants} &8• &fсоперник {enemy_selected}/{max_participants}",
    val noScoreText: String = "&8—",
    val readyText: String = "&#5EFD7DREADY",
    val waitingText: String = "&#FFD700WAIT",
    val actionBattle: String = "&#FC3737✖ &fБой уже идёт.",
    val actionCountdown: String = "&#FC3737⚔ &fНе меняйте состав: старт через &e{seconds} сек.",
    val actionLobbySelectedReady: String = "&#5EFD7D✔ &fВы в составе. Сторона READY: {own_ready} &8• &fсоперник: {enemy_ready} &8• &#FFD700ПКМ &fснять READY.",
    val actionLobbySelected: String = "&#5EA9FD✔ &fВы в составе. &#FFD700ПКМ &f— READY стороны, &#FC3737ЛКМ &f— выйти.",
    val actionLobbyNotSelected: String = "&#FF8702➥ &fЛКМ — войти в состав. READY: {own_ready} &8• &fсоперник: {enemy_ready}.",
    val actionIncoming: String = "&#FF8702➥ &fПримите или отклоните вызов справа.",
    val actionOutgoing: String = "&#5EA9FD⌚ &fОжидайте ответа соперника.",
    val actionIdle: String = "&#FF8702➥ &fВыберите соперника ниже.",
    val backSlot: Int = 45,
    val backMaterial: String = "RED_CANDLE",
    val backName: String = "&#FC3737⏎ Вернуться в штаб",
    val backLore: List<String> = listOf("", "&#FC65DF «Переход»", " &7- &fВернуться к управлению кланом.", " &7- &fНовый вызов отправлен не будет.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться"),
    val previousSlot: Int = 47,
    val previousMaterial: String = "SPECTRAL_ARROW",
    val previousName: String = "&#5EA9FD← Предыдущая страница",
    val previousLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fОткрыть предыдущую группу соперников.", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
    val disabledPreviousMaterial: String = "RED_DYE",
    val disabledPreviousName: String = "&#FC3737← Предыдущая страница недоступна",
    val disabledPreviousLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &fВы уже на первой странице списка."),
    val pageSlot: Int = 49,
    val pageMaterial: String = "PAPER",
    val pageName: String = "&#FFD700✦ Список соперников",
    val pageLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fСтраница: &#5EA9FD{page} &7/ &f{pages}", " &7- &fДоступно кланов: &#5EFD7D{opponents}", "", "&8Запись 1/1 означает одну страницу, а не счёт боя."),
    val nextSlot: Int = 51,
    val nextMaterial: String = "SPECTRAL_ARROW",
    val nextName: String = "&#5EA9FDСледующая страница →",
    val nextLore: List<String> = listOf("", "&#9EFC65 «Навигация»", " &7- &fОткрыть следующую группу соперников.", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
    val disabledNextMaterial: String = "RED_DYE",
    val disabledNextName: String = "&#FC3737Следующая страница недоступна →",
    val disabledNextLore: List<String> = listOf("", "&#FC3737 «Недоступно»", " &7- &fВы уже на последней странице списка."),
    val refreshSlot: Int = 53,
    val refreshMaterial: String = "COMPASS",
    val refreshName: String = "&#5EA9FD⟳ Обновить разведданные",
    val refreshLore: List<String> = listOf("", "&#9EFC65 «Что обновится»", " &7- &fОнлайн и MMR доступных кланов.", " &7- &fВызовы, lobby и состояние активного боя.", "", "&#FC65DF «Когда использовать»", " &7- &fПосле входа соперника или ответа", " &7- &fна отправленный вызов.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы обновить")
)

@Serializable
data class ClanBattlesConfig(
    // Kept at schema 3 until the compatibility backfill is installed for existing administrator-owned files.
    val schemaVersion: Int = 3,
    @YamlComment("Keep disabled until every arena world, spawn and radius has been configured and tested.")
    val enabled: Boolean = false,
    val title: String = "&#FC3737« Клановые битвы »",
    val rows: Int = 6,
    @YamlComment("Challenge lifetime in seconds before it is accepted or declined.")
    val challengeTimeoutSeconds: Long = 120L,
    @YamlComment("How long an accepted challenge may stay in roster/READY lobby before it expires.")
    val lobbyTimeoutSeconds: Long = 120L,
    @YamlComment("Countdown between both sides becoming READY and teleporting the frozen roster to the arena.")
    val countdownSeconds: Long = 5L,
    @YamlComment("Automatically add the challenge sender and accepting player to their lobby rosters when they are eligible.")
    val autoSelectOrganizers: Boolean = true,
    @YamlComment("Battle duration in seconds.")
    val battleDurationSeconds: Long = 600L,
    @YamlComment("Minimum selected participants required on each side.")
    val minimumOnlineMembers: Int = 1,
    @YamlComment("Maximum manually selected participants from each clan.")
    val maximumParticipants: Int = 5,
    @YamlComment("Kills needed to win before the timer ends.")
    val scoreToWin: Int = 10,
    val ratingWin: Int = 30,
    val ratingLoss: Int = 20,
    val pointsWin: Long = 1000L,
    val pointsPerKill: Long = 25L,
    @YamlComment("Keep player inventory and suppress item drops on deaths inside organized clan battles.")
    val keepInventoryOnDeath: Boolean = true,
    @YamlComment("Keep player level/experience and suppress dropped experience inside organized clan battles.")
    val keepExperienceOnDeath: Boolean = true,
    @YamlComment("Allow damage between participants from the same clan inside an organized battle.")
    val allowFriendlyFire: Boolean = false,
    @YamlComment("Prevent active participants from moving or teleporting outside the configured arena radius.")
    val enforceArenaBoundary: Boolean = true,
    @YamlComment("Forfeit when the last online participant of one side disconnects or is removed from the clan.")
    val forfeitWhenNoParticipantsOnline: Boolean = true,
    @YamlComment("Opponent cards glow when absolute MMR difference is at most this value. Set below 0 to disable the glow rule.")
    val similarMmrGlowThreshold: Int = 150,
    val display: ClanBattleDisplayConfig = ClanBattleDisplayConfig(),
    @YamlComment("At least one valid arena is required to create a battle lobby.")
    val arenas: Map<String, ClanBattleArenaConfig> = mapOf("default" to ClanBattleArenaConfig())
)
