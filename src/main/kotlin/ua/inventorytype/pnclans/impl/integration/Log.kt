package ua.inventorytype.pnclans.impl.integration

import org.pnfolder.pnlibrary.api.logging.PnLogger
import java.util.logging.Level
import java.util.logging.Logger

/** Единая точка логирования плагина: логгер модуля pnLibrary, а без него (до регистрации и после закрытия) обычный логгер плагина. */
object Log {
    @Volatile private var module: PnLogger? = null
    @Volatile private var fallback: Logger? = null

    /** Запасной логгер плагина; задаётся как можно раньше. */
    @JvmStatic fun fallback(logger: Logger) { fallback = logger }

    /** Подключает логгер модуля после регистрации в pnLibrary. */
    @JvmStatic fun attach(logger: PnLogger) { module = logger }

    /** Отключает логгер модуля при закрытии. */
    @JvmStatic fun detach() { module = null }

    private fun plain(): Logger = fallback ?: Logger.getLogger("pnClans")

    @JvmStatic fun info(message: String) = emit({ it.info(message) }) { it.info(message) }
    @JvmStatic fun success(message: String) = emit({ it.success(message) }) { it.info(message) }
    @JvmStatic fun warning(message: String) = emit({ it.warning(message) }) { it.warning(message) }
    /** У логгера модуля нет предупреждения с исключением: стек уходит через JUL-логгер плагина (библиотека перехватывает его сама). */
    @JvmStatic fun warning(message: String, error: Throwable?) = plain().log(Level.WARNING, message, error)
    @JvmStatic fun error(message: String) = emit({ it.error(message) }) { it.severe(message) }
    @JvmStatic fun error(message: String, error: Throwable?) = emit({ it.error(message, error) }) { it.log(Level.SEVERE, message, error) }

    private inline fun emit(viaModule: (PnLogger) -> Unit, viaPlain: (Logger) -> Unit) {
        val target = module
        if (target != null) {
            try {
                viaModule(target)
                return
            } catch (_: RuntimeException) {
                // модуль уже закрыт — пишем через логгер плагина
            }
        }
        viaPlain(plain())
    }
}
