package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

/**
 * Modular framework settings enabling or disabling specific plugin sub-features.
 */
@Serializable
data class ModuleConfig(
    @YamlComment("Включить модуль клановых точек дома (/clan home, меню точек в GUI)")
    val homes: Boolean = true,

    @YamlComment("Включить модуль казны и банка клана (пополнение, снятие, история операций)")
    val treasury: Boolean = true,

    @YamlComment("Включить модуль общего кланового склада (/clan chest, виртуальный сундук)")
    val chest: Boolean = true,

    @YamlComment("Включить модуль эволюции и прокачки уровней клана")
    val upgrades: Boolean = true,

    @YamlComment("Включить модуль управления PvP режимом соклановцев")
    val pvp: Boolean = true
)

/**
 * Global configuration settings for pnClans.
 * Deserialized from `plugins/pnClans/config.yml` via kaml YAML parser.
 */
@Serializable
class Settings {

    @YamlComment("Модули фреймворка. Отключите ненужный функционал, чтобы скрыть кнопки в GUI и отключить подкоманды.")
    val modules: ModuleConfig = ModuleConfig()

    @YamlComment("Тип хранилища данных кланов и сундуков: SQLITE (рекомендуется) или JSON")
    val storageType: String = "SQLITE"

    @YamlComment("Проверять ли наличие новых версий на GitHub при запуске сервера")
    val checkUpdates: Boolean = true

    @YamlComment("Автоматически скачивать последнюю версию плагина в папку plugins/update/ (применяется при перезапуске по умолчанию)")
    val autoUpdate: Boolean = true

    @YamlComment("Стоимость создания клана в монетах экономики Vault (0 — бесплатно)")
    val createClanCost: Double = 1000.0

    @YamlComment("Время ввода ника при приглашении через меню в секундах")
    val invitePromptTimeoutSeconds: Int = 15

    @YamlComment("Время жизни отправленного приглашения в секундах")
    val inviteLifetimeSeconds: Int = 60

    @YamlComment("Быстрые суммы пополнения казны, выводятся в виде отдельных кнопок.")
    val treasuryDepositPresets: List<Int> = listOf(500, 1000)

    @YamlComment("Быстрые суммы снятия казны, выводятся в виде отдельных кнопок.")
    val treasuryWithdrawPresets: List<Int> = listOf(500, 1000)

    @YamlComment("Требовать ли пустое хранилище предметов (Clan Chest) перед распуском клана")
    val disbandRequireEmptyChest: Boolean = true

    @YamlComment("Требовать ли лидеру вручную вывести все деньги из казны перед распуском клана (true — блокирует распуск пока есть деньги)")
    val disbandRequireEmptyBank: Boolean = true

    @YamlComment("Автоматически возвращать остаток казны на баланс лидера при распуске (актуально если disbandRequireEmptyBank = false)")
    val disbandAutoRefundBank: Boolean = false

    @YamlComment("Названия ролей клана для отображения в чате, меню и плейсхолдерах {clan_role}")
    val roleLeader: String = "Лидер"
    val roleDeputy: String = "Заместитель"
    val roleElder: String = "Старейшина"
    val roleMember: String = "Участник"

    @YamlComment("Системные сообщения плагина. Поддерживают форматирование цвета (&, HEX) и плейсхолдеры {clan}, {player}, {target}, {role}, {amount}, {cost}")
    val msgClanCreated: String = "&aВы успешно создали клан &e{clan}&a!"
    val msgClanDisbanded: String = "&cКлан {clan} был распущен лидером."
    val msgAlreadyInClan: String = "&cВы уже состоите в клане."
    val msgNoClan: String = "&cВы не состоите в клане."
    val msgInviteSent: String = "&aВы отправили приглашение игроку &e{target}&a."
    val msgInviteReceived: String = "&aИгрок &e{sender} &aприглашает вас в клан &e{clan}&a! Используйте &e/clan accept &aили &c/clan deny&a."
    val msgInviteAccepted: String = "&aВы успешно вступили в клан &e{clan}&a!"
    val msgInviteDenied: String = "&cВы отклонили приглашение в клан {clan}."
    val msgNoMoney: String = "&cУ вас недостаточно денег (требуется {cost}$)."
    val msgDepositSuccess: String = "&aВы внесли &e{amount} ⛁ &aв казну клана {clan}."
    val msgWithdrawSuccess: String = "&aВы сняли &e{amount} ⛁ &aиз казны клана {clan}."
    val msgNoPermission: String = "&cУ вас недостаточно прав для выполнения этого действия."
    val msgPvpDisabled: String = "&c[pnClans] Урон по соклановцам отключён!"
    val msgChatFormat: String = "&8[&6Клан &e{clan}&8] &7[{role}] &7{player}&8: &f{message}"
    val msgJoinNotice: String = "&8[&6Клан&8] &aУчастник &e{player} &aвошёл на сервер!"
    val msgQuitNotice: String = "&8[&6Клан&8] &cУчастник &e{player} &cвышел с сервера!"

    @YamlComment("Анимационные кадры, используемые в анимированных плейсхолдерах (например, скрытый баланс казны).")
    val animations: AnimationConfig = AnimationConfig()
}

/**
 * Configurable collection of animation frames used across the plugin.
 */
@Serializable
data class AnimationConfig(
    @YamlComment("Интервал между кадрами анимации в миллисекундах")
    val frameIntervalMs: Int = 600,

    @YamlComment("Кадры для скрытого баланса казны. Поддерживают HEX/& цвета и плейсхолдеры.")
    val hiddenBalance: List<String> = listOf(
        "&#FC3737∗ &8∗ &5∗ &8∗ &6∗",
        "&#FC3737∗ &8∗ &5∗ &6∗ &8∗",
        "&#FC3737∗ &8∗ &6∗ &5∗ &8∗",
        "&#FC3737∗ &6∗ &5∗ &8∗ &8∗"
    ),

    @YamlComment("Кадры для светящегося маяка эволюции (ожидание ритуала).")
    val upgradeIdle: List<String> = listOf(
        "&#FC7D37✦ Подготовка",
        "&#FC7D37✦ Подготовка.",
        "&#FC7D37✦ Подготовка..",
        "&#FC7D37✦ Подготовка..."
    ),

    @YamlComment("Кадры для маяка эволюции, когда все условия прокачки выполнены.")
    val upgradeReady: List<String> = listOf(
        "&#FFD700✦ Ритуал готов",
        "&#FFD700✦ Ритуал готов.",
        "&#FFD700✦ Ритуал готов..",
        "&#FFD700✦ Ритуал готов..."
    ),

    @YamlComment("Кадры для маяка эволюции, когда не хватает ресурсов на прокачку.")
    val upgradeBusy: List<String> = listOf(
        "&#FC3737✦ Нужны ресурсы",
        "&#FC3737✦ Нужны ресурсы.",
        "&#FC3737✦ Нужны ресурсы..",
        "&#FC3737✦ Нужны ресурсы..."
    )
)
