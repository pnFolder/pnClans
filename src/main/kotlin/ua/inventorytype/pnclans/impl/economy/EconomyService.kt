package ua.inventorytype.pnclans.impl.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class EconomyService {

    var economy: Economy? = null
        private set

    val isEnabled: Boolean
        get() = economy != null

    /** Returns the player's Vault balance, or null when no economy provider is connected. */
    fun balance(player: Player): Double? = economy?.getBalance(player)

    /**
     * Инициализация интеграции с Vault.
     * Вызывается в onEnable главного класса.
     */
    fun setup(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false // Vault не установлен на сервере
        }

        val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return economy != null
    }

    /**
     * Проверка: хватает ли у игрока денег
     */
    fun has(player: Player, amount: Double): Boolean =
        amount.isFinite() && amount >= 0.0 && (economy?.has(player, amount) ?: false)

    /**
     * Снятие денег с баланса игрока
     */
    fun withdraw(player: Player, amount: Double): Boolean {
        val eco = economy ?: return false
        if (!amount.isFinite() || amount <= 0.0) return false

        val response = eco.withdrawPlayer(player, amount)
        return response.transactionSuccess()
    }

    /**
     * Пополнить денег на баланса игрока
     */
    fun depositPlayer(player: Player, amount: Double): Boolean {
        val eco = economy ?: return false
        if (!amount.isFinite() || amount <= 0.0) return false

        val response = eco.depositPlayer(player, amount)
        return response.transactionSuccess()
    }
}
