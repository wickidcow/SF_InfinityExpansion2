package net.guizhanss.infinityexpansion2.core.migration

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.plugin.ServicePriority
import java.lang.reflect.Proxy
import java.util.logging.Level

/**
 * Optional bridge into Slimefun Legacy's addon-doctor API.
 *
 * The API is resolved reflectively so IE2 can still load on Slimefun forks that do not expose
 * Legacy's diagnostics package. The actual migration work always remains in
 * [LegacyMigrationService], which keeps `/ie2 doctor` and `/sf doctor addons` on one code path.
 */
object LegacyAddonDoctorBridge {

    private const val ADDON_NAME = "InfinityExpansion2 IE1 Migration"
    private const val DOCTOR_CLASS = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor"
    private const val REPORT_CLASS = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport"

    @Volatile
    private var registered = false

    fun register(plugin: InfinityExpansion2) {
        if (registered) return

        val slimefun = plugin.server.pluginManager.getPlugin("Slimefun") ?: return
        val loader = slimefun.javaClass.classLoader

        try {
            val doctorClass = Class.forName(DOCTOR_CLASS, false, loader)
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

            val provider = Proxy.newProxyInstance(doctorClass.classLoader, arrayOf(doctorClass)) { proxy, method, args ->
                when (method.name) {
                    "getAddonName" -> ADDON_NAME
                    "runDoctor" -> {
                        val repair = args?.firstOrNull() as? Boolean ?: false
                        val stats = InfinityExpansion2.migrationService.scanLoaded(repair)
                        val issues = stats.legacyBlocksFound.toLong() + stats.legacyItemsFound.toLong()
                        val repaired = stats.blocksMigrated.toLong() + stats.itemsMigrated.toLong()
                        val failures = stats.blockFailures.toLong() + stats.itemFailures.toLong()
                        val details = buildList {
                            add("IE1 block records found: ${stats.legacyBlocksFound}; migrated: ${stats.blocksMigrated}; failures: ${stats.blockFailures}")
                            add("IE1 item stacks found: ${stats.legacyItemsFound}; migrated: ${stats.itemsMigrated}; failures: ${stats.itemFailures}")
                            add("Legacy aliases resolved: ${InfinityExpansion2.migrationService.aliasesInstalled.totalResolved}")
                            add("Loaded chunks, loaded inventories/entities and online players were checked; unloaded chunks migrate when loaded.")
                            if (repair) {
                                add("Run /sf doctor addons scan or /sf doctor ie2 scan again after a clean shutdown/restart to verify the loaded scope is clean.")
                            }
                        }

                        reportConstructor.newInstance(
                            ADDON_NAME,
                            repair,
                            issues,
                            issues,
                            repaired,
                            failures,
                            details,
                        )
                    }
                    "toString" -> "$ADDON_NAME provider"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }

            @Suppress("UNCHECKED_CAST")
            plugin.server.servicesManager.register(
                doctorClass as Class<Any>,
                provider,
                plugin,
                ServicePriority.Normal,
            )
            registered = true
            InfinityExpansion2.log(Level.INFO, "Registered IE1 migration with Slimefun Legacy addon doctor.")
        } catch (_: ClassNotFoundException) {
            // Expected on Slimefun implementations that do not provide the Legacy diagnostics API.
        } catch (t: Throwable) {
            InfinityExpansion2.log(Level.WARNING, t, "Unable to register the Slimefun Legacy addon-doctor bridge")
        }
    }
}
