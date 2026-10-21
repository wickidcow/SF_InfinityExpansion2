package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks the actual entities created during a storage break on its owning thread.
 * The world callback registers each entity before ItemSpawnEvent runs, so a
 * cancelled spawn or a write-then-throw can be undone using that exact handle.
 * Source slots and cache state remain owned by the caller until all receipts pass.
 * This is a synchronous receipt, not a forced-crash transaction journal.
 */
public final class StorageBreakDropReceipt {
    private final List<Item> entities = new ArrayList<>();
    private final List<ItemStack> expected = new ArrayList<>();

    public void spawn(ItemStack stack, BiConsumer<ItemStack, Consumer<Item>> dropper) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(dropper, "dropper");
        if (stack.getType().isAir() || stack.getAmount() <= 0) {
            throw new IllegalArgumentException("A storage-break drop must contain actual items");
        }
        ItemStack frozen = stack.clone();
        int before = entities.size();
        dropper.accept(frozen.clone(), entity -> {
            Objects.requireNonNull(entity, "created entity");
            if (entities.stream().anyMatch(previous -> previous.getUniqueId().equals(entity.getUniqueId()))) {
                throw new IllegalStateException("Storage-break drop reused an existing entity receipt");
            }
            entities.add(entity);
            expected.add(frozen);
        });
        if (entities.size() != before + 1) {
            throw new IllegalStateException("Storage-break drop did not create exactly one tracked entity");
        }
        verify();
    }

    public void verify() {
        for (int i = 0; i < entities.size(); i++) {
            Item entity = entities.get(i);
            if (!entity.isValid() || !expected.get(i).equals(entity.getItemStack())) {
                throw new IllegalStateException("Storage-break entity did not retain its exact item identity/quantity");
            }
        }
    }

    /** Remove only this attempt's captured entities; never create a speculative refund. */
    public boolean rollback(Throwable originalFailure) {
        boolean verified = true;
        for (int i = entities.size() - 1; i >= 0; i--) {
            try {
                entities.get(i).remove();
            } catch (RuntimeException | LinkageError removalFailure) {
                originalFailure.addSuppressed(removalFailure);
                verified = false;
            }
        }
        for (Item entity : entities) {
            try {
                if (entity.isValid()) {
                    originalFailure.addSuppressed(new IllegalStateException(
                        "Storage-break entity survived rollback: " + entity.getUniqueId()));
                    verified = false;
                }
            } catch (RuntimeException | LinkageError readFailure) {
                originalFailure.addSuppressed(readFailure);
                verified = false;
            }
        }
        return verified;
    }
}
