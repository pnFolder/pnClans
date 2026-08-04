package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Native, NMS-free anvil inventory GUI for numeric input prompts.
 *
 * Opens a standard Bukkit [InventoryType.ANVIL] inventory without any reflection or NMS.
 * The player renames the paper item in slot 0 (the left input slot) and clicks
 * the output item in slot 2 to confirm the entered value.
 *
 * Compatible with all Paper/Spigot server versions without external library dependencies.
 *
 * @param clanService The clan service providing plugin context for message dispatching.
 * @param titleText The inventory title shown above the anvil interface.
 * @param defaultText The pre-filled default text in the rename field (visible on the paper item).
 * @param onSubmit Callback invoked with the confirming player and the parsed numeric amount.
 */
class AnvilInputUX(
    clanService: ClanService,
    titleText: String,
    defaultText: String = "100",
    val onSubmit: (Player, Double) -> Unit
) : BaseGui(clanService) {

    init {
        type(InventoryType.ANVIL)
        title(titleText)

        slot(0) {
            item(Material.PAPER) {
                name(defaultText)
                lore("&7Нажмите на галочку справа для ввода")
            }
        }

        slot(2) {
            item(Material.NAME_TAG) {
                name("&#5EFD7D✔ Подтвердить")
                lore("&7Нажмите для завершения операции")
            }
            onClick { player, event ->
                val item = event.currentItem ?: this@AnvilInputUX.inventory.getItem(0)
                val rawName = item?.itemMeta?.displayName?.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "")?.trim() ?: defaultText
                val amount = rawName.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    this@AnvilInputUX.onSubmit(player, amount)
                } else {
                    val cfg = this@AnvilInputUX.clanService.plugin.configService
                    cfg.send(player, cfg.messages.general.invalidInput)
                    TreasuryUX(this@AnvilInputUX.clanService).open(player)
                }
            }
        }
    }
}
