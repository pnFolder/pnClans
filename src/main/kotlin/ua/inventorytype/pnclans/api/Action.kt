package ua.inventorytype.pnclans.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.placeholder.PlaceholderRegistry
import ua.inventorytype.pnclans.impl.ux.MainUX
import ua.inventorytype.pnclans.impl.ux.MembersUX
import ua.inventorytype.pnclans.impl.ux.SettingsUX
import ua.inventorytype.pnclans.impl.ux.TopClansUX
import ua.inventorytype.pnclans.impl.ux.TreasuryUX
import ua.inventorytype.pnclans.impl.ux.UpgradeUX
import kotlin.random.Random

/**
 * Context object provided during action execution containing player and placeholder data.
 */
data class ActionContext(
    val player: Player,
    val placeholderRegistry: PlaceholderRegistry,
    val placeholders: Map<String, String> = emptyMap(),
    val plugin: BukkitPlugin? = null,
    val durationSeconds: Int? = null
)

/**
 * Sealed interface representing polymorphic YAML actions executable from GUI clicks or triggers.
 */
@Serializable
sealed interface Action {
    /**
     * Executes the action within the given context.
     *
     * @param context The execution context containing player and plugin dependencies.
     */
    fun execute(context: ActionContext)
}

/**
 * Sends a colorized chat message to the player.
 * YAML tag: !message { text: "&aПривет, {player_name}!" }
 */
@Serializable
@SerialName("message")
data class MessageAction(
    val text: String
) : Action {
    override fun execute(context: ActionContext) {
        val formatted = context.placeholderRegistry.process(context.player, text, context.placeholders)
        context.player.sendMessage(formatted)
    }
}

/**
 * Sends a MiniMessage formatted chat message to the player (Adventure API).
 * YAML tag: !minimessage { text: "<green>Привет, {player_name}!</green>" }
 */
@Serializable
@SerialName("minimessage")
data class MiniMessageAction(
    val text: String
) : Action {
    override fun execute(context: ActionContext) {
        val raw = context.placeholderRegistry.process(context.player, text, context.placeholders, colorize = false)
        // MiniMessage strictly forbids legacy '§' character. Strip any legacy colors that might have leaked through PlaceholderAPI
        val clean = raw.replace(Regex("§[0-9a-fk-orx]"), "")
        context.player.sendMessage(MiniMessage.miniMessage().deserialize(clean))
    }
}

/**
 * Displays a large title and subtitle on the player's screen ("Товар", "Шанс", "Уровень").
 * YAML tag: !title { title: "&6КЛАНОВЫЙ ТИТУЛ", subtitle: "&eУспешно обновлено!", fadeIn: 10, stay: 70, fadeOut: 20 }
 */
@Serializable
@SerialName("title")
data class TitleAction(
    val title: String,
    val subtitle: String = "",
    val fadeIn: Int = 10,
    val stay: Int = 70,
    val fadeOut: Int = 20
) : Action {
    @Suppress("DEPRECATION")
    override fun execute(context: ActionContext) {
        val formattedTitle = context.placeholderRegistry.process(context.player, title, context.placeholders)
        val formattedSubtitle = context.placeholderRegistry.process(context.player, subtitle, context.placeholders)
        context.player.sendTitle(formattedTitle, formattedSubtitle, fadeIn, stay, fadeOut)
    }
}

/**
 * Displays a message directly above the player's hotbar in the ActionBar.
 * YAML tag: !actionbar { text: "&aБаланс казны пополнен на +500 ⛁" }
 */
@Serializable
@SerialName("actionbar")
data class ActionBarAction(
    val text: String
) : Action {
    @Suppress("DEPRECATION")
    override fun execute(context: ActionContext) {
        val formatted = context.placeholderRegistry.process(context.player, text, context.placeholders)
        context.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(formatted))
    }
}

/**
 * Displays a timed BossBar whose progress decreases until it disappears.
 *
 * YAML tag: !bossbar { text: "&eВведите никнейм", color: "YELLOW", style: "SOLID" }
 */
@Serializable
@SerialName("bossbar")
data class BossBarAction(
    val text: String,
    val color: String = "YELLOW",
    val style: String = "SOLID",
    val durationSeconds: Int = 15
) : Action {
    override fun execute(context: ActionContext) {
        val plugin = context.plugin ?: return
        val formatted = context.placeholderRegistry.process(context.player, text, context.placeholders)
        plugin.timedBossBarService.show(
            context.player,
            formatted,
            color,
            style,
            context.durationSeconds ?: durationSeconds
        )
    }
}

/**
 * Plays a sound effect to the player.
 * YAML tag: !sound { sound: "ENTITY_PLAYER_LEVELUP", volume: 1.0, pitch: 1.0 }
 */
@Serializable
@SerialName("sound")
data class SoundAction(
    val sound: String,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f
) : Action {
    @Suppress("DEPRECATION")
    override fun execute(context: ActionContext) {
        runCatching {
            val soundEnum = Sound.valueOf(sound.uppercase())
            context.player.playSound(context.player.location, soundEnum, volume, pitch)
        }
    }
}

