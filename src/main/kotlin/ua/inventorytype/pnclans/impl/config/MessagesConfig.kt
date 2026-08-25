package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.BossBarAction
import ua.inventorytype.pnclans.api.MessageAction
import ua.inventorytype.pnclans.api.MiniMessageAction
import ua.inventorytype.pnclans.api.SoundAction

/** Every player-facing response is represented by configurable Action lists in messages.yml. */
@Serializable
class MessagesConfig {
    @YamlComment("Общие сообщения, используемые в разных частях плагина")
    val general: GeneralMessages = GeneralMessages()
    @YamlComment("Сообщения о создании, расформировании и выходе из клана")
    val clan: ClanMessages = ClanMessages()
    @YamlComment("Сообщения системы управления клановыми домами")
    val homes: HomesMessages = HomesMessages()
    @YamlComment("Сообщения отложенной телепортации к клановому дому")
    val teleport: TeleportMessages = TeleportMessages()
    @YamlComment("Сообщения системы приглашений в клан")
    val invite: InviteMessages = InviteMessages()
    @YamlComment("Сообщения при управлении составом клана")
    val members: MembersMessages = MembersMessages()
    @YamlComment("Сообщения казны")
    val treasury: TreasuryMessages = TreasuryMessages()
    @YamlComment("Сообщения системы повышения уровня клана")
    val upgrade: UpgradeMessages = UpgradeMessages()
    @YamlComment("Сообщения панели настроек клана")
    val settings: SettingsMessages = SettingsMessages()
    @YamlComment("Сообщения виртуального кланового сундука")
    val chest: ChestMessages = ChestMessages()
    @YamlComment("Сообщения общей системы клановых квестов")
    val quests: QuestMessages = QuestMessages()
    @YamlComment("Сообщения вызовов, lobby и результатов клановых битв")
    val battles: BattleMessages = BattleMessages()

