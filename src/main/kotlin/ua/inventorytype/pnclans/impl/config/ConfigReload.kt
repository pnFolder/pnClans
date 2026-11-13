package ua.inventorytype.pnclans.impl.config

import ua.inventorytype.pnclans.BukkitPlugin

/** Loads configs through compatibility protection, exposes new keys and reports invalid references. */
internal object ConfigReload {
    fun run(plugin: BukkitPlugin, configs: ConfigService) {
        val migrationSafety = ConfigMigrationSafety.capture(plugin)
        configs.loadAll()
        if (migrationSafety.reconcile(plugin, configs)) {
            configs.loadAll()
        }
        ConfigValidator.validate(plugin, configs)
        PointsConfigValidator.validate(plugin, configs)
        MenuConfigValidator.validate(plugin, configs)
    }
}
