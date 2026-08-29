package ua.inventorytype.pnclans.impl.command

import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.Action
import ua.inventorytype.pnclans.api.ItemRewardAction
import ua.inventorytype.pnclans.api.SerializedItemRewardAction
import ua.inventorytype.pnclans.api.shop.ClanShopCurrency
import ua.inventorytype.pnclans.impl.config.ClanShopPaymentOption
import ua.inventorytype.pnclans.impl.config.ClanShopProductConfig
import ua.inventorytype.pnclans.impl.storage.ItemStackSerializer
import ua.inventorytype.pnclans.impl.util.ColorUtil
import kotlin.math.ceil

/** Safe in-game editor for the most common shop administration operations. */
internal class ClanAdminShopCommandHandler(private val plugin: BukkitPlugin) {

    fun execute(sender: CommandSender, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> help(sender)
            "list" -> list(sender, args.drop(1))
            "info" -> info(sender, args.drop(1))
            "add" -> add(sender, args.drop(1), fromHand = false)
            "addhand" -> add(sender, args.drop(1), fromHand = true)
            "remove" -> remove(sender, args.drop(1))
            "price" -> price(sender, args.drop(1))
            "category" -> category(sender, args.drop(1))
            "slot" -> slot(sender, args.drop(1))
            "clone" -> clone(sender, args.drop(1))
            else -> usage(sender)
        }
    }

    fun complete(args: List<String>): List<String> {
        val actions = listOf("help", "list", "info", "add", "addhand", "remove", "price", "category", "slot", "clone")
        if (args.isEmpty()) return actions
        val current = args.last()
        val candidates = when (args.size) {
            1 -> actions
            2 -> when (args[0].lowercase()) {
                "info", "remove", "price", "category", "slot", "clone" -> productIds()
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "add" -> Material.entries.asSequence().filter { it.isItem }.map { it.name }.take(100).toList()
                "category" -> categoryIds()
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "add", "addhand", "price" -> ClanShopCurrency.entries.map { it.name }
                "clone" -> emptyList()
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "add", "addhand" -> categoryIds()
                else -> emptyList()
            }
            else -> emptyList()
        }
        return candidates.filter { it.startsWith(current, ignoreCase = true) }
    }

    private fun list(sender: CommandSender, args: List<String>) {
        val products = plugin.configService.shop.products.toList().sortedBy { it.first.lowercase() }
        val pageSize = 10
        val pages = maxOf(1, ceil(products.size.toDouble() / pageSize).toInt())
        val page = (args.firstOrNull()?.toIntOrNull() ?: 1).coerceIn(1, pages)
        sender.reply("")
        sender.reply("&#FC7D37✦ &fТовары магазина &8• &f$page/$pages &8• всего ${products.size}")
        products.drop((page - 1) * pageSize).take(pageSize).forEach { (id, product) ->
            val prices = product.payments.joinToString(" / ") { "${it.amount} ${it.currency.name}" }
            sender.reply("&#5EA9FD$id &8• &f${product.material} x${product.itemAmount} &8• &7$prices &8• ${product.category}")
        }
        sender.reply("")
    }

    private fun info(sender: CommandSender, args: List<String>) {
        val id = args.firstOrNull() ?: return usage(sender)
        val product = plugin.configService.shop.products[id] ?: return notFound(sender, id)
        sender.reply("")
        sender.reply("&#FC7D37✦ &fТовар &#5EA9FD$id")
        sender.reply("&8Название: &f${product.name}")
        sender.reply("&8Material: &f${product.material} &8• amount: &f${product.itemAmount} &8• slot: &f${product.slot}")
        sender.reply("&8Категория: &f${product.category} &8• редкость: &f${product.rarity} &8• sort: &f${product.sortOrder}")
        product.payments.forEach { sender.reply("&8Оплата: &#FFD700${it.amount} &f${it.currency}${it.permission?.let { permission -> " &8• permission=$permission" } ?: ""}") }
        sender.reply("&8Rewards: &f${product.rewards.size} &8• полный ItemStack: &f${if (product.itemStack.isNullOrBlank()) "нет" else "да"}")
        sender.reply("")
    }

    private fun add(sender: CommandSender, args: List<String>, fromHand: Boolean) {
        val id = args.getOrNull(0)?.normalizeId() ?: return usage(sender)
        if (!VALID_ID.matches(id)) {
            sender.reply("&#FC3737✖ &fID товара: только a-z, 0-9, _ и -.")
            return
        }
        if (plugin.configService.shop.products.containsKey(id)) {
            sender.reply("&#FC3737✖ &fТовар &#5EA9FD$id &fуже существует. Используйте другое ID или удалите старый товар.")
            return
        }

        val (material, amount, itemStack, reward, nextIndex) = if (fromHand) {
            val player = sender as? Player ?: run {
                sender.reply("&#FC3737✖ &fКоманда addhand доступна только игроку.")
                return
            }
            val held = player.inventory.itemInMainHand.clone()
            if (held.type.isAir) {
                sender.reply("&#FC3737✖ &fВозьмите предмет в основную руку.")
                return
            }
            val serialized = ItemStackSerializer.toBase64(arrayOf(held))
            AddItemData(
                material = held.type,
                amount = held.amount.coerceAtLeast(1),
                itemStack = serialized,
                reward = SerializedItemRewardAction(serialized),
                nextIndex = 1
            )
        } else {
            val material = args.getOrNull(1)?.let { runCatching { Material.valueOf(it.uppercase()) }.getOrNull() }
                ?.takeIf { it.isItem }
                ?: return usage(sender)
            AddItemData(
                material = material,
                amount = 1,
                itemStack = null,
                reward = ItemRewardAction(material.name, 1),
                nextIndex = 2
            )
        }

        val price = args.getOrNull(nextIndex)?.toLongOrNull()?.takeIf { it > 0L } ?: return usage(sender)
        val currency = args.getOrNull(nextIndex + 1)?.parseCurrency() ?: ClanShopCurrency.CLAN_POINTS
        val category = args.getOrNull(nextIndex + 2)?.takeIf { it in plugin.configService.shop.categories }
            ?: defaultCategory()
        val rarity = defaultRarity()
        val slot = freeProductSlot()
        val displayName = if (fromHand) {
            val player = sender as Player
            player.inventory.itemInMainHand.itemMeta?.takeIf { it.hasDisplayName() }?.displayName
                ?: "&f${material.name.lowercase().replace('_', ' ')}"
        } else {
            "&f${material.name.lowercase().replace('_', ' ')}"
        }
        val product = ClanShopProductConfig(
            slot = slot,
            material = material.name,
            name = displayName,
            lore = listOf("", "&7Добавлено администратором через /clan admin shop."),
            itemAmount = amount,
            itemStack = itemStack,
            payments = listOf(ClanShopPaymentOption(currency, price)),
            rewards = listOf(reward),
            category = category,
            sortOrder = nextSortOrder(),
            rarity = rarity
        )
        if (!replaceProducts(sender, plugin.configService.shop.products + (id to product), "add product $id")) return
        sender.reply("&#5EFD7D✔ &fДобавлен товар &#5EA9FD$id &8• &#FFD700$price $currency&f.")
    }

    private fun remove(sender: CommandSender, args: List<String>) {
        val id = args.firstOrNull() ?: return usage(sender)
        if (id !in plugin.configService.shop.products) return notFound(sender, id)
        if (!replaceProducts(sender, plugin.configService.shop.products - id, "remove product $id")) return
        sender.reply("&#5EFD7D✔ &fТовар &#5EA9FD$id &fудалён из shop.yml.")
    }

    private fun price(sender: CommandSender, args: List<String>) {
        val id = args.getOrNull(0) ?: return usage(sender)
        val product = plugin.configService.shop.products[id] ?: return notFound(sender, id)
        val amount = args.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L } ?: return usage(sender)
        val currency = args.getOrNull(2)?.parseCurrency()
        val payments = if (currency == null) {
            if (product.payments.isEmpty()) listOf(ClanShopPaymentOption(ClanShopCurrency.CLAN_POINTS, amount))
            else product.payments.mapIndexed { index, payment -> if (index == 0) payment.copy(amount = amount) else payment }
        } else {
            val existing = product.payments.indexOfFirst { it.currency == currency }
            if (existing >= 0) product.payments.mapIndexed { index, payment -> if (index == existing) payment.copy(amount = amount) else payment }
            else product.payments + ClanShopPaymentOption(currency, amount)
        }
        if (!replaceProduct(sender, id, product.copy(payments = payments), "price product $id amount=$amount currency=${currency ?: payments.first().currency}")) return
        sender.reply("&#5EFD7D✔ &fЦена товара &#5EA9FD$id &fобновлена.")
    }

    private fun category(sender: CommandSender, args: List<String>) {
        val id = args.getOrNull(0) ?: return usage(sender)
        val product = plugin.configService.shop.products[id] ?: return notFound(sender, id)
        val category = args.getOrNull(1)?.takeIf { it in plugin.configService.shop.categories } ?: return usage(sender)
        if (!replaceProduct(sender, id, product.copy(category = category), "category product $id category=$category")) return
        sender.reply("&#5EFD7D✔ &fКатегория товара &#5EA9FD$id&f: &e$category&f.")
    }

    private fun slot(sender: CommandSender, args: List<String>) {
        val id = args.getOrNull(0) ?: return usage(sender)
        val product = plugin.configService.shop.products[id] ?: return notFound(sender, id)
        val slot = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..53 } ?: return usage(sender)
        if (!replaceProduct(sender, id, product.copy(slot = slot), "slot product $id slot=$slot")) return
        sender.reply("&#5EFD7D✔ &fSlot товара &#5EA9FD$id&f: &e$slot&f.")
    }

    private fun clone(sender: CommandSender, args: List<String>) {
        val sourceId = args.getOrNull(0) ?: return usage(sender)
        val newId = args.getOrNull(1)?.normalizeId() ?: return usage(sender)
        val source = plugin.configService.shop.products[sourceId] ?: return notFound(sender, sourceId)
        if (!VALID_ID.matches(newId) || newId in plugin.configService.shop.products) {
            sender.reply("&#FC3737✖ &fНовый ID недопустим или уже занят.")
            return
        }
        val copy = source.copy(slot = freeProductSlot(), sortOrder = nextSortOrder())
        if (!replaceProducts(sender, plugin.configService.shop.products + (newId to copy), "clone product $sourceId -> $newId")) return
        sender.reply("&#5EFD7D✔ &fТовар &#5EA9FD$sourceId &fскопирован как &#5EA9FD$newId&f.")
    }

    private fun replaceProduct(sender: CommandSender, id: String, product: ClanShopProductConfig, audit: String): Boolean =
        replaceProducts(sender, plugin.configService.shop.products + (id to product), audit)

    private fun replaceProducts(sender: CommandSender, updated: Map<String, ClanShopProductConfig>, audit: String): Boolean {
        val shop = plugin.configService.shop
        val previous = shop.products
        shop.products = updated
        val saved = runCatching { plugin.configService.saveShop() }.isSuccess
        if (!saved) {
            shop.products = previous
            sender.reply("&#FC3737✖ &fНе удалось сохранить shop.yml. Изменение отменено.")
            return false
        }
        plugin.logger.info("[pnClans/Admin] ${sender.name}: $audit")
        return true
    }

    private fun freeProductSlot(): Int {
        val used = plugin.configService.shop.products.values.map { it.slot }.toSet()
        return plugin.configService.shop.display.productSlots.firstOrNull { it !in used }
            ?: plugin.configService.shop.display.productSlots.firstOrNull()
            ?: 0
    }

    private fun nextSortOrder(): Int = (plugin.configService.shop.products.values.maxOfOrNull { it.sortOrder } ?: 0) + 1

    private fun defaultCategory(): String = plugin.configService.shop.categories.keys.firstOrNull { it != "all" }
        ?: plugin.configService.shop.categories.keys.firstOrNull()
        ?: "general"

    private fun defaultRarity(): String = plugin.configService.shop.rarities.keys.firstOrNull() ?: "common"

    private fun productIds(): List<String> = plugin.configService.shop.products.keys.sorted()
    private fun categoryIds(): List<String> = plugin.configService.shop.categories.keys.sorted()

    private fun String.parseCurrency(): ClanShopCurrency? = runCatching { ClanShopCurrency.valueOf(uppercase()) }.getOrNull()
    private fun String.normalizeId(): String = lowercase().trim()

    private fun notFound(sender: CommandSender, id: String) {
        sender.reply("&#FC3737✖ &fТовар &#5EA9FD$id &fне найден.")
    }

    private fun usage(sender: CommandSender) {
        sender.reply("&#FFD700Использование: &f/clan admin shop <help|list|info|add|addhand|remove|price|category|slot|clone> ...")
    }

    private fun help(sender: CommandSender) {
        sender.reply("")
        sender.reply("&#FC7D37✦ &fУправление клановым магазином")
        sender.reply("&#5EA9FD/clan admin shop list [page]")
        sender.reply("&#5EA9FD/clan admin shop info <product-id>")
        sender.reply("&#5EA9FD/clan admin shop add <id> <material> <price> [currency] [category]")
        sender.reply("&#5EA9FD/clan admin shop addhand <id> <price> [currency] [category] &8• точная копия предмета из руки")
        sender.reply("&#5EA9FD/clan admin shop price <id> <price> [currency]")
        sender.reply("&#5EA9FD/clan admin shop category <id> <category>")
        sender.reply("&#5EA9FD/clan admin shop slot <id> <0..53>")
        sender.reply("&#5EA9FD/clan admin shop clone <id> <new-id>")
        sender.reply("&#5EA9FD/clan admin shop remove <id>")
        sender.reply("")
    }

    private fun CommandSender.reply(text: String) {
        sendMessage(ColorUtil.color(text))
    }

    private data class AddItemData(
        val material: Material,
        val amount: Int,
        val itemStack: String?,
        val reward: Action,
        val nextIndex: Int
    )

    private companion object {
        val VALID_ID = Regex("^[a-z0-9_-]{1,64}$")
    }
}
