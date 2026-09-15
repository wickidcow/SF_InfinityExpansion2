package net.guizhanss.infinityexpansion2.core.migration

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.plugin.ServicePriority
import java.lang.reflect.Proxy
import java.util.logging.Level

/**
 * Optional bridge into Slimefun Legacy's dedicated legacy-item migration provider API.
 *
 * Placed blocks are deliberately excluded from this older broad provider. New Legacy builds
 * receive them through [LegacyBlockMigrationProviderBridge], where exact loaded locations and
 * state claims are fingerprinted and revalidated before mutation.
 */
object LegacyMigrationProviderBridge {

    private const val MIGRATION_NAME = "InfinityExpansion2 IE1 Item Migration"
    private const val PROVIDER_CLASS =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider"
    private const val REPORT_CLASS = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport"

    @Volatile
    private var registered = false

    fun register(plugin: InfinityExpansion2) {
        // Offer the exact placed-block lane first when the running Slimefun Legacy exposes it.
        LegacyBlockMigrationProviderBridge.register(plugin)

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
            val itemService = LegacyDoctorItemMigrationService()
            val blockService = LegacyDoctorBlockMigrationService()

            val provider = Proxy.newProxyInstance(providerClass.classLoader, arrayOf(providerClass)) { proxy, method, args ->
                when (method.name) {
                    "getMigrationName" -> MIGRATION_NAME
                    "getLegacyItemMappings" -> LegacyIdMapper.resolvedAliases()
                    "runMigration" -> {
                        val repair = args?.firstOrNull() as? Boolean ?: false
                        val stats = itemService.scanLoaded(repair)
                        val blockCandidates = blockService.scanLoadedCandidates().size
                        val issues = blockCandidates.toLong() + stats.legacyItemsFound.toLong()
                        val repaired = stats.itemsMigrated.toLong()
                        val failures = stats.itemFailures.toLong()
                        val details = buildList {
                            add("IE1 exact placed-block candidates found: $blockCandidates; this item provider never rewrites them.")
                            add("IE1 item stacks found: ${stats.legacyItemsFound}; migrated: ${stats.itemsMigrated}; failures: ${stats.itemFailures}")
                            add("Legacy aliases resolved: ${InfinityExpansion2.migrationService.aliasesInstalled.totalResolved}")
                            add("Loaded chunks, loaded inventories/entities and online players were checked; unloaded chunks were not force-loaded.")
                            add("Placed blocks require Slimefun Legacy's exact block migration provider with location/state revalidation.")
                            add("With automatic migration disabled, load additional areas normally and rerun the providers to include them.")
                            if (repair) {
                                add("Run /sf doctor migrations scan InfinityExpansion2 again after normal world activity to verify the loaded item scope is clean.")
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
            InfinityExpansion2.log(Level.INFO, "Registered IE1 item migration with Slimefun Legacy migration provider API.")
        } catch (_: ClassNotFoundException) {
            // Expected on Slimefun implementations that do not provide the Legacy provider API.
        } catch (t: Throwable) {
            InfinityExpansion2.log(Level.WARNING, t, "Unable to register the Slimefun Legacy item migration provider bridge")
        }
    }
}
