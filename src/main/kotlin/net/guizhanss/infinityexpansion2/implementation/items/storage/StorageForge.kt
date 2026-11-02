package net.guizhanss.infinityexpansion2.implementation.items.storage

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI
import net.guizhanss.infinityexpansion2.core.IERegistry
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType
import net.guizhanss.infinityexpansion2.core.recipes.MachineRecipe
import net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts.AbstractCraftingMachine
import org.bukkit.inventory.ItemStack

class StorageForge(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
) : AbstractCraftingMachine(itemGroup, itemStack, recipeType, recipe, MenuLayout.CRAFTING_DEFAULT) {

    override val recipes: List<MachineRecipe>
        get() = IERegistry.storageForgeRecipes

    override val strictIngredientCheck = false

    /**
     * Upgrading a storage unit keeps its stored items: the contents of the input unit are
     * carried onto the new unit, with the new unit's capacity.
     */
    override fun prepareOutput(input: Array<ItemStack?>, output: ItemStack): ItemStack? {
        val target = SlimefunItem.getByItem(output) as? StorageUnit
        if (target == null) {
            // Never consume a populated or unrecognized storage ledger when the
            // output cannot be identified as an upgradable storage unit.
            return if (input.any { it?.itemMeta?.persistentDataContainer?.has(StorageUnit.STORAGE_KEY) == true }) {
                null
            } else {
                output
            }
        }
        return transferContents(input, output, target.capacity)
    }

    companion object {

        /**
         * Copies the stored contents of the storage unit in [input] onto [output].
         * Returns null when the contents cannot be moved without loss: more than one input
         * unit holds items, or the stored amount exceeds [capacity].
         */
        @JvmStatic
        fun transferContents(input: Array<ItemStack?>, output: ItemStack, capacity: Int): ItemStack? {
            if (capacity <= 0) return null

            val stored = mutableListOf<StorageCache>()
            for (stack in input) {
                val meta = stack?.itemMeta ?: continue
                if (!meta.persistentDataContainer.has(StorageUnit.STORAGE_KEY)) continue

                // A record with an unknown type, missing item identity or corrupt
                // amount is not an empty unit. Refuse before consuming anything.
                if (!meta.persistentDataContainer.has(StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE)) {
                    return null
                }
                val cache = PersistentDataAPI.get(meta, StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE)
                    ?: return null
                if (cache.hasUnassignedItems() || cache.hasInvalidBalance()) return null
                if (!cache.isEmpty()) stored.add(cache)
            }

            if (stored.isEmpty()) return output
            if (stored.size > 1 || stored[0].amount > capacity) return null

            val old = stored[0]
            val cache = StorageCache(old.itemStack?.clone(), old.amount, capacity, old.voidExcess)
            val meta = output.itemMeta
            PersistentDataAPI.set(meta, StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE, cache)
            cache.addLore(meta)
            output.itemMeta = meta

            // The actual result ItemStack, not only the detached ItemMeta, must
            // retain the precise stored identity and balance before consumption.
            val readback = PersistentDataAPI.get(
                output.itemMeta, StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE
            )
            return if (readback != null
                && readback.itemStack == cache.itemStack
                && readback.amount == cache.amount
                && readback.limit == cache.limit
                && readback.voidExcess == cache.voidExcess
            ) output else null
        }
    }
}
