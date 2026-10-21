package net.guizhanss.infinityexpansion2.implementation.listeners

import me.mrCookieSlime.Slimefun.api.BlockStorage
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobDataCard
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Refreshes encountered cards after inventory transactions have completed. No event-time item
 * snapshot is written back: the entity-owned callback reads each current slot and cursor again.
 * Machine menus keep their own processing path, and virtual guide icons are never inventory data.
 */
internal class MobDataCardRefreshListener(
    private val plugin: Plugin,
    private val refresh: (ItemStack) -> ItemStack? = MobDataCard::refreshAppearance,
    private val isRefreshEnabled: () -> Boolean = { InfinityExpansion2.configService.mobSimRefreshCardAppearance.value },
    private val schedule: (Player, Runnable, Runnable) -> Boolean = { player, task, retired ->
        player.scheduler.runDelayed(plugin, { task.run() }, retired, 1L) != null
    },
    private val ownsLocation: (Location) -> Boolean = { Bukkit.isOwnedByCurrentRegion(it) },
    private val isSlimefunBlock: (Location) -> Boolean = { BlockStorage.hasBlockInfo(it) },
) : Listener {
    private class PendingRefresh(includeEnderChest: Boolean) {
        val includeEnderChest = AtomicBoolean(includeEnderChest)
    }

    private val pending = ConcurrentHashMap<UUID, PendingRefresh>()
    private val reportedFailure = AtomicBoolean()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = enqueue(event.player, includeEnderChest = true)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        (event.player as? Player)?.let { enqueue(it, includeEnderChest = true) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        (event.player as? Player)?.let(::enqueue)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        (event.whoClicked as? Player)?.let(::enqueue)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        (event.whoClicked as? Player)?.let(::enqueue)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        (event.entity as? Player)?.let(::enqueue)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) = enqueue(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) = enqueue(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)
    }

    private fun enqueue(player: Player) = enqueue(player, includeEnderChest = false)

    private fun enqueue(player: Player, includeEnderChest: Boolean) {
        if (!plugin.isEnabled || !isRefreshEnabled()) return
        val id = player.uniqueId
        val request = PendingRefresh(includeEnderChest)
        val existing = pending.putIfAbsent(id, request)
        if (existing != null) {
            if (includeEnderChest) existing.includeEnderChest.set(true)
            return
        }

        val retired = Runnable { pending.remove(id, request) }
        try {
            val accepted = schedule(player, Runnable {
                if (!pending.remove(id, request) || !plugin.isEnabled || !player.isOnline || !isRefreshEnabled()) return@Runnable
                refreshInventory(player.inventory)
                refreshCursor(player)
                if (request.includeEnderChest.get()) refreshInventory(player.enderChest)
                try {
                    refreshOpenedStorage(player)
                } catch (ex: RuntimeException) {
                    reportFailure(ex)
                }
            }, retired)
            if (!accepted) retired.run()
        } catch (ex: RuntimeException) {
            retired.run()
            reportFailure(ex)
        }
    }

    private fun refreshInventory(inventory: Inventory) {
        for (slot in 0 until inventory.size) {
            val current = inventory.getItem(slot) ?: continue
            try {
                val updated = refresh(current) ?: continue
                // The resolver is side-effect free. This extra check also protects a slot if
                // another integration synchronously replaces it while inspecting presentation.
                if (inventory.getItem(slot) == current) inventory.setItem(slot, updated)
            } catch (ex: RuntimeException) {
                reportFailure(ex)
            }
        }
    }

    private fun refreshCursor(player: Player) {
        val current = player.itemOnCursor
        try {
            val updated = refresh(current) ?: return
            if (player.itemOnCursor == current) player.setItemOnCursor(updated)
        } catch (ex: RuntimeException) {
            reportFailure(ex)
        }
    }

    private fun refreshOpenedStorage(player: Player) {
        val inventory = player.openInventory.topInventory
        if (inventory.type !in ORDINARY_STORAGE_TYPES) return
        val holder = inventory.holder as? Container ?: return
        if (!holder.isPlaced) return
        val location = holder.location
        // Opening a remote container does not grant its region to the player's scheduler.
        // Do not load chunks, scan other regions, or touch a Slimefun-owned vanilla container.
        if (!ownsLocation(location) || !holder.world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) return
        if (isSlimefunBlock(location) || holder.inventory != inventory) return
        refreshInventory(inventory)
    }

    private fun reportFailure(ex: RuntimeException) {
        if (reportedFailure.compareAndSet(false, true)) {
            plugin.logger.log(Level.WARNING, "Mob Data Card appearance refresh skipped an unsafe item or inventory.", ex)
        }
    }

    companion object {
        private val ORDINARY_STORAGE_TYPES = setOf(InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX)
    }
}
