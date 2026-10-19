package net.guizhanss.infinityexpansion2.core.scheduling

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import java.util.concurrent.TimeUnit

/**
 * Minimal Paper/Folia scheduler bridge for IE2.
 *
 * Paper/Purpur keep their historical Bukkit scheduler behavior. Folia uses
 * the native global, region, entity, and async schedulers so Bukkit world or
 * entity state is only touched by the owning execution context.
 */
object IEScheduler {
    private const val FOLIA_RUNTIME_CLASS = "io.papermc.paper.threadedregions.RegionizedServer"

    val isFolia: Boolean by lazy {
        try {
            Class.forName(FOLIA_RUNTIME_CLASS, false, Bukkit.getServer().javaClass.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private val plugin
        get() = InfinityExpansion2.instance

    fun run(task: Runnable) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    fun runLater(delayTicks: Long, task: Runnable) {
        require(delayTicks >= 0) { "Delay cannot be negative" }
        if (delayTicks == 0L) {
            run(task)
            return
        }

        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delayTicks)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        }
    }

    fun repeat(periodTicks: Long, task: Runnable) {
        require(periodTicks > 0) { "Period must be greater than zero" }
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task.run() }, 1L, periodTicks)
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, periodTicks)
        }
    }

    fun runAsync(task: Runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    fun runAsyncLater(delayTicks: Long, task: Runnable) {
        require(delayTicks >= 0) { "Delay cannot be negative" }
        if (delayTicks == 0L) {
            runAsync(task)
            return
        }

        if (isFolia) {
            Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                { task.run() },
                ticksToMillis(delayTicks),
                TimeUnit.MILLISECONDS
            )
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks)
        }
    }

    fun runAt(location: Location, task: Runnable): Boolean {
        requireNotNull(location.world) { "Location must have a world" }
        if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, location) { task.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
        return true
    }

    fun runAtLater(location: Location, delayTicks: Long, task: Runnable): Boolean {
        requireNotNull(location.world) { "Location must have a world" }
        require(delayTicks >= 0) { "Delay cannot be negative" }
        if (delayTicks == 0L) return runAt(location, task)

        if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, { task.run() }, delayTicks)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        }
        return true
    }

    fun runFor(entity: Entity, task: Runnable, retired: Runnable = Runnable {}): Boolean {
        if (!isFolia) {
            Bukkit.getScheduler().runTask(plugin, task)
            return true
        }

        val scheduled = entity.scheduler.run(plugin, { task.run() }, retired)
        if (scheduled == null) {
            retired.run()
            return false
        }
        return true
    }

    fun runForLater(
        entity: Entity,
        delayTicks: Long,
        task: Runnable,
        retired: Runnable = Runnable {}
    ): Boolean {
        require(delayTicks >= 0) { "Delay cannot be negative" }
        if (delayTicks == 0L) return runFor(entity, task, retired)

        if (!isFolia) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
            return true
        }

        val scheduled = entity.scheduler.runDelayed(plugin, { task.run() }, retired, delayTicks)
        if (scheduled == null) {
            retired.run()
            return false
        }
        return true
    }

    fun isOwned(location: Location): Boolean =
        !isFolia || (location.world != null && Bukkit.isOwnedByCurrentRegion(location))

    private fun ticksToMillis(ticks: Long): Long =
        if (ticks <= 0) 0 else Math.multiplyExact(ticks, 50L)
}
