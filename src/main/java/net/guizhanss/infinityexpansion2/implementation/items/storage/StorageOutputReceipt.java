package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verifies an IE2 cached-source debit against the one physical output slot.
 *
 * The caller must reserve/persist source stock before physical delivery.
 * After any ambiguous writer, dirty callback or getter, quarantine only the
 * affected cache. Never invent a refund or world drop: either could duplicate
 * items already delivered by an unverified callback.
 */
public final class StorageOutputReceipt {
    private StorageOutputReceipt() { }

    public static int transfer(
        @NotNull ItemStack cacheTemplate,
        int sourceAmount,
        @NotNull Supplier<@Nullable ItemStack> readSourceTemplate,
        @NotNull Supplier<@Nullable ItemStack> readOutput,
        @NotNull IntFunction<@Nullable ItemStack> reserveAndPersist,
        @NotNull IntSupplier readSourceAmount,
        @NotNull Consumer<@NotNull ItemStack> insertOutput,
        @NotNull Runnable markDirty,
        @NotNull Runnable quarantine
    ) {
        Objects.requireNonNull(cacheTemplate, "cacheTemplate");
        Objects.requireNonNull(readSourceTemplate, "readSourceTemplate");
        Objects.requireNonNull(readOutput, "readOutput");
        Objects.requireNonNull(reserveAndPersist, "reserveAndPersist");
        Objects.requireNonNull(readSourceAmount, "readSourceAmount");
        Objects.requireNonNull(insertOutput, "insertOutput");
        Objects.requireNonNull(markDirty, "markDirty");
        Objects.requireNonNull(quarantine, "quarantine");

        if (sourceAmount <= 0 || cacheTemplate.getType().isAir()) {
            return 0;
        }
        final ItemStack expected = snapshot(cacheTemplate);
        if (expected == null) {
            return 0;
        }

        // Before any debit, incompatible/full physical output is a harmless
        // no-op. No source or destination is mutated by this preflight.
        final ItemStack original = snapshot(readOutput.get());
        if (original != null && !original.isSimilar(expected)) {
            return 0;
        }
        final int max = expected.getMaxStackSize();
        final int previous = original == null ? 0 : original.getAmount();
        if (max <= 0 || previous >= max) {
            return 0;
        }
        final int request = Math.min(sourceAmount, max - previous);
        if (request <= 0) {
            return 0;
        }

        // reserveAndPersist may debit even if its callback later throws.
        try {
            final ItemStack reserved = snapshot(reserveAndPersist.apply(request));
            if (reserved == null || !reserved.isSimilar(expected)
                || reserved.getAmount() <= 0 || reserved.getAmount() > request) {
                throw new IllegalStateException("Ticker source returned an unverified output reservation");
            }
            final int delivered = reserved.getAmount();
            verifySource(readSourceAmount.getAsInt(), sourceAmount, delivered);
            verifyTemplate(readSourceTemplate.get(), expected);
            insertOutput.accept(reserved.clone()); // Pusher may mutate the offer.
            verifyPhysical(readOutput.get(), original, reserved, delivered);
            markDirty.run();
            verifyPhysical(readOutput.get(), original, reserved, delivered);
            verifySource(readSourceAmount.getAsInt(), sourceAmount, delivered);
            verifyTemplate(readSourceTemplate.get(), expected);
            return delivered;
        } catch (RuntimeException | LinkageError failure) {
            try {
                quarantine.run();
            } catch (RuntimeException | LinkageError holdFailure) {
                failure.addSuppressed(holdFailure);
            }
            throw new IllegalStateException(
                "IE2 physical output could not be reconciled with its source debit", failure);
        }
    }

    private static void verifySource(int observed, int previous, int delivered) {
        if (observed != previous - delivered) {
            throw new IllegalStateException("Ticker cache balance changed after its output debit");
        }
    }

    private static void verifyTemplate(@Nullable ItemStack actual, @NotNull ItemStack required) {
        final ItemStack live = snapshot(actual);
        if (live == null || !live.isSimilar(required)) {
            throw new IllegalStateException("Ticker source identity changed during output callbacks");
        }
    }

    private static @Nullable ItemStack snapshot(@Nullable ItemStack input) {
        if (input == null || input.getType().isAir() || input.getAmount() <= 0) {
            return null;
        }
        return input.clone();
    }

    private static void verifyPhysical(
        @Nullable ItemStack current, @Nullable ItemStack before,
        @NotNull ItemStack credited, int amount
    ) {
        final ItemStack after = snapshot(current);
        final int previous = before == null ? 0 : before.getAmount();
        if (after == null || !after.isSimilar(credited) || after.getAmount() != previous + amount) {
            throw new IllegalStateException("Ticker output did not preserve exact item identity/quantity");
        }
    }
}
