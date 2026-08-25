package ua.inventorytype.pnclans.impl.config

import org.bukkit.Material
import org.bukkit.entity.EntityType
import ua.inventorytype.pnclans.BukkitPlugin

/** Reports dangerous or contradictory administrator configuration without silently changing it. */
internal object ConfigValidator {
    fun validate(plugin: BukkitPlugin, config: ConfigService): Int {
        val issues = mutableListOf<String>()
        fun issue(path: String, message: String) {
            issues += "$path: $message"
        }

        validateTreasury(config, ::issue)
        validateShop(config, ::issue)
        validateQuests(config, ::issue)
        validateBattles(config, ::issue)

        issues.forEach { plugin.logger.warning("[pnClans/Config] $it") }
        if (issues.isNotEmpty()) {
            plugin.logger.warning("[pnClans/Config] Found ${issues.size} configuration issue(s). The plugin kept the administrator values; review the warnings above.")
        }
        return issues.size
    }

    private fun validateTreasury(config: ConfigService, issue: (String, String) -> Unit) {
        val settings = config.settings
        val menu = config.menus.treasuryMenu
        val maxSlot = menu.rows * 9 - 1

        if (settings.treasuryPromptTimeoutSeconds <= 0) {
            issue("config.yml.treasuryPromptTimeoutSeconds", "must be greater than 0")
        }
        if (settings.treasuryPromptCancelInputs.none { it.isNotBlank() }) {
            issue("config.yml.treasuryPromptCancelInputs", "must contain at least one non-empty cancel word")
        }
        validatePresetPairs(
            "treasuryDepositPresets",
            settings.treasuryDepositPresets,
            settings.treasuryDepositPresetSlots,
            maxSlot,
            issue
        )
        validatePresetPairs(
            "treasuryWithdrawPresets",
            settings.treasuryWithdrawPresets,
            settings.treasuryWithdrawPresetSlots,
            maxSlot,
            issue
        )

        val protectedSlots = menu.items
            .filterKeys { it !in setOf("depositPresets", "withdrawPresets") }
            .mapValues { it.value.slot }
        (settings.treasuryDepositPresetSlots + settings.treasuryWithdrawPresetSlots).forEach { slot ->
            protectedSlots.entries.firstOrNull { it.value == slot }?.let { collision ->
                issue("config.yml.treasuryPresetSlots", "slot $slot collides with menus.yml treasury item '${collision.key}'")
            }
        }
    }

    private fun validatePresetPairs(
        name: String,
        amounts: List<Int>,
        slots: List<Int>,
        maxSlot: Int,
        issue: (String, String) -> Unit
    ) {
        if (amounts.size != slots.size) {
            issue("config.yml.$name", "contains ${amounts.size} amount(s), but its slot list contains ${slots.size}")
        }
        amounts.forEachIndexed { index, amount ->
            if (amount <= 0) issue("config.yml.$name[$index]", "amount must be greater than 0")
        }
        slots.forEachIndexed { index, slot ->
            if (slot !in 0..maxSlot) issue("config.yml.${name}Slots[$index]", "slot $slot is outside 0..$maxSlot")
        }
        slots.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { slot ->
            issue("config.yml.${name}Slots", "slot $slot is used more than once")
        }
    }

