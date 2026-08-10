package ua.inventorytype.pnclans.impl.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanHighlightColor
import ua.inventorytype.pnclans.api.clan.ClanHighlightType
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.impl.clan.ClanImpl
import ua.inventorytype.pnclans.impl.clan.ClanUser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType
import ua.inventorytype.pnclans.api.clan.ClanPointsTransaction
import ua.inventorytype.pnclans.api.clan.ClanPointsTransactionType
import ua.inventorytype.pnclans.api.clan.ClanPointsSource
import ua.inventorytype.pnclans.api.permission.ClanPerms

@Serializable
data class ClanDataModel(
    val id: String,
    val name: String,
    val level: Int = 1,
    val mmr: Int = 1000,
    val kills: Int = 0,
    val deaths: Int = 0,
    val bankBalance: Double = 0.0,
    val points: Long = 0L,
    val activityPointsDate: String = "",
    val activityPointsAwardedToday: Long = 0L,
    val highlightColor: String = ClanHighlightColor.AQUA.name,
    val highlightEnabled: Boolean? = null,
    val highlightMode: String? = null,
    val highlightType: String = ClanHighlightType.ARMOR.name,
    val members: List<ClanMemberModel> = emptyList(),
    val settings: Map<String, Boolean> = emptyMap(),
    val homes: Map<String, ClanHomeModel> = emptyMap(),
    val treasuryLogs: List<TreasuryLogModel> = emptyList(),
    val pointsLogs: List<ClanPointsLogModel> = emptyList(),
    val rolePermissions: Map<String, Map<String, Boolean>> = emptyMap(),
    val userPermissions: Map<String, Map<String, Boolean>> = emptyMap()
)

@Serializable
data class TreasuryLogModel(
    val type: String,
    val playerName: String,
    val amount: Double,
    val timestamp: Long
)

@Serializable
data class ClanMemberModel(
    val uuid: String,
    val name: String,
    val role: String,
    val kills: Int = 0,
    val deaths: Int = 0,
    val playtimeSeconds: Long = 0L,
    val points: Int = 0
)

@Serializable
data class ClanPointsLogModel(
    val type: String,
    val source: String,
    val amount: Long,
    val balanceAfter: Long,
    val timestamp: Long
)

