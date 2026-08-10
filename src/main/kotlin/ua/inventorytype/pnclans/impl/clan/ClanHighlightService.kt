package ua.inventorytype.pnclans.impl.clan

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.player.Equipment
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanHighlightColor
import ua.inventorytype.pnclans.api.clan.ClanHighlightType
import ua.inventorytype.pnclans.api.event.ClanCreatedEvent
import ua.inventorytype.pnclans.api.event.ClanDisbandedEvent
import ua.inventorytype.pnclans.api.event.ClanSavedEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-viewer clanmate highlight with two visual styles:
 *
 * - [ClanHighlightType.ARMOR]: virtual dyed leather armor (server inventory is never changed).
 * - [ClanHighlightType.GLOW]: team-colored glow outline.
 *
 * Only the selected viewer receives the packets for their clanmates.
 */
class ClanHighlightService(private val plugin: BukkitPlugin) : Listener {

    private data class ViewerTargets(
        val clanId: String,
        val type: ClanHighlightType,
        val color: ClanHighlightColor,
        val targets: Set<UUID>
    )

    private var packetListenerRegistered = false
    private val viewerTargets = ConcurrentHashMap<UUID, ViewerTargets>()
    private val entityIds = ConcurrentHashMap<Int, UUID>()
    private val pendingClanSyncs = ConcurrentHashMap<String, Clan>()

    private val packetListener = object : PacketListenerAbstract(PacketListenerPriority.NORMAL) {
        override fun onPacketSend(event: PacketSendEvent) {
            try {
                when (event.packetType) {
                    PacketType.Play.Server.ENTITY_EQUIPMENT -> rewriteEquipment(event)
                    PacketType.Play.Server.ENTITY_METADATA -> rewriteMetadata(event)
                }
            } catch (_: Throwable) {
                // A cosmetic feature must never break the pipeline.
            }
        }
    }

    fun syncAll() {
        if (!PacketEvents.getAPI().isInitialized) return
        registerPacketListener()

        entityIds.clear()
        viewerTargets.clear()
        Bukkit.getOnlinePlayers().forEach(::trackPlayer)
        plugin.clanService.getAllClans().forEach(::syncClan)
    }

    fun syncPlayer(player: Player) {
        trackPlayer(player)
        val clan = plugin.clanService.getClanUser(player) ?: return
        syncClan(clan)
        resyncClanLater(clan)
    }

    fun syncClan(clan: Clan) {
        if (!PacketEvents.getAPI().isInitialized) return

        val onlineMembers = clan.users.mapNotNull { Bukkit.getPlayer(it.uuid) }
        val active = isHighlightActive(clan)
        val type = clan.highlightType
        onlineMembers.forEach(::trackPlayer)

        onlineMembers.forEach { viewer ->
            try {
                val previous = viewerTargets[viewer.uniqueId]
                val previousTargets = previous?.targets.orEmpty()
                val currentTargets = onlineMembers
                    .asSequence()
                    .map { it.uniqueId }
                    .filter { it != viewer.uniqueId }
                    .toSet()

                if (active) {
                    viewerTargets[viewer.uniqueId] = ViewerTargets(clan.id, type, clan.highlightColor, currentTargets)
                } else {
                    viewerTargets.remove(viewer.uniqueId)
                }

                (previousTargets + currentTargets).forEach { targetUuid ->
                    val target = Bukkit.getPlayer(targetUuid) ?: return@forEach
                    if (active && targetUuid in currentTargets) {
                        applyHighlight(viewer, target, type, clan.highlightColor)
                    } else {
                        restoreHighlight(viewer, target, previous?.type)
                    }
                }
            } catch (throwable: Throwable) {
                plugin.logger.log(
                    java.util.logging.Level.SEVERE,
                    "[pnClans] Ошибка syncClan (клана «${clan.name}», игрок ${viewer.name})",
                    throwable
                )
            }
        }
    }

    fun clearClan(clan: Clan) {
        viewerTargets.entries
            .filter { it.value.clanId == clan.id }
            .forEach { (viewerUuid, state) ->
                viewerTargets.remove(viewerUuid)
                val viewer = Bukkit.getPlayer(viewerUuid) ?: return@forEach
                state.targets.forEach { targetUuid ->
                    Bukkit.getPlayer(targetUuid)?.let { target ->
                        restoreHighlight(viewer, target, state.type)
                    }
                }
            }
    }

