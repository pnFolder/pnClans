package ua.inventorytype.pnclans.impl.shop

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import java.util.UUID

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
        val method = api.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterCount == arguments.size
        } ?: return null
        return runCatching { method.invoke(api, *arguments) }.getOrNull()
    }

    private fun playerPointsApi(): Any? {
        val playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints") ?: return null
        if (!playerPoints.isEnabled) return null
        return runCatching {
            playerPoints.javaClass.methods.firstOrNull { it.name == "getAPI" && it.parameterCount == 0 }
                ?.invoke(playerPoints)
        }.getOrNull()
    }
}
