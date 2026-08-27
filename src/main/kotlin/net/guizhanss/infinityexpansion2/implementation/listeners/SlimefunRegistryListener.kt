@file:Suppress("unused")

package net.guizhanss.infinityexpansion2.implementation.listeners

import io.github.thebusybiscuit.slimefun4.api.events.SlimefunItemRegistryFinalizedEvent
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.items.attributes.DelayedTaskItem
import net.guizhanss.infinityexpansion2.implementation.setup.DynamicItemSetup
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.logging.Level

class SlimefunRegistryListener(plugin: InfinityExpansion2) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @Suppress("unused_parameter")
    fun onLoad(e: SlimefunItemRegistryFinalizedEvent) {
        InfinityExpansion2.log(Level.INFO, "Executing delayed registering tasks...")

        // Dynamic IE2 ids are registered once during normal plugin enable so
        // dependent addons can resolve them. Retry here for custom definitions
        // whose ingredients/targets came from addons that registered later.
        DynamicItemSetup.loadAvailable(finalPass = true)

        // delayed task items
        Slimefun.getRegistry().enabledSlimefunItems.forEach { item ->
            if (item !is DelayedTaskItem) return@forEach

            if (item.isSync) {
                InfinityExpansion2.scheduler().run(item::delayedTask)
            } else {
                InfinityExpansion2.scheduler().runAsync(item::delayedTask)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @Suppress("unused_parameter")
    fun installMigrationAliases(e: SlimefunItemRegistryFinalizedEvent) {
        // At this point normal addon onEnable registration (and lower-priority finalized
        // handlers) has already claimed its canonical ids. The migration bridge therefore
        // skips real addon ownership instead of pre-claiming those ids for an IE2 alias.
        if (InfinityExpansion2.configService.migrationEnabled.value) {
            InfinityExpansion2.migrationService.installAliases()
        }
    }
}
