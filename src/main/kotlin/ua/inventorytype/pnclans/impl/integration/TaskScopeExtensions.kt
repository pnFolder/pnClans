package ua.inventorytype.pnclans.impl.integration

import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration

/** Запускает [action] в потоке сущности [target] как можно скорее. */
fun TaskScope.entity(target: Any, action: Runnable): TaskHandle = schedule(
    TaskSpec.builder()
        .name("pnclans-entity")
        .execution(TaskExecution.entity(target))
        .action { action.run() }
        .build(),
)

/** Запускает [action] один раз в потоке сущности [target] через [delay]. */
fun TaskScope.laterEntity(target: Any, delay: Duration, action: Runnable): TaskHandle = schedule(
    TaskSpec.builder()
        .name("pnclans-later-entity")
        .execution(TaskExecution.entity(target))
        .delay(delay)
        .action { action.run() }
        .build(),
)

/** Повторяет [action] в потоке сущности [target] с периодом [period] после задержки [delay]. */
fun TaskScope.repeatEntity(target: Any, delay: Duration, period: Duration, action: Runnable): TaskHandle = schedule(
    TaskSpec.builder()
        .name("pnclans-repeat-entity")
        .execution(TaskExecution.entity(target))
        .delay(delay)
        .interval(period)
        .action { action.run() }
        .build(),
)

private fun TaskScope.global(name: String, execution: TaskExecution, delayTicks: Long, periodTicks: Long?, action: Runnable): TaskHandle = schedule(
    TaskSpec.builder()
        .name(name)
        .execution(execution)
        .delayTicks(delayTicks)
        .apply { if (periodTicks != null) intervalTicks(periodTicks) }
        .action { action.run() }
        .build(),
)

/** Запускает [action] в основном потоке на ближайшем тике (аналог runTask). */
fun TaskScope.sync(action: Runnable): TaskHandle = global("pnclans-sync", TaskExecution.global(), 0L, null, action)

/** Запускает [action] один раз в основном потоке через [delayTicks] тиков (аналог runTaskLater). */
fun TaskScope.later(delayTicks: Long, action: Runnable): TaskHandle = global("pnclans-later", TaskExecution.global(), delayTicks, null, action)

/** Повторяет [action] в основном потоке с периодом [periodTicks] после [delayTicks] тиков (аналог runTaskTimer). */
fun TaskScope.repeat(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle = global("pnclans-repeat", TaskExecution.global(), delayTicks, periodTicks, action)

/** Запускает [action] вне основного потока (аналог runTaskAsynchronously). */
fun TaskScope.async(action: Runnable): TaskHandle = global("pnclans-async", TaskExecution.async(), 0L, null, action)
