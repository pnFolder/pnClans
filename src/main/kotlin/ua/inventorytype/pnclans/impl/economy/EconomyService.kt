package ua.inventorytype.pnclans.impl.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class EconomyService {

    var economy: Economy? = null
        private set

    val isEnabled: Boolean
        get() = economy != null

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
    fun has(player: Player, amount: Double): Boolean {
        return economy?.has(player, amount) ?: error("Не удалось проверить баланс игрока ${player.name}: Vault не подключен!") // Если экономики нет, пропускаем бесплатно
    }

    /**
     * Снятие денег с баланса игрока
     */
    fun withdraw(player: Player, amount: Double): Boolean {
        val eco = economy ?: error("Не удалось снять баланс игрока ${player.name}: Vault не подключен!")
        if (amount <= 0.0) return true

        val response = eco.withdrawPlayer(player, amount)
        return response.transactionSuccess()
    }

    /**
     * Пополнить денег на баланса игрока
     */
    fun depositPlayer(player: Player, amount: Double): Boolean {
        val eco = economy ?: error("Не удалось пополнить баланс игрока ${player.name}: Vault не подключен!")
        if (amount <= 0.0) return true

        val response = eco.depositPlayer(player, amount)
        return response.transactionSuccess()
    }
}