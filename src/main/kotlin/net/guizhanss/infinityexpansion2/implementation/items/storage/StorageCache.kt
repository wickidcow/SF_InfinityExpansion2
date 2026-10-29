package net.guizhanss.infinityexpansion2.implementation.items.storage

import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils
import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.utils.constant.Strings
import net.guizhanss.infinityexpansion2.utils.items.ModernText
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class StorageCache(
    stack: ItemStack?,
    var amount: Int,
    val limit: Int,
    var voidExcess: Boolean,
) {

    // Runtime ownership/incident guard only; preserve the existing storage data format.
    @Volatile private var withdrawalActive = false
    @Volatile private var inputActive = false
    @Volatile private var transferUnverified = false
    private var allowedInputAmount = 0
    private var allowedInputIdentity: ItemStack? = null
    private var inputCreditConsumed = false

    /**
     * A migrated or corrupted balance must not be silently clamped: its physical
     * owners cannot be reconstructed by choosing a new capacity or template.
     * Keep the original amount for administrator reconciliation.
     */
    fun hasInvalidBalance() = limit <= 0 || amount < 0 || amount > limit

    fun hasBlockedTransfer() = withdrawalActive || inputActive || transferUnverified || hasInvalidBalance()

    @Synchronized
    fun beginInputReceipt(): Boolean {
        if (inputActive) throw IllegalStateException("Recursive IE2 storage input into the same cache")
        if (hasBlockedTransfer()) return false
        inputActive = true
        return true
    }

    @Synchronized
    fun authorizeInputCredit(item: ItemStack, amount: Int) {
        check(inputActive && !withdrawalActive && !transferUnverified
            && allowedInputAmount == 0 && !inputCreditConsumed && amount > 0) {
            "Input credit cannot be authorized more than once"
        }
        allowedInputAmount = amount
        allowedInputIdentity = item.clone()
    }

    @Synchronized
    fun permitsInputCredit(item: ItemStack, amount: Int): Boolean =
        inputActive && !withdrawalActive && !transferUnverified && !inputCreditConsumed &&
            allowedInputAmount == amount && allowedInputIdentity?.isSimilar(item) == true

    @Synchronized
    fun wasInputCreditConsumed() = inputCreditConsumed

    @Synchronized
    fun quarantineFailedInput(previousBalance: Int) {
        // Do not preserve a possible double-credit or auto-refund an ambiguous source.
        if (amount > previousBalance) amount = previousBalance
        transferUnverified = true
        allowedInputAmount = 0
        allowedInputIdentity = null
    }

    @Synchronized
    fun finishInputReceipt() {
        inputActive = false
        allowedInputAmount = 0
        allowedInputIdentity = null
        inputCreditConsumed = false
    }

    @Synchronized
    internal fun beginWithdrawal(): Boolean {
        if (hasBlockedTransfer()) return false
        withdrawalActive = true
        return true
    }

    @Synchronized
    internal fun finishWithdrawal() {
        withdrawalActive = false
    }

    internal fun blockUnverifiedTransfer() {
        transferUnverified = true
    }

    private var _meta: ItemMeta? = null
    private var metaCached: Boolean = false

    var itemStack: ItemStack? = stack
        internal set(value) {
            field = value
            metaCached = false
            _meta = null
        }

    val itemMeta: ItemMeta?
        get() {
            if (_meta == null && !metaCached) {
                _meta = if (itemStack != null && itemStack!!.hasItemMeta()) itemStack!!.itemMeta else null
                metaCached = !metaCached
            }
            return _meta
        }

    fun isEmpty() = amount == 0 || itemStack.isAir()

    /** A positive legacy balance without its identity is not empty, seedable storage. */
    fun hasUnassignedItems() = amount > 0 && itemStack.isAir()

    /**
     * Check if the current [ItemStack] matches the storage unit
     */
    fun matches(stack: ItemStack?): Boolean {
        if (stack == null) return itemStack == null

        return itemStack?.type == stack.type
            && (itemMeta != null) == stack.hasItemMeta()
            && (itemMeta == null || itemMeta == stack.itemMeta)
    }

    /**
     * Increase the amount of the storage unit by the given amount.
     * Returns leftover amount.
     */
    @Synchronized
    fun increaseAmount(amount: Int): Int {
        if (amount <= 0) return amount
        // Check BEFORE consuming any authorized one-use input receipt.
        // Void-excess applies to NEW stock, never to already-persisted items.
        if (hasInvalidBalance()) return amount
        if (inputActive) {
            if (allowedInputAmount != amount || inputCreditConsumed
                || withdrawalActive || transferUnverified) return amount
            allowedInputAmount = 0
            allowedInputIdentity = null
            inputCreditConsumed = true
        } else if (hasBlockedTransfer()) {
            return amount
        }
        val newAmount: Long = this.amount.toLong() + amount.toLong()
        if (newAmount > limit) {
            this.amount = limit
            if (!voidExcess) return (newAmount - limit).toInt()
        } else {
            this.amount = newAmount.toInt()
        }
        return 0
    }

    /**
     * Prepare a non-mutating output recovery receipt for block breaking.
     * Physical output was ALREADY debited from storage; input Void Excess is
     * never an acceptable reason to destroy that existing output.
     * The same persisted fields and item template are retained.
     */
    @Synchronized
    fun snapshotPhysicalOutputForBreak(output: ItemStack?): Pair<StorageCache, Int> {
        check(!hasUnassignedItems() && !hasBlockedTransfer())
        // StorageCache.matches() uses a memoized ItemMeta; an in-place template edit
        // can leave that memo stale. Freeze ONE fresh template clone for both the
        // identity test and the item persisted in the returned storage shell.
        val liveTemplate = itemStack?.clone()
        val absorbed = if (output != null && !output.isAir() && output.amount > 0
            && liveTemplate != null && liveTemplate.isSimilar(output)) {
            minOf(output.amount.toLong(), (limit.toLong() - amount.toLong()).coerceAtLeast(0L)).toInt()
        } else 0
        return StorageCache(liveTemplate, amount + absorbed, limit, voidExcess) to absorbed
    }

    /**
     * Add the lore to the target [ItemMeta] of the storage unit
     */
    fun addLore(meta: ItemMeta) {
        // no need to add lore if the storage unit is empty
        if (isEmpty()) return

        ModernText.appendLore(
            meta,
            "",
            InfinityExpansion2.localization.getLore("storage.item", ItemUtils.getItemName(itemStack)),
            InfinityExpansion2.localization.getLore("storage.amount", "$amount / $limit"),
            InfinityExpansion2.localization.getLore(
                "storage.void-excess",
                if (voidExcess) Strings.CHECK else Strings.CROSS
            )
        )
    }

    override fun toString() = "StorageCache(itemStack=$itemStack, amount=$amount, limit=$limit, voidExcess=$voidExcess)"
}
