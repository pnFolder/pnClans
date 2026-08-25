package ua.inventorytype.pnclans.impl.shop

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Level

/** Adapter layer for every currency available to the clan shop. */
internal class ClanShopCurrencyService(private val plugin: BukkitPlugin) {
    fun isAvailable(currency: ClanShopCurrency): Boolean = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> true
        ClanShopCurrency.VAULT -> plugin.economyService.isEnabled
        ClanShopCurrency.PLAYER_POINTS -> playerPointsApi() != null
    }

    fun balance(currency: ClanShopCurrency, player: Player, clan: Clan): Double? = when (currency) {
        ClanShopCurrency.CLAN_POINTS -> clan.points.toDouble()
        ClanShopCurrency.VAULT -> plugin.economyService.balance(player)
        ClanShopCurrency.PLAYER_POINTS -> playerPointsBalance(player.uniqueId)?.toDouble()
    }

    fun withdraw(currency: ClanShopCurrency, player: Player, clan: Clan, amount: Long): Boolean {
        if (amount <= 0L) return false
        return when (currency) {
            ClanShopCurrency.CLAN_POINTS -> plugin.clanPointsService.spend(clan, amount, ClanPointsSource.SHOP)
            ClanShopCurrency.VAULT -> plugin.economyService.isEnabled && plugin.economyService.withdraw(player, amount.toDouble())
            ClanShopCurrency.PLAYER_POINTS -> if (amount <= Int.MAX_VALUE) {
                invokePlayerPoints("take", player.uniqueId, amount.toInt()) as? Boolean ?: false
            } else {
                false
            }
        }
    }

    /** Refunds a previously charged amount and reports whether the refund really succeeded. */
    fun refund(currency: ClanShopCurrency, player: Player, clan: Clan, amount: Long): Boolean {
        if (amount <= 0L) return false
        return when (currency) {
            ClanShopCurrency.CLAN_POINTS -> plugin.clanPointsService.award(clan, amount, ClanPointsSource.SHOP)
            ClanShopCurrency.VAULT -> plugin.economyService.isEnabled && plugin.economyService.depositPlayer(player, amount.toDouble())
            ClanShopCurrency.PLAYER_POINTS -> if (amount <= Int.MAX_VALUE) {
                invokePlayerPoints("give", player.uniqueId, amount.toInt()) as? Boolean ?: false
            } else {
                false
            }
        }
    }

    private fun playerPointsBalance(playerId: UUID): Int? = (invokePlayerPoints("look", playerId) as? Number)?.toInt()

    private fun invokePlayerPoints(methodName: String, vararg arguments: Any): Any? {
        val api = playerPointsApi() ?: return null
        val method = api.javaClass.methods
            .asSequence()
            .filter { it.name == methodName && it.parameterCount == arguments.size }
            .filter { method -> methodAccepts(method, arguments) }
            .sortedByDescending { method -> exactMatchCount(method, arguments) }
            .firstOrNull()
            ?: run {
                plugin.logger.warning(
                    "[pnClans] PlayerPoints API method '$methodName' with compatible argument types was not found."
                )
                return null
            }

        return runCatching { method.invoke(api, *arguments) }
            .onFailure { error ->
                plugin.logger.log(
                    Level.WARNING,
                    "[pnClans] PlayerPoints API call '$methodName' failed using ${method.toGenericString()}.",
                    error
                )
            }
            .getOrNull()
    }

    private fun methodAccepts(method: Method, arguments: Array<out Any>): Boolean =
        method.parameterTypes.zip(arguments).all { (parameterType, argument) ->
            boxed(parameterType).isAssignableFrom(argument.javaClass)
        }

    private fun exactMatchCount(method: Method, arguments: Array<out Any>): Int =
        method.parameterTypes.zip(arguments).count { (parameterType, argument) ->
            boxed(parameterType) == argument.javaClass
        }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> type
    }

    private fun playerPointsApi(): Any? {
        val playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints") ?: return null
        if (!playerPoints.isEnabled) return null
        return runCatching {
            playerPoints.javaClass.methods.firstOrNull { it.name == "getAPI" && it.parameterCount == 0 }
                ?.invoke(playerPoints)
        }.onFailure { error ->
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to resolve the PlayerPoints API.", error)
        }.getOrNull()
    }
}
