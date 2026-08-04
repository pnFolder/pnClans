package ua.inventorytype.pnclans.impl.util

import java.util.regex.Pattern

object ColorUtil {

    private val HEX_PATTERN: Pattern = Pattern.compile("(?:&#|#|<#)([a-fA-F0-9]{6})>?")

    fun color(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        var formatted = text

        // 1. Парсим HEX (#FFFFFF, &#FFFFFF, <#FFFFFF>)
        val matcher = HEX_PATTERN.matcher(formatted)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val hexCode = matcher.group(1)
            val replacement = buildString {
                append("§x")
                for (ch in hexCode) {
                    append('§').append(ch)
                }
            }
            matcher.appendReplacement(buffer, replacement)
        }
        matcher.appendTail(buffer)
        formatted = buffer.toString()

        // 2. Тупо заменяем & на § без всяких ChatColor
        return formatted.replace('&', '§')
    }

    fun color(lines: List<String>): List<String> {
        return lines.map { color(it) }
    }
}