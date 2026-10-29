package net.guizhanss.infinityexpansion2.implementation.items.storage

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.schntgaispock.slimehud.waila.HudRequest
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu
import net.guizhanss.guizhanlib.common.Cooldown
import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.guizhanlib.kt.minecraft.items.edit
import net.guizhanss.guizhanlib.kt.slimefun.extensions.isSlimefunItem
import net.guizhanss.guizhanlib.kt.slimefun.extensions.position
import net.guizhanss.guizhanlib.kt.slimefun.items.builder.asMaterialType
import net.guizhanss.guizhanlib.kt.slimefun.utils.getBlockMenu
import net.guizhanss.guizhanlib.kt.slimefun.utils.getBoolean
import net.guizhanss.guizhanlib.kt.slimefun.utils.getInt
import net.guizhanss.guizhanlib.kt.slimefun.utils.setBoolean
import net.guizhanss.guizhanlib.kt.slimefun.utils.setInt
import net.guizhanss.guizhanlib.slimefun.machines.MenuBlock
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.debug.DebugCase
import net.guizhanss.infinityexpansion2.core.items.annotations.HudProvider
import net.guizhanss.infinityexpansion2.core.items.attributes.InformationalRecipeDisplayItem
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType
import net.guizhanss.infinityexpansion2.utils.Debug
import net.guizhanss.infinityexpansion2.utils.bukkitext.ie2Key
import net.guizhanss.infinityexpansion2.utils.items.isSimilar
import net.guizhanss.infinityexpansion2.utils.items.isDisplayItem
import net.guizhanss.infinityexpansion2.utils.items.removeDisplayItem
import net.guizhanss.infinityexpansion2.utils.items.toDisplayItem
import net.guizhanss.infinityexpansion2.utils.tags.IETag
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID

