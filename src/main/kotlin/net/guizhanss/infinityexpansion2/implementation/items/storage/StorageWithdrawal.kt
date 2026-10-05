package net.guizhanss.infinityexpansion2.implementation.items.storage

import org.bukkit.inventory.Inventory

/** Transfers one requested stack while retaining ownership of any inventory leftovers. */
internal object StorageWithdrawal {

    @JvmStatic
    fun transfer(cache: StorageCache, inventory: Inventory, requested: Int): Int {
        val template = cache.itemStack ?: return 0
        if (requested <= 0 || cache.amount <= 0 || template.type.isAir) return 0

        val amount = minOf(requested, cache.amount, template.maxStackSize)
        val offered = template.clone().apply { this.amount = amount }
        val leftovers = inventory.addItem(offered)
        // Some inventories mutate the offered stack, so use the amount captured before addItem.
        val transferred = amount - leftovers.values.sumOf { it.amount }
        cache.amount -= transferred
        return transferred
    }
}
