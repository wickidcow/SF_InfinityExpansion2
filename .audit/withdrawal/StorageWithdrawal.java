package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Accounts for partial insertion using Bukkit's returned leftovers, not its mutable input. */
final class StorageWithdrawal {
    private StorageWithdrawal() {}

    /**
     * Moves at most requested items and debits exactly the accepted amount.
     * Call on the inventory's owning server thread. Persistence and display updates remain
     * with the storage-unit caller; this is not a transaction across plugins or a server crash.
     */
    static int transfer(Inventory inventory, StorageCache cache, int requested) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(cache, "cache");
        ItemStack template = cache.getItemStack();
        if (requested <= 0 || cache.getAmount() <= 0 || template == null || template.getType().isAir()) {
            return 0;
        }

        int offered = Math.min(requested, cache.getAmount());
        ItemStack output = template.clone();
        output.setAmount(offered);
        var leftovers = inventory.addItem(output);
        long remaining = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getType().isAir() || leftover.getAmount() < 0) {
                throw new IllegalStateException("Inventory returned an invalid withdrawal remainder");
            }
            remaining += leftover.getAmount();
        }
        if (remaining > offered) {
            throw new IllegalStateException("Inventory returned more items than were offered");
        }
        int accepted = offered - (int) remaining;
        if (accepted > 0) {
            cache.setAmount(cache.getAmount() - accepted);
        }
        return accepted;
    }
}
