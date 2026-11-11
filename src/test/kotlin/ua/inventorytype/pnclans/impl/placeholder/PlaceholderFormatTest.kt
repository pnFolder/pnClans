package ua.inventorytype.pnclans.impl.placeholder

import ua.inventorytype.pnclans.impl.config.PlaceholderConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderFormatTest {
    private val defaults = PlaceholderConfig()

    @Test
    fun `defaults keep the old output`() {
        assertEquals("Wolves", PlaceholderFormat.render(defaults.clanFormat, "Wolves"))
        assertEquals("Нет", PlaceholderFormat.render(defaults.noClan, null))
        assertEquals("§8[§6Wolves§8]", PlaceholderFormat.render(defaults.tagFormat, "Wolves"))
        assertEquals("", PlaceholderFormat.render(defaults.noTag, null))
    }

    @Test
    fun `custom style and hex colours`() {
        assertEquals("§7[§6Wolves§7]", PlaceholderFormat.render("&7[&6{clan}&7]", "Wolves"))
        assertEquals("§x§F§F§0§0§0§0Нет клана", PlaceholderFormat.render("&#FF0000Нет клана", null))
    }

    @Test
    fun `ampersand in a clan name is not a colour`() {
        assertEquals("§6A&cB", PlaceholderFormat.render("&6{clan}", "A&cB"))
    }
}
