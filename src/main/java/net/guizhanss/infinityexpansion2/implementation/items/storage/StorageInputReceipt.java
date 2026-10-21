package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Objects;
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

        if (!cache.beginInputReceipt()) return 0;

        int balanceAtEntry = 0;
        boolean balanceCaptured = false;
        boolean sourceMayHaveChanged = false;
        try {
            balanceAtEntry = cache.getAmount();
            balanceCaptured = true;
            final boolean voidAtEntry = cache.getVoidExcess();
            final ItemStack templateAtEntry = detached(cache.getItemStack());

            if (cache.hasUnassignedItems()) return 0;
            sourceMayHaveChanged = true; // A source getter may invoke a plugin callback.
            ItemStack original = detached(sourceReader.get());
            requirePrecreditState(cache, balanceAtEntry, voidAtEntry, templateAtEntry);
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
            requirePrecreditState(cache, balanceAtEntry, voidAtEntry, templateAtEntry);
            afterDebit.run();
            requireSlot(expectedRemainder, sourceReader.get());
            requirePrecreditState(cache, balanceAtEntry, voidAtEntry, templateAtEntry);

            ItemStack offered = original.clone();
            offered.setAmount(planned);
            cache.authorizeInputCredit(offered, planned);
            Integer acknowledged = credit.apply(offered);
            if (acknowledged == null || acknowledged != planned || !cache.wasInputCreditConsumed()) {
                throw new IllegalStateException("Storage did not acknowledge one exact debited input");
            }

            // Recheck after template persistence and block-storage callbacks.
            requireSlot(expectedRemainder, sourceReader.get());
            long expectedBalance = Math.min((long) limit, (long) before + planned);
            // The cache's memoized ItemMeta can be stale after a callback mutates
            // the live ItemStack in place. Compare the actual template too.
            ItemStack storedTemplate = cache.getItemStack();
            if (cache.getAmount() != expectedBalance || !cache.matches(original)
                || storedTemplate == null || !storedTemplate.isSimilar(original)
                || cache.getVoidExcess() != voidAtEntry) {
                throw new IllegalStateException("Storage balance/identity/void policy changed during input credit");
            }
            // A verified zero balance may be seeded with the exact incoming type.
            // Keep the old identity immutable only while it represents stored stock;
            // the exact incoming-template checks above still protect a fresh seed.
            if (balanceAtEntry > 0 && templateAtEntry != null) {
                requireTemplate(templateAtEntry, cache.getItemStack());
            }
            return planned;
        } catch (RuntimeException | LinkageError failure) {
            if (sourceMayHaveChanged && balanceCaptured) {
                cache.quarantineFailedInput(balanceAtEntry);
            }
            throw failure;
        } finally {
            cache.finishInputReceipt();
        }
    }

    private static void requirePrecreditState(
        StorageCache cache, int balance, boolean voidExcess, ItemStack template) {
        if (cache.getAmount() != balance || cache.getVoidExcess() != voidExcess) {
            throw new IllegalStateException("IE2 cached balance or void-excess policy changed during input debit");
        }
        requireTemplate(template, cache.getItemStack());
    }

    private static void requireTemplate(ItemStack expected, ItemStack current) {
        if (expected == null && (current == null || current.getType().isAir())) return;
        if (expected == null || current == null || current.getType().isAir()
            || expected.getAmount() != current.getAmount() || !expected.isSimilar(current)) {
            throw new IllegalStateException("IE2 storage template identity changed during input transaction");
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