    /** Restores visual state for all observers before a member leaves the clan. */
    fun removeMember(clan: Clan, memberUuid: UUID) {
        val member = Bukkit.getPlayer(memberUuid)
        val memberState = viewerTargets.remove(memberUuid)

        if (member != null && memberState?.clanId == clan.id) {
            memberState.targets.forEach { targetUuid ->
                Bukkit.getPlayer(targetUuid)?.let { target ->
                    restoreHighlight(member, target, memberState.type)
                }
            }
        }

        viewerTargets.entries
            .filter { it.value.clanId == clan.id && memberUuid in it.value.targets }
            .forEach { (viewerUuid, state) ->
                viewerTargets[viewerUuid] = state.copy(targets = state.targets - memberUuid)
                if (member != null) {
                    Bukkit.getPlayer(viewerUuid)?.let { viewer ->
                        restoreHighlight(viewer, member, state.type)
                    }
                }
            }
    }

    fun forgetPlayer(player: Player) {
        entityIds.remove(player.entityId)
        viewerTargets.remove(player.uniqueId)
        viewerTargets.entries.forEach { (viewerUuid, state) ->
            if (player.uniqueId in state.targets) {
                viewerTargets[viewerUuid] = state.copy(targets = state.targets - player.uniqueId)
            }
        }
    }

