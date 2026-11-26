package ua.inventorytype.pnclans.impl.integration

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValue
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine
import ua.inventorytype.pnclans.impl.placeholder.ClanPlaceholderValues

/** Ключи кланов с `_` должны отвечать на `%pnclans_<ключ>%`, а короткие `{clan_battle_wins}` работать внутри плагина. */
class PlaceholderAddressesTest {
    private var answers: PublishedAnswers? = null
    private val engine = PlaceholderEngine { throw AssertionError(it) }
    private val keys = listOf("clan", "name", "battle_wins", "battle_losses", "wins", "members")

    init {
        engine.register(object : PlaceholderAdapter {
            override fun getId() = "placeholderapi"
            override fun isAvailable() = true
            override fun render(playerId: UUID?, text: String) = text
            override fun publish(prefix: String, published: PublishedAnswers): AutoCloseable {
                answers = published
                return AutoCloseable { }
            }
        })
        val scope = engine.scope("pnclans/pnclans")
        fun reg(name: String, papi: String?, value: String) =
            scope.value(name, "", papi, PlaceholderValue { value })
        keys.forEach { reg(ClanPlaceholderValues.address(it), "pnclans", it.uppercase()) }
        keys.filter { it != "clan" }.forEach { reg("clan_$it", null, "HIDDEN_${it.uppercase()}") }
        scope.registerPublished("pnclans")
    }

    private fun papi(params: String) = answers!!.answer(UUID.randomUUID(), params, 0)

    @Test
    fun `underscore keys answer to old papi names`() {
        keys.forEach { assertEquals(it.uppercase(), papi(it), it) }
    }

    @Test
    fun `short hidden names with underscore keep working`() {
        val text = engine.render("{clan_battle_wins} {clan_name}").toCompletableFuture().get()
        assertEquals("HIDDEN_BATTLE_WINS HIDDEN_NAME", text)
    }
}
