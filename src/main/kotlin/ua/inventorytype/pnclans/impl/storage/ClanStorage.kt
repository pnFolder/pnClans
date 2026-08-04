package ua.inventorytype.pnclans.impl.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import ua.inventorytype.pnclans.BukkitPlugin
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.api.clan.ClanSetting
import ua.inventorytype.pnclans.impl.clan.ClanImpl
import ua.inventorytype.pnclans.impl.clan.ClanUser
import java.io.File
import java.util.UUID

@Serializable
data class ClanDataModel(
    val id: String,
    val name: String,
    val level: Int = 1,
    val mmr: Int = 1000,
    val kills: Int = 0,
    val deaths: Int = 0,
    val bankBalance: Double = 0.0,
    val members: List<ClanMemberModel> = emptyList(),
    val settings: Map<String, Boolean> = emptyMap(),
    val homes: Map<String, ClanHomeModel> = emptyMap()
)

@Serializable
data class ClanMemberModel(
    val uuid: String,
    val name: String,
    val role: String
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

    override fun saveClan(clan: Clan) {
        runCatching {
            val memberModels = clan.users.map { user ->
                ClanMemberModel(
                    uuid = user.uuid.toString(),
                    name = user.playerName,
                    role = clan.getUserRole(user).name
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
                members = memberModels,
                settings = settingModels,
                homes = homeModels
            )

            val file = File(storageDir, "${clan.id}.json")
            file.writeText(json.encodeToString(dataModel))
        }.onFailure { ex ->
            plugin.logger.severe("Failed to save clan ${clan.name}: ${ex.message}")
        }
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
                    ClanUser(uuid, m.name) to role
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
            file.writeText(encoded)
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
