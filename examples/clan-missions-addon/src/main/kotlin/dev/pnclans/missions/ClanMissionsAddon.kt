package dev.pnclans.missions

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import ua.inventorytype.pnclans.api.PnClansApi
import ua.inventorytype.pnclans.api.PnClansProvider
import ua.inventorytype.pnclans.api.addon.AddonContext
import ua.inventorytype.pnclans.api.addon.PnClansAddon
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.api.command.ClanCommandContext
import ua.inventorytype.pnclans.api.command.ClanSubcommand
import ua.inventorytype.pnclans.api.event.ClanCreatedEvent
import ua.inventorytype.pnclans.api.event.ClanDisbandedEvent
import ua.inventorytype.pnclans.api.event.ClanSavedEvent
import ua.inventorytype.pnclans.api.event.ClanTreasuryTransactionEvent
import ua.inventorytype.pnclans.api.menu.ClanMainMenuButton
import ua.inventorytype.pnclans.api.menu.ClanMainMenuContext
import java.util.concurrent.ConcurrentHashMap

class ClanMissionsAddon : JavaPlugin(), PnClansAddon, Listener {
    override val id = "clan-missions"
    override val addonVersion = "1.0.0"
    override val author = "ExampleDeveloper"
    override val summary = "A treasury-funded clan mission example"
    override val website = "https://example.org/clan-missions"

    private lateinit var api: PnClansApi
    private val donated = ConcurrentHashMap<String, Double>()
    private val claimed = ConcurrentHashMap.newKeySet<String>()

    override fun onEnable() {
        api = PnClansProvider.require()
        check(api.addons.register(this, this)) { "ClanMissions could not register with pnClans" }
    }

    override fun onDisable() {
        if (::api.isInitialized) uninstall()
    }

    override fun onEnable(context: AddonContext) {
        api = context.api
        server.pluginManager.registerEvents(this, this)
        check(api.subcommands.register(this, missionsCommand)) { "The /clan missions command is already registered" }
        check(api.menus.registerMainButton(this, missionsButton)) { "The missions menu slot is already occupied" }
    }

    private fun uninstall() {
        api.subcommands.unregister(this, missionsCommand.name)
        api.menus.unregisterMainButton(this, missionsButton.id)
        org.bukkit.event.HandlerList.unregisterAll(this as Listener)
    }

    private val missionsCommand = object : ClanSubcommand {
        override val name = "missions"
        override val aliases = setOf("mission")
        override val usage = "/clan missions [claim]"

        override fun execute(context: ClanCommandContext): Boolean {
            val player = context.player ?: return true
            val clan = context.clan ?: run {
                player.sendMessage("§cВы не состоите в клане.")
                return true
            }

            if (context.args.firstOrNull()?.equals("claim", true) == true) {
                claim(player, clan.id)
            } else {
                openMissions(player, clan.id)
            }
            return true
        }

        override fun tabComplete(context: ClanCommandContext): List<String> =
            listOf("claim").filter { it.startsWith(context.args.lastOrNull().orEmpty(), true) }
    }

    private val missionsButton = object : ClanMainMenuButton {
        override val id = "clan-missions-button"
        override val slot = 22

        override fun createItem(context: ClanMainMenuContext): ItemStack {
            val current = donated[context.clan.id] ?: 0.0
            return item(Material.WRITABLE_BOOK, "§6Миссии клана", listOf(
                "",
                "§a «Общая цель»",
                " §7- §fВнести в казну: §e${current.toInt()}§7/§e5000 ⛁",
                "",
                "§6➥ §fНажмите, чтобы открыть миссии"
            ))
        }

        override fun onClick(context: ClanMainMenuContext) {
            openMissions(context.player, context.clan.id)
        }
    }

    @EventHandler
    fun onTreasuryTransaction(event: ClanTreasuryTransactionEvent) {
        if (event.transaction.type != TreasuryTransactionType.DEPOSIT) return

        val progress = donated.merge(event.clan.id, event.transaction.amount, Double::plus) ?: 0.0
        if (progress >= GOAL && !claimed.contains(event.clan.id)) {
            Bukkit.getPlayer(event.transaction.playerName)?.sendMessage("§aЦель клана выполнена. Награду можно забрать через /clan missions claim.")
        }
    }

    @EventHandler
    fun onClanCreated(event: ClanCreatedEvent) {
        donated.putIfAbsent(event.clan.id, 0.0)
    }

    @EventHandler
    fun onClanSaved(event: ClanSavedEvent) {
        donated.putIfAbsent(event.clan.id, 0.0)
    }

    @EventHandler
    fun onClanDisbanded(event: ClanDisbandedEvent) {
        donated.remove(event.clan.id)
        claimed.remove(event.clan.id)
    }

    @EventHandler
    fun onMissionInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? MissionsHolder ?: return
        event.isCancelled = true
        if (event.rawSlot != CLAIM_SLOT) return
        val player = event.whoClicked as? Player ?: return
        claim(player, holder.clanId)
        player.closeInventory()
    }

    private fun openMissions(player: Player, clanId: String) {
        val holder = MissionsHolder(clanId)
        val inventory = Bukkit.createInventory(holder, 27, "§8Миссии клана")
        holder.backingInventory = inventory

        val current = donated[clanId] ?: 0.0
        inventory.setItem(13, item(Material.EMERALD, "§aКазначейская цель", listOf(
            "",
            "§a «Прогресс»",
            " §7- §fВнесено: §e${current.toInt()}§7/§e${GOAL.toInt()} ⛁",
            "",
            if (current >= GOAL) "§a✔ Цель выполнена" else "§c✘ Цель ещё не выполнена"
        )))
        inventory.setItem(CLAIM_SLOT, item(
            if (current >= GOAL && !claimed.contains(clanId)) Material.CHEST else Material.GRAY_DYE,
            if (current >= GOAL && !claimed.contains(clanId)) "§6Забрать награду" else "§7Награда недоступна",
            listOf("", "§6➥ §fНажмите, чтобы получить награду")
        ))
        player.openInventory(inventory)
    }

    private fun claim(player: Player, clanId: String) {
        if ((donated[clanId] ?: 0.0) < GOAL) {
            player.sendMessage("§cКлан ещё не выполнил цель.")
            return
        }
        if (!claimed.add(clanId)) {
            player.sendMessage("§cНаграда уже была получена.")
            return
        }
        player.inventory.addItem(ItemStack(Material.DIAMOND, 3))
        player.sendMessage("§aКлан получил 3 алмаза за выполнение миссии.")
    }

    private fun item(material: Material, name: String, lore: List<String>): ItemStack =
        ItemStack(material).apply {
            itemMeta = itemMeta.apply {
                setDisplayName(name)
                this.lore = lore
            }
        }

    private class MissionsHolder(val clanId: String) : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    private companion object {
        const val CLAIM_SLOT = 15
        const val GOAL = 5000.0
    }
}
