package ua.inventorytype.pnclans.impl.ux

import org.bukkit.Material
import org.bukkit.entity.Player
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.permission.ClanPerms
import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.config.AnimationKey
import ua.inventorytype.pnclans.impl.config.GuiItemConfig
import ua.inventorytype.pnclans.impl.inventory.BaseGui
import ua.inventorytype.pnclans.impl.inventory.builder.ItemBuilder

/**
 * Six-step clan progression screen with animated beacon state.
 *
 * All visible text and materials come from [ua.inventorytype.pnclans.impl.config.MenusConfig.upgradeMenu]
 * in `menus.yml`; animation frames live in [ua.inventorytype.pnclans.impl.config.Settings.animations].
 */
class UpgradeUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val cfg = clanService.plugin.configService
        val menuCfg = cfg.menus.upgradeMenu

        title(menuCfg.title)
        rows(menuCfg.rows)
        background(clanService.plugin.configService.menus.background)

        val levelSlots = listOf(20, 22, 24, 29, 31)
        menuCfg.items["level"]?.let { levelTemplate ->
            ClanLevels.LEVELS.entries
                .sortedBy { it.key }
                .forEachIndexed { index, (_, levelData) ->
                    val slotIndex = levelSlots.getOrNull(index) ?: return@forEachIndexed
                    slot(slotIndex) {
                        dynamicItem(this@UpgradeUX.parseMaterial(levelTemplate.material, levelData.icon)) { player ->
                            val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@dynamicItem null
                            this@UpgradeUX.renderConfigItem(
                                this,
                                player,
                                levelTemplate,
                                this@UpgradeUX.levelPlaceholders(levelData, clan)
                            )
                            null
                        }
                    }
                }
        }

        menuCfg.items["overview"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@UpgradeUX.parseMaterial(itemCfg.material, Material.EXPERIENCE_BOTTLE)) { player ->
                    val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    this@UpgradeUX.renderConfigItem(this, player, itemCfg, this@UpgradeUX.overviewPlaceholders(player, clan))
                    null
                }
            }
        }

        menuCfg.items["upgrade"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@UpgradeUX.parseMaterial(itemCfg.material, Material.BEACON)) { player ->
                    val clan = this@UpgradeUX.clanService.getClanUser(player) ?: return@dynamicItem null
                    this@UpgradeUX.renderConfigItem(this, player, itemCfg, this@UpgradeUX.beaconPlaceholders(player, clan))
                    null
                }
                onClick { player, _ -> this@UpgradeUX.performUpgrade(player) }
            }
        }

        menuCfg.items["back"]?.let { itemCfg ->
            slot(itemCfg.slot) {
                dynamicItem(this@UpgradeUX.parseMaterial(itemCfg.material, Material.RED_CANDLE)) { player ->
                    this@UpgradeUX.renderConfigItem(this, player, itemCfg, emptyMap())
                    null
                }
                onClick { player, _ -> MainUX(this@UpgradeUX.clanService).open(player) }
            }
        }
    }

    private fun performUpgrade(player: Player) {
        val service = clanService
        val cfg = service.plugin.configService
        val clan = service.getClanUser(player) ?: return
        val user = clan.getMember(player.uniqueId) ?: return

        if (clan.level >= ClanLevels.MAX_LEVEL) {
            cfg.send(player, cfg.messages.upgrade.maxLevel)
            return
        }

        if (!clan.hasPermission(user, ClanPerms.Action.UPGRADE_LEVEL)) {
            cfg.send(player, cfg.messages.upgrade.noPermission)
            return
        }

        val next = ClanLevels.getNext(clan.level) ?: return

        if (clan.bankBalance < next.costMoney) {
            cfg.send(player, cfg.messages.upgrade.insufficientFunds)
            return
        }
        if (clan.mmr < next.requiredMmr) {
            cfg.send(player, cfg.messages.upgrade.insufficientMmr)
            return
        }
        val completedQuests = service.plugin.clanQuestService.completedQuestCount(clan)
        if (service.plugin.configService.quests.enabled && completedQuests < next.requiredQuests) {
            cfg.send(
                player,
                cfg.messages.upgrade.insufficientQuests,
                mapOf("completed_quests" to completedQuests.toString(), "required_quests" to next.requiredQuests.toString())
            )
            return
        }

        val previousBalance = clan.bankBalance
        val previousLevel = clan.level
        clan.withdrawBank(next.costMoney)
        clan.level = next.level
        if (!service.saveClan(clan)) {
            clan.bankBalance = previousBalance
            clan.level = previousLevel
            player.sendMessage(cfg.formatMessage(player, "&#FC3737✖ &fУлучшение отменено: сохранить данные клана не удалось."))
            return
        }
        service.notifyClanUpdated(player.uniqueId)

        cfg.send(player, cfg.messages.upgrade.levelUp, mapOf("level" to next.level.toString()))
        update(player)
    }

    private fun overviewPlaceholders(player: Player, clan: Clan): Map<String, String> {
        val cfg = clanService.plugin.configService
        val next = ClanLevels.getNext(clan.level)
        return mapOf(
            "clan" to clan.name,
            "clan_level" to clan.level.toString(),
            "next_level" to (next?.level ?: clan.level).toString(),
            "clan_required_money" to (next?.costMoney?.toString() ?: "0"),
            "clan_required_mmr" to (next?.requiredMmr?.toString() ?: clan.mmr.toString()),
            "clan_quests" to serviceQuestCount(clan).toString(),
            "clan_required_quests" to (next?.requiredQuests?.takeIf { cfg.quests.enabled } ?: 0).toString(),
            "clan_slots" to (clan.level * PER_LEVEL_MEMBERS).toString(),
            "clan_chest_rows" to (clan.level * PER_LEVEL_CHEST_ROWS).toString(),
            "clan_homes" to (clan.level * PER_LEVEL_HOMES).toString(),
            "beacon_state" to cfg.animatedFrame(beaconFramesFor(clan))
        )
    }

    private fun beaconPlaceholders(player: Player, clan: Clan): Map<String, String> {
        val cfg = clanService.plugin.configService
        if (clan.level >= ClanLevels.MAX_LEVEL) {
            return mapOf(
                "beacon_title" to "&#FFD700✦ Абсолютное Величие",
                "beacon_state" to "&#FFD700✦ Максимальный уровень достигнут",
                "beacon_action" to "&#FFD700➥ &fВы — легенды этого сервера.",
                "clan_money" to clan.bankBalance.toString(),
                "clan_money_color" to "&#FFD700",
                "clan_mmr" to clan.mmr.toString(),
                "clan_mmr_color" to "&#FFD700",
                "clan_quests" to serviceQuestCount(clan).toString(),
                "clan_quests_color" to "&#FFD700",
                "clan_required_money" to "0",
                "clan_required_mmr" to "0",
                "clan_required_quests" to "0"
            )
        }
        val next = ClanLevels.getNext(clan.level)!!
        val hasMoney = clan.bankBalance >= next.costMoney
        val hasMMR = clan.mmr >= next.requiredMmr
        val completedQuests = serviceQuestCount(clan)
        val questsEnabled = cfg.quests.enabled
        val hasQuests = !questsEnabled || completedQuests >= next.requiredQuests
        val ready = hasMoney && hasMMR && hasQuests
        val state = cfg.animatedFrame(beaconFramesFor(clan))

        return mapOf(
            "beacon_title" to (if (ready) "&#FFD700✦ Провести Ритуал Возвышения" else "&#FC7D37✦ Ритуал Возвышения"),
            "beacon_state" to state,
            "beacon_action" to (if (ready) {
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы повысить клан до &6${next.level} &fуровня!"
            } else {
                "&c➥ &fНедостаточно ресурсов для прокачки"
            }),
            "clan_money" to clan.bankBalance.toString(),
            "clan_money_color" to (if (hasMoney) "&#5EFD7D" else "&#FC3737"),
            "clan_mmr" to clan.mmr.toString(),
            "clan_mmr_color" to (if (hasMMR) "&#5EFD7D" else "&#FC3737"),
            "clan_quests" to completedQuests.toString(),
            "clan_quests_color" to (if (hasQuests) "&#5EFD7D" else "&#FC3737"),
            "clan_required_money" to next.costMoney.toString(),
            "clan_required_mmr" to next.requiredMmr.toString(),
            "clan_required_quests" to (if (questsEnabled) next.requiredQuests else 0).toString()
        )
    }

    private fun levelPlaceholders(levelData: ClanLevelData, clan: Clan): Map<String, String> {
        val unlocked = clan.level >= levelData.level
        val isNext = clan.level + 1 == levelData.level
        val stateText = when {
            unlocked -> "&#5EFD7D[Разблокировано] &7— клан уже достиг этого уровня"
            isNext -> "&#FF8702[Доступно] &7— выполните условия и откройте ритуал"
            else -> "&#FC3737[Закрыто] &7— сначала прокачайте предыдущий уровень"
        }
        return mapOf(
            "level" to levelData.level.toString(),
            "level_title" to levelData.title,
            "level_state" to stateText,
            "level_max_members" to levelData.maxMembers.toString(),
            "level_chest_rows" to levelData.chestRows.toString(),
            "level_perk" to levelData.unlockedPerk,
            "level_cost" to levelData.costMoney.toString(),
            "level_required_mmr" to levelData.requiredMmr.toString(),
            "level_required_quests" to (if (clanService.plugin.configService.quests.enabled) levelData.requiredQuests else 0).toString()
        )
    }

    private fun beaconFramesFor(clan: Clan): List<String> {
        if (clan.level >= ClanLevels.MAX_LEVEL) return emptyList()
        val cfg = clanService.plugin.configService
        val next = ClanLevels.getNext(clan.level) ?: return emptyList()
        val hasMoney = clan.bankBalance >= next.costMoney
        val hasMMR = clan.mmr >= next.requiredMmr
        val hasQuests = !cfg.quests.enabled || serviceQuestCount(clan) >= next.requiredQuests
        val key = if (hasMoney && hasMMR && hasQuests) AnimationKey.UPGRADE_READY else AnimationKey.UPGRADE_BUSY
        return cfg.animationFrames(key)
    }

    private fun renderConfigItem(
        builder: ItemBuilder,
        player: Player,
        itemCfg: GuiItemConfig,
        placeholders: Map<String, String>
    ) {
        builder.name(clanService.plugin.configService.formatMessage(player, itemCfg.name, placeholders))
        builder.lore(itemCfg.lore.map { line -> clanService.plugin.configService.formatMessage(player, line, placeholders) })
        builder.glow(itemCfg.glow)
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrDefault(fallback)

    private fun serviceQuestCount(clan: Clan): Int = clanService.plugin.clanQuestService.completedQuestCount(clan)

    private companion object {
        const val PER_LEVEL_MEMBERS = 5
        const val PER_LEVEL_CHEST_ROWS = 2
        const val PER_LEVEL_HOMES = 5
    }
}

