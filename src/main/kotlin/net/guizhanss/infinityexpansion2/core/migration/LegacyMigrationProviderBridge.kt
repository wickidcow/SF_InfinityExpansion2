package net.guizhanss.infinityexpansion2.core.migration

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.plugin.ServicePriority
import java.lang.reflect.Proxy
import java.util.logging.Level

/**
 * Optional bridge into Slimefun Legacy's dedicated legacy-item migration provider API.
 *
 * The provider API is resolved reflectively so the same IE2 jar remains loadable on
 * Slimefun implementations that do not expose this Legacy-only execution boundary.
 * Slimefun core only validates and delegates; all actual migration remains in
 * [LegacyMigrationService].
 */
object LegacyMigrationProviderBridge {

    private const val MIGRATION_NAME = "InfinityExpansion2 IE1 Migration"
    private const val PROVIDER_CLASS =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider"
    private const val REPORT_CLASS = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport"

    @Volatile
    private var registered = false

    fun register(plugin: InfinityExpansion2) {
        if (registered || !InfinityExpansion2.configService.migrationEnabled.value) return

        val slimefun = plugin.server.pluginManager.getPlugin("Slimefun") ?: return
        val loader = slimefun.javaClass.classLoader

        try {
            val providerClass = Class.forName(PROVIDER_CLASS, false, loader)
            val reportClass = Class.forName(REPORT_CLASS, false, loader)
            val reportConstructor = reportClass.getConstructor(
                String::class.java,
                java.lang.Boolean.TYPE,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                List::class.java,
            )

            val provider = Proxy.newProxyInstance(providerClass.classLoader, arrayOf(providerClass)) { proxy, method, args ->
                when (method.name) {
                    "getMigrationName" -> MIGRATION_NAME
                    "getLegacyItemMappings" -> LegacyIdMapper.resolvedAliases()
                    "runMigration" -> {
                        val repair = args?.firstOrNull() as? Boolean ?: false
                        val stats = InfinityExpansion2.migrationService.scanLoaded(repair)
                        val issues = stats.legacyBlocksFound.toLong() + stats.legacyItemsFound.toLong()
                        val repaired = stats.blocksMigrated.toLong() + stats.itemsMigrated.toLong()
                        val failures = stats.blockFailures.toLong() + stats.itemFailures.toLong()
                        val details = buildList {
                            add("IE1 block records found: ${stats.legacyBlocksFound}; migrated: ${stats.blocksMigrated}; failures: ${stats.blockFailures}")
                            add("IE1 item stacks found: ${stats.legacyItemsFound}; migrated: ${stats.itemsMigrated}; failures: ${stats.itemFailures}")
                            add("Legacy aliases resolved: ${InfinityExpansion2.migrationService.aliasesInstalled.totalResolved}")
                            add("Loaded chunks, loaded inventories/entities and online players were checked; unloaded chunks were not force-loaded.")
                            add("With automatic migration disabled, load additional areas normally and rerun this provider to include them.")
                            if (repair) {
                                add("Run /sf doctor migrations scan InfinityExpansion2 again after normal world activity to verify the loaded scope is clean.")
                            }
                        }

                        reportConstructor.newInstance(
                            MIGRATION_NAME,
                            repair,
                            issues,
                            issues,
                            repaired,
                            failures,
                            details,
                        )
                    }
                    "toString" -> "$MIGRATION_NAME provider"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }

            @Suppress("UNCHECKED_CAST")
            plugin.server.servicesManager.register(
                providerClass as Class<Any>,
                provider,
                plugin,
                ServicePriority.Normal,
            )
            registered = true
            InfinityExpansion2.log(Level.INFO, "Registered IE1 migration with Slimefun Legacy migration provider API.")
        } catch (_: ClassNotFoundException) {
            // Expected on Slimefun implementations that do not provide the Legacy provider API.
        } catch (t: Throwable) {
            InfinityExpansion2.log(Level.WARNING, t, "Unable to register the Slimefun Legacy migration provider bridge")
        }
    }
}