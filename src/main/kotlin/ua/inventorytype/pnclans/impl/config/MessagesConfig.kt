package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.MessageAction
import ua.inventorytype.pnclans.api.SoundAction

/**
 * Top-level container for all plugin event responses, loaded from `messages.yml`.
 *
 * Each field is a **list of [Action]** objects instead of a plain string.
 * This means every event response can trigger any combination of:
 * `!message`, `!sound`, `!title`, `!actionbar`, `!particle`, `!broadcast`, etc.
 *
 * Administrators can fully customize all responses without touching code.
 * Supports color codes (`&a`), hex colors (`&#FC7D37`), and placeholder tokens (`{player}`, `{clan}`, etc.).
 *
 * Example YAML:
 * ```yaml
 * general:
 *   noPermission:
 *     - !message { text: '&cУ вас нет прав.' }
 *     - !sound { sound: 'ENTITY_VILLAGER_NO', volume: 1.0, pitch: 1.2 }
 * ```
 */
@Serializable
class MessagesConfig {

    @YamlComment("Общие сообщения, используемые в разных частях плагина")
    val general: GeneralMessages = GeneralMessages()

    @YamlComment("Сообщения о создании, расформировании и выходе из клана")
    val clan: ClanMessages = ClanMessages()

    @YamlComment("Сообщения системы управления клановыми домами (точками телепортации)")
    val homes: HomesMessages = HomesMessages()

    @YamlComment("Сообщения системы приглашений в клан")
    val invite: InviteMessages = InviteMessages()

    @YamlComment("Сообщения при управлении составом клана (повышение, понижение, кик)")
    val members: MembersMessages = MembersMessages()

    @YamlComment("Сообщения казны (пополнение, снятие средств)")
    val treasury: TreasuryMessages = TreasuryMessages()

    @YamlComment("Сообщения системы повышения уровня клана (Ритуал Возвышения)")
    val upgrade: UpgradeMessages = UpgradeMessages()

    @YamlComment("Сообщения панели настроек клана")
    val settings: SettingsMessages = SettingsMessages()

    @YamlComment("Сообщения виртуального клановое сундука")
    val chest: ChestMessages = ChestMessages()

