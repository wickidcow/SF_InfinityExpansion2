package net.guizhanss.infinityexpansion2.implementation.items.storage

import com.jeff_media.morepersistentdatatypes.DataType
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI
import net.guizhanss.infinityexpansion2.core.IERegistry
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType
import net.guizhanss.infinityexpansion2.core.recipes.MachineRecipe
import net.guizhanss.infinityexpansion2.utils.bukkitext.ie2Key
import net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts.AbstractCraftingMachine
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

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
                // The historical reader supplies defaults for missing fields. In an
                // upgrade, a missing amount/capacity or malformed optional field
                // must never silently become zero/false and discard saved state.
                val raw = meta.persistentDataContainer.get(
                    StorageUnit.STORAGE_KEY, PersistentDataType.TAG_CONTAINER
                ) ?: return null
                if (!raw.has(ie2Key("amount"), PersistentDataType.INTEGER)
                    || !raw.has(ie2Key("max_amount"), PersistentDataType.INTEGER)
                    || (raw.has(ie2Key("void_excess"))
                        && !raw.has(ie2Key("void_excess"), PersistentDataType.BOOLEAN))
                    || (raw.has(ie2Key("item")) && !raw.has(ie2Key("item"), DataType.ITEM_STACK))) {
                    return null
                }
                // The historical decoder only knows these four fields. An unknown
                // nested field could contain future or third-party stored state;
                // rebuilding the cache would silently discard it. Quarantine the
                // upgrade instead of consuming the original storage unit.
                val knownKeys = setOf(
                    ie2Key("item"), ie2Key("amount"), ie2Key("max_amount"), ie2Key("void_excess")
                )
                if (raw.keys.any { it !in knownKeys }) return null
                // Recipes consume one unit at a time. A stacked item carrying a
                // populated ledger could leave an identical ledger behind.
                if (stack.amount != 1) return null
                val cache = try {
                    PersistentDataAPI.get(meta, StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE)
                } catch (failure: RuntimeException) {
                    // Corrupt nested item templates may throw while decoding.
                    return null
                } ?: return null
                if (cache.hasUnassignedItems() || cache.hasInvalidBalance()) return null
                // A zero-balance unit can still have a seeded item identity and
                // void-excess setting. Preserve the entire ledger, not just stock.
                stored.add(cache)
            }

            if (stored.isEmpty()) return output
            if (stored.size != 1 || stored[0].amount > capacity) return null
            // Never copy one ledger into multiple outputs or overwrite a result
            // that already contains a distinct storage record.
            if (output.amount != 1 || output.itemMeta?.persistentDataContainer?.has(StorageUnit.STORAGE_KEY) == true) {
                return null
            }

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
