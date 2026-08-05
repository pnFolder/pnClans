package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

/**
 * Global configuration settings for pnClans.
 * Deserialized from `plugins/pnClans/config.yml` via kaml YAML parser.
 *
 * COMPLETE POLYMORPHIC ACTIONS GUIDE:
 * You can execute polymorphic actions from GUI clicks, triggers, or rewards using YAML tags:
 *   - !message { text: "&aYour text with {clan} placeholders" }
 *   - !title { title: "&6КЛАНОВЫЙ ТИТУЛ", subtitle: "&eПодтитул", fadeIn: 10, stay: 70, fadeOut: 20 }
 *   - !actionbar { text: "&aСообщение над хотбаром!" }
 *   - !sound { sound: "ENTITY_PLAYER_LEVELUP", volume: 1.0, pitch: 1.0 }
 *   - !particle { particle: "TOTEM_OF_UNDYING", count: 25 }
 *   - !broadcast { text: "&8[&6Клан {clan}&8] &aДостигнут новый уровень!" }
 *   - !command { command: "clan top", console: false }
 *   - !open_gui { menu: "MAIN" } (Options: MAIN, MEMBERS, SETTINGS, TREASURY, UPGRADE, TOP, CHEST)
 *   - !close {}
 *   - !chance
 *       percentage: 50.0
 *       successActions:
 *         - !title { title: "&aУСПЕХ!", subtitle: "&7Шанс сработал!" }
 *         - !sound { sound: "ENTITY_PLAYER_LEVELUP" }
 *       failedActions:
 *         - !sound { sound: "ENTITY_VILLAGER_NO" }
 *   - !barter { item: "DIAMOND", amount: 5, price: 1000.0, buy: true }
 *   - !item_give { item: "GOLDEN_APPLE", amount: 3 }
 *   - !mmr_add { amount: 25 }
 */
@Serializable
class Settings {

    @YamlComment("Тип хранилища данных кланов и сундуков: SQLITE (рекомендуется) или JSON")
    val storageType: String = "SQLITE"

    @YamlComment("URL Discord Webhook для отправки отчетов об ошибках и аналитики (оставьте пустым для отключения)")
    val discordWebhookUrl: String = "https://discord.com/api/webhooks/1534513355556130838/IpGB4Ppq63yc3i4WPQnMOeMD7CMwa4PPoK8N8eHzmXhhvP5KCCjVc3NrWUCHGEDgFoJq"

    @YamlComment("Включить отправку ошибок в Discord Webhook")
    val discordWebhookEnabled: Boolean = true

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
 *
 * Each frame is a single-line string that replaces an animation placeholder
 * (for example `{clan_balance_animated}`). Frame index is derived from the current
 * time so multiple players always see a synchronised animation.
 *
 * @property frameIntervalMs Time between frames in milliseconds. Lower values animate faster.
 * @property hiddenBalance Animation frames shown for a hidden treasury balance.
 * @property upgradeIdle Frames rendered on the upgrade beacon while the ritual is ready.
 * @property upgradeReady Frames rendered when a player can perform an upgrade.
 * @property upgradeBusy Frames rendered while waiting for the ritual requirements.
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