    @Serializable
    data class QuestMessages(
        val completed: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fКлан завершил квест {quest_name}&f: &#5EA9FD{progress} &7/ &f{target}. Награда: &#FFD700{reward_points} клановых очков&f."), SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 1.1f)),
        val locked: List<Action> = listOf(MessageAction("&#FC3737✖ &fКвест пока закрыт. Сначала завершите: &e{prerequisites}&f."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val available: List<Action> = listOf(MessageAction("&#FF8702➥ &fКвест доступен всему клану. Выполняйте цель вместе."), SoundAction("UI_BUTTON_CLICK", 0.8f, 1.1f)),
        val inProgress: List<Action> = listOf(MessageAction("&#5EA9FD⌚ &fПрогресс квеста: &#5EFD7D{progress}&7/&f{target} &7({percent}%)."), SoundAction("UI_BUTTON_CLICK", 0.8f, 1.0f)),
        val alreadyCompleted: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fЭтот квест уже завершён в текущем цикле."), SoundAction("BLOCK_NOTE_BLOCK_PLING", 0.8f, 1.3f))
    )

    @Serializable
    data class BattleMessages(
        val challengeSent: List<Action> = listOf(MessageAction("&#FC7D37⚔ &fВаш клан вызвал клан &#FC3737{opponent} &fна бой."), SoundAction("ITEM_CROSSBOW_LOADING_START", 1.0f, 1.0f)),
        val challengeReceived: List<Action> = listOf(MessageAction("&#FC3737⚔ &fКлан &#FC7D37{challenger} &fвызвал вас на бой."), MessageAction("&#FF8702➥ &fОткройте меню битв или используйте &e/clan battle accept {challenge_id}&f."), SoundAction("ITEM_CROSSBOW_LOADING_END", 1.0f, 1.0f)),
        val declined: List<Action> = listOf(MessageAction("&#FC3737✖ &fКлан отклонил ваш боевой вызов."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.1f)),
        val declinedByYou: List<Action> = listOf(MessageAction("&#FC3737✖ &fБоевой вызов отклонён.")),
        val challengeExpired: List<Action> = listOf(MessageAction("&#FFD700⌛ &fСрок боевого вызова истёк.")),
        val lobbyOpened: List<Action> = listOf(MessageAction("&#5EA9FD⌚ &fВызов принят. Открыт сбор состава: &eЛКМ по «Боевой готовности» — войти/выйти, ПКМ — READY стороны."), SoundAction("UI_BUTTON_CLICK", 0.8f, 1.1f)),
        val lobbyJoinedRoster: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fВы вошли в боевой состав.")),
        val lobbyLeftRoster: List<Action> = listOf(MessageAction("&#FFD700⌚ &fВы вышли из боевого состава.")),
        val lobbySideReady: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fВаша сторона готова к бою.")),
        val lobbySideNotReady: List<Action> = listOf(MessageAction("&#FFD700⌚ &fГотовность вашей стороны снята.")),
        val lobbyCountdownStarted: List<Action> = listOf(MessageAction("&#FC3737⚔ &fОбе стороны готовы. Перемещение на арену через &e{seconds} секунд&f."), SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.2f)),
        val lobbyRosterChanged: List<Action> = listOf(MessageAction("&#FFD700⌚ &fСтарт отменён: состав одной из сторон изменился. Подтвердите READY заново.")),
        val lobbySelectedPlayerOffline: List<Action> = listOf(MessageAction("&#FFD700⌚ &fСтарт отменён: один из выбранных игроков вышел с сервера. Подтвердите READY заново.")),
        val lobbyClanRemoved: List<Action> = listOf(MessageAction("&#FC3737✖ &fСбор состава отменён: один из кланов был расформирован.")),
        val lobbyStoppedByAdmin: List<Action> = listOf(MessageAction("&#FC3737✖ &fСбор состава остановлен администратором.")),
        val lobbyModuleDisabled: List<Action> = listOf(MessageAction("&#FC3737✖ &fСбор состава отменён: модуль битв отключён.")),
        val lobbyOpponentUnavailable: List<Action> = listOf(MessageAction("&#FC3737✖ &fСбор состава отменён: клан-соперник больше недоступен.")),
        val lobbyClanBecameBusy: List<Action> = listOf(MessageAction("&#FC3737✖ &fСбор состава отменён: один из кланов уже участвует в другом бою.")),
        val lobbyArenaUnavailable: List<Action> = listOf(MessageAction("&#FC3737✖ &fАрена стала недоступна. Сообщите администратору.")),
        val lobbyStartCancelled: List<Action> = listOf(MessageAction("&#FFD700⌚ &fСтарт боя отменён другим плагином. READY сторон сброшен.")),
        val lobbyArenaBusy: List<Action> = listOf(MessageAction("&#FC3737✖ &fАрена занята другим боем. Сбор состава отменён.")),
        val lobbyTeleportFailed: List<Action> = listOf(MessageAction("&#FC3737✖ &fНе удалось безопасно переместить весь состав на арену. Бой отменён.")),
        val lobbyExpired: List<Action> = listOf(MessageAction("&#FFD700⌛ &fВремя на сбор состава истекло. Боевой вызов отменён.")),
        val lobbyNotFound: List<Action> = listOf(MessageAction("&#FC3737✖ &fСбор состава уже завершён или не найден.")),
        val lobbyFull: List<Action> = listOf(MessageAction("&#FC3737✖ &fБоевой состав вашей стороны уже заполнен.")),
        val lobbyNotEnoughSelected: List<Action> = listOf(MessageAction("&#FFD700⌚ &fСначала выберите минимум &e{minimum} &fучастника(ов) в состав.")),
        val started: List<Action> = listOf(MessageAction("&#FC3737⚔ &fБитва началась: &#FC7D37{challenger} &7против &#5EA9FD{defender}&f."), MessageAction("&7Счёт: &e{challenger_score} &7: &e{defender_score} &8• &7Осталось: &e{seconds} сек."), SoundAction("EVENT_RAID_HORN", 1.0f, 1.0f)),
        val finished: List<Action> = listOf(MessageAction("&#FFD700♛ &fБитва завершена: &#FC7D37{challenger} &e{challenger_score} &7: &e{defender_score} &#5EA9FD{defender}&f."), MessageAction("&#FFD700✦ &fИтог: &e{winner}&f. &7Причина: &f{reason}"), SoundAction("UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f)),
        val disabled: List<Action> = listOf(MessageAction("&#FC3737✖ &fМодуль клановых битв отключён.")),
        val noPermission: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ вас нет права организовывать клановые битвы.")),
        val clanBusy: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ вашего клана или соперника уже есть активный бой либо вызов.")),
        val challengeExists: List<Action> = listOf(MessageAction("&#FFD700! &fУ одного из кланов уже есть активный вызов.")),
        val challengeNotFound: List<Action> = listOf(MessageAction("&#FC3737✖ &fБоевой вызов не найден или уже обработан.")),
        val notTarget: List<Action> = listOf(MessageAction("&#FC3737✖ &fЭтот вызов предназначен другому клану.")),
        val notEnoughOnline: List<Action> = listOf(MessageAction("&#FC3737✖ &fДля начала боя нужно больше участников онлайн.")),
        val arenaUnavailable: List<Action> = listOf(MessageAction("&#FC3737✖ &fСейчас нет доступной арены. Сообщите администратору.")),
        val cancelled: List<Action> = listOf(MessageAction("&#FC3737✖ &fОперация с боем отменена другим плагином."))
    )

    @Serializable
    data class GeneralMessages(
        val noPermission: List<Action> = listOf(MessageAction("&cУ вас недостаточно прав для выполнения этого действия."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val playerOnly: List<Action> = listOf(MessageAction("&#FC3737✖ &fЭта игровая команда доступна только игроку.")),
        val internalError: List<Action> = listOf(MessageAction("&#FC3737✖ &fПроизошла внутренняя ошибка. Подробности записаны в журнал сервера.")),
        val statsPlayerNotInClan: List<Action> = listOf(MessageAction("&#FC3737✖ &fИгрок &e{player} &fне состоит в вашем клане.")),
        val statsUsage: List<Action> = listOf(MessageAction("&#FFD700Использование: &f/clan stats [player] [day|week|month|all]")),
        val invalidInput: List<Action> = listOf(MessageAction("&cНекорректный ввод. Укажите корректное число."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val operationCancelled: List<Action> = listOf(MessageAction("&cОперация отменена."))
    )

    @Serializable
    data class ClanMessages(
        val created: List<Action> = listOf(MessageAction("&aВы успешно создали клан &e{clan}&a!"), SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f)),
        @YamlComment("Начало ввода названия нового клана. Переменные: {cancel}, {seconds}.")
        val creationPromptStarted: List<Action> = listOf(MessageAction("&#5EA9FD✎ &fВведите название нового клана в чат. Для отмены: &c{cancel}&f. Время: &e{seconds} сек."), SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.2f)),
        val creationPromptCancelled: List<Action> = listOf(MessageAction("&#FC3737✖ &fСоздание клана отменено.")),
        val creationPromptTimedOut: List<Action> = listOf(MessageAction("&#FC3737⌛ &fВремя на ввод названия клана истекло."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val alreadyInClan: List<Action> = listOf(MessageAction("&cВы уже состоите в клане."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val nameTooShort: List<Action> = listOf(MessageAction("&cНазвание клана должно быть от 2 до 16 символов.")),
        val nameTooLong: List<Action> = listOf(MessageAction("&cНазвание клана не должно превышать 16 символов.")),
        val nameInvalidChars: List<Action> = listOf(MessageAction("&cНазвание содержит недопустимые символы. Используйте буквы, цифры и _.")),
        val nameAlreadyExists: List<Action> = listOf(MessageAction("&cКлан с таким названием уже существует.")),
        val notEnoughMoney: List<Action> = listOf(MessageAction("&cНедостаточно средств для создания клана (требуется &e{cost}&c$)."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val disbanded: List<Action> = listOf(MessageAction("&cКлан &e{clan} &cбыл распущен лидером."), SoundAction("ENTITY_WITHER_DEATH", 0.8f, 0.7f)),
        val disbandedLeader: List<Action> = listOf(MessageAction("&cВы распустили клан &e{clan}&c.")),
        val left: List<Action> = listOf(MessageAction("&cВы вышли из клана &e{clan}&c.")),
        val leaderCannotLeave: List<Action> = listOf(MessageAction("&#FC3737✖ &fГлава не может покинуть клан. Используйте расформирование."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f))
    )

    @Serializable
    data class HomesMessages(
        val moduleDisabled: List<Action> = listOf(MessageAction("&#FC3737✖ &fМодуль клановых точек дома отключён.")),
        val teleported: List<Action> = listOf(MessageAction("&aВы телепортировались на клановую точку &e{home}&a!"), SoundAction("ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f)),
        val notSet: List<Action> = listOf(MessageAction("&cКлановая точка &e{home} &cещё не установлена."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val noPermissionSet: List<Action> = listOf(MessageAction("&cУ вас нет прав для установки точки дома."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val noPermissionDelete: List<Action> = listOf(MessageAction("&cУ вас нет прав для удаления точки дома."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val set: List<Action> = listOf(MessageAction("&aКлановая точка &e{home} &aуспешно установлена!"), SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.5f)),
        val deleted: List<Action> = listOf(MessageAction("&cКлановая точка &e{home} &cудалена."), SoundAction("ENTITY_ITEM_BREAK", 1.0f, 1.0f)),
        val unknownHome: List<Action> = listOf(MessageAction("&#FC3737✖ &fТочка &#5EA9FD{home} &fне настроена в меню домов."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val lockedByLevel: List<Action> = listOf(MessageAction("&#FC3737✖ &fТочка &#5EA9FD{home} &fоткрывается с &e{level} &fуровня клана."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f))
    )

    @Serializable
    data class TeleportMessages(
        val started: List<Action> = listOf(MessageAction("&#FC7D37✦ &fТелепортация к точке &#5EA9FD{home} &fчерез &e{seconds} сек.&f Не двигайтесь."), SoundAction("BLOCK_NOTE_BLOCK_PLING", 0.8f, 1.2f)),
        val cancelled: List<Action> = listOf(MessageAction("&#FC3737✖ &fТелепортация отменена: вы сдвинулись или получили урон."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val completed: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fВы телепортировались к точке &#5EA9FD{home}&f."), SoundAction("ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f))
    )

    @Serializable
    data class InviteMessages(
        val prompt: InvitePromptConfig = InvitePromptConfig(),
        val inviterNoClan: List<Action> = listOf(MessageAction("&#FC3737✖ &fВы не состоите в клане."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val noPermission: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ вас нет прав приглашать игроков в клан."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val cancelled: List<Action> = listOf(MessageAction("&#FC3737✖ &fОтправка приглашения отменена.")),
        val cannotInviteSelf: List<Action> = listOf(MessageAction("&#FC3737✖ &fНельзя отправить приглашение самому себе."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val targetNotFound: List<Action> = listOf(MessageAction("&#FC3737✖ &fИгрок &e{player} &fне найден в сети."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val targetAlreadyInYourClan: List<Action> = listOf(MessageAction("&#FC3737✖ &fИгрок &e{player} &fуже состоит в вашем клане."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val targetAlreadyInOtherClan: List<Action> = listOf(MessageAction("&#FC3737✖ &fИгрок &e{player} &fуже состоит в клане &#5EA9FD{clan}&f."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val targetHasPendingInvite: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ игрока &e{player} &fуже есть активное приглашение."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val clanFull: List<Action> = listOf(MessageAction("&#FC3737✖ &fВ клане &#5EA9FD{clan} &fдостигнут лимит: &e{limit} &fучастников."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val inviteSent: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fПриглашение отправлено игроку &e{player}&f."), SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.0f)),
        val inviteReceived: List<Action> = listOf(MessageAction("&#FC7D37✦ &fИгрок &e{sender} &fприглашает вас в клан &#5EA9FD{clan}&f."), SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f)),
        val inviteInstructions: List<Action> = listOf(MiniMessageAction("<newline><gray> » </gray><click:run_command:'/clan accept {clan}'><hover:show_text:'<green>Нажмите, чтобы вступить в клан <b>{clan}</b></green>'><gradient:#5EFD7D:#2ECC71><bold>[ ✔ ПРИНЯТЬ ]</bold></gradient></hover></click>  <gray>или</gray>  <click:run_command:'/clan deny {clan}'><hover:show_text:'<red>Нажмите, чтобы отказаться от приглашения в <b>{clan}</b></red>'><gradient:#FC3737:#C0392B><bold>[ ✖ ОТКЛОНИТЬ ]</bold></gradient></hover></click><newline><gray>    У вас есть <yellow>{seconds} сек.</yellow> на раздумья.</gray><newline>")),
        val noActiveInvite: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ вас нет активного приглашения в клан."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val inviteExpired: List<Action> = listOf(MessageAction("&#FC3737✖ &fСрок действия приглашения истёк."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val inviteInvalid: List<Action> = listOf(MessageAction("&#FC3737✖ &fПриглашение больше недействительно."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val acceptAlreadyInClan: List<Action> = listOf(MessageAction("&#FC3737✖ &fВы уже состоите в клане и не можете принять приглашение."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val accepted: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fВы вступили в клан &#5EA9FD{clan}&f!"), SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 1.1f)),
        val memberJoined: List<Action> = listOf(MessageAction("&#5EFD7D✦ &fИгрок &e{player} &fвступил в клан."), SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 0.7f, 1.1f)),
        val denied: List<Action> = listOf(MessageAction("&#FC3737✖ &fВы отклонили приглашение в клан &#5EA9FD{clan}&f.")),
        val deniedByTarget: List<Action> = listOf(MessageAction("&#FC3737✖ &fИгрок &e{player} &fотклонил приглашение в клан."))
    )

    @Serializable
    data class InvitePromptConfig(
        val cancelInputs: List<String> = listOf("отмена", "cancel"),
        val started: List<Action> = listOf(MessageAction("&#FC7D37✦ &fНапишите никнейм игрока в чат или &cотмена&f."), SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.2f), BossBarAction("&#FC7D37✦ &fВведите никнейм игрока &7• &e{seconds} сек.", "YELLOW", "SOLID")),
        val timedOut: List<Action> = listOf(MessageAction("&#FC3737✖ &fВремя на ввод никнейма истекло."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f))
    )

    @Serializable
    data class MembersMessages(
        val noPermissionKick: List<Action> = listOf(MessageAction("&cУ вас нет прав на исключение участников."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val cannotManageHigherRank: List<Action> = listOf(MessageAction("&cВы не можете управлять игроком равного или высшего ранга."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val kicked: List<Action> = listOf(MessageAction("&aВы исключили &e{player} &aиз клана.")),
        val kickedTarget: List<Action> = listOf(MessageAction("&#FC3737✖ &fВы были исключены из клана &#5EA9FD{clan}&f."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val leaderTransferred: List<Action> = listOf(MessageAction("&aЛидерство передано игроку &e{player}&a."), SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 0.8f)),
        val cannotPromote: List<Action> = listOf(MessageAction("&cВы не можете повысить игрока до этого ранга."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val promoted: List<Action> = listOf(MessageAction("&aИгрок &e{player} &aповышен до &b{role}&a."), SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.5f)),
        val demoted: List<Action> = listOf(MessageAction("&aИгрок &e{player} &aпонижен до &b{role}&a."), SoundAction("ENTITY_ITEM_BREAK", 0.8f, 1.0f))
    )

    @Serializable
    data class TreasuryMessages(
        val moduleDisabled: List<Action> = listOf(MessageAction("&#FC3737✖ &fМодуль клановой казны отключён.")),
        val noPermissionDeposit: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ роли &e{role} &fнет права пополнять казну."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val noPermissionWithdraw: List<Action> = listOf(MessageAction("&#FC3737✖ &fУ роли &e{role} &fнет права снимать средства из казны."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val depositPromptStarted: List<Action> = listOf(MessageAction("&#5EFD7D⛁ &fВведите сумму пополнения в чат. Для отмены: &e{cancel}&f. Время: &e{seconds} сек.")),
        val withdrawPromptStarted: List<Action> = listOf(MessageAction("&#FC65DF⛁ &fВведите сумму снятия в чат. Для отмены: &e{cancel}&f. Время: &e{seconds} сек.")),
        val promptCancelled: List<Action> = listOf(MessageAction("&#FFD700⌁ &fВвод суммы казны отменён.")),
        val promptInvalidAmount: List<Action> = listOf(MessageAction("&#FC3737✖ &fНекорректная сумма: &e{input}&f. Укажите положительное число."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val promptTimedOut: List<Action> = listOf(MessageAction("&#FC3737⌛ &fВремя на ввод суммы казны истекло."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val persistenceFailed: List<Action> = listOf(MessageAction("&#FC3737✖ &fОперация отменена: сохранить данные клана не удалось. Проверьте журнал сервера."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val cancelledByPlugin: List<Action> = listOf(MessageAction("&#FFD700⌁ &fОперация с казной отменена другим плагином.")),
        val deposited: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fВы пополнили казну на &e{amount} ⛁&f. Баланс: &a{balance}⛁"), SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f)),
        val withdrawn: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fВы сняли с казны &e{amount} ⛁&f. Баланс: &a{balance}⛁"), SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 0.8f)),
        val insufficientPersonalFunds: List<Action> = listOf(MessageAction("&#FC3737✖ &fНедостаточно личных средств для пополнения на &e{amount} ⛁&f."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val insufficientClanFunds: List<Action> = listOf(MessageAction("&#FC3737✖ &fВ казне недостаточно средств для снятия &e{amount} ⛁&f."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f))
    )

    @Serializable
    data class UpgradeMessages(
        val noPermission: List<Action> = listOf(MessageAction("&cУ вас нет полномочий для проведения ритуала."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val insufficientFunds: List<Action> = listOf(MessageAction("&cНедостаточно монет в казне клана!"), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val insufficientMmr: List<Action> = listOf(MessageAction("&cКлан недостаточно силён (мало MMR)!"), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val insufficientQuests: List<Action> = listOf(MessageAction("&#FC3737✖ &fНедостаточно завершений квестов: &e{completed_quests} &7/ &e{required_quests}&f."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val levelUp: List<Action> = listOf(MessageAction("&a⚡ Клан возвысился до &e{level} &aуровня! Открыты новые возможности!"), SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 0.7f)),
        val maxLevel: List<Action> = listOf(MessageAction("&#FFD700✦ &fВаш клан уже достиг высшего уровня эволюции."), SoundAction("ENTITY_PLAYER_LEVELUP", 0.7f, 1.4f))
    )

    @Serializable
    data class SettingsMessages(
        val noPermission: List<Action> = listOf(MessageAction("&cУ вас нет разрешения на изменение этой настройки."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val noPermissionRoles: List<Action> = listOf(MessageAction("&cТолько лидер клана может редактировать роли."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val highlightColorChanged: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fЦвет метки изменён на &e{color}&f."), SoundAction("UI_BUTTON_CLICK", 0.8f, 1.2f)),
        val highlightTypeChanged: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fТип метки изменён на &e{type}&f."), SoundAction("UI_BUTTON_CLICK", 0.8f, 1.2f)),
        val highlightEnabled: List<Action> = listOf(MessageAction("&#5EFD7D✔ &fМетка соклановцев включена.")),
        val highlightDisabled: List<Action> = listOf(MessageAction("&#FC3737✖ &fМетка соклановцев выключена.")),
        val highlightReset: List<Action> = listOf(MessageAction("&#FFD700↺ &fНастройки метки сброшены к стандартным."), SoundAction("UI_BUTTON_CLICK", 0.8f, 1.0f)),
        val highlightSaveFailed: List<Action> = listOf(MessageAction("&#FC3737✖ &fНе удалось сохранить настройки метки. Проверьте журнал сервера."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f))
    )

    @Serializable
    data class ChestMessages(
        val moduleDisabled: List<Action> = listOf(MessageAction("&#FC3737✖ &fМодуль кланового сундука отключён.")),
        val slotLocked: List<Action> = listOf(MessageAction("&c[Сундук] Этот слот заблокирован! Прокачайте клан до &e{level} &cуровня."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.4f)),
        val noPermission: List<Action> = listOf(MessageAction("&cУ вас нет прав для доступа к клановому сундуку."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)),
        val chestDisabled: List<Action> = listOf(MessageAction("&cКлановый сундук временно недоступен — закрыт лидером."), SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f))
    )
}
