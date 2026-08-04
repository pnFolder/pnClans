package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.api.permission.Permission
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

class SettingsUX(
    val _clanService: ClanService,
    val editorRolesUX: EditorRolesUX = EditorRolesUX(_clanService)
) : BaseGui(_clanService) {

    init {
        title("Клан > Настройки")
        rows(3)
        border(Material.GRAY_STAINED_GLASS_PANE)

        // ----------------------------------------------------
        // Slot 10: Режим ПвП
        // ----------------------------------------------------
        slot(10) {
            dynamicItem(Material.DIAMOND_SWORD) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player)!!
                val isS = clan.isSettingEnabled(ClanSetting.PVP)

                name("&#FC7D37Режим ПвП")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fСостояние: ${if (isS) "&aВключён" else "&cВыключен"}",
                    " &7- &fУрон по соклановцам: ${if (isS) "&aРазрешён" else "&cЗаблокирован"}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fОпределяет, могут ли соклановцы",
                    " &7- &fнаносить урон друг другу в бою.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (isS) "&cВыключить" else "&aВключить"}"
                )
            }

            onClick { player, event ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Settings.TOGGLE_PVP) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет разрешения на это.")
                    return@onClick
                }

                clan.toggleSetting(ClanSetting.PVP)
                this@SettingsUX.updateSlot(event.slot, player)
            }
        }

        // ----------------------------------------------------
        // Slot 11: Клановый Чат
        // ----------------------------------------------------
        slot(11) {
            dynamicItem(Material.WRITABLE_BOOK) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player)!!
                val isS = clan.isSettingEnabled(ClanSetting.CHAT)

                name("&#FC7D37Клановый Чат")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fСостояние: ${if (isS) "&aДоступен" else "&cЗаблокирован"}",
                    " &7- &fКанал общения: &eВнутриигровой",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fРазрешает участникам отправлять",
                    " &7- &fсообщения в закрытый чат клана.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (isS) "&cВыключить" else "&aВключить"}"
                )
            }

            onClick { player, event ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Settings.TOGGLE_CHAT) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет разрешения на это.")
                    return@onClick
                }

                clan.toggleSetting(ClanSetting.CHAT)
                this@SettingsUX.updateSlot(event.slot, player)
            }
        }

        // ----------------------------------------------------
        // Slot 12: Клановый Сундук
        // ----------------------------------------------------
        slot(12) {
            dynamicItem(Material.CHEST) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player)!!
                val isS = clan.isSettingEnabled(ClanSetting.CHEST)

                name("&#FC7D37Клановый Сундук")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fСостояние: ${if (isS) "&aОткрыт" else "&cЗакрыт"}",
                    " &7- &fОбщее хранилище: &eДоступно",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fКонтролирует общий доступ к складу",
                    " &7- &fи ресурсам виртуального сундука.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (isS) "&cВыключить" else "&aВключить"}"
                )
            }

            onClick { player, event ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Action.OPEN_CHEST) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет разрешения на это.")
                    return@onClick
                }

                clan.toggleSetting(ClanSetting.CHEST)
                this@SettingsUX.updateSlot(event.slot, player)
            }
        }

        // ----------------------------------------------------
        // Slot 13: Оповещения о Входе
        // ----------------------------------------------------
        slot(13) {
            dynamicItem(Material.BELL) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player)!!
                val isS = clan.isSettingEnabled(ClanSetting.JOIN)

                val totalMembers = clan.users.size
                val onlineMembers = clan.onlineCount

                name("&#FC7D37Оповещения о Входе")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fСостояние: ${if (isS) "&aВключены" else "&cВыключены"}",
                    " &7- &fСоклановцев в сети: &e$onlineMembers&7/&f$totalMembers",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fПоказывает системные сообщения в чате",
                    " &7- &fпри входе или выходе участников.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ${if (isS) "&cВыключить" else "&aВключить"}"
                )
            }

            onClick { player, event ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.hasPermission(user, ClanPerms.Settings.TOGGLE_JOIN) != Permission.Flag.TRUE) {
                    player.sendMessage("&cУ вас нет разрешения на это.")
                    return@onClick
                }

                clan.toggleSetting(ClanSetting.JOIN)
                this@SettingsUX.updateSlot(event.slot, player)
            }
        }

        // ----------------------------------------------------
        // Slot 14: Управление Ролями
        // ----------------------------------------------------
        slot(14) {
            dynamicItem(Material.ARMOR_STAND) { player ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@dynamicItem
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@dynamicItem

                val totalMembers = clan.users.size
                val rolesCount = ClanRole.entries.size
                val myRole = clan.getUserRole(user)

                name("&#FC7D37Управление ролями")
                lore(
                    "",
                    "&#9EFC65 «Информация»",
                    " &7- &fДоступных ролей: &e$rolesCount рангов",
                    " &7- &fУчастников в клане: &e$totalMembers чел.",
                    " &7- &fТвой текущий ранг: &#5EFD7D${this@SettingsUX.getDisplayNameRole(myRole)}",
                    "",
                    "&#FC65DF «Описание»",
                    " &7- &fРедактирование прав для каждого ранга.",
                    " &7- &fНастройка доступа к казне, правам",
                    " &7- &fприглашений, кикам и управлению.",
                    "",
                    "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть список ролей"
                )
            }

            onClick { player, _ ->
                val clan = this@SettingsUX.clanService.getClanUser(player) ?: return@onClick
                val user = clan.users.find { it.uuid == player.uniqueId } ?: return@onClick

                if (clan.getUserRole(user) != ClanRole.LEADER) {
                    player.sendMessage("&cУ вас нет разрешения на редактирование ролей.")
                    return@onClick
                }

                this@SettingsUX.editorRolesUX.open(player)
            }
        }
    }

    private fun getDisplayNameRole(role: ClanRole): String { //TODO
        return when (role) {
            ClanRole.MEMBER -> "Участник"
            ClanRole.ELDER -> "Старейшина"
            ClanRole.DEPUTY -> "Заместитель"
            ClanRole.LEADER -> "Лидер"
        }
    }
}