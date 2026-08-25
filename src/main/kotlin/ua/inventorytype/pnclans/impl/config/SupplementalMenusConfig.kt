package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ua.inventorytype.pnclans.BukkitPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level

/** Additional legacy menus being migrated to the same menus.yml without rewriting the large MenusConfig model. */
@Serializable
data class SupplementalMenusConfig(
    val noClanMenu: GuiMenuConfig = defaultNoClanMenu(),
    val treasuryHistoryMenu: TreasuryHistoryMenuConfig = TreasuryHistoryMenuConfig()
)

@Serializable
data class TreasuryHistoryMenuConfig(
    val title: String = "&#FC7D37« История Казны » &7({page}/{pages})",
    val rows: Int = 6,
    val entrySlots: List<Int> = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    ),
    val dateFormat: String = "dd.MM.yyyy",
    val timeFormat: String = "HH:mm:ss",
    val items: Map<String, GuiItemConfig> = mapOf(
        "depositEntry" to GuiItemConfig(
            material = "EMERALD",
            name = "&#5EFD7DПополнение казны",
            lore = listOf(
                "",
                "&#9EFC65 «Детали операции»",
                " &7- &fТип: &#5EFD7D{operation}",
                " &7- &fИнициатор: &e{player}",
                "",
                "&#9EFC65 «Сумма»",
                " &7- &fОперация: &#5EFD7D{signed_amount} ⛁",
                "",
                "&#5EA9FD «Время операции»",
                " &7- &fДата: &b{date}",
                " &7- &fВремя: &b{time}"
            )
        ),
        "withdrawEntry" to GuiItemConfig(
            material = "REDSTONE",
            name = "&#FC3737Снятие из казны",
            lore = listOf(
                "",
                "&#9EFC65 «Детали операции»",
                " &7- &fТип: &#FC3737{operation}",
                " &7- &fИнициатор: &e{player}",
                "",
                "&#9EFC65 «Сумма»",
                " &7- &fОперация: &#FC3737{signed_amount} ⛁",
                "",
                "&#5EA9FD «Время операции»",
                " &7- &fДата: &b{date}",
                " &7- &fВремя: &b{time}"
            )
        ),
        "upgradeEntry" to GuiItemConfig(
            material = "NETHER_STAR",
            name = "&#FC65DFОплата улучшения",
            lore = listOf(
                "",
                "&#9EFC65 «Детали операции»",
                " &7- &fТип: &#FC65DF{operation}",
                " &7- &fИнициатор: &e{player}",
                "",
                "&#9EFC65 «Сумма»",
                " &7- &fОперация: &#FC65DF{signed_amount} ⛁",
                "",
                "&#5EA9FD «Время операции»",
                " &7- &fДата: &b{date}",
                " &7- &fВремя: &b{time}"
            )
        ),
        "previous" to GuiItemConfig(
            slot = 48,
            material = "ARROW",
            name = "&#5EFD7D← Предыдущая страница",
            lore = listOf("", "&#9EFC65 «Навигация»", " &7- &fПерейти на страницу &e{target_page} &7/ &f{pages}", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
            glow = true
        ),
        "previousDisabled" to GuiItemConfig(slot = 48, material = "BLACK_STAINED_GLASS_PANE", name = " "),
        "back" to GuiItemConfig(
            slot = 49,
            material = "OAK_DOOR",
            name = "&#FC3737⏎ Вернуться в банк",
            lore = listOf("", "&#FC65DF «Переход»", " &7- &fОткрывает главное меню банка.", "", "&#FF8702➥ &fНажмите &eЛКМ &fчтобы вернуться")
        ),
        "next" to GuiItemConfig(
            slot = 50,
            material = "ARROW",
            name = "&#5EFD7DСледующая страница →",
            lore = listOf("", "&#9EFC65 «Навигация»", " &7- &fПерейти на страницу &e{target_page} &7/ &f{pages}", "", "&#FF8702➥ &fНажмите &eЛКМ &fдля перехода"),
            glow = true
        ),
        "nextDisabled" to GuiItemConfig(slot = 50, material = "BLACK_STAINED_GLASS_PANE", name = " ")
    )
)

