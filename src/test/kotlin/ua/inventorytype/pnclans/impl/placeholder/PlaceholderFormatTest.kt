package ua.inventorytype.pnclans.impl.placeholder

import ua.inventorytype.pnclans.impl.config.PlaceholderConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderFormatTest {
    private val defaults = PlaceholderConfig()
    private val color = { text: String -> text.replace('&', '§') }

    private fun render(template: String, clan: String?) = PlaceholderFormat.render(template, clan, color)

    @Test
    fun `defaults keep the old output`() {
        assertEquals("Wolves", render(defaults.clanFormat, "Wolves"))
        assertEquals("Нет", render(defaults.noClan, null))
        assertEquals("§8[§6Wolves§8]", render(defaults.tagFormat, "Wolves"))
        assertEquals("", render(defaults.noTag, null))
    }

    @Test
    fun `custom style`() {
        assertEquals("§7[§6Wolves§7]", render("&7[&6{clan}&7]", "Wolves"))
    }

    @Test
    fun `ampersand in a clan name is not a colour`() {
        assertEquals("§6A&cB", render("&6{clan}", "A&cB"))
    }
}