@HudProvider
class StorageUnit(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    val capacity: Int,
) : MenuBlock(itemGroup, itemStack, recipeType, recipe), InformationalRecipeDisplayItem, DistinctiveItem {

    init {
        addItemHandler(object : BlockTicker() {
            // The mutable StorageCache and BlockMenu are also accessed by
            // player actions, Cargo and Networks on the server thread. Running
            // this producer asynchronously can race withdrawals/deposits and
            // expose the same output twice. Keep item movement on the owner
            // thread; this does not change storage data or recipe behavior.
            override fun isSynchronized() = true

            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                tick(b)
            }

            @Deprecated("Compatibility bridge; use the block-data ticker overload")
            override fun tick(b: Block, item: SlimefunItem, data: me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config) {
                tick(b)
            }
        })
    }

    override fun setup(preset: BlockMenuPreset) {
        LAYOUT.setupPreset(preset)

        preset.drawBackground(ACTION_DEPOSIT_ITEM, intArrayOf(ACTION_DEPOSIT_SLOT))
        preset.drawBackground(ACTION_WITHDRAW_SINGLE_ITEM, intArrayOf(ACTION_WITHDRAW_SINGLE_SLOT))
        preset.drawBackground(ACTION_WITHDRAW_ALL_ITEM, intArrayOf(ACTION_WITHDRAW_ALL_SLOT))
        preset.addMenuClickHandler(AMOUNT_SLOT, ChestMenuUtils.getEmptyClickHandler())
        preset.addMenuClickHandler(ITEM_SLOT, ChestMenuUtils.getEmptyClickHandler())
    }

    // Networks discovers these no-argument methods through the public JVM API.
    // Kotlin otherwise inherits MenuBlock's protected visibility silently.
    public override fun getInputSlots() = LAYOUT.inputSlots

    public override fun getOutputSlots() = LAYOUT.outputSlots

    val caches: Map<BlockPosition, StorageCache>
        get() = _caches

    private val _caches = mutableMapOf<BlockPosition, StorageCache>()

    override fun onNewInstance(menu: BlockMenu, b: Block) {
        menu.addMenuClickHandler(ACTION_VOID_SLOT) { _, _, _, _ ->
            toggleVoid(menu)
            false
        }

        // inv interactions
        menu.addMenuClickHandler(ACTION_DEPOSIT_SLOT) { p, _, _, _ ->
            interaction(menu, p, InteractionMode.DEPOSIT)
            false
        }
        menu.addMenuClickHandler(ACTION_WITHDRAW_SINGLE_SLOT) { p, _, _, _ ->
            interaction(menu, p, InteractionMode.WITHDRAW_SINGLE)
            false
        }
        menu.addMenuClickHandler(ACTION_WITHDRAW_ALL_SLOT) { p, _, _, _ ->
            interaction(menu, p, InteractionMode.WITHDRAW_ALL)
            false
        }

        val cache = _caches[menu.position] ?: addCache(menu)
        // ITEM_SLOT is also the existing persisted template, not only a GUI icon.
        // Initialize it even when nobody has opened this menu.
        menu.persistTemplate(cache)
        menu.updateDisplay(cache)
    }

    override fun getInputSlots(menu: DirtyChestMenu, item: ItemStack): IntArray {
        val cache = _caches[(menu as BlockMenu).position]
        return if (
            cache != null &&
            !cache.hasUnassignedItems() && !cache.hasBlockedTransfer() &&
            !item.isBlacklisted() &&
            (cache.isEmpty() || (cache.matches(item) && isSimilar(item, menu.getItemInSlot(outputSlots[0]))))
        ) {
            inputSlots
        } else {
            intArrayOf()
        }
    }

    private fun tick(b: Block) {
        val menu = b.getBlockMenu() ?: run {
            _caches.remove(b.position)
            return
        }

        val cache = _caches[menu.position] ?: return
        if (cache.hasUnassignedItems() || cache.hasBlockedTransfer()) return

        menu.updateDisplay(cache)

        // Debit the authoritative physical input slot before crediting storage.
        val inputSlot = inputSlots[0]
        val inputItem = menu.getItemInSlot(inputSlot)
        if (!inputItem.isAir() && !inputItem.isBlacklisted()) {
            val consumed = try {
                StorageInputReceipt.transfer(
                    cache,
                    { menu.getItemInSlot(inputSlot) },
                    { remainder -> menu.replaceExistingItem(inputSlot, remainder ?: ItemStack(Material.AIR)) },
                    { menu.markDirty() },
                    { requested -> menu.inputItem(cache, requested) }
                )
            } catch (failure: RuntimeException) {
                holdFailedInput(menu, cache, failure)
                return // Stop the output phase after an uncertain input debit.
            }
            if (consumed > 0) {
                menu.updateDisplay(cache)
            }
        }

        // output item
        // Reserve and persist cached items BEFORE crediting the physical output.
        // Verified post-push/post-markDirty reads replace an unchecked delivery.
        // Only this existing single output slot is eligible; normal tick rates stay intact.
        if (cache.amount > 0 && cache.itemStack != null) {
            try {
                val delivered = StorageOutputReceipt.transfer(
                    cache.itemStack!!,
                    cache.amount,
                    { cache.itemStack },
                    { menu.getItemInSlot(outputSlots[0]) },
                    { requested -> menu.outputItem(cache, requested) },
                    { cache.amount },
                    { stack -> menu.pushItem(stack, *outputSlots) },
                    { menu.markDirty() },
                    {
                        // An ambiguous physical write cannot be automatically refunded.
                        cache.blockUnverifiedTransfer()
                        menu.location.save(cache)
                    }
                )
                if (delivered > 0) menu.updateDisplay(cache)
            } catch (failure: RuntimeException) {
                InfinityExpansion2.instance.logger.log(java.util.logging.Level.SEVERE,
                    "Unverified ticker output at ${menu.location}; IE2 storage is blocked " +
                        "for operator reconciliation. No automatic refund was made.", failure)
                return
            }
        }

        // TODO: try to update signs and holo through tasks
    }

    override fun onPlace(e: BlockPlaceEvent, b: Block) {
        val item = e.itemInHand
        val meta = item.itemMeta
        val cache = PersistentDataAPI.get(meta, STORAGE_KEY, PersistentStorageCacheType.TYPE) ?: return

        b.location.save(cache)
        _caches[BlockPosition(b)] = cache
        // Some cores initialize the menu before the placement callback; others
        // schedule it afterwards. Cover both without opening or repainting the GUI.
        b.getBlockMenu()?.persistTemplate(cache)
    }

    override fun onBreak(e: BlockBreakEvent, menu: BlockMenu) {
        if (e.isCancelled) return
        val cache = _caches[menu.position]
        if (cache == null || cache.hasUnassignedItems() || cache.hasBlockedTransfer()) {
            e.isCancelled = true
            e.player.sendMessage("[InfinityExpansion2] Storage is unavailable or unverified. Ask an administrator to inspect it.")
            return
        }

        val loc = menu.location
        val dropLocation = loc.clone().add(0.5, 0.5, 0.5)
        val outputItem: ItemStack?
        val shell: ItemStack
        val remainder: Int
        val physicalSources: Map<Int, ItemStack?>
        val initialAmount: Int
        val initialTemplate: ItemStack?
        val initialVoid = cache.voidExcess

        // Complete all fallible serialization BEFORE input drops, world spawns,
        // removing the runtime cache, or changing the original stored amount.
        try {
            // Frozen physical snapshot; never modify the item in the live menu.
            physicalSources = (inputSlots + outputSlots).distinct().associateWith { menu.getItemInSlot(it)?.clone() }
            outputItem = physicalSources[outputSlots[0]]
            initialAmount = cache.amount
            initialTemplate = cache.itemStack?.clone()
            val (prepared, absorbed) = cache.snapshotPhysicalOutputForBreak(outputItem)
            remainder = if (outputItem == null || outputItem.isAir()) 0
                else (outputItem.amount - absorbed).coerceAtLeast(0)
            shell = item.clone()
            val meta = shell.itemMeta
            PersistentDataAPI.set(meta, STORAGE_KEY, PersistentStorageCacheType.TYPE, prepared)
            prepared.addLore(meta)
            shell.itemMeta = meta
            // The ItemStack setter may reject metadata or another plugin may
            // corrupt the serialized PDC. Prove the *actual dropped shell* can
            // deserialize the prepared ledger before touching physical slots.
            val readback = PersistentDataAPI.get(
                shell.itemMeta, STORAGE_KEY, PersistentStorageCacheType.TYPE)
            check(readback != null && readback.amount == prepared.amount
                && readback.limit == prepared.limit
                && readback.voidExcess == prepared.voidExcess
                && readback.itemStack == prepared.itemStack) {
                "IE2 storage shell metadata/PDC did not survive serialization"
            }

            // ItemStack equality includes stack quantity and full live metadata/PDC.
            // isSimilar alone would miss a changed template amount.
            val sameTemplate = initialTemplate == cache.itemStack
            val outputNow = menu.getItemInSlot(outputSlots[0])
            val sameOutput = if (outputItem == null || outputItem.isAir()) {
                outputNow == null || outputNow.isAir()
            } else {
                outputItem == outputNow
            }
            check(!e.isCancelled && !cache.hasBlockedTransfer()
                && cache.amount == initialAmount && cache.voidExcess == initialVoid && sameTemplate && sameOutput
                && physicalSources.all { (slot, original) ->
                    if (original.isAir()) menu.getItemInSlot(slot).isAir() else original == menu.getItemInSlot(slot)
                }) {
                "IE2 storage changed during break serialization"
            }
        } catch (failure: Throwable) {
            if (failure !is RuntimeException && failure !is LinkageError) throw failure
            // No physical source was touched; do not speculate about a refund.
            e.isCancelled = true
            InfinityExpansion2.instance.logger.log(java.util.logging.Level.SEVERE,
                "IE2 storage break preflight failed at $loc; physical slots untouched.", failure)
            e.player.sendMessage("[InfinityExpansion2] Storage break could not be verified. Ask an administrator.")
            return
        }

        // World item-spawn listeners can re-enter this block's handlers. Keep
        // the existing cross-direction ownership guard until every drop has
        // either completed or the cache has been quarantined.
        e.isCancelled = true
        if (!cache.beginWithdrawal()) {
            return
        }
        val drops = StorageBreakDropReceipt()
        val changedSlots = linkedSetOf<Int>()
        fun originalSourcesMatch() = _caches[menu.position] === cache
            && cache.amount == initialAmount && cache.itemStack == initialTemplate
            && cache.voidExcess == initialVoid
            && physicalSources.all { (slot, original) ->
                if (original.isAir()) menu.getItemInSlot(slot).isAir() else original == menu.getItemInSlot(slot)
            }
        fun spawn(stack: ItemStack) {
            drops.spawn(stack) { offered, capture ->
                // Register before ItemSpawnEvent, including cancelled spawns and
                // an exception thrown after the world has created the entity.
                loc.world.dropItem(dropLocation, offered) { created -> capture.accept(created) }
            }
        }
        try {
            check(originalSourcesMatch()) { "IE2 source changed before break drops" }
            for (slot in inputSlots) {
                val input = physicalSources[slot]
                if (!input.isAir()) spawn(input!!.clone())
            }
            if (remainder > 0 && outputItem != null) {
                spawn(outputItem.edit { amount(remainder) })
            }
            spawn(shell)
            check(originalSourcesMatch()) { "IE2 source changed during break drops" }
            drops.verify()

            // Retain every original physical source until all exact world
            // receipts pass. No input/output is dropped through an unchecked
            // helper that clears its slot after a cancelled spawn.
            for (slot in physicalSources.keys) {
                changedSlots.add(slot) // The setter may write before throwing.
                menu.replaceExistingItem(slot, ItemStack(Material.AIR))
                check(menu.getItemInSlot(slot).isAir()) { "IE2 break source slot was not cleared" }
            }
            menu.markDirty()
            check(physicalSources.keys.all { menu.getItemInSlot(it).isAir() }
                && _caches[menu.position] === cache && cache.amount == initialAmount
                && cache.itemStack == initialTemplate && cache.voidExcess == initialVoid) {
                "IE2 source changed during break commit"
            }
            drops.verify()
            check(_caches.remove(menu.position, cache)) { "IE2 break cache ownership changed" }
            e.isDropItems = false
            e.isCancelled = false
        } catch (failure: Throwable) {
            if (failure !is RuntimeException && failure !is LinkageError) throw failure
            // Remove only actual entities captured for this attempt. Restore
            // our cleared slots only after every entity is verified absent.
            // A failed/ambiguous removal never authorizes a duplicate refund.
            var restored = drops.rollback(failure)
            if (restored) {
                try {
                    for (slot in changedSlots) {
                        val actual = menu.getItemInSlot(slot)
                        val original = physicalSources[slot]
                        check(actual.isAir() || actual == original) {
                            "IE2 rollback would overwrite a foreign source-slot change"
                        }
                        menu.replaceExistingItem(slot, original?.clone() ?: ItemStack(Material.AIR))
                    }
                    if (changedSlots.isNotEmpty()) menu.markDirty()
                    check(originalSourcesMatch()) { "IE2 original source did not survive break rollback" }
                } catch (restoreFailure: Throwable) {
                    if (restoreFailure !is RuntimeException && restoreFailure !is LinkageError) throw restoreFailure
                    failure.addSuppressed(restoreFailure)
                    restored = false
                }
            }
            if (!restored) cache.blockUnverifiedTransfer()
            InfinityExpansion2.instance.logger.log(java.util.logging.Level.SEVERE,
                if (restored) "IE2 storage break rolled back at $loc; original contents retained."
                else "IE2 storage break is unverified at $loc; operator reconciliation required.", failure)
            e.player.sendMessage(if (restored) "[InfinityExpansion2] Storage break was cancelled; contents were preserved."
                else "[InfinityExpansion2] Unverified storage break. Ask an administrator.")
        } finally {
            cache.finishWithdrawal()
        }
    }

    private fun toggleVoid(menu: BlockMenu) {
        val cache = _caches[menu.position] ?: return
        if (cache.hasBlockedTransfer()) return
        cache.voidExcess = !cache.voidExcess
        menu.updateDisplay(cache)
        menu.location.save(cache)
    }

    /**
     * Handle interaction.
     */
    private fun interaction(menu: BlockMenu, p: Player, mode: InteractionMode) {
        val cache = _caches[menu.position] ?: return
        // A surviving count is not proof of what item it represented. Do not
        // relabel that balance using an arbitrary output stack or player deposit.
        if (cache.hasUnassignedItems() || cache.hasBlockedTransfer()) {
            p.sendMessage("[InfinityExpansion2] This storage has an unverified item identity or transfer. Ask an administrator to inspect it.")
            return
        }
        val pInv = p.inventory

        if (!INTERACTION_COOLDOWN.check(p.uniqueId)) {
            InfinityExpansion2.integrationService.sendMessage(p, "storage.interaction-cd")
        } else {
            INTERACTION_COOLDOWN.set(p.uniqueId, INTERACTION_CD)
        }

        when (mode) {
            InteractionMode.DEPOSIT -> {
                val outputItem = menu.getItemInSlot(outputSlots[0])

                // empty storage && no item in output
                if (cache.isEmpty() && outputItem.isAir()) return

                // Fix: only set the item type if storage is actually empty
                if (cache.isEmpty()) {
                    cache.itemStack = outputItem.edit { amount(1) }
                }

                // storageContents may contain detached items. Use real player slot
                // setters and post-write reads instead of mutating getter results.
                for (slot in pInv.storageContents.indices) {
                    val source = pInv.getItem(slot) ?: continue
                    if (source.isBlacklisted() || !cache.matches(source)) continue

                    val consumed = try {
                        StorageInputReceipt.transfer(
                            cache,
                            { pInv.getItem(slot) },
                            { remainder -> pInv.setItem(slot, remainder) },
                            { },
                            { requested -> menu.inputItem(cache, requested) }
                        )
                    } catch (failure: RuntimeException) {
                        holdFailedInput(menu, cache, failure)
                        p.sendMessage("[InfinityExpansion2] This storage input could not be verified. Ask an administrator to inspect it.")
                        return // Stop player slot processing after an incident.
                    }
                    if (consumed > 0) {
                        menu.updateDisplay(cache)
                        menu.location.save(cache)
                    }
                }
            }

            InteractionMode.WITHDRAW_SINGLE -> withdrawToPlayer(menu, cache, p, false)
            InteractionMode.WITHDRAW_ALL -> withdrawToPlayer(menu, cache, p, true)
        }
    }

    private fun holdFailedInput(menu: BlockMenu, cache: StorageCache, failure: RuntimeException) {
        cache.blockUnverifiedTransfer()
        try {
            menu.location.save(cache)
        } catch (saveFailure: RuntimeException) {
            failure.addSuppressed(saveFailure)
        }
        InfinityExpansion2.instance.logger.log(java.util.logging.Level.SEVERE,
            "Unverified storage input at ${menu.location}; storage blocked. " +
                "Preserve logs and source inventory for administrator reconciliation before restarting.",
            failure)
    }

    private fun withdrawToPlayer(menu: BlockMenu, cache: StorageCache, player: Player, all: Boolean) {
        try {
            val persist = Runnable { menu.location.save(cache) }
            if (all) StorageWithdrawal.transferAll(cache, player.inventory, persist)
            else StorageWithdrawal.transfer(cache, player.inventory, cache.itemStack?.maxStackSize ?: 0, persist)
            if (!cache.hasBlockedTransfer()) menu.updateDisplay(cache)
        } catch (failure: IllegalStateException) {
            // Also save a conservative reservation when the destination threw. Never let a
            // clean reload restore a balance which might already exist in player inventory.
            try {
                menu.location.save(cache)
            } catch (saveFailure: RuntimeException) {
                failure.addSuppressed(saveFailure)
            }
            InfinityExpansion2.instance.logger.log(java.util.logging.Level.SEVERE,
                "Unverified player withdrawal at ${menu.location}; storage is blocked. Preserve logs and " +
                    "inspect the stored/player balances before restarting. No automatic item refund was made.", failure)
            player.sendMessage("[InfinityExpansion2] This storage transfer could not be verified. Ask an administrator to inspect it.")
        }
    }

    /**
     * Create the [StorageCache] instance based on the given [menu] and add it to the cache map.
     */
    private fun addCache(menu: BlockMenu): StorageCache {
        val loc = menu.location
        val amount = loc.getInt(BS_AMOUNT)
        val voidExcess = loc.getBoolean(BS_VOID)
        val item: ItemStack? = menu.getItemInSlot(ITEM_SLOT)

        val cache = createCache(item, menu, amount, voidExcess)
        Debug.log(DebugCase.STORAGE_UNIT, "created cache at $loc: $cache")
        if (cache.hasUnassignedItems()) {
            // Report once per cache load, not on every ticker or transfer attempt.
            InfinityExpansion2.instance.logger.severe(
                "Storage Unit at $loc retains ${cache.amount} item(s) without a saved item identity. " +
                    "Input, interaction and player-break operations are blocked. Preserve the storage and backups for inspection; no replacement type was inferred."
            )
        }

        _caches[menu.position] = cache
        return cache
    }

    /**
     * Create a new [StorageCache] instance with the given [item], [amount], [capacity], and [voidExcess].
     */
    private fun createCache(item: ItemStack?, menu: BlockMenu, amount: Int, voidExcess: Boolean): StorageCache {
        return if (item.isAir() || isSimilar(item, NO_ITEM)) {
            menu.addItem(ITEM_SLOT, NO_ITEM)
            StorageCache(null, amount, capacity, voidExcess)
        } else {
            val cache = StorageCache(item.removeDisplayItem(), amount, capacity, voidExcess)

            menu.updateDisplay(cache, true)
            cache
        }
    }

    override fun canStack(meta1: ItemMeta, meta2: ItemMeta) =
        meta1.persistentDataContainer == meta2.persistentDataContainer

    override fun getInfoItems() = listOf(
        capacityItem(capacity)
    )

    companion object {

        // layout
        private val LAYOUT = MenuLayout.STORAGE_UNIT

        // slots
        const val AMOUNT_SLOT = 4
        const val ITEM_SLOT = 13
        const val ACTION_VOID_SLOT = 27
        const val ACTION_DEPOSIT_SLOT = 28
        const val ACTION_WITHDRAW_SINGLE_SLOT = 34
        const val ACTION_WITHDRAW_ALL_SLOT = 35

        // block storage keys
        const val BS_AMOUNT = "stored_amount"
        const val BS_VOID = "void_excess"

        // inv interaction modes
        enum class InteractionMode {

            DEPOSIT,
            WITHDRAW_SINGLE,
            WITHDRAW_ALL
        }

        // PDC key
        val STORAGE_KEY = ie2Key("storage")

        // gui items
        private val ACTION_DEPOSIT_ITEM = InfinityExpansion2.localization.getGuiItem(
            // https://minecraft-heads.com/custom-heads/head/106284-elevator-arrow-button-down-green
            "5815b66ce57add6abc82082cf81debed843d9ff36d920f1a482a038bf3cf1b6d".asMaterialType(),
            "storage_deposit"
        )
        private val ACTION_WITHDRAW_SINGLE_ITEM = InfinityExpansion2.localization.getGuiItem(
            // https://minecraft-heads.com/custom-heads/head/106285-elevator-arrow-up-red
            "a979cca43315030d76fa4112e0e6b809a4f463872cc1787c86659917fd94e8a0".asMaterialType(),
            "storage_withdraw_single"
        )
        private val ACTION_WITHDRAW_ALL_ITEM = InfinityExpansion2.localization.getGuiItem(
            // https://minecraft-heads.com/custom-heads/head/106283-elevator-arrow-up-green
            "5fa1c6c7ead75a04585395f63135dc96fa078fb920484699ef8e564e142d64cb".asMaterialType(),
            "storage_withdraw_all"
        )
        private val NO_ITEM = InfinityExpansion2.localization.getGuiItem(
            Material.BARRIER.asMaterialType(),
            "storage_no_item"
        ).toDisplayItem()
        private val VOID_ON_ITEM = InfinityExpansion2.localization.getGuiItem(
            // https://minecraft-heads.com/custom-heads/head/587-void-charge
            "32697929465950ccd3a4923a5e6eb15b4fe2ba2b28715f4a7996785d706158".asMaterialType(),
            "storage_void_excess_on"
        ).toDisplayItem()
        private val VOID_OFF_ITEM = InfinityExpansion2.localization.getGuiItem(
            // https://minecraft-heads.com/custom-heads/head/51667-storage-component
            "3dbbf5ef39cfe42775915a2494e1814ca123966c2b74bf5debc07195bc213795".asMaterialType(),
            "storage_void_excess_off"
        ).toDisplayItem()

        private fun amountItem(amount: Int, limit: Int) = InfinityExpansion2.localization.getGuiItem(
            Material.BARREL.asMaterialType(),
            "storage_amount",
            "&7$amount / $limit"
        ).toDisplayItem()

        private fun capacityItem(capacity: Int) = InfinityExpansion2.localization.getGuiItem(
            Material.BARREL.asMaterialType(),
            "storage_capacity",
            "&7$capacity"
        ).toDisplayItem()

        // per player interaction cd
        private val INTERACTION_COOLDOWN = Cooldown<UUID>()
        const val INTERACTION_CD = 1000L // 1s

        /**
         * Check if the [ItemStack] is not allowed in the storage unit.
         */
        fun ItemStack.isBlacklisted() = isAir()
            || type.maxDurability < 0
            || Tag.SHULKER_BOXES.isTagged(type)
            || IETag.BUNDLE.isTagged(type)
            || isSlimefunItem<StorageUnit>()

        /**
         * Handles [ItemStack] input.
         *
         * Return the amount of items that should be consumed
         */
        fun BlockMenu.inputItem(cache: StorageCache, item: ItemStack, amount: Int = item.amount): Int {
            val ownedInput = cache.permitsInputCredit(item, amount)
            if (item.isBlacklisted() || cache.hasUnassignedItems() ||
                (cache.hasBlockedTransfer() && !ownedInput)) return 0
            Debug.log(DebugCase.STORAGE_UNIT, "storage unit input attempt with item: $item, cache: $cache")

            if (cache.isEmpty()) {
                cache.itemStack = item.edit { amount(1) }
                // Persist the existing template slot before crediting a new
                // balance. This must not depend on a player opening the GUI.
                persistTemplate(cache)
                val leftover = cache.increaseAmount(amount)
                Debug.log(DebugCase.STORAGE_UNIT, "empty cache, updated: $cache, leftover: $leftover")
                location.save(cache)
                return item.amount - leftover
            } else if (cache.matches(item)) {
                val leftover = cache.increaseAmount(amount)
                Debug.log(DebugCase.STORAGE_UNIT, "item matches, updated cache: $cache, leftover: $leftover")
                location.save(cache)
                return item.amount - leftover
            }

            Debug.log(DebugCase.STORAGE_UNIT, "cache not empty and item not match")
            return 0
        }

        /**
         * Handle item output.
         *
         * A full [ItemStack] is expected.
         */
        fun BlockMenu.outputItem(cache: StorageCache): ItemStack? {
            if (cache.hasBlockedTransfer() || cache.isEmpty()) return null
            return outputItem(cache, cache.itemStack!!.maxStackSize)
        }

        /**
         * Handle item output.
         *
         * Custom amount is expected, but only a single [ItemStack] is returned.
         */
        fun BlockMenu.outputItem(cache: StorageCache, amount: Int): ItemStack? {
            if (amount <= 0 || cache.hasBlockedTransfer() || cache.isEmpty()) return null

            val decreaseAmount = amount.coerceAtMost(cache.amount)
            val item = cache.itemStack!!.edit { amount(decreaseAmount) }
            cache.amount -= decreaseAmount
            location.save(cache)
            return item
        }

        /**
         * Keep the historical persisted template slot current, independently
         * of optional GUI rendering. Called on assignment/initialization only,
         * not every idle tick. Unchanged templates avoid repeat menu writes.
         */
        private fun BlockMenu.persistTemplate(cache: StorageCache) {
            val template = cache.itemStack ?: return
            if (template.isAir()) return
            val existing = getItemInSlot(ITEM_SLOT)
            // Display markers contain a random byte. Compare decoded identity
            // before encoding, so that randomness cannot force redundant writes.
            if (!existing.isAir() && existing.amount == 1 && existing.isDisplayItem() &&
                existing.removeDisplayItem().isSimilar(template)) return

            val encoded = StorageTemplatePersistence.snapshot(template).toDisplayItem()
            encoded.amount = 1
            check(encoded.removeDisplayItem().isSimilar(template)) {
                "Storage template display encoding changed the stored item identity"
            }
            StorageTemplatePersistence.update(
                encoded,
                { getItemInSlot(ITEM_SLOT) },
                { replaceExistingItem(ITEM_SLOT, it) },
                { markDirty() }
            )
        }

        /**
         * Update all the display items in [BlockMenu].
         */
        private fun BlockMenu.updateDisplay(cache: StorageCache, forceUpdate: Boolean = false) {
            if (!forceUpdate && !hasViewer()) return

            // item slot
            if (cache.isEmpty()) {
                replaceExistingItem(ITEM_SLOT, NO_ITEM)
            } else {
                val item = cache.itemStack!!.toDisplayItem()
                item.amount = 1
                replaceExistingItem(ITEM_SLOT, item)
            }

            // amount slot
            replaceExistingItem(AMOUNT_SLOT, amountItem(cache.amount, cache.limit))

            // void slot
            replaceExistingItem(ACTION_VOID_SLOT, if (cache.voidExcess) VOID_ON_ITEM else VOID_OFF_ITEM)
        }

        /**
         * Save the [StorageCache] to block storage.
         */
        private fun Location.save(cache: StorageCache) {
            setInt(BS_AMOUNT, cache.amount)
            setBoolean(BS_VOID, cache.voidExcess)
        }

        @Suppress("unused")
        fun hudHandler(request: HudRequest): String {
            val menu = request.location.getBlockMenu()
            val storage = request.slimefunItem as StorageUnit
            val cache = storage.caches[menu.position] ?: return "Invalid Storage Unit"

            if (cache.isEmpty()) return "Empty"

            val response = mutableListOf<String>()
            response.add(InfinityExpansion2.integrationService.getTranslatedItemName(request.player, cache.itemStack!!))
            response.add("${cache.amount} / ${cache.limit}")

            return response.joinToString("&7 | ")
        }
    }
}