private fun defaultNoClanMenu(): GuiMenuConfig = GuiMenuConfig(
    title = "&#FC7D37« Кланы »",
    rows = 5,
    items = mapOf(
        "info" to GuiItemConfig(
            slot = 22,
            material = "BEACON",
            name = "&#FC7D37✦ Путь к величию",
            lore = listOf(
                "",
                "&#9EFC65 «Добро пожаловать»",
                " &7- &fСейчас вы не состоите в клане.",
                " &7- &fОснуйте свой или примите приглашение от другого лидера.",
                "",
                "&#5EA9FD «Ваше будущее»",
                " &7- &fРазвивайте клан, выполняйте задания",
                " &7- &fи поднимайтесь в рейтинге.",
                "",
                "&#FF8702➥ &fВыберите свой путь ниже"
            ),
            glow = true
        ),
        "create" to GuiItemConfig(
            slot = 31,
            material = "EMERALD",
            name = "&#5EFD7D✚ Основать свой клан",
            lore = listOf(
                "",
                "&#9EFC65 «Условия создания»",
                " &7- &fСтоимость: &e{cost} ⛁",
                " &7- &fВаша роль: &#5EFD7DЛидер клана",
                "",
                "&#FC65DF «После основания»",
                " &7- &fПриглашайте игроков и распределяйте роли.",
                " &7- &fРазвивайте клан и покоряйте рейтинг!",
                "",
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы ввести название"
            ),
            glow = true
        ),
        "top" to GuiItemConfig(
            slot = 29,
            material = "GOLDEN_HELMET",
            name = "&#FC65DF♛ Топ кланов",
            lore = listOf(
                "",
                "&#9EFC65 «Рейтинг сервера»",
                " &7- &fУзнайте, кто удерживает вершину",
                " &7- &fи доминирует среди кланов.",
                "",
                "&#5EA9FD «Показатели»",
                " &7- &fПозиция, уровень, очки и MMR.",
                "",
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть рейтинг"
            )
        ),
        "help" to GuiItemConfig(
            slot = 33,
            material = "BOOK",
            name = "&#5EA9FD❖ Путеводитель по кланам",
            lore = listOf(
                "",
                "&#9EFC65 «Справочник»",
                " &7- &fВсё о развитии и эволюции клана.",
                " &7- &fПодсказки для быстрого старта.",
                "",
                "&#FC65DF «Что внутри?»",
                " &7- &fУровни, привилегии, очки, цели и награды.",
                "",
                "&#FF8702➥ &fНажмите, &eЛКМ &fчтобы открыть справочник"
            )
        )
    )
)

/** Loads only supplemental sections from menus.yml and appends missing sections without touching existing menus. */
internal object SupplementalMenusLoader {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            polymorphismStyle = PolymorphismStyle.Tag
        )
    )

    fun loadAndBackfill(plugin: BukkitPlugin): SupplementalMenusConfig {
        val file = File(plugin.dataFolder, "menus.yml")
        val raw = file.takeIf(File::exists)?.readText().orEmpty()
        val loaded = runCatching { yaml.decodeFromString(SupplementalMenusConfig.serializer(), raw) }
            .onFailure { error -> plugin.logger.log(Level.WARNING, "[pnClans] Failed to load supplemental menus from menus.yml; defaults will be used.", error) }
            .getOrElse { SupplementalMenusConfig() }

        if (file.exists()) {
            appendMissingSections(plugin, file, raw, yaml.encodeToString(SupplementalMenusConfig.serializer(), loaded))
        }
        return loaded
    }

    private fun appendMissingSections(plugin: BukkitPlugin, file: File, existing: String, source: String) {
        val keys = listOf("noClanMenu", "treasuryHistoryMenu")
        val sourceLines = source.lines()
        val blocks = rootBlocks(sourceLines)
        val missing = keys.mapNotNull { key ->
            if (Regex("(?m)^${Regex.escape(key)}\\s*:").containsMatchIn(existing)) null else blocks[key]
        }
        if (missing.isEmpty()) return

        val merged = buildString {
            append(existing.trimEnd())
            if (isNotEmpty()) append("\n\n")
            append(missing.joinToString("\n\n") { it.joinToString("\n") })
            append('\n')
        }
        val temp = File(file.parentFile, "${file.name}.supplemental.tmp")
        runCatching {
            temp.writeText(merged)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { error ->
            temp.delete()
            plugin.logger.log(Level.WARNING, "[pnClans] Failed to append supplemental menus to menus.yml.", error)
        }
    }

    private fun rootBlocks(lines: List<String>): Map<String, List<String>> {
        val starts = lines.indices.mapNotNull { index ->
            val line = lines[index]
            if (line.isBlank() || line.startsWith(' ') || line.trimStart().startsWith('#')) return@mapNotNull null
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            index to line.substring(0, colon).trim().trim('"', '\'')
        }
        return starts.mapIndexed { position, (start, key) ->
            val end = starts.getOrNull(position + 1)?.first ?: lines.size
            key to lines.subList(start, end).dropLastWhile(String::isBlank)
        }.toMap()
    }
}
