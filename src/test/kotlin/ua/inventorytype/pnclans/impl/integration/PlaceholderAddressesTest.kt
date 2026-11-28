package ua.inventorytype.pnclans.impl.integration

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine
import ua.inventorytype.pnclans.impl.config.PlaceholderConfig
import ua.inventorytype.pnclans.impl.placeholder.ClanPlaceholderValues
import ua.inventorytype.pnclans.impl.placeholder.ClanValues

/** Класс `ClanValues` отвечает и на `{name}`, и на `%pnclans_name%`. */
class PlaceholderAddressesTest {
    private var answers: PublishedAnswers? = null
    private val engine = PlaceholderEngine { throw AssertionError(it) }

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
        val values = ClanPlaceholderValues(
            formats = { PlaceholderConfig(clanFormat = "{clan}", noClan = "-", tagFormat = "{clan}", noTag = "") },
            roleName = { it.name },
            color = { it },
        )
        engine.scope("pnclans/pnclans").registerPublished("pnclans", ClanValues(values) { null })
    }

    @Test
    fun `papi name has the group`() {
        assertEquals("-", answers!!.answer(UUID.randomUUID(), "name", 0))
        assertEquals("0/0", answers!!.answer(UUID.randomUUID(), "members", 0))
    }

    @Test
    fun `text address works`() {
        assertEquals("- 0", engine.render("{name} {level}", org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo.of(UUID.randomUUID())).toCompletableFuture().get())
    }
}
