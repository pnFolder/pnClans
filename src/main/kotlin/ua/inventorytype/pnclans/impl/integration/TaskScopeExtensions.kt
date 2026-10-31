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
