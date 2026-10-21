package net.guizhanss.infinityexpansion2.implementation.items.storage

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** Player withdrawals reserve the source before any destination write and verify real inventory gains. */
internal object StorageWithdrawal {
    @JvmStatic
    @JvmOverloads
    fun transfer(cache: StorageCache, inventory: Inventory, requested: Int, persist: Runnable = Runnable {}): Int =
        transferBounded(cache, inventory, requested, true, persist)

    /** One bounded inventory pass replaces repeated scans and direct mutation of partial-stack references. */
    @JvmStatic
    @JvmOverloads
    fun transferAll(cache: StorageCache, inventory: Inventory, persist: Runnable = Runnable {}): Int =
        transferBounded(cache, inventory, Int.MAX_VALUE, false, persist)

    private fun transferBounded(
        cache: StorageCache,
        inventory: Inventory,
        requested: Int,
        singleStack: Boolean,
        persist: Runnable,
    ): Int {
        if (requested <= 0 || !cache.beginWithdrawal()) return 0
        var reservedBalance = -1
        try {
            val template = cache.itemStack ?: return 0
            if (cache.amount <= 0 || template.type.isAir) return 0
            val identity = StorageTemplatePersistence.snapshot(template)
            val originalBalance = cache.amount
            val before = snapshot(inventory.storageContents)
            verifySource(cache, originalBalance, identity)
            val stackLimit = minOf(identity.maxStackSize, inventory.maxStackSize)
            check(stackLimit > 0) { "Invalid player inventory stack limit" }
            val capacity = before.sumOf { stack ->
                when {
                    stack == null -> stackLimit.toLong()
                    identity.isSimilar(stack) -> (stackLimit - stack.amount).coerceAtLeast(0).toLong()
                    else -> 0L
                }
            }
            val amount = minOf(originalBalance.toLong(), requested.toLong(), capacity,
                if (singleStack) stackLimit.toLong() else Int.MAX_VALUE.toLong()).toInt()
            if (amount <= 0) return 0
            val offers = Array((amount - 1) / stackLimit + 1) { index ->
                StorageTemplatePersistence.snapshot(identity).apply {
                    this.amount = minOf(stackLimit, amount - index * stackLimit)
                }
            }

            // Persist the conservative reservation BEFORE a plugin-owned inventory can write then throw.
            // This uses the existing block-data save callback; it is not an fsync/crash-atomicity claim.
            reservedBalance = originalBalance - amount
            cache.amount = reservedBalance
            persist.run()
            verifySource(cache, reservedBalance, identity)
            verifyUnchanged(before, inventory.storageContents)

            // addItem is allowed to mutate its arguments. Neither those arguments nor its
            // returned leftovers are evidence of what the authoritative inventory received.
            inventory.addItem(*offers)
            val after = snapshot(inventory.storageContents)
            val received = verifiedGain(before, after, identity, amount)
            verifySource(cache, reservedBalance, identity)

            cache.amount = originalBalance - received
            persist.run()
            verifySource(cache, originalBalance - received, identity)
            verifyUnchanged(after, inventory.storageContents)
            return received
        } catch (failure: RuntimeException) {
            failClosed(cache, reservedBalance, failure)
        } catch (failure: LinkageError) {
            failClosed(cache, reservedBalance, failure)
        } finally {
            cache.finishWithdrawal()
        }
    }

    private fun snapshot(contents: Array<ItemStack?>): Array<ItemStack?> =
        Array(contents.size) { index ->
            val stack = contents[index]
            if (stack == null || stack.type.isAir || stack.amount == 0) null
            else {
                check(stack.amount > 0) { "Negative player inventory stack" }
                StorageTemplatePersistence.snapshot(stack)
            }
        }

    private fun verifySource(cache: StorageCache, expected: Int, identity: ItemStack) {
        val current = cache.itemStack
        check(cache.amount == expected && current != null && identity.isSimilar(current)) {
            "Storage balance or identity changed during player withdrawal"
        }
    }

    private fun verifyUnchanged(expected: Array<ItemStack?>, actual: Array<ItemStack?>) {
        check(expected.size == actual.size) { "Player inventory shape changed" }
        for (index in expected.indices) {
            val before = expected[index]
            val after = actual[index]
            check(if (before == null) empty(after)
                else after != null && before.amount == after.amount && before.isSimilar(after)) {
                "Player inventory changed during withdrawal persistence"
            }
        }
    }

    private fun verifiedGain(
        before: Array<ItemStack?>, after: Array<ItemStack?>, identity: ItemStack, maximum: Int,
    ): Int {
        check(before.size == after.size) { "Player inventory shape changed during insertion" }
        var gained = 0L
        for (index in before.indices) {
            val old = before[index]
            val current = after[index]
            if (old == null && current == null) continue
            if (old != null && current != null && old.amount == current.amount && old.isSimilar(current)) continue
            check(current != null && identity.isSimilar(current)
                && (old == null || identity.isSimilar(old))) {
                "Unrelated item changed during player inventory insertion"
            }
            val delta = current.amount - (old?.amount ?: 0)
            check(delta >= 0 && current.amount <= current.maxStackSize) {
                "Player inventory insertion removed items or overfilled a stack"
            }
            gained += delta
            check(gained <= maximum) { "Player inventory gained more than the reserved source" }
        }
        return gained.toInt()
    }

    private fun empty(stack: ItemStack?) = stack == null || stack.type.isAir || stack.amount == 0

    private fun failClosed(cache: StorageCache, reservedBalance: Int, failure: Throwable): Nothing {
        // Revoke any refund if a later read/save callback invalidated its receipt. Never increase
        // a balance that a callback reduced, invent an item refund, or overwrite player inventory.
        if (reservedBalance >= 0 && cache.amount > reservedBalance) cache.amount = reservedBalance
        cache.blockUnverifiedTransfer()
        throw IllegalStateException(
            "IE2 player withdrawal is unverified; storage is blocked and no speculative refund was created", failure)
    }
}