    fun resyncClanLater(clan: Clan) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.isEnabled) syncClan(clan)
        }, 1L)
    }

    /**
     * Visual diagnostic for PacketEvents: sends dark-purple virtual leather armor
     * for [target] to [viewer], then restores the real armor after [seconds].
     */
    fun testHighlight(viewer: Player, target: Player, seconds: Long = 10) {
        if (!PacketEvents.getAPI().isInitialized) {
            viewer.sendMessage("§c[pnClans] PacketEvents не инициализирован (isInitialized = false).")
            return
        }
        sendArmor(viewer, target, coloredLeatherArmor(Color.fromRGB(170, 0, 170)))
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (viewer.isOnline && target.isOnline) sendDefaultArmor(viewer, target)
        }, seconds * 20)
    }

    fun shutdown() {
        if (packetListenerRegistered && PacketEvents.getAPI().isInitialized) {
            PacketEvents.getAPI().eventManager.unregisterListener(packetListener)
            packetListenerRegistered = false
        }
        viewerTargets.clear()
        entityIds.clear()
        pendingClanSyncs.clear()
    }

    @EventHandler
    fun onClanSaved(event: ClanSavedEvent) {
        if (pendingClanSyncs.putIfAbsent(event.clan.id, event.clan) == null) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val clan = pendingClanSyncs.remove(event.clan.id) ?: return@Runnable
                if (plugin.isEnabled) syncClan(clan)
            })
        }
    }

    @EventHandler
    fun onClanCreated(event: ClanCreatedEvent) {
        syncClan(event.clan)
    }

    @EventHandler
    fun onClanDisbanded(event: ClanDisbandedEvent) {
        clearClan(event.clan)
    }

    private fun isHighlightActive(clan: Clan): Boolean {
        return clan.highlightEnabled
    }

    private fun registerPacketListener() {
        if (packetListenerRegistered) return
        PacketEvents.getAPI().eventManager.registerListener(packetListener)
        packetListenerRegistered = true
    }

    private fun trackPlayer(player: Player) {
        entityIds[player.entityId] = player.uniqueId
    }

    // --- packet rewriting -------------------------------------------------

    private fun rewriteEquipment(event: PacketSendEvent) {
        val viewerUuid = event.user.getUUID() ?: return
        val state = viewerTargets[viewerUuid] ?: return
        if (state.type != ClanHighlightType.ARMOR) return
        val wrapper = WrapperPlayServerEntityEquipment(event)
        val targetUuid = entityIds[wrapper.entityId] ?: return
        if (targetUuid !in state.targets) return
        wrapper.equipment = coloredLeatherArmor(state.color)
    }

    private fun rewriteMetadata(event: PacketSendEvent) {
        val viewerUuid = event.user.getUUID() ?: return
        val state = viewerTargets[viewerUuid] ?: return
        if (state.type != ClanHighlightType.GLOW) return
        val wrapper = WrapperPlayServerEntityMetadata(event)
        val targetUuid = entityIds[wrapper.entityId] ?: return
        if (targetUuid !in state.targets) return
        wrapper.entityMetadata = withGlowFlag(wrapper.entityMetadata)
    }

    private fun withGlowFlag(metadata: List<EntityData<*>>): List<EntityData<*>> {
        val list = metadata.toMutableList()
        val flagsIndex = list.indexOfFirst { it.index == 0 }
        val current = if (flagsIndex >= 0) (list[flagsIndex].value as? Byte)?.toInt() ?: 0 else 0
        val flags = (current or 0x40).toByte()
        if (flagsIndex >= 0) {
            list[flagsIndex] = EntityData(0, EntityDataTypes.BYTE, flags)
        } else {
            list.add(EntityData(0, EntityDataTypes.BYTE, flags))
        }
        return list
    }

    // --- highlight application --------------------------------------------

    private fun applyHighlight(viewer: Player, target: Player, type: ClanHighlightType, color: ClanHighlightColor) {
        when (type) {
            ClanHighlightType.ARMOR -> sendArmor(viewer, target, coloredLeatherArmor(color))
            ClanHighlightType.GLOW -> sendGlow(viewer, target, color)
        }
    }

    private fun restoreHighlight(viewer: Player, target: Player, previousType: ClanHighlightType?) {
        if (previousType == ClanHighlightType.GLOW) {
            clearGlow(viewer, target)
        } else {
            sendDefaultArmor(viewer, target)
        }
    }

    private fun sendGlow(viewer: Player, target: Player, color: ClanHighlightColor) {
        PacketEvents.getAPI().playerManager.sendPacket(
            viewer,
            WrapperPlayServerTeams(
                glowTeamName(target.uniqueId),
                WrapperPlayServerTeams.TeamMode.CREATE,
                WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(""),
                    Component.text(""),
                    Component.text(""),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                    WrapperPlayServerTeams.CollisionRule.ALWAYS,
                    color.chatFormatting,
                    WrapperPlayServerTeams.OptionData.NONE
                ),
                listOf(target.name)
            )
        )

        try {
            val metadata = SpigotConversionUtil.getEntityMetadata(target).toMutableList()
            PacketEvents.getAPI().playerManager.sendPacket(
                viewer,
                WrapperPlayServerEntityMetadata(target.entityId, setGlowFlag(metadata, true))
            )
        } catch (throwable: Throwable) {
            plugin.logger.log(
                java.util.logging.Level.SEVERE,
                "[pnClans] Не удалось получить метаданные для подсветки ${target.name}",
                throwable
            )
        }
    }

    private fun clearGlow(viewer: Player, target: Player) {
        PacketEvents.getAPI().playerManager.sendPacket(
            viewer,
            WrapperPlayServerTeams(
                glowTeamName(target.uniqueId),
                WrapperPlayServerTeams.TeamMode.REMOVE,
                null as WrapperPlayServerTeams.ScoreBoardTeamInfo?,
                emptyList<String>()
            )
        )

        try {
            val metadata = SpigotConversionUtil.getEntityMetadata(target).toMutableList()
            PacketEvents.getAPI().playerManager.sendPacket(
                viewer,
                WrapperPlayServerEntityMetadata(target.entityId, setGlowFlag(metadata, false))
            )
        } catch (throwable: Throwable) {
            plugin.logger.log(
                java.util.logging.Level.SEVERE,
                "[pnClans] Не удалось очистить подсветку ${target.name}",
                throwable
            )
        }
    }

    private fun setGlowFlag(metadata: MutableList<EntityData<*>>, glowing: Boolean): List<EntityData<*>> {
        val flagsIndex = metadata.indexOfFirst { it.index == 0 }
        val currentFlags = if (flagsIndex >= 0) {
            (metadata[flagsIndex].value as? Byte)?.toInt() ?: 0
        } else {
            0
        }
        val flags = if (glowing) {
            (currentFlags or 0x40).toByte()
        } else {
            (currentFlags and 0xBF).toByte()
        }

        return if (flagsIndex >= 0) {
            metadata[flagsIndex] = EntityData(0, EntityDataTypes.BYTE, flags)
            metadata
        } else {
            metadata + EntityData(0, EntityDataTypes.BYTE, flags)
        }
    }

    private fun glowTeamName(uuid: UUID): String = "pn_${uuid.toString().replace("-", "").take(12)}"

    // --- armor packets ----------------------------------------------------

    private fun sendDefaultArmor(viewer: Player, target: Player) {
        sendArmor(viewer, target, actualArmor(target))
    }

    private fun sendArmor(viewer: Player, target: Player, armor: List<Equipment>) {
        PacketEvents.getAPI().playerManager.sendPacket(
            viewer,
            WrapperPlayServerEntityEquipment(target.entityId, armor)
        )
    }

    private fun coloredLeatherArmor(color: ClanHighlightColor): List<Equipment> =
        coloredLeatherArmor(color.bukkitColor)

    private fun coloredLeatherArmor(color: Color): List<Equipment> {
        return listOf(
            Equipment(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(dyedLeather(Material.LEATHER_HELMET, color))),
            Equipment(EquipmentSlot.CHEST_PLATE, SpigotConversionUtil.fromBukkitItemStack(dyedLeather(Material.LEATHER_CHESTPLATE, color))),
            Equipment(EquipmentSlot.LEGGINGS, SpigotConversionUtil.fromBukkitItemStack(dyedLeather(Material.LEATHER_LEGGINGS, color))),
            Equipment(EquipmentSlot.BOOTS, SpigotConversionUtil.fromBukkitItemStack(dyedLeather(Material.LEATHER_BOOTS, color)))
        )
    }

    private fun actualArmor(target: Player): List<Equipment> {
        val inventory = target.inventory
        return listOf(
            Equipment(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(inventory.helmet ?: ItemStack(Material.AIR))),
            Equipment(EquipmentSlot.CHEST_PLATE, SpigotConversionUtil.fromBukkitItemStack(inventory.chestplate ?: ItemStack(Material.AIR))),
            Equipment(EquipmentSlot.LEGGINGS, SpigotConversionUtil.fromBukkitItemStack(inventory.leggings ?: ItemStack(Material.AIR))),
            Equipment(EquipmentSlot.BOOTS, SpigotConversionUtil.fromBukkitItemStack(inventory.boots ?: ItemStack(Material.AIR)))
        )
    }

    private fun dyedLeather(material: Material, color: Color): ItemStack {
        return ItemStack(material).apply {
            val meta = itemMeta as LeatherArmorMeta
            meta.setColor(color)
            itemMeta = meta
        }
    }
}

