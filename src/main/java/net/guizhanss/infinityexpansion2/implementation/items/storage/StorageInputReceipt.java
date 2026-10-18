package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * Verifies a physical source-slot debit BEFORE accepting a StorageCache credit.
 *
 * The reader/writer must address the same authoritative player/menu slot.
 * No slot reference or item snapshot is retained after this operation.
 */
public final class StorageInputReceipt {
    private static final Set<StorageCache> ACTIVE =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private StorageInputReceipt() {
    }

    public static int transfer(
        StorageCache cache,
        Supplier<ItemStack> sourceReader,
        Consumer<ItemStack> sourceWriter,
        Runnable afterDebit,
        Function<ItemStack, Integer> credit) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(sourceReader, "sourceReader");
        Objects.requireNonNull(sourceWriter, "sourceWriter");
        Objects.requireNonNull(afterDebit, "afterDebit");
        Objects.requireNonNull(credit, "credit");

        synchronized (ACTIVE) {
            if (!ACTIVE.add(cache)) {
                throw new IllegalStateException("Recursive IE2 storage input into the same cache");
            }
        }
        try {
            if (cache.hasUnassignedItems()) return 0;
            ItemStack original = detached(sourceReader.get());
            if (original == null || original.getAmount() <= 0) return 0;
            if (!cache.isEmpty() && !cache.matches(original)) return 0;

            int before = cache.getAmount();
            int limit = cache.getLimit();
            if (before < 0 || before > limit || limit < 0) {
                throw new IllegalStateException("Invalid IE2 storage balance or capacity");
            }

            int available = cache.getVoidExcess()
                ? original.getAmount() // Preserve intentional overflow voiding.
                : Math.max(0, limit - before);
            int planned = Math.min(original.getAmount(), available);
            if (planned <= 0) return 0;

            ItemStack expectedRemainder = original.getAmount() == planned
                ? null : original.clone();
            if (expectedRemainder != null) {
                expectedRemainder.setAmount(original.getAmount() - planned);
            }

            // Write a fresh detached remainder, never mutate the item returned
            // by getStorageContents(), getItemInSlot(), or another getter.
            sourceWriter.accept(detached(expectedRemainder));
            requireSlot(expectedRemainder, sourceReader.get());

            // A menu dirty callback may alter a slot; verify it as well before credit.
            afterDebit.run();
            requireSlot(expectedRemainder, sourceReader.get());
            if (cache.getAmount() != before) {
                throw new IllegalStateException("Storage cache changed during source debit");
            }

            ItemStack offered = original.clone();
            offered.setAmount(planned);
            Integer acknowledged = credit.apply(offered);
            if (acknowledged == null || acknowledged != planned) {
                throw new IllegalStateException("Storage did not acknowledge the exact debited input");
            }

            // Recheck after template persistence and block-storage callbacks.
            requireSlot(expectedRemainder, sourceReader.get());
            long expectedBalance = Math.min((long) limit, (long) before + planned);
            if (cache.getAmount() != expectedBalance || !cache.matches(original)) {
                throw new IllegalStateException("Storage balance/identity changed during input credit");
            }
            return planned;
        } finally {
            synchronized (ACTIVE) {
                ACTIVE.remove(cache);
            }
        }
    }

    private static ItemStack detached(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemStack copy = stack.clone();
        if (copy == stack || !copy.isSimilar(stack) || copy.getAmount() != stack.getAmount()) {
            throw new IllegalStateException("Unable to snapshot source item identity");
        }
        return copy;
    }

    private static void requireSlot(ItemStack expected, ItemStack observed) {
        boolean expectedEmpty = expected == null || expected.getType().isAir();
        boolean observedEmpty = observed == null || observed.getType().isAir();
        if (expectedEmpty && observedEmpty) return;
        if (expectedEmpty || observedEmpty ||
            expected.getAmount() != observed.getAmount() || !expected.isSimilar(observed)) {
            throw new IllegalStateException("IE2 input source slot did not retain verified debit");
        }
    }
}
