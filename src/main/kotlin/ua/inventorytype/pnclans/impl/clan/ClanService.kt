package ua.inventorytype.pnclans.impl.clan

import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.impl.config.ConfigService
import ua.inventorytype.pnclans.impl.economy.EconomyService
import java.util.UUID

class ClanService(
    val plugin: BukkitPlugin,
    val configService: ConfigService = plugin.configService,
    val economy: EconomyService = plugin.economyService,
) {

    private val _clans = mutableSetOf<Clan>()

    // Список функций-слушателей изменений в клане
    private val clanUpdateListeners = mutableListOf<(playerUuid: UUID) -> Unit>()

    /**
     * Подписаться на изменения кланового статуса игрока
     */
    fun subscribe(onUpdate: (playerUuid: UUID) -> Unit) {
        clanUpdateListeners.add(onUpdate)
    }

    /**
     * Метод, который ТЫ вызываешь в ClanService, когда игрока кикают,
     * он сам выходит или клан удаляется.
     */
    fun notifyClanUpdated(playerUuid: UUID) {
        clanUpdateListeners.forEach { listener -> listener.invoke(playerUuid) }
    }

    /**
     * Если удаляется весь клан — уведомляем всех его участников
     */
    fun notifyClanDisbanded(memberUuids: List<UUID>) {
        memberUuids.forEach { notifyClanUpdated(it) }
    }

    val requiredClanCreate: (Player) -> Boolean = requiredClanCreate@ { player ->
        // Проверка 1: Состоит ли уже в клане
        val inClan = _clans.any { clan ->
            clan.users.any { user -> user.uuid == player.uniqueId }
        }

        if (inClan) {
            player.sendMessage("§cОшибка! Вы уже состоите в клане.")
            return@requiredClanCreate false
        }

        // Проверка 3: Проверка экономики через runCatching
        val hasMoney = runCatching {
            economy.has(player, configService.settings.createClanCost)
        }
            .onSuccess { hasEnough ->
                if (!hasEnough) {
                    player.sendMessage("§cОшибка! У вас недостаточно денег (нужно ${configService.settings.createClanCost}$).")
                }
            }
            .onFailure { exception ->
                player.sendMessage("§cПроизошла ошибка. Обратитесь к администратору.")
                plugin.logger.severe(exception.message)
            }
            .getOrDefault(false)

        if (!hasMoney) {
            return@requiredClanCreate false
        }

        true
    }

    fun createClan(clan: Clan) {}

    fun getClanUser(player: Player): Clan? = _clans.find { it.users.any { user -> user.uuid == player.uniqueId } }

}