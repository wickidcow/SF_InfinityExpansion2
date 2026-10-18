@file:Suppress("deprecation")

package net.guizhanss.infinityexpansion2.implementation.items.storage

import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils
import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.utils.constant.Strings
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

    fun hasBlockedTransfer() = withdrawalActive || inputActive || transferUnverified

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
     * Add the lore to the target [ItemMeta] of the storage unit
     */
    fun addLore(meta: ItemMeta) {
        // no need to add lore if the storage unit is empty
        if (isEmpty()) return

        val lore = if (meta.hasLore()) meta.lore!! else mutableListOf()

        lore.add("")
        lore.add(InfinityExpansion2.localization.getLore("storage.item", ItemUtils.getItemName(itemStack)))
        lore.add(InfinityExpansion2.localization.getLore("storage.amount", "$amount / $limit"))
        lore.add(
            InfinityExpansion2.localization.getLore(
                "storage.void-excess",
                if (voidExcess) Strings.CHECK else Strings.CROSS
            )
        )

        meta.lore = lore
    }

    override fun toString() = "StorageCache(itemStack=$itemStack, amount=$amount, limit=$limit, voidExcess=$voidExcess)"
}