/**
 * Spawns particle effects around the player's position.
 * YAML tag: !particle { particle: "TOTEM_OF_UNDYING", count: 25 }
 */
@Serializable
@SerialName("particle")
data class ParticleAction(
    val particle: String,
    val count: Int = 15
) : Action {
    override fun execute(context: ActionContext) {
        runCatching {
            val particleEnum = Particle.valueOf(particle.uppercase())
            context.player.world.spawnParticle(particleEnum, context.player.location.add(0.0, 1.0, 0.0), count, 0.5, 0.5, 0.5, 0.1)
        }
    }
}

/**
 * Broadcasts a formatted message to all online players or clan members.
 * YAML tag: !broadcast { text: "&8[&6Клан {clan}&8] &aКлан достиг {clan_level} уровня!" }
 */
@Serializable
@SerialName("broadcast")
data class BroadcastAction(
    val text: String
) : Action {
    @Suppress("DEPRECATION")
    override fun execute(context: ActionContext) {
        val formatted = context.placeholderRegistry.process(context.player, text, context.placeholders)
        Bukkit.broadcastMessage(formatted)
    }
}

/**
 * Rolls a percentage chance (0.0% to 100.0%), executing success or failure action branches ("Шанс").
 * YAML tag:
 * !chance
 *   percentage: 50.0
 *   successActions:
 *     - !sound { sound: "ENTITY_PLAYER_LEVELUP" }
 *     - !title { title: "&aУСПЕХ!", subtitle: "&7Вы выиграли бонус!" }
 *   failedActions:
 *     - !sound { sound: "ENTITY_VILLAGER_NO" }
 *     - !message { text: "&cНеудача! Попробуйте снова." }
 */
@Serializable
@SerialName("chance")
data class ChanceAction(
    val percentage: Double,
    val successActions: List<Action> = emptyList(),
    val failedActions: List<Action> = emptyList()
) : Action {
    override fun execute(context: ActionContext) {
        val roll = Random.nextDouble(0.0, 100.0)
        if (roll <= percentage) {
            successActions.forEach { it.execute(context) }
        } else {
            failedActions.forEach { it.execute(context) }
        }
    }
}

/**
 * Barter / Item Trade Action: exchanges money for items or items for money ("Товар" / "Бартер").
 * YAML tag: !barter { item: "DIAMOND", amount: 5, price: 1000.0, buy: true }
 */
@Serializable
@SerialName("barter")
data class BarterAction(
    val item: String,
    val amount: Int = 1,
    val price: Double = 0.0,
    val buy: Boolean = true
) : Action {
    override fun execute(context: ActionContext) {
        val plugin = context.plugin ?: return
        val player = context.player
        val material = runCatching { Material.valueOf(item.uppercase()) }.getOrNull() ?: return

        if (buy) {
            if (price > 0 && !plugin.economyService.withdraw(player, price)) {
                player.sendMessage("§cНедостаточно денег для покупки товара ($price$).")
                return
            }
            player.inventory.addItem(ItemStack(material, amount))
            player.sendMessage("§aВы успешно приобрели ${amount}x ${material.name} за $price$!")
        } else {
            val itemStack = ItemStack(material, amount)
            if (!player.inventory.containsAtLeast(itemStack, amount)) {
                player.sendMessage("§cУ вас нет ${amount}x ${material.name} в инвентаре.")
                return
            }
            player.inventory.removeItem(itemStack)
            if (price > 0) plugin.economyService.depositPlayer(player, price)
            player.sendMessage("§aВы продали ${amount}x ${material.name} и получили $price$!")
        }
    }
}

/**
 * Gives an item directly to the player's inventory ("Товар").
 * YAML tag: !item_give { item: "GOLDEN_APPLE", amount: 3 }
 */
@Serializable
@SerialName("item_give")
data class GiveItemAction(
    val item: String,
    val amount: Int = 1
) : Action {
    override fun execute(context: ActionContext) {
        val material = runCatching { Material.valueOf(item.uppercase()) }.getOrNull()
            ?: throw IllegalArgumentException("Unknown item material: $item")
        giveItem(context.player, ItemStack(material), amount)
    }
}

/**
 * Gives a configurable item with display metadata and enchantments.
 * YAML tag:
 * !item_reward
 *   item: DIAMOND_SWORD
 *   amount: 1
 *   name: "&bКлинок клана"
 *   lore: ["&7Награда для {player}"]
 *   enchantments: { SHARPNESS: 5, UNBREAKING: 3 }
 */