    private fun validateShop(config: ConfigService, issue: (String, String) -> Unit) {
        val shop = config.shop
        val maxSlot = shop.rows * 9 - 1
        if (shop.rows !in 1..6) issue("shop.yml.rows", "must be between 1 and 6")
        if (shop.categories.isEmpty()) issue("shop.yml.categories", "must contain at least one category")
        if (shop.rarities.isEmpty()) issue("shop.yml.rarities", "must contain at least one rarity")

        shop.categories.forEach { (id, category) ->
            if (category.slot !in 0..maxSlot) issue("shop.yml.categories.$id.slot", "slot ${category.slot} is outside 0..$maxSlot")
            validateMaterial("shop.yml.categories.$id.material", category.material, issue)
        }

        shop.display.productSlots.forEachIndexed { index, slot ->
            if (slot !in 0..maxSlot) issue("shop.yml.display.productSlots[$index]", "slot $slot is outside 0..$maxSlot")
        }

        shop.products.forEach { (id, product) ->
            val path = "shop.yml.products.$id"
            validateMaterial("$path.material", product.material, issue)
            if (product.category !in shop.categories) issue("$path.category", "unknown category '${product.category}'")
            if (product.rarity !in shop.rarities) issue("$path.rarity", "unknown rarity '${product.rarity}'")
            if (product.itemAmount <= 0) issue("$path.itemAmount", "must be greater than 0")
            if (product.payments.isEmpty()) issue("$path.payments", "must contain at least one payment option")
            product.payments.forEachIndexed { index, payment ->
                if (payment.amount <= 0L) issue("$path.payments[$index].amount", "must be greater than 0")
            }
            if (product.conditions.minimumClanLevel < 0) issue("$path.conditions.minimumClanLevel", "cannot be negative")
            if (product.conditions.minimumMembers < 0) issue("$path.conditions.minimumMembers", "cannot be negative")
            if (product.conditions.dailyClanLimit < 0) issue("$path.conditions.dailyClanLimit", "cannot be negative")
            if (product.conditions.dailyGlobalLimit < 0) issue("$path.conditions.dailyGlobalLimit", "cannot be negative")
            product.conditions.requiredQuests.filterNot(config.quests.quests::containsKey).forEach { questId ->
                issue("$path.conditions.requiredQuests", "unknown quest '$questId'")
            }
        }
    }

    private fun validateQuests(config: ConfigService, issue: (String, String) -> Unit) {
        val quests = config.quests
        val maxSlot = quests.rows * 9 - 1
        if (quests.rows !in 1..6) issue("quests.yml.rows", "must be between 1 and 6")

        quests.display.entrySlots.forEachIndexed { index, slot ->
            if (slot !in 0..maxSlot) issue("quests.yml.display.entrySlots[$index]", "slot $slot is outside 0..$maxSlot")
        }

        quests.quests.forEach { (id, quest) ->
            val path = "quests.yml.quests.$id"
            validateMaterial("$path.material", quest.material, issue)
            if (quest.target <= 0L) issue("$path.target", "must be greater than 0")
            if (quest.rewardPoints < 0L) issue("$path.rewardPoints", "cannot be negative")
            if (id in quest.prerequisites) issue("$path.prerequisites", "quest cannot require itself")
            quest.prerequisites.filterNot(quests.quests::containsKey).forEach { required ->
                issue("$path.prerequisites", "unknown quest '$required'")
            }
            if (quest.repeatable && quest.reset == ClanQuestReset.NONE) {
                issue("$path.reset", "repeatable=true requires DAILY or WEEKLY reset")
            }
            if (!quest.repeatable && quest.reset != ClanQuestReset.NONE) {
                issue("$path.repeatable", "reset=${quest.reset} has no effect while repeatable=false")
            }
            if (quest.objective == ClanQuestObjective.MOB_KILL) {
                quest.entityTypes.forEach { entityName ->
                    if (runCatching { EntityType.valueOf(entityName.uppercase()) }.isFailure) {
                        issue("$path.entityTypes", "unknown EntityType '$entityName'")
                    }
                }
            }
        }

        detectQuestCycles(quests.quests, issue)
    }

    private fun detectQuestCycles(
        quests: Map<String, ClanQuestConfig>,
        issue: (String, String) -> Unit
    ) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val reported = mutableSetOf<Set<String>>()

        fun visit(id: String, stack: List<String>) {
            if (id in visited) return
            if (!visiting.add(id)) {
                val cycleStart = stack.indexOf(id).takeIf { it >= 0 } ?: 0
                val cycle = (stack.drop(cycleStart) + id)
                val signature = cycle.toSet()
                if (reported.add(signature)) {
                    issue("quests.yml.quests.$id.prerequisites", "dependency cycle detected: ${cycle.joinToString(" -> ")}")
                }
                return
            }
            val nextStack = stack + id
            quests[id]?.prerequisites?.filter(quests::containsKey)?.forEach { prerequisite -> visit(prerequisite, nextStack) }
            visiting.remove(id)
            visited.add(id)
        }