    /**
     * General-purpose messages shared across multiple plugin features.
     *
     * @property noPermission Actions fired when a player attempts an action without required clan permission.
     * @property invalidInput Actions fired when a player provides a non-numeric or invalid input.
     * @property operationCancelled Actions fired when the player explicitly cancels an input prompt.
     */
    @Serializable
    data class GeneralMessages(
        @YamlComment("Действия при отсутствии прав на операцию")
        val noPermission: List<Action> = listOf(
            MessageAction("&cУ вас недостаточно прав для выполнения этого действия."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Действия при некорректном вводе числа")
        val invalidInput: List<Action> = listOf(
            MessageAction("&cНекорректный ввод. Укажите корректное число."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Действия при отмене активного запроса ввода")
        val operationCancelled: List<Action> = listOf(
            MessageAction("&cОперация отменена.")
        )
    )

    /**
     * Messages related to clan lifecycle: creation, disbanding, and leaving.
     *
     * Supported placeholders: `{clan}`, `{cost}`
     *
     * @property created Actions when a clan is successfully created.
     * @property alreadyInClan Actions when the player is already in a clan.
     * @property nameTooShort Actions when the clan name is too short.
     * @property nameTooLong Actions when the clan name exceeds the limit.
     * @property nameInvalidChars Actions when the clan name contains illegal characters.
     * @property nameAlreadyExists Actions when the clan name is taken.
     * @property notEnoughMoney Actions when the player cannot afford clan creation.
     * @property disbanded Actions sent to all members when the clan is disbanded.
     * @property disbandedLeader Actions sent to the leader upon disbanding.
     * @property left Actions sent to the player upon leaving.
     */
    @Serializable
    data class ClanMessages(
        @YamlComment("Клан успешно создан. Переменная: {clan}")
        val created: List<Action> = listOf(
            MessageAction("&aВы успешно создали клан &e{clan}&a!"),
            SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f)
        ),

        @YamlComment("Игрок уже состоит в клане")
        val alreadyInClan: List<Action> = listOf(
            MessageAction("&cВы уже состоите в клане."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Название клана слишком короткое (менее 2 символов)")
        val nameTooShort: List<Action> = listOf(
            MessageAction("&cНазвание клана должно быть от 2 до 16 символов.")
        ),

        @YamlComment("Название клана слишком длинное (более 16 символов)")
        val nameTooLong: List<Action> = listOf(
            MessageAction("&cНазвание клана не должно превышать 16 символов.")
        ),

        @YamlComment("Недопустимые символы в названии клана")
        val nameInvalidChars: List<Action> = listOf(
            MessageAction("&cНазвание содержит недопустимые символы. Используйте буквы, цифры и _.")
        ),

        @YamlComment("Клан с таким названием уже существует")
        val nameAlreadyExists: List<Action> = listOf(
            MessageAction("&cКлан с таким названием уже существует.")
        ),

        @YamlComment("Недостаточно денег для создания клана. Переменная: {cost}")
        val notEnoughMoney: List<Action> = listOf(
            MessageAction("&cНедостаточно средств для создания клана (требуется &e{cost}&c$)."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Рассылка участникам при роспуске клана. Переменная: {clan}")
        val disbanded: List<Action> = listOf(
            MessageAction("&cКлан &e{clan} &cбыл распущен лидером."),
            SoundAction("ENTITY_WITHER_DEATH", 0.8f, 0.7f)
        ),

        @YamlComment("Сообщение лидеру после роспуска клана. Переменная: {clan}")
        val disbandedLeader: List<Action> = listOf(
            MessageAction("&cВы распустили клан &e{clan}&c.")
        ),

        @YamlComment("Игрок покинул клан. Переменная: {clan}")
        val left: List<Action> = listOf(
            MessageAction("&cВы вышли из клана &e{clan}&c.")
        )
    )

    /**
     * Messages for the clan home (waypoint) management system.
     *
     * Supported placeholders: `{home}`
     *
     * @property teleported Actions when a player teleports to a home.
     * @property notSet Actions when the home point is not configured.
     * @property noPermissionSet Actions when lacking SET home permission.
     * @property noPermissionDelete Actions when lacking DELETE home permission.
     * @property set Actions when a home is successfully configured.
     * @property deleted Actions when a home is successfully deleted.
     */
    @Serializable
    data class HomesMessages(
        @YamlComment("Телепортация на клановую точку. Переменная: {home}")
        val teleported: List<Action> = listOf(
            MessageAction("&aВы телепортировались на клановую точку &e{home}&a!"),
            SoundAction("ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f)
        ),

        @YamlComment("Точка дома не установлена. Переменная: {home}")
        val notSet: List<Action> = listOf(
            MessageAction("&cКлановая точка &e{home} &cещё не установлена."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Нет прав для установки точки дома")
        val noPermissionSet: List<Action> = listOf(
            MessageAction("&cУ вас нет прав для установки точки дома."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Нет прав для удаления точки дома")
        val noPermissionDelete: List<Action> = listOf(
            MessageAction("&cУ вас нет прав для удаления точки дома."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Точка дома успешно установлена. Переменная: {home}")
        val set: List<Action> = listOf(
            MessageAction("&aКлановая точка &e{home} &aуспешно установлена!"),
            SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.5f)
        ),

        @YamlComment("Точка дома удалена. Переменная: {home}")
        val deleted: List<Action> = listOf(
            MessageAction("&cКлановая точка &e{home} &cудалена."),
            SoundAction("ENTITY_ITEM_BREAK", 1.0f, 1.0f)
        )
    )

    /**
     * Messages for the player invitation system.
     *
     * Supported placeholders: `{player}`, `{clan}`
     *
     * @property noPermission Actions when the inviter lacks INVITE permission.
     * @property cancelled Actions when the invite prompt is cancelled.
     * @property targetNotFound Actions when the target is offline.
     * @property targetAlreadyInYourClan Actions when the target is in the same clan.
     * @property targetAlreadyInOtherClan Actions when the target is in a different clan.
     * @property inviteSent Actions sent to the inviter on success.
     * @property inviteReceived Actions sent to the invited player.
     * @property inviteInstructions Follow-up instructions sent to the invited player.
     */
    @Serializable
    data class InviteMessages(
        @YamlComment("Нет прав на приглашение игроков")
        val noPermission: List<Action> = listOf(
            MessageAction("&cУ вас нет прав приглашать игроков в клан."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Отмена отправки приглашения")
        val cancelled: List<Action> = listOf(
            MessageAction("&cПриглашение отменено.")
        ),

        @YamlComment("Игрок не найден в сети. Переменная: {player}")
        val targetNotFound: List<Action> = listOf(
            MessageAction("&cИгрок &e{player} &cне найден в сети."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Игрок уже в вашем клане. Переменная: {player}")
        val targetAlreadyInYourClan: List<Action> = listOf(
            MessageAction("&cИгрок &e{player} &cуже состоит в вашем клане."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Игрок уже в другом клане. Переменные: {player}, {clan}")
        val targetAlreadyInOtherClan: List<Action> = listOf(
            MessageAction("&cИгрок &e{player} &cуже состоит в клане &e{clan}&c."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Приглашение успешно отправлено. Переменная: {player}")
        val inviteSent: List<Action> = listOf(
            MessageAction("&aПриглашение успешно отправлено игроку &e{player}&a!"),
            SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.0f)
        ),

        @YamlComment("Игрок получил приглашение. Переменная: {clan}")
        val inviteReceived: List<Action> = listOf(
            MessageAction("&a✉ Клан &e{clan} &aприглашает вас вступить!"),
            SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f)
        ),

        @YamlComment("Инструкция для принятия приглашения. Переменная: {clan}")
        val inviteInstructions: List<Action> = listOf(
            MessageAction("&aИспользуйте &e/clan join {clan} &aдля принятия приглашения.")
        )
    )

    /**
     * Messages for member management actions (kick, promote, demote, leader transfer).
     *
     * Supported placeholders: `{player}`, `{role}`
     *
     * @property noPermissionKick Actions when lacking KICK permission.
     * @property cannotManageHigherRank Actions when targeting a player of equal or higher rank.
     * @property kicked Actions on successful kick.
     * @property leaderTransferred Actions on successful leadership transfer.
     * @property cannotPromote Actions when the target cannot be promoted.
     * @property promoted Actions on successful promotion.
     * @property demoted Actions on successful demotion.
     */
    @Serializable
    data class MembersMessages(
        @YamlComment("Нет прав на исключение участника")
        val noPermissionKick: List<Action> = listOf(
            MessageAction("&cУ вас нет прав на исключение участников."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Попытка управлять игроком равного или высшего ранга")
        val cannotManageHigherRank: List<Action> = listOf(
            MessageAction("&cВы не можете управлять игроком равного или высшего ранга."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Участник исключён из клана. Переменная: {player}")
        val kicked: List<Action> = listOf(
            MessageAction("&aВы исключили &e{player} &aиз клана.")
        ),

        @YamlComment("Передача лидерства. Переменная: {player}")
        val leaderTransferred: List<Action> = listOf(
            MessageAction("&aЛидерство передано игроку &e{player}&a."),
            SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 0.8f)
        ),

        @YamlComment("Невозможно повысить до целевого ранга")
        val cannotPromote: List<Action> = listOf(
            MessageAction("&cВы не можете повысить игрока до этого ранга."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Игрок повышен в ранге. Переменные: {player}, {role}")
        val promoted: List<Action> = listOf(
            MessageAction("&aИгрок &e{player} &aповышен до &b{role}&a."),
            SoundAction("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.5f)
        ),

        @YamlComment("Игрок понижен в ранге. Переменные: {player}, {role}")
        val demoted: List<Action> = listOf(
            MessageAction("&aИгрок &e{player} &aпонижен до &b{role}&a."),
            SoundAction("ENTITY_ITEM_BREAK", 0.8f, 1.0f)
        )
    )

    /**
     * Messages for clan treasury (bank) deposit and withdrawal operations.
     *
     * Supported placeholders: `{amount}`
     *
     * @property noPermissionDeposit Actions when lacking DEPOSIT permission.
     * @property noPermissionWithdraw Actions when lacking WITHDRAW permission.
     * @property deposited Actions on successful deposit.
     * @property withdrawn Actions on successful withdrawal.
     * @property insufficientPersonalFunds Actions when the player cannot afford the amount.
     * @property insufficientClanFunds Actions when the clan bank balance is too low.
     */
    @Serializable
    data class TreasuryMessages(
        @YamlComment("Нет прав на пополнение казны")
        val noPermissionDeposit: List<Action> = listOf(
            MessageAction("&cУ вас нет прав на пополнение казны."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Нет прав на снятие средств из казны")
        val noPermissionWithdraw: List<Action> = listOf(
            MessageAction("&cУ вас нет прав на снятие средств из казны."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Средства успешно внесены в казну. Переменная: {amount}")
        val deposited: List<Action> = listOf(
            MessageAction("&aВы внесли &e{amount} ⛁ &aв казну клана!"),
            SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f)
        ),

        @YamlComment("Средства успешно сняты с казны. Переменная: {amount}")
        val withdrawn: List<Action> = listOf(
            MessageAction("&aВы сняли &e{amount} ⛁ &aиз казны клана!"),
            SoundAction("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 0.8f)
        ),

        @YamlComment("Недостаточно личных средств для пополнения")
        val insufficientPersonalFunds: List<Action> = listOf(
            MessageAction("&cУ вас недостаточно личных средств."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Недостаточно средств в казне клана")
        val insufficientClanFunds: List<Action> = listOf(
            MessageAction("&cНедостаточно средств в казне клана."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        )
    )

    /**
     * Messages for the clan level upgrade system (Ritual of Elevation).
     *
     * Supported placeholders: `{level}`
     *
     * @property noPermission Actions when the player lacks UPGRADE_LEVEL permission.
     * @property insufficientFunds Actions when the clan bank balance is below the required amount.
     * @property insufficientMmr Actions when clan MMR is below the threshold.
     * @property levelUp Actions fired on successful level upgrade.
     */
    @Serializable
    data class UpgradeMessages(
        @YamlComment("Нет прав для проведения Ритуала Возвышения")
        val noPermission: List<Action> = listOf(
            MessageAction("&cУ вас нет полномочий для проведения ритуала."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Недостаточно монет в казне для улучшения")
        val insufficientFunds: List<Action> = listOf(
            MessageAction("&cНедостаточно монет в казне клана!"),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Недостаточно MMR для улучшения")
        val insufficientMmr: List<Action> = listOf(
            MessageAction("&cКлан недостаточно силён (мало MMR)!"),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Клан успешно повышен до нового уровня. Переменная: {level}")
        val levelUp: List<Action> = listOf(
            MessageAction("&a⚡ Клан возвысился до &e{level} &aуровня! Открыты новые возможности!"),
            SoundAction("ENTITY_PLAYER_LEVELUP", 1.0f, 0.7f)
        )
    )

    /**
     * Messages for clan settings panel toggle interactions.
     *
     * @property noPermission Actions when the player lacks permission to change a setting.
     * @property noPermissionRoles Actions when lacking permission to open the role editor.
     */
    @Serializable
    data class SettingsMessages(
        @YamlComment("Нет прав на изменение настройки")
        val noPermission: List<Action> = listOf(
            MessageAction("&cУ вас нет разрешения на изменение этой настройки."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Нет прав на редактирование ролей (только для лидера)")
        val noPermissionRoles: List<Action> = listOf(
            MessageAction("&cТолько лидер клана может редактировать роли."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        )
    )

    /**
     * Messages for the clan virtual chest (shared storage).
     *
     * Supported placeholders: `{level}`
     *
     * @property slotLocked Actions when clicking a locked chest slot.
     * @property noPermission Actions when lacking OPEN_CHEST permission.
     * @property chestDisabled Actions when the clan chest is disabled by the leader.
     */
    @Serializable
    data class ChestMessages(
        @YamlComment("Слот заблокирован — требуется уровень клана. Переменная: {level}")
        val slotLocked: List<Action> = listOf(
            MessageAction("&c[Сундук] Этот слот заблокирован! Прокачайте клан до &e{level} &cуровня."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.4f)
        ),

        @YamlComment("Нет прав на открытие кланового сундука")
        val noPermission: List<Action> = listOf(
            MessageAction("&cУ вас нет прав для доступа к клановому сундуку."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        ),

        @YamlComment("Клановый сундук временно закрыт лидером")
        val chestDisabled: List<Action> = listOf(
            MessageAction("&cКлановый сундук временно недоступен — закрыт лидером."),
            SoundAction("ENTITY_VILLAGER_NO", 1.0f, 1.2f)
        )
    )
}
