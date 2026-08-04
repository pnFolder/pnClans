package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * Virtual clan chest GUI providing shared item storage across all clan members.
 *
 * Slot availability scales with clan level (9 slots per level, up to 45 unlocked slots).
 * Locked slots display a styled red glass pane with unlock level requirements.
 * Items are persisted to the storage backend automatically on inventory close and on
 * manual navigation via the back/close buttons.
 *
 * The navigation bar (slots 45–53) contains analytics, a return button, the storage core,
 * a shortcut to [UpgradeUX], and a close button.
 *
 * @param clanService The clan service providing chest persistence and plugin access.
 * @param clan The owning clan whose chest contents are displayed and managed.
 */
class ClanChestUX(
    clanService: ClanService,
    val clan: Clan
) : BaseGui(clanService) {

    val unlockedSlotsCount: Int = when (clan.level) {
        1 -> 9
        2 -> 18
        3 -> 27
        4 -> 36
        else -> 45
    }

    init {
        title("&8Хранилище Клана ${clan.name} (Ур. ${clan.level})")
        rows(6)

        // Загружаем сохраненные предметы
        val savedItems = clanService.getChestItems(clan.id)
        for (slotIndex in 0 until unlockedSlotsCount) {
            val item = savedItems.getOrNull(slotIndex)
            if (item != null && item.type != Material.AIR) {
                inventory.setItem(slotIndex, item)
            }
        }

        // Заполняем заблокированные слоты (от unlockedSlotsCount до 44)
        for (slotIndex in unlockedSlotsCount until 45) {
            val requiredLevel = when {
                slotIndex < 18 -> 2
                slotIndex < 27 -> 3
                slotIndex < 36 -> 4
                else -> 5
            }

            slot(slotIndex) {
                item(Material.RED_STAINED_GLASS_PANE) {
                    name("&#FF3B3B🔒 СЛОТ ЗАБЛОКИРОВАН")
                    lore(
                        "",
                        "&#9EFC65 «Информация»",
                        " &7- &fСтатус: &#FC3737Закрыт для хранения",
                        " &7- &fТребуется уровень клана: &e$requiredLevel лвл.",
                        "",
                        "&#FC65DF «Как разблокировать?»",
                        " &7- &fКаждый уровень клана открывает",
                        " &7- &fдополнительно &e9 новых слотов&f!",
                        "",
                        "&#FF8702➥ &fНажмите &eЭволюция Клана &fдля прокачки!"
                    )
                }
                onClick { player, event ->
                    event.isCancelled = true
                    val cfg = this@ClanChestUX.clanService.plugin.configService
                    cfg.send(player, cfg.messages.chest.slotLocked, mapOf("level" to requiredLevel.toString()))
                }
            }
        }

        // Нижний ряд навигации (слоты 45 - 53)
        val controlDecor = listOf(46, 47, 51, 52)
        for (i in controlDecor) {
            slot(i) { item(Material.BLACK_STAINED_GLASS_PANE) { name(" ") } }
        }

        // Слот [45]: Аналитика склада
        slot(45) {
            dynamicItem(Material.KNOWLEDGE_BOOK) { _ ->
                val maxSlots = this@ClanChestUX.unlockedSlotsCount
                val itemsStored = (0 until maxSlots).count { slotIdx ->
                    val item = this@ClanChestUX.inventory.getItem(slotIdx)
                    item != null && item.type != Material.AIR
                }
                val percent = if (maxSlots > 0) (itemsStored * 100) / maxSlots else 0
                val progressBar = this@ClanChestUX.buildProgressBar(percent)
                val bankBal = this@ClanChestUX.clan.bankBalance

                name("&#5EFD7D📊 СТАТИСТИКА СКЛАДА")
                lore(
                    "",
                    "&#9EFC65 «Заполненность»",
                    " &7- &fЗанято слотов: &e$itemsStored &7/ &f$maxSlots",
                    " &7- &fЗагрузка: $progressBar &7(&e$percent%&7)",
                    "",
                    "&#FC65DF «Финансы»",
                    " &7- &fКазна клана: &#5EFD7D$bankBal ⛁"
                )
                null
            }
            onClick { _, event -> event.isCancelled = true }
        }

        // Слот [48]: Возврат в Главное Меню
        slot(48) {
            item(Material.OAK_DOOR) {
                name("&c🚪 Вернуться в главное меню")
                lore("&7Нажмите, чтобы открыть главное меню клана.")
            }
            onClick { player, _ ->
                this@ClanChestUX.saveChestContents()
                MainUX(this@ClanChestUX.clanService).open(player)
            }
        }

        // Слот [49]: Ядро хранилища
        slot(49) {
            dynamicItem(Material.BEACON) { _ ->
                glow(true)
                val lvl = this@ClanChestUX.clan.level
                val count = this@ClanChestUX.unlockedSlotsCount
                name("&#FC7D37⚡ ЯДРО ХРАНИЛИЩА")
                lore(
                    "",
                    "&#9EFC65 «Текущий статус»",
                    " &7- &fУровень клана: &e$lvl лвл.",
                    " &7- &fДоступно рядов: &b${count / 9} из 5",
                    " &7- &fСохранение данных: &aАКТИВНО"
                )
                null
            }
            onClick { _, event -> event.isCancelled = true }
        }

        // Слот [50]: Переход в Эволюцию Клана
        slot(50) {
            item(Material.NETHER_STAR) {
                glow(true)
                name("&#FC65DF✨ ЭВОЛЮЦИЯ КЛАНА")
                lore(
                    "",
                    "&#9EFC65 «Прокачка»",
                    " &7- &fНажмите, чтобы перейти в меню",
                    " &7- &fулучшений и открыть новые слоты!"
                )
            }
            onClick { player, _ ->
                this@ClanChestUX.saveChestContents()
                UpgradeUX(this@ClanChestUX.clanService).open(player)
            }
        }

        // Слот [53]: Закрыть
        slot(53) {
            item(Material.BARRIER) {
                name("&c✖ Закрыть меню")
            }
            onClick { player, _ ->
                this@ClanChestUX.saveChestContents()
                player.closeInventory()
            }
        }
    }

    override fun handleClick(e: InventoryClickEvent) {
        val slot = e.slot
        if (e.clickedInventory == inventory) {
            if (slot in 45..53 || slot >= unlockedSlotsCount) {
                e.isCancelled = true
            } else {
                e.isCancelled = false
            }
        } else {
            e.isCancelled = false
        }

        super.handleClick(e)
    }

    override fun handleClose(e: InventoryCloseEvent) {
        saveChestContents()
        super.handleClose(e)
    }

    fun saveChestContents() {
        val items = arrayOfNulls<ItemStack>(54)
        for (i in 0 until unlockedSlotsCount) {
            val item = inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                items[i] = item
            }
        }
        clanService.saveChestItems(clan.id, items)
    }

    private fun buildProgressBar(percent: Int): String {
        val filled = (percent / 10).coerceIn(0, 10)
        val empty = 10 - filled
        return "&a" + "■".repeat(filled) + "&7" + "□".repeat(empty)
    }
}