@Serializable
@SerialName("item_reward")
data class ItemRewardAction(
    val item: String,
    val amount: Int = 1,
    val name: String? = null,
    val lore: List<String> = emptyList(),
    val enchantments: Map<String, Int> = emptyMap(),
    val unbreakable: Boolean = false,
    val unsafeEnchantments: Boolean = false
) : Action {
    @Suppress("DEPRECATION")
    override fun execute(context: ActionContext) {
        val material = runCatching { Material.valueOf(item.uppercase()) }.getOrNull()
            ?: throw IllegalArgumentException("Unknown reward material: $item")
        val reward = ItemStack(material)
        val meta = reward.itemMeta ?: return

        name?.let { meta.setDisplayName(context.placeholderRegistry.process(context.player, it, context.placeholders)) }
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { context.placeholderRegistry.process(context.player, it, context.placeholders) }
        }
        meta.isUnbreakable = unbreakable
        reward.itemMeta = meta

        enchantments.forEach { (enchantmentName, level) ->
            val enchantment = enchantment(enchantmentName)
                ?: throw IllegalArgumentException("Unknown enchantment: $enchantmentName")
            if (unsafeEnchantments) {
                reward.addUnsafeEnchantment(enchantment, level)
            } else {
                reward.addEnchantment(enchantment, level)
            }
        }
        giveItem(context.player, reward, amount)
    }

    @Suppress("DEPRECATION")
    private fun enchantment(name: String): Enchantment? {
        val legacyName = ENCHANTMENT_ALIASES[name.uppercase()] ?: name.uppercase()
        return Enchantment.getByName(legacyName)
    }

    private companion object {
        val ENCHANTMENT_ALIASES = mapOf(
            "SHARPNESS" to "DAMAGE_ALL",
            "SMITE" to "DAMAGE_UNDEAD",
            "BANE_OF_ARTHROPODS" to "DAMAGE_ARTHROPODS",
            "UNBREAKING" to "DURABILITY",
            "EFFICIENCY" to "DIG_SPEED",
            "FORTUNE" to "LOOT_BONUS_BLOCKS",
            "LOOTING" to "LOOT_BONUS_MOBS",
            "PROTECTION" to "PROTECTION_ENVIRONMENTAL",
            "FIRE_PROTECTION" to "PROTECTION_FIRE",
            "FEATHER_FALLING" to "PROTECTION_FALL",
            "BLAST_PROTECTION" to "PROTECTION_EXPLOSIONS",
            "PROJECTILE_PROTECTION" to "PROTECTION_PROJECTILE",
            "POWER" to "ARROW_DAMAGE",
            "PUNCH" to "ARROW_KNOCKBACK",
            "FLAME" to "ARROW_FIRE",
            "INFINITY" to "ARROW_INFINITE"
        )
    }
}

/**
 * Runs a server-console command after formatting action placeholders.
 * YAML tag: !console_command { command: "minecraft:give {player} diamond 3" }
 */
@Serializable
@SerialName("console_command")
data class ConsoleCommandAction(val command: String) : Action {
    override fun execute(context: ActionContext) {
        val formatted = context.placeholderRegistry.process(context.player, command, context.placeholders, colorize = false)
            .removePrefix("/")
            .trim()
        require(formatted.isNotEmpty()) { "Console command cannot be empty" }
        check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted)) { "Console command failed: $formatted" }
    }
}

/** Delivers every configured unit, dropping overflow rather than silently truncating the reward. */
private fun giveItem(player: Player, template: ItemStack, amount: Int) {
    var remaining = amount.coerceAtLeast(1)
    while (remaining > 0) {
        val stack = template.clone().apply { this.amount = minOf(remaining, maxStackSize) }
        player.inventory.addItem(stack).values.forEach { overflow ->
            player.world.dropItemNaturally(player.location, overflow)
        }
        remaining -= stack.amount
    }
}

/**
 * Adds MMR points to the player's clan rating.
 * YAML tag: !mmr_add { amount: 25 }
 */
@Serializable
@SerialName("mmr_add")
data class AddMmrAction(
    val amount: Int
) : Action {
    override fun execute(context: ActionContext) {
        val plugin = context.plugin ?: return
        val clan = plugin.clanService.getClanUser(context.player) ?: return
        clan.mmr += amount
        plugin.clanService.saveClan(clan)
        context.player.sendMessage("§aРейтинг клана увеличен на +$amount MMR!")
    }
}


/**
 * Opens a specific clan GUI menu for the player.
 * YAML tag: !open_gui { menu: "MAIN" }
 */
@Serializable
@SerialName("open_gui")
data class OpenGuiAction(
    val menu: String
) : Action {
    override fun execute(context: ActionContext) {
        val plugin = context.plugin ?: return
        val clanService = plugin.clanService
        when (menu.uppercase()) {
            "MAIN" -> MainUX(clanService).open(context.player)
            "MEMBERS" -> MembersUX(clanService).open(context.player)
            "SETTINGS" -> SettingsUX(clanService).open(context.player)
            "TREASURY" -> TreasuryUX(clanService).open(context.player)
            "UPGRADE" -> UpgradeUX(clanService).open(context.player)
            "TOP" -> TopClansUX(clanService).open(context.player)
            "CHEST" -> {
                val clan = clanService.getClanUser(context.player)
                if (clan != null) clanService.openClanChest(context.player, clan)
            }
        }
    }
}

/**
 * Closes the player's currently open inventory.
 * YAML tag: !close {}
 */
@Serializable
@SerialName("close")
class CloseAction : Action {
    override fun execute(context: ActionContext) {
        context.player.closeInventory()
    }
}
