package net.guizhanss.infinityexpansion2.implementation.items.storage

import com.jeff_media.morepersistentdatatypes.DataType
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI
import net.guizhanss.infinityexpansion2.core.IERegistry
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType
import net.guizhanss.infinityexpansion2.core.recipes.MachineRecipe
import net.guizhanss.infinityexpansion2.utils.bukkitext.ie2Key
import net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts.AbstractCraftingMachine
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import org.bukkit.Material
import java.util.logging.Level
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

    /**
     * A stored ledger must never be merged into another output stack. The
     * generic push path can return a remainder after ingredients have already
     * been debited (MENU_FIT and MENU_INSERT have different admission checks).
     * For ledger-bearing upgrades, use the Forge's single physical output slot
     * and verify the *actual* full PDC item before acknowledging consumption.
     * Other recipes keep their original crafting behavior.
     *
     * All operations here run in the owning menu handler. This can recover a
     * synchronous rejected write, but is not a crash-atomic database transaction.
     */
    override fun commitCraft(
        menu: BlockMenu,
        recipe: MachineRecipe,
        input: Array<ItemStack?>,
        output: ItemStack,
    ): Boolean {
        if (input.none { it?.itemMeta?.persistentDataContainer?.has(StorageUnit.STORAGE_KEY) == true }) {
            return super.commitCraft(menu, recipe, input, output)
        }

        // A ledger-bearing output is uniquely owned by one unit. Stacking it
        // would clone its stored items even when the normal fit check says yes.
        val destination = outputSlots.singleOrNull() ?: return false
        val occupied = menu.getItemInSlot(destination)
        if (occupied != null && !occupied.type.isAir) return false
        if (output.amount != 1
            || output.itemMeta?.persistentDataContainer?.has(StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE)
                != true
        ) return false

        val originals = input.map { it?.clone() }
        try {
            recipe.consume(input)
            // A successful output write must never be acknowledged unless every
            // required ingredient was actually debited. The recipe consumer
            // mutates the menu-backed ItemStacks in place and returns no receipt.
            // Compare both the mutated references and physical menu readback;
            // on any mismatch the empty-output rollback below restores inputs.
            val debited = recipe.recipe.indices.all { index ->
                val required = recipe.recipe[index]?.amount ?: 0
                val original = originals[index]
                val expectedAmount = (original?.amount ?: 0) - required
                val actual = menu.getItemInSlot(inputSlots[index])
                val reference = input[index]
                expectedAmount >= 0 && if (expectedAmount == 0) {
                    (actual == null || actual.type.isAir) &&
                        (reference == null || reference.type.isAir || reference.amount == 0)
                } else {
                    original != null && actual != null && reference != null &&
                        actual.amount == expectedAmount && reference.amount == expectedAmount &&
                        actual.isSimilar(original) && reference.isSimilar(original)
                }
            }
            if (!debited) {
                InfinityExpansion2.log(
                    Level.SEVERE,
                    IllegalStateException("Forge ingredient debit readback mismatch"),
                    "Refusing output delivery; attempting exact input rollback"
                )
                return rollbackUncommittedForgeInputs(menu, originals)
            }
            // This is an owned machine-output slot, not a network insertion.
            // Direct replacement avoids a silent rejected push/remainder.
            menu.replaceExistingItem(destination, output.clone())
            if (menu.getItemInSlot(destination) == output) return true
        } catch (failure: Exception) {
            InfinityExpansion2.log(Level.SEVERE, failure, "Storage Forge output commit failed; attempting in-memory recovery")
        }

        // A partially written or unexpectedly changed output must not be
        // deleted speculatively: it could be the only remaining copy of stock.
        val actual = menu.getItemInSlot(destination)
        if (actual == output) return true
        if (actual != null && !actual.type.isAir) {
            InfinityExpansion2.log(
                Level.SEVERE,
                IllegalStateException("Unexpected nonempty Forge output during recovery"),
                "Quarantining uncertain Forge output rather than duplicating a source ledger"
            )
            return false
        }

        // The output slot is still empty. Restore *every* exact ingredient
        // snapshot; no output means the input is the only safe stock holder.
        return try {
            originals.forEachIndexed { i, original ->
                menu.replaceExistingItem(inputSlots[i], original?.clone() ?: ItemStack(Material.AIR))
            }
            val restored = originals.indices.all { originals[it] == menu.getItemInSlot(inputSlots[it]) }
            if (!restored) {
                InfinityExpansion2.log(
                    Level.SEVERE,
                    IllegalStateException("Forge input rollback readback mismatch"),
                    "Forge recovery needs manual inspection"
                )
            }
            false
        } catch (failure: Exception) {
            InfinityExpansion2.log(Level.SEVERE, failure, "Forge input rollback failed; manual recovery required")
            false
        }
    }

    private fun rollbackUncommittedForgeInputs(menu: BlockMenu, originals: List<ItemStack?>): Boolean {
        return try {
            originals.forEachIndexed { index, original ->
                menu.replaceExistingItem(inputSlots[index], original?.clone() ?: ItemStack(Material.AIR))
            }
            if (!originals.indices.all { originals[it] == menu.getItemInSlot(inputSlots[it]) }) {
                InfinityExpansion2.log(
                    Level.SEVERE,
                    IllegalStateException("Forge ingredient rollback readback mismatch"),
                    "Forge recovery needs manual inspection"
                )
            }
            false
        } catch (failure: Exception) {
            InfinityExpansion2.log(Level.SEVERE, failure, "Forge ingredient rollback failed; manual recovery required")
            false
        }
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

            // A recipe result carrying its own storage ledger would mint stock without
            // any source ledger. Never trust a pre-populated output, even for plain inputs.
            if (output.itemMeta?.persistentDataContainer?.has(StorageUnit.STORAGE_KEY) == true) return null
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
