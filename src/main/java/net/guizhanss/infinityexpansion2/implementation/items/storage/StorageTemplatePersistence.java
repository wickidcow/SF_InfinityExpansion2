package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/** Verified writes of the existing protected template slot, without any GUI/viewer requirement. */
public final class StorageTemplatePersistence {
    private StorageTemplatePersistence() {
    }

    public static ItemStack snapshot(ItemStack original) {
        Objects.requireNonNull(original, "Storage template");
        ItemStack detached = original.clone();
        if (detached == original || detached.getAmount() != original.getAmount()
            || !original.isSimilar(detached)) {
            throw new IllegalStateException("Storage template could not be copied without changing its identity");
        }
        return detached;
    }

    /**
     * Write a display-encoded, one-item template and prove the real slot retains it.
     * The callbacks must refer to the same authoritative slot. No stack or menu is
     * retained after the call; repeated identical templates avoid writes and dirty marks.
     */
    public static boolean update(
        ItemStack encoded, Supplier<ItemStack> reader, Consumer<ItemStack> writer, Runnable markDirty) {
        if (encoded == null || encoded.getType().isAir() || encoded.getAmount() != 1) {
            throw new IllegalStateException("Expected exactly one display-encoded template item");
        }
        ItemStack expected = snapshot(encoded);
        if (matches(expected, reader.get())) {
            return false;
        }
        // A foreign setter must never be able to mutate the verification snapshot.
        writer.accept(snapshot(expected));
        verify(expected, reader.get());
        markDirty.run();
        verify(expected, reader.get());
        return true;
    }

    /**
     * Repair only the private persisted template slot, leaving the separately
     * stored balance and Void Excess setting completely untouched. The caller
     * must verify that both templates represent the same registered Slimefun ID
     * and functional metadata before invoking this method.
     *
     * A callback may write and then throw. A failed upgrade therefore attempts
     * an idempotent restore of the original protected slot and propagates any
     * failure. An abrupt process stop can leave either template; both must be
     * semantically equivalent, and neither path modifies the item ledger.
     */
    public static boolean updateCosmeticWithRollback(
        ItemStack encodedOriginal, ItemStack encodedCanonical,
        Supplier<ItemStack> reader, Consumer<ItemStack> writer, Runnable markDirty) {
        ItemStack previous = snapshot(encodedOriginal);
        ItemStack next = snapshot(encodedCanonical);
        if (previous.getAmount() != 1 || next.getAmount() != 1) {
            throw new IllegalStateException("Cosmetic repair requires one-item private display templates");
        }
        try {
            return update(next, reader, writer, markDirty);
        } catch (RuntimeException failure) {
            try {
                update(previous, reader, writer, markDirty);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static boolean matches(ItemStack expected, ItemStack actual) {
        return actual != null && actual.getAmount() == 1 && expected.isSimilar(actual);
    }

    private static void verify(ItemStack expected, ItemStack actual) {
        if (!matches(expected, actual)) {
            throw new IllegalStateException("Storage template slot did not retain its exact item identity");
        }
    }
}