/** Immutable snapshot of requirements and rewards for a single clan level. */
data class ClanLevelData(
    val level: Int,
    val costMoney: Double,
    val requiredMmr: Int,
    val requiredQuests: Int,
    val maxMembers: Int,
    val chestRows: Int,
    val unlockedPerk: String,
    val icon: Material,
    val title: String = unlockedPerk
)

/** Static registry of all clan level definitions. */
object ClanLevels {
    val MAX_LEVEL = 5

    val LEVELS = mapOf(
        1 to ClanLevelData(1, 0.0, 0, 0, 10, 1, "Создание клана", Material.COAL, "Создание клана"),
        2 to ClanLevelData(2, 50000.0, 1200, 5, 15, 3, "Доступ к расширению сундука", Material.IRON_INGOT, "Казнохранилище"),
        3 to ClanLevelData(3, 150000.0, 1800, 15, 20, 4, "Символ клана над головой", Material.GOLD_INGOT, "Герб клана"),
        4 to ClanLevelData(4, 500000.0, 2500, 35, 25, 5, "Вечные баффы клана", Material.DIAMOND, "Вечные баффы"),
        5 to ClanLevelData(5, 1500000.0, 4000, 75, 30, 6, "Кастомные титулы и частицы", Material.NETHER_STAR, "Легенда сервера")
    )

    fun get(level: Int): ClanLevelData = LEVELS[level] ?: LEVELS[1]!!

    fun getNext(currentLevel: Int): ClanLevelData? = LEVELS[currentLevel + 1]
}
