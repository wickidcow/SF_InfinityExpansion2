package net.guizhanss.infinityexpansion2.core.migration

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.plugin.ServicePriority
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.logging.Level

/** Optional reflective bridge into Slimefun Legacy's exact placed-block migration API. */
object LegacyBlockMigrationProviderBridge {

    private const val MIGRATION_NAME = "InfinityExpansion2 IE1 Exact Block Migration"
    private const val PROVIDER_CLASS =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationProvider"
    private const val CANDIDATE_CLASS =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationCandidate"
    private const val RESULT_CLASS =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationResult"

    @Volatile
    private var registered = false

    fun register(plugin: InfinityExpansion2) {
        if (registered || !InfinityExpansion2.configService.migrationEnabled.value) return
        val slimefun = plugin.server.pluginManager.getPlugin("Slimefun") ?: return
        val loader = slimefun.javaClass.classLoader

        try {
            val providerClass = Class.forName(PROVIDER_CLASS, false, loader)
            val candidateClass = Class.forName(CANDIDATE_CLASS, false, loader)
            val resultClass = Class.forName(RESULT_CLASS, false, loader)
            val service = LegacyDoctorBlockMigrationService()
            val candidateConstructor = candidateClass.getConstructor(
                UUID::class.java,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            val worldId = candidateClass.getMethod("worldId")
            val x = candidateClass.getMethod("x")
            val y = candidateClass.getMethod("y")
            val z = candidateClass.getMethod("z")
            val sourceId = candidateClass.getMethod("sourceId")
            val targetId = candidateClass.getMethod("targetId")
            val stateClaim = candidateClass.getMethod("stateClaim")
            val migrated = resultClass.getMethod("migrated", String::class.java)
            val skipped = resultClass.getMethod("skipped", String::class.java)
            val blocked = resultClass.getMethod("blocked", String::class.java)
            val failed = resultClass.getMethod("failed", String::class.java)

            fun decode(raw: Any?): LegacyDoctorBlockMigrationService.Candidate? {
                if (!candidateClass.isInstance(raw)) return null
                return LegacyDoctorBlockMigrationService.Candidate(
                    worldId.invoke(raw) as UUID,
                    x.invoke(raw) as Int,
                    y.invoke(raw) as Int,
                    z.invoke(raw) as Int,
                    sourceId.invoke(raw) as String,
                    targetId.invoke(raw) as String,
                    stateClaim.invoke(raw) as String,
                )
            }

            val provider = Proxy.newProxyInstance(providerClass.classLoader, arrayOf(providerClass)) { proxy, method, args ->
                when (method.name) {
                    "getMigrationName" -> MIGRATION_NAME
                    "getLegacyBlockMappings" -> service.mappings()
                    "scanLoadedCandidates" -> service.scanLoadedCandidates().map { candidate ->
                        candidateConstructor.newInstance(
                            candidate.worldId,
                            candidate.x,
                            candidate.y,
                            candidate.z,
                            candidate.sourceId,
                            candidate.targetId,
                            candidate.stateClaim,
                        )
                    }
                    "isCandidateStillValid" -> decode(args?.firstOrNull())?.let(service::isCandidateStillValid) ?: false
                    "migrate" -> {
                        val candidate = decode(args?.firstOrNull())
                            ?: return@newProxyInstance blocked.invoke(null, "Unrecognized candidate type.")
                        val result = service.migrate(candidate)
                        when (result.status) {
                            LegacyDoctorBlockMigrationService.Status.MIGRATED -> migrated.invoke(null, result.detail)
                            LegacyDoctorBlockMigrationService.Status.SKIPPED_CHANGED -> skipped.invoke(null, result.detail)
                            LegacyDoctorBlockMigrationService.Status.BLOCKED -> blocked.invoke(null, result.detail)
                            LegacyDoctorBlockMigrationService.Status.FAILED -> failed.invoke(null, result.detail)
                        }
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
            InfinityExpansion2.log(Level.INFO, "Registered exact IE1 block migration with Slimefun Doctor.")
        } catch (_: ClassNotFoundException) {
            // Expected on Slimefun implementations without Legacy's exact block migration API.
        } catch (t: Throwable) {
            InfinityExpansion2.log(Level.WARNING, t, "Unable to register the exact IE1 block migration bridge")
        }
    }
}
