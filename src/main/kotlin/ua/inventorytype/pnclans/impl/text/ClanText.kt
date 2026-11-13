package ua.inventorytype.pnclans.impl.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType
import org.pnfolder.pnlibrary.api.text.ComponentService

/**
 * Цвета и оформление текста через pnLibrary: `&a`, `§a`, `&#rrggbb`, `#rrggbb`, MiniMessage и JSON в одной строке.
 * Подключается один раз при старте ([connect]), до этого текст не оформляется.
 */
object ClanText {
    private lateinit var components: ComponentService

    fun connect(service: ComponentService) {
        components = service
    }

    /** Оформленный текст для чата, заголовков и предметов. */
    fun component(text: String): Component = components.deserialize(text, ComponentSerializerType.ADAPTIVE)

    /** Название или строка описания предмета: без курсива, который клиент ставит по умолчанию. */
    fun itemLine(text: String): Component = component(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    /** Строка с кодами `§` для старых мест Bukkit, которые принимают только текст. */
    fun legacy(text: String): String =
        if (text.isEmpty()) "" else components.serializer(ComponentSerializerType.LEGACY_SECTION).serialize(component(text))
}
