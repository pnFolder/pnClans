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
 * @param onCancel Callback invoked when the player closes the anvil without confirming.
 */
class AnvilInputUX(
    clanService: ClanService,
    titleText: String,
    defaultText: String = "100",
    val onSubmit: (Player, Double) -> Unit,
    val onCancel: (Player) -> Unit = { player ->
        val cfg = clanService.plugin.configService
        cfg.send(player, cfg.messages.general.operationCancelled)
        TreasuryUX(clanService).open(player)
    }
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
                val rawInput = readAmount(event.currentItem)
                    ?: readAmount(this@AnvilInputUX.inventory.getItem(0))
                    ?: readAmount(this@AnvilInputUX.inventory.getItem(2))
                    ?: defaultText
                val amount = rawInput.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    this@AnvilInputUX.onSubmit(player, amount)
                } else {
                    val cfg = this@AnvilInputUX.clanService.plugin.configService
                    cfg.send(player, cfg.messages.general.invalidInput)
                    this@AnvilInputUX.onCancel(player)
                }
            }
        }
    }

    override fun handleClose(event: org.bukkit.event.inventory.InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.reason == org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLAYER) {
            onCancel(player)
        }
        super.handleClose(event)
    }

    private companion object {
        private val COLOR_PATTERN = Regex("§[0-9a-fk-orA-FK-OR]")
        private val NON_NUMERIC_PATTERN = Regex("[^0-9.,\\-]")

        /**
         * Extracts a numeric amount from a renamed anvil item.
         *
         * Strips both Minecraft color codes and any non-numeric decoration, then normalizes
         * locale-specific decimal separators to dots so values like `1 000` or `1,5` still parse.
         */
        fun readAmount(stack: org.bukkit.inventory.ItemStack?): String? {
            if (stack == null || !stack.hasItemMeta()) return null
            val raw = stack.itemMeta.displayName
                ?.replace(COLOR_PATTERN, "")
                ?.replace(NON_NUMERIC_PATTERN, "")
                ?.trim()
                ?: return null
            if (raw.isEmpty()) return null
            return raw.replace(',', '.')
        }
    }
}
