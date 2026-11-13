package ua.inventorytype.pnclans.impl.placeholder

import ua.inventorytype.pnclans.impl.text.ClanText

/** Строит значение плейсхолдера из шаблона конфига. */
internal object PlaceholderFormat {
    /** Красит шаблон и только потом подставляет название клана, чтобы `&` в названии не стал цветом. */
    fun render(template: String, clanName: String?, color: (String) -> String = ClanText::legacy): String =
        color(template).replace("{clan}", clanName.orEmpty())
}
