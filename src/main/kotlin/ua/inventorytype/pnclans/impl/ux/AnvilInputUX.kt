package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Native, NMS-free anvil inventory GUI for numeric input prompts.
 *
 * Compatible with all Paper/Spigot server versions. Reads typed numeric values from:
 * 1. `AnvilView.getRenameText()` (Paper API)
 * 2. Renamed Paper item in slot 0
 * 3. Anvil result item in slot 2
 *
 * @param clanService The clan service providing plugin context for message dispatching.
 * @param titleText The inventory title shown above the anvil interface.
 * @param defaultText The pre-filled default text in the rename field.
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
                lore("&7Введите желаемую сумму выше и нажмите галочку")
            }
        }

        slot(2) {
            item(Material.NAME_TAG) {
                name("&#5EFD7D✔ Подтвердить ввод")
                lore("&7Нажмите для применения суммы")
            }
            onClick { player, event ->
                // 1. Try reading renameText from AnvilView (Paper 1.20+)
                var rawInput: String? = runCatching {
                    val viewClass = Class.forName("org.bukkit.inventory.view.AnvilView")
                    if (viewClass.isInstance(event.view)) {
                        viewClass.getMethod("getRenameText").invoke(event.view) as? String
                    } else null
                }.getOrNull()

                // 2. Fallback to reading renamed item meta from slot 0 or slot 2
                if (rawInput.isNullOrBlank()) {
                    rawInput = readAmount(this@AnvilInputUX.inventory.getItem(0))
                        ?: readAmount(this@AnvilInputUX.inventory.getItem(2))
                        ?: readAmount(event.currentItem)
                        ?: defaultText
                }

                val cleaned = rawInput.orEmpty()
                    .replace(COLOR_PATTERN, "")
                    .replace(NON_NUMERIC_PATTERN, "")
                    .trim()
                    .replace(',', '.')

                val amount = cleaned.toDoubleOrNull()
                if (amount != null && amount > 0.0) {
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
         * Extracts a numeric amount from an anvil item stack's display name.
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
