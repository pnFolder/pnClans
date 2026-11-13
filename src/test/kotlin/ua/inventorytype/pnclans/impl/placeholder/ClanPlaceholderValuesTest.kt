package ua.inventorytype.pnclans.impl.placeholder

import ua.inventorytype.pnclans.api.User
import ua.inventorytype.pnclans.api.clan.Clan
import ua.inventorytype.pnclans.api.clan.ClanRole
import ua.inventorytype.pnclans.impl.config.PlaceholderConfig
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ClanPlaceholderValuesTest {
    private val playerId = UUID.randomUUID()
    private val config = PlaceholderConfig()

    private val values = ClanPlaceholderValues(
        formats = { config },
        roleName = { "Роль ${it.name}" },
        color = { it.replace('&', '§') },
    )

    private fun user(id: UUID): User =
        Proxy.newProxyInstance(User::class.java.classLoader, arrayOf(User::class.java)) { proxy, method, args ->
            when (method.name) {
                "getUuid" -> id
                "hashCode" -> id.hashCode()
                "equals" -> proxy === args[0]
                else -> error(method.name)
            }
        } as User

    private fun clan(): Clan {
        val user = user(playerId)
        val answers = mapOf<String, Any?>(
            "getName" to "Wolves", "getLevel" to 3, "getBankBalance" to 12.5, "getPoints" to 40L, "getMmr" to 1200,
            "getKills" to 7, "getDeaths" to 2, "getBattleWins" to 5, "getBattleLosses" to 1, "getOnlineCount" to 1,
            "getUsers" to listOf(user, user(UUID.randomUUID())).toSet(), "getMember" to user, "getUserRole" to ClanRole.LEADER,
        )
        return Proxy.newProxyInstance(Clan::class.java.classLoader, arrayOf(Clan::class.java)) { _, method, _ ->
            if (method.name in answers) answers[method.name] else error(method.name)
        } as Clan
    }

    @Test
    fun `values of a clan`() {
        val clan = clan()
        val expected = mapOf(
            "name" to "Wolves", "level" to "3", "balance" to "12.5", "bank" to "12.5", "points" to "40", "mmr" to "1200",
            "kills" to "7", "deaths" to "2", "battle_wins" to "5", "wins" to "5", "battle_losses" to "1", "losses" to "1",
            "tag" to "§8[§6Wolves§8]", "role" to "LEADER",
        )
        expected.forEach { (key, value) -> assertEquals(value, values.value(key, clan, playerId, roleTitle = false), key) }
        assertEquals("Роль LEADER", values.value("role", clan, playerId, roleTitle = true))
    }

    @Test
    fun `without a clan`() {
        assertEquals("Нет", values.value("name", null, playerId, false))
        assertEquals("Нет", values.value("clan", null, playerId, false))
        assertEquals("", values.value("tag", null, playerId, false))
        assertEquals("Нет", values.value("role", null, playerId, true))
        assertEquals("0", values.value("level", null, playerId, false))
        assertEquals("0", values.value("balance", null, playerId, false))
        assertEquals("0/0", values.value("members", null, playerId, false))
    }

    @Test
    fun `every key is answered and unknown is empty`() {
        val clan = clan()
        values.keys.forEach { assert(values.value(it, clan, playerId, false).isNotEmpty() || it == "tag") { it } }
        assertEquals("", values.value("unknown", clan, playerId, false))
        assertEquals("1/2", values.value("members", clan, playerId, false))
    }

    @Test
    fun `custom formats still apply`() {
        val custom = ClanPlaceholderValues(
            formats = { PlaceholderConfig(clanFormat = "&7{clan}", noClan = "-", tagFormat = "<{clan}>", noTag = "") },
            roleName = { it.name },
            color = { it.replace('&', '§') },
        )
        assertEquals("§7Wolves", custom.value("name", clan(), playerId, false))
        assertEquals("-", custom.value("name", null, playerId, false))
        assertEquals("<Wolves>", custom.value("tag", clan(), playerId, false))
    }
}
