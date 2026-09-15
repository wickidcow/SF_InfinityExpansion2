package net.guizhanss.infinityexpansion2.core.migration

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.plugin.ServicePriority
import java.lang.reflect.Proxy
import java.util.logging.Level

/**
 * Optional bridge into Slimefun Legacy's addon-doctor API.
 *
 * This generic Doctor path remains useful on older Slimefun cores, but it intentionally never
 * rewrites placed block identities. Exact placed-block mutation belongs to the dedicated
 * fingerprinted [LegacyBlockMigrationProviderBridge] lane.
 */
object LegacyAddonDoctorBridge {

    private const val ADDON_NAME = "InfinityExpansion2 IE1 Migration"
    private const val DOCTOR_CLASS = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor"
    private const val REPORT_CLASS = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport"

    @Volatile
    private var registered = false

    fun register(plugin: InfinityExpansion2) {
        if (registered || !InfinityExpansion2.configService.migrationEnabled.value) return

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
            val itemService = LegacyDoctorItemMigrationService()
            val blockService = LegacyDoctorBlockMigrationService()

            val provider = Proxy.newProxyInstance(doctorClass.classLoader, arrayOf(doctorClass)) { proxy, method, args ->
                when (method.name) {
                    "getAddonName" -> ADDON_NAME
                    "runDoctor" -> {
                        val repair = args?.firstOrNull() as? Boolean ?: false
                        val stats = itemService.scanLoaded(repair)
                        val blockCandidates = blockService.scanLoadedCandidates().size
                        val issues = blockCandidates.toLong() + stats.legacyItemsFound.toLong()
                        val repaired = stats.itemsMigrated.toLong()
                        val failures = stats.itemFailures.toLong()
                        val details = buildList {
                            add("IE1 exact placed-block candidates found: $blockCandidates; generic Addon Doctor never rewrites them.")
                            add("IE1 item stacks found: ${stats.legacyItemsFound}; migrated: ${stats.itemsMigrated}; failures: ${stats.itemFailures}")
                            add("Legacy aliases resolved: ${InfinityExpansion2.migrationService.aliasesInstalled.totalResolved}")
                            add("Loaded chunks, loaded inventories/entities and online players were checked; unloaded chunks were not force-loaded.")
                            add("New Slimefun Legacy builds migrate placed blocks only through the exact location/state provider.")
                            add("With automatic migration disabled, load additional areas normally and rerun Doctor to include them.")
                            if (repair) {
                                add("Only loaded ItemStacks were eligible for repair in this generic Doctor pass.")
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