        quests.keys.forEach { visit(it, emptyList()) }
    }

    private fun validateBattles(config: ConfigService, issue: (String, String) -> Unit) {
        val battles = config.battles
        val maxSlot = battles.rows * 9 - 1
        if (battles.rows !in 1..6) issue("battles.yml.rows", "must be between 1 and 6")
        if (battles.challengeTimeoutSeconds <= 0L) issue("battles.yml.challengeTimeoutSeconds", "must be greater than 0")
        if (battles.lobbyTimeoutSeconds <= 0L) issue("battles.yml.lobbyTimeoutSeconds", "must be greater than 0")
        if (battles.countdownSeconds < 0L) issue("battles.yml.countdownSeconds", "cannot be negative")
        if (battles.battleDurationSeconds <= 0L) issue("battles.yml.battleDurationSeconds", "must be greater than 0")
        if (battles.minimumOnlineMembers <= 0) issue("battles.yml.minimumOnlineMembers", "must be greater than 0")
        if (battles.maximumParticipants < battles.minimumOnlineMembers) {
            issue("battles.yml.maximumParticipants", "must be at least minimumOnlineMembers (${battles.minimumOnlineMembers})")
        }
        if (battles.scoreToWin <= 0) issue("battles.yml.scoreToWin", "must be greater than 0")
        if (battles.ratingWin < 0 || battles.ratingLoss < 0) issue("battles.yml.rating", "win/loss changes cannot be negative")
        if (battles.pointsWin < 0L || battles.pointsPerKill < 0L) issue("battles.yml.points", "battle point rewards cannot be negative")
        if (battles.arenas.isEmpty()) issue("battles.yml.arenas", "must contain at least one arena")

        battles.arenas.forEach { (id, arena) ->
            if (arena.world.isBlank()) issue("battles.yml.arenas.$id.world", "cannot be blank")
            if (!arena.radius.isFinite() || arena.radius <= 0.0) issue("battles.yml.arenas.$id.radius", "must be a finite value greater than 0")
        }

        val display = battles.display
        val slots = buildList {
            add(display.headerSlot)
            add(display.ownSlot)
            add(display.incomingSlot)
            add(display.backSlot)
            add(display.previousSlot)
            add(display.pageSlot)
            add(display.nextSlot)
            add(display.refreshSlot)
            addAll(display.opponentSlots)
        }
        slots.forEach { slot ->
            if (slot !in 0..maxSlot) issue("battles.yml.display", "slot $slot is outside 0..$maxSlot")
        }
        listOf(
            "headerMaterial" to display.headerMaterial,
            "ownMaterial" to display.ownMaterial,
            "activeBattleMaterial" to display.activeBattleMaterial,
            "lobbyMaterial" to display.lobbyMaterial,
            "countdownMaterial" to display.countdownMaterial,
            "incomingMaterial" to display.incomingMaterial,
            "opponentMaterial" to display.opponentMaterial,
            "emptyMaterial" to display.emptyMaterial,
            "backMaterial" to display.backMaterial,
            "previousMaterial" to display.previousMaterial,
            "disabledPreviousMaterial" to display.disabledPreviousMaterial,
            "pageMaterial" to display.pageMaterial,
            "nextMaterial" to display.nextMaterial,
            "disabledNextMaterial" to display.disabledNextMaterial,
            "refreshMaterial" to display.refreshMaterial
        ).forEach { (name, material) -> validateMaterial("battles.yml.display.$name", material, issue) }
    }

    private fun validateMaterial(path: String, material: String, issue: (String, String) -> Unit) {
        if (Material.matchMaterial(material) == null) issue(path, "unknown Material '$material'")
    }
}
