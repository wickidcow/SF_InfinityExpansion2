@file:Suppress("deprecation")

package net.guizhanss.infinityexpansion2.implementation.listeners

import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI
import me.mrCookieSlime.Slimefun.api.BlockStorage
import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.guizhanlib.kt.slimefun.extensions.getSlimefunItem
import net.guizhanss.guizhanlib.kt.slimefun.extensions.isSlimefunItem
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.debug.DebugCase
import net.guizhanss.infinityexpansion2.core.scheduling.IEScheduler
import net.guizhanss.infinityexpansion2.implementation.IEItems
import net.guizhanss.infinityexpansion2.implementation.items.tools.VeinMinerRune
import net.guizhanss.infinityexpansion2.utils.Debug
import net.guizhanss.infinityexpansion2.utils.constant.Keys
import net.guizhanss.infinityexpansion2.utils.tags.IETag
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class VeinMinerListener(plugin: InfinityExpansion2) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onRuneDrop(e: PlayerDropItemEvent) {
        val item = e.itemDrop
        if (item.itemStack.amount == 1 && item.itemStack.isRune(e.player)) {
            // Translation/permission lookup belongs to the player-owned event context.
            // The delayed world mutation itself is owned by the dropped item.
            val lore = InfinityExpansion2.integrationService.getLore(e.player, "vein-miner")
            IEScheduler.runForLater(
                item,
                20L,
                Runnable { useRune(e.player, item, lore) }
            )
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        val b = e.block
        if (processing.contains(BlockKey.of(b))) return

        val p = e.player
        if (!p.isSneaking) return

        val item = p.inventory.itemInMainHand
        if (!item.hasVeinMiner()) return

        // check if vein miner rune is disabled
        if (IEItems.VEIN_MINER_RUNE.item?.isDisabledIn(b.world) == true) return

        if (p.foodLevel < 1) {
            InfinityExpansion2.integrationService.sendMessage(p, "vein-miner.no-hunger")
            return
        }

        // check if the block is allowed
        val blockType = b.type
        if (!IETag.VEIN_MINER_BLOCKS.isTagged(blockType)) return

        // no sf block vein mining
        val loc = b.location
        if (BlockStorage.hasBlockInfo(loc)) return

        val foundBlocks = mutableSetOf<Block>()
        val checkedLocations = mutableSetOf<Location>()
        checkedLocations.add(loc)
        getVein(checkedLocations, foundBlocks, loc, b)

        val world = b.world

        foundBlocks.forEach { foundBlock ->
            val key = BlockKey.of(foundBlock)
            if (!processing.add(key)) return@forEach
            try {
                val event = BlockBreakEvent(foundBlock, p)
                Bukkit.getPluginManager().callEvent(event)
                if (!event.isCancelled) {
                    if (event.isDropItems) {
                        foundBlock.getDrops(item).forEach { drop ->
                            world.dropItemNaturally(foundBlock.location, drop)
                        }
                    }
                    foundBlock.type = Material.AIR
                }
            } finally {
                processing.remove(key)
            }
        }

        if (blockType.toString().endsWith("ORE")) {
            world.spawn(b.location, ExperienceOrb::class.java).experience = foundBlocks.size * 2
        }

        if (Random.nextBoolean()) {
            val event = FoodLevelChangeEvent(p, p.foodLevel - 1)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                p.foodLevel = event.foodLevel
            }
        }
    }

    /**
     * Check if an [ItemStack] is a vein miner rune, and can be used by [Player].
     */
    private fun ItemStack?.isRune(p: Player): Boolean {
        if (!this.isSlimefunItem<VeinMinerRune>()) return false
        val rune = this.getSlimefunItem<VeinMinerRune>()
        return rune.canUse(p, false)
    }

    /**
     * Try to use the rune. Find the target item and apply vein miner to it.
     *
     * Player permission/translation state was checked on the original drop event.
     * This delayed phase only mutates world state owned by the rune's entity region.
     */
    private fun useRune(p: Player, rune: Item, lore: String) {
        Debug.log(DebugCase.VEIN_MINER, "Vein miner rune activating")
        if (!rune.isValid) return
        if (rune.itemStack.amount != 1 || !rune.itemStack.isSlimefunItem<VeinMinerRune>()) return
        Debug.log(DebugCase.VEIN_MINER, "Rune is valid")

        val loc = rune.location
        if (!loc.isWorldLoaded) return
        val world = loc.world!!
        Debug.log(DebugCase.VEIN_MINER, "World is loaded")

        val range = VeinMinerRune.ACTIVATE_RANGE
        val targetItem = if (IEScheduler.isFolia) {
            // A 1.5-block search can straddle a region boundary. Restrict the Folia
            // path to entities in the currently owned chunk instead of reading
            // another region's entity set.
            rune.chunk.entities.asSequence()
                .filterIsInstance<Item>()
                .firstOrNull {
                    it.location.distanceSquared(loc) <= range * range &&
                        it.itemStack.isValidItem() &&
                        !it.itemStack.hasVeinMiner() &&
                        !it.itemStack.isSlimefunItem<VeinMinerRune>()
                }
        } else {
            world.getNearbyEntities(loc, range, range, range) {
                it is Item && it.itemStack.isValidItem() && !it.itemStack.hasVeinMiner() &&
                    !it.itemStack.isSlimefunItem<VeinMinerRune>()
            }.firstOrNull() as? Item
        } ?: return
        Debug.log(DebugCase.VEIN_MINER, "Found a nearby item")

        world.strikeLightningEffect(loc)
        world.createExplosion(loc, 0F)
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.3F, 1F)

        val target = targetItem.itemStack.clone()
        rune.remove()
        targetItem.remove()
        applyVeinMiner(target, lore)
        world.dropItem(loc, target)

        // Messaging a player that may have moved regions must be entity-owned.
        IEScheduler.runFor(
            p,
            Runnable { InfinityExpansion2.integrationService.sendMessage(p, "vein-miner.applied") }
        )
    }

    /**
     * Check if the item can be applied with vein miner.
     */
    private fun ItemStack.isValidItem(): Boolean {
        if (this.amount != 1) return false
        if (!this.hasItemMeta()) return false
        return this.itemMeta!! is Damageable
    }

    /**
     * Check if an item is applied with vein miner.
     */
    private fun ItemStack?.hasVeinMiner() =
        !this.isAir() && this.hasItemMeta() && PersistentDataAPI.hasBoolean(itemMeta!!, Keys.VEIN_MINER)

    private fun applyVeinMiner(item: ItemStack, loreLine: String) {
        if (!item.hasItemMeta()) return
        val meta = item.itemMeta!!

        PersistentDataAPI.setBoolean(meta, Keys.VEIN_MINER, true)

        val lore = meta.lore ?: mutableListOf()
        lore.add(loreLine)
        meta.lore = lore

        item.itemMeta = meta
    }

    private fun getVein(checked: MutableSet<Location>, found: MutableSet<Block>, l: Location, block: Block) {
        if (found.size >= MAX) return

        getAdjacentLocations(l).forEach { loc ->
            if (found.size >= MAX) return@forEach
            if (!checked.add(loc)) return@forEach
            if (!IEScheduler.isOwned(loc)) return@forEach

            val adjacent = loc.block
            if (adjacent.type == block.type && !BlockStorage.hasBlockInfo(loc)) {
                found.add(adjacent)
                getVein(checked, found, loc, adjacent)
            }
        }
    }

    private fun getAdjacentLocations(l: Location): List<Location> {
        val list = mutableListOf<Location>()
        list.add(l.clone().add(1.0, 0.0, 0.0))
        list.add(l.clone().add(-1.0, 0.0, 0.0))
        list.add(l.clone().add(0.0, 1.0, 0.0))
        list.add(l.clone().add(0.0, -1.0, 0.0))
        list.add(l.clone().add(0.0, 0.0, 1.0))
        list.add(l.clone().add(0.0, 0.0, -1.0))
        list.shuffle()
        return list
    }

    private data class BlockKey(val worldId: UUID, val x: Int, val y: Int, val z: Int) {
        companion object {
            fun of(block: Block) = BlockKey(block.world.uid, block.x, block.y, block.z)
        }
    }

    companion object {
        private val processing: MutableSet<BlockKey> = ConcurrentHashMap.newKeySet()
        private const val MAX = 64
    }
}