@Serializable
data class ClanHomeModel(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

class ClanStorage(private val plugin: BukkitPlugin) : IClanStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir: File
        get() = File(plugin.dataFolder, "clans").apply { if (!exists()) mkdirs() }

    private val chestDir: File
        get() = File(plugin.dataFolder, "chests").apply { if (!exists()) mkdirs() }

    override fun saveClan(clan: Clan): Boolean {
        return runCatching {
            val memberModels = clan.users.map { user ->
                ClanMemberModel(
                    uuid = user.uuid.toString(),
                    name = user.playerName,
                    role = clan.getUserRole(user).name,
                    kills = (user as? ClanUser)?.kills ?: 0,
                    deaths = (user as? ClanUser)?.deaths ?: 0,
                    playtimeSeconds = (user as? ClanUser)?.playtimeSeconds ?: 0L,
                    points = (user as? ClanUser)?.points ?: 0
                )
            }

            val settingModels = clan.settings.mapKeys { it.key.key }
            val homeModels = clan.homes.mapValues { (_, loc) ->
                ClanHomeModel(
                    world = loc.world?.name ?: "world",
                    x = loc.x,
                    y = loc.y,
                    z = loc.z,
                    yaw = loc.yaw,
                    pitch = loc.pitch
                )
            }

            val dataModel = ClanDataModel(
                id = clan.id,
                name = clan.name,
                level = clan.level,
                mmr = clan.mmr,
                kills = clan.kills,
                deaths = clan.deaths,
                bankBalance = clan.bankBalance,
                points = clan.points,
                activityPointsDate = clan.activityPointsDate,
                activityPointsAwardedToday = clan.activityPointsAwardedToday,
                highlightColor = clan.highlightColor.name,
                highlightEnabled = clan.highlightEnabled,
                highlightType = clan.highlightType.name,
                members = memberModels,
                settings = settingModels,
                homes = homeModels
                ,treasuryLogs = clan.treasuryLogs.map { TreasuryLogModel(it.type.name, it.playerName, it.amount, it.timestamp) },
                pointsLogs = clan.pointsLogs.map { ClanPointsLogModel(it.type.name, it.source.name, it.amount, it.balanceAfter, it.timestamp) },
                rolePermissions = (clan as? ClanImpl)?.rolePermissions.orEmpty()
                    .mapKeys { it.key.name }
                    .mapValues { (_, values) -> values.associate { it.first.node to it.second } },
                userPermissions = (clan as? ClanImpl)?.userPermissions.orEmpty()
                    .mapKeys { it.key.toString() }
                    .mapValues { (_, values) -> values.associate { it.first.node to it.second } }
            )

            val file = File(storageDir, "${clan.id}.json")
            writeAtomically(file, json.encodeToString(dataModel))
            true
        }.onFailure { ex ->
            plugin.logger.severe("Failed to save clan ${clan.name}: ${ex.message}")
        }.getOrDefault(false)
    }

    override fun loadAllClans(): List<Clan> {
        val loaded = mutableListOf<Clan>()
        val files = storageDir.listFiles { _, name -> name.endsWith(".json") } ?: return loaded

        for (file in files) {
            runCatching {
                val content = file.readText()
                val model = json.decodeFromString<ClanDataModel>(content)

                val members = model.members.mapNotNull { m ->
                    val uuid = runCatching { UUID.fromString(m.uuid) }.getOrNull() ?: return@mapNotNull null
                    val role = runCatching { ClanRole.valueOf(m.role) }.getOrDefault(ClanRole.MEMBER)
                    ClanUser(
                        uuid = uuid,
                        playerName = m.name,
                        kills = m.kills,
                        deaths = m.deaths,
                        playtimeSeconds = m.playtimeSeconds,
                        points = m.points
                    ) to role
                }.toSet()

                val clan = ClanImpl(
                    id = model.id,
                    name = model.name,
                    initialUsers = members
                ).apply {
                    level = model.level
                    mmr = model.mmr
                    kills = model.kills
                    deaths = model.deaths
                    bankBalance = model.bankBalance
                    points = model.points
                    activityPointsDate = model.activityPointsDate
                    activityPointsAwardedToday = model.activityPointsAwardedToday
                    highlightColor = ClanHighlightColor.fromKey(model.highlightColor) ?: ClanHighlightColor.AQUA
                    highlightEnabled = model.highlightEnabled ?: (model.highlightMode != null && !model.highlightMode.equals("OFF", true))
                    highlightType = ClanHighlightType.fromKey(model.highlightType) ?: ClanHighlightType.ARMOR
                    model.treasuryLogs.forEach { entry ->
                        runCatching { addTreasuryLog(TreasuryTransaction(TreasuryTransactionType.valueOf(entry.type), entry.playerName, entry.amount, entry.timestamp)) }
                    }
                    model.pointsLogs.forEach { entry ->
                        runCatching { addPointsLog(ClanPointsTransaction(ClanPointsTransactionType.valueOf(entry.type), ClanPointsSource.valueOf(entry.source), entry.amount, entry.balanceAfter, entry.timestamp)) }
                    }
                    restorePermissionOverrides(this, model.rolePermissions, model.userPermissions)
                }

                model.settings.forEach { (key, valBool) ->
                    ClanSetting.fromKey(key)?.let { setting ->
                        clan.setSetting(setting, valBool)
                    }
                }

                model.homes.forEach { (name, h) ->
                    val world = Bukkit.getWorld(h.world) ?: Bukkit.getWorlds().firstOrNull()
                    if (world != null) {
                        clan.setHome(name, Location(world, h.x, h.y, h.z, h.yaw, h.pitch))
                    }
                }

                loaded.add(clan)
            }.onFailure { ex ->
                plugin.logger.warning("Failed to load clan file ${file.name}: ${ex.message}")
            }
        }

        return loaded
    }

    override fun deleteClan(clan: Clan) {
        val file = File(storageDir, "${clan.id}.json")
        if (file.exists()) file.delete()
        val chestFile = File(chestDir, "${clan.id}.dat")
        if (chestFile.exists()) chestFile.delete()
    }

    override fun saveChest(clanId: String, items: Array<ItemStack?>) {
        runCatching {
            val file = File(chestDir, "$clanId.dat")
            val encoded = ItemStackSerializer.toBase64(items)
            writeAtomically(file, encoded)
        }.onFailure { ex ->
            plugin.logger.severe("Failed to save chest for clan $clanId: ${ex.message}")
        }
    }

    override fun loadChest(clanId: String): Array<ItemStack?> {
        val file = File(chestDir, "$clanId.dat")
        if (!file.exists()) return arrayOfNulls(54)
        return runCatching {
            ItemStackSerializer.fromBase64(file.readText())
        }.getOrDefault(arrayOfNulls(54))
    }
}

private fun writeAtomically(file: File, content: String) {
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeText(content)
    try {
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun restorePermissionOverrides(
    clan: ClanImpl,
    rolePermissions: Map<String, Map<String, Boolean>>,
    userPermissions: Map<String, Map<String, Boolean>>
) {
    rolePermissions.forEach { (roleName, permissions) ->
        val role = runCatching { ClanRole.valueOf(roleName) }.getOrNull() ?: return@forEach
        permissions.forEach { (node, enabled) ->
            ClanPerms.ALL_PERMISSIONS.firstOrNull { it.node == node }
                ?.let { clan.grantRolePermission(role, it to enabled) }
        }
    }
    userPermissions.forEach { (uuidText, permissions) ->
        val user = runCatching { UUID.fromString(uuidText) }.getOrNull()?.let(clan::getMember) ?: return@forEach
        permissions.forEach { (node, enabled) ->
            ClanPerms.ALL_PERMISSIONS.firstOrNull { it.node == node }
                ?.let { clan.grantUserPermission(user, it to enabled) }
        }
    }
}