private val ClanHighlightColor.bukkitColor: Color
    get() = when (this) {
        ClanHighlightColor.AQUA -> Color.fromRGB(85, 255, 255)
        ClanHighlightColor.BLUE -> Color.fromRGB(85, 85, 255)
        ClanHighlightColor.DARK_AQUA -> Color.fromRGB(0, 170, 170)
        ClanHighlightColor.GREEN -> Color.fromRGB(85, 255, 85)
        ClanHighlightColor.RED -> Color.fromRGB(255, 85, 85)
        ClanHighlightColor.GOLD -> Color.fromRGB(255, 170, 0)
        ClanHighlightColor.YELLOW -> Color.fromRGB(255, 255, 85)
        ClanHighlightColor.LIGHT_PURPLE -> Color.fromRGB(255, 85, 255)
        ClanHighlightColor.WHITE -> Color.WHITE
    }

private val ClanHighlightColor.chatFormatting: NamedTextColor
    get() = when (this) {
        ClanHighlightColor.AQUA -> NamedTextColor.AQUA
        ClanHighlightColor.BLUE -> NamedTextColor.BLUE
        ClanHighlightColor.DARK_AQUA -> NamedTextColor.DARK_AQUA
        ClanHighlightColor.GREEN -> NamedTextColor.GREEN
        ClanHighlightColor.RED -> NamedTextColor.RED
        ClanHighlightColor.GOLD -> NamedTextColor.GOLD
        ClanHighlightColor.YELLOW -> NamedTextColor.YELLOW
        ClanHighlightColor.LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE
        ClanHighlightColor.WHITE -> NamedTextColor.WHITE
    }
