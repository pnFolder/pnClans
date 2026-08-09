package ua.inventorytype.pnclans.impl.storage

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
import java.sql.DriverManager
import java.util.UUID
import ua.inventorytype.pnclans.api.clan.TreasuryTransaction
import ua.inventorytype.pnclans.api.clan.TreasuryTransactionType

/**
 * SQLite database storage implementation utilizing JDBC connection pooling and JSON payload fallback.
 */
class SQLiteClanStorage(private val plugin: BukkitPlugin) : IClanStorage {

    private val dbFile = File(plugin.dataFolder, "clans.db")
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    init {
        Class.forName("org.sqlite.JDBC")
        initDb()
    }

    @Volatile
    private var connection: java.sql.Connection? = null

    @Synchronized
    private fun getConnection(): java.sql.Connection {
        var conn = connection
        if (conn == null || conn.isClosed) {
            conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            connection = conn
        }
        return conn
    }

    private fun initDb() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            // Enable WAL mode for high-concurrency performance and zero disk locks
            stmt.execute("PRAGMA journal_mode=WAL;")
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS clans (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    level INTEGER NOT NULL,
                    mmr INTEGER NOT NULL,
                    kills INTEGER NOT NULL,
                    deaths INTEGER NOT NULL,
                    bank REAL NOT NULL,
                    highlight TEXT NOT NULL DEFAULT 'AQUA',
                    highlight_mode TEXT NOT NULL DEFAULT 'ALWAYS',
                    data TEXT NOT NULL
                );
                """.trimIndent()
            )

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chests (
                    clan_id TEXT PRIMARY KEY,
                    items TEXT NOT NULL
                );
                """.trimIndent()
            )

            runCatching {
                val existingCols = mutableSetOf<String>()
                val rs = stmt.executeQuery("PRAGMA table_info(clans)")
                while (rs.next()) {
                    existingCols.add(rs.getString("name").lowercase())
                }
                if (!existingCols.contains("id")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN id TEXT DEFAULT ''")
                if (!existingCols.contains("name")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN name TEXT DEFAULT ''")
                if (!existingCols.contains("level")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN level INTEGER DEFAULT 1")
                if (!existingCols.contains("mmr")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN mmr INTEGER DEFAULT 1000")
                if (!existingCols.contains("kills")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN kills INTEGER DEFAULT 0")
                if (!existingCols.contains("deaths")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN deaths INTEGER DEFAULT 0")
                if (!existingCols.contains("bank")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN bank REAL DEFAULT 0.0")
                if (!existingCols.contains("highlight")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN highlight TEXT DEFAULT 'AQUA'")
                if (!existingCols.contains("highlight_mode")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN highlight_mode TEXT DEFAULT 'ALWAYS'")
                if (!existingCols.contains("data")) stmt.executeUpdate("ALTER TABLE clans ADD COLUMN data TEXT DEFAULT ''")
            }
        }
    }

    override fun saveClan(clan: Clan) {
        runCatching {
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
                    x = loc.x, y = loc.y, z = loc.z, yaw = loc.yaw, pitch = loc.pitch
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
                highlightColor = clan.highlightColor.name,
                highlightEnabled = clan.highlightEnabled,
                highlightType = clan.highlightType.name,
                members = memberModels,
                settings = settingModels,
                homes = homeModels
                ,treasuryLogs = clan.treasuryLogs.map { TreasuryLogModel(it.type.name, it.playerName, it.amount, it.timestamp) }
            )

            val jsonStr = json.encodeToString(dataModel)
            val sql = """
                    REPLACE INTO clans(id, name, level, mmr, kills, deaths, bank, highlight, highlight_mode, data)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()

            getConnection().prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, clan.id)
                pstmt.setString(2, clan.name)
                pstmt.setInt(3, clan.level)
                pstmt.setInt(4, clan.mmr)
                pstmt.setInt(5, clan.kills)
                pstmt.setInt(6, clan.deaths)
                    pstmt.setDouble(7, clan.bankBalance)
                    pstmt.setString(8, clan.highlightColor.name)
                    pstmt.setString(9, if (clan.highlightEnabled) "ON" else "OFF")
                    pstmt.setString(10, jsonStr)
                pstmt.executeUpdate()
            }
        }.onFailure { ex ->
            plugin.logger.severe("SQLite: Failed to save clan ${clan.name}: ${ex.message}")
        }
    }

    override fun loadAllClans(): List<Clan> {
        val loaded = mutableListOf<Clan>()
        runCatching {
            getConnection().createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT data FROM clans")
                while (rs.next()) {
                    val jsonStr = rs.getString("data")
                    val model = json.decodeFromString<ClanDataModel>(jsonStr)

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
                            highlightColor = ClanHighlightColor.fromKey(model.highlightColor) ?: ClanHighlightColor.AQUA
                            highlightEnabled = model.highlightEnabled ?: (model.highlightMode != null && !model.highlightMode.equals("OFF", true))
                            highlightType = ClanHighlightType.fromKey(model.highlightType) ?: ClanHighlightType.ARMOR
                            model.treasuryLogs.forEach { entry ->
                                runCatching { addTreasuryLog(TreasuryTransaction(TreasuryTransactionType.valueOf(entry.type), entry.playerName, entry.amount, entry.timestamp)) }
                            }
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
                }
            }
        }.onFailure { ex ->
            plugin.logger.severe("SQLite: Failed to load clans: ${ex.message}")
        }
        return loaded
    }

    override fun deleteClan(clan: Clan) {
        runCatching {
            val conn = getConnection()
            conn.prepareStatement("DELETE FROM clans WHERE id = ?").use { pstmt ->
                pstmt.setString(1, clan.id)
                pstmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM chests WHERE clan_id = ?").use { pstmt ->
                pstmt.setString(1, clan.id)
                pstmt.executeUpdate()
            }
        }
    }

    override fun saveChest(clanId: String, items: Array<ItemStack?>) {
        runCatching {
            val base64 = ItemStackSerializer.toBase64(items)
            val sql = "REPLACE INTO chests(clan_id, items) VALUES(?, ?);"
            getConnection().prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, clanId)
                pstmt.setString(2, base64)
                pstmt.executeUpdate()
            }
        }.onFailure { ex ->
            plugin.logger.severe("SQLite: Failed to save chest for $clanId: ${ex.message}")
        }
    }

    override fun loadChest(clanId: String): Array<ItemStack?> {
        return runCatching {
            getConnection().prepareStatement("SELECT items FROM chests WHERE clan_id = ?").use { pstmt ->
                pstmt.setString(1, clanId)
                val rs = pstmt.executeQuery()
                if (rs.next()) {
                    val base64 = rs.getString("items")
                    return@runCatching ItemStackSerializer.fromBase64(base64)
                }
            }
            arrayOfNulls<ItemStack>(54)
        }.getOrDefault(arrayOfNulls(54))
    }
}
