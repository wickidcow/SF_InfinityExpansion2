package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Injected inventory/menu getter and setter faults around the IE2 input receipt.
 * Not an actual networked Minecraft player or a crash-durability simulation.
 */
class StorageInputReceiptTest {
    private AtomicReference<ItemStack> physical;
    private StorageCache cache;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        physical = new AtomicReference<>(new ItemStack(Material.DIAMOND, 8));
        cache = new StorageCache(new ItemStack(Material.DIAMOND), 0, 1000, false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void detachedReaderMustRemoveExactlyEightBeforeCreditingEight() {
        assertEquals(8, transfer(physical::set, () -> {}));
        assertNull(physical.get());
        assertEquals(8, cache.getAmount());
    }

    @Test
    void ignoredSetterCannotCreditDetachedSource() {
        assertThrows(IllegalStateException.class, () -> transfer(ignored -> {}, () -> {}));
        assertEquals(8, physical.get().getAmount());
        assertEquals(0, cache.getAmount());
    }

    @Test
    void partialSourceWriteCannotCreditAnEightItemReceipt() {
        assertThrows(IllegalStateException.class, () -> transfer(ignored -> {
            physical.set(new ItemStack(Material.DIAMOND, 4));
        }, () -> {}));
        assertEquals(4, physical.get().getAmount());
        assertEquals(0, cache.getAmount());
    }

    @Test
    void dirtyCallbackRestoringPhysicalSourceBlocksCredit() {
        ItemStack original = physical.get().clone();
        assertThrows(IllegalStateException.class, () ->
            transfer(physical::set, () -> physical.set(original.clone())));
        assertEquals(8, physical.get().getAmount());
        assertEquals(0, cache.getAmount());
    }

    @Test
    void capacityBoundaryOnlyDebitsOneAndLeavesSevenInSource() {
        cache = new StorageCache(new ItemStack(Material.DIAMOND), 9, 10, false);
        assertEquals(1, transfer(physical::set, () -> {}));
        assertEquals(7, physical.get().getAmount());
        assertEquals(10, cache.getAmount());
    }

    @Test
    void voidExcessPreservesIntentionalOverflowDisposal() {
        cache = new StorageCache(new ItemStack(Material.DIAMOND), 9, 10, true);
        assertEquals(8, transfer(physical::set, () -> {}));
        assertNull(physical.get());
        assertEquals(10, cache.getAmount());
    }

    @Test
    void renamedTypedPdcIdentityIsCopiedExactly() {
        ItemStack named = new ItemStack(Material.DIAMOND, 8);
        named.editMeta(meta -> {
            meta.displayName(Component.text("Batch #91"));
            meta.getPersistentDataContainer().set(
                new NamespacedKey("ie2-test", "serial"), PersistentDataType.LONG, 9_123_456_789L);
        });
        physical.set(named.clone());
        cache = new StorageCache(named.clone(), 0, 1000, false);
        var observed = new AtomicReference<ItemStack>();
        assertEquals(8, StorageInputReceipt.transfer(cache, this::readCopy, physical::set,
            () -> {}, item -> {
                observed.set(item.clone());
                return credit(item);
            }));
        assertNull(physical.get());
        assertEquals(named.getItemMeta(), observed.get().getItemMeta());
        assertEquals(8, cache.getAmount());
    }

    @Test
    void nestedAttemptAgainstSameCacheCannotCreditAgain() {
        AtomicInteger nestedRejections = new AtomicInteger();
        assertEquals(8, StorageInputReceipt.transfer(cache, this::readCopy, physical::set,
            () -> {}, item -> {
                assertThrows(IllegalStateException.class, () ->
                    StorageInputReceipt.transfer(cache, this::readCopy, physical::set,
                        () -> {}, this::credit));
                nestedRejections.incrementAndGet();
                return credit(item);
            }));
        assertEquals(1, nestedRejections.get());
        assertNull(physical.get());
        assertEquals(8, cache.getAmount());
    }

    @Test
    void changingExpectedRemainderObjectCannotFoolAuthoritativeReadback() {
        cache = new StorageCache(new ItemStack(Material.DIAMOND), 7, 10, false);
        assertThrows(IllegalStateException.class, () -> transfer(proposed -> {
            proposed.setAmount(1);
            physical.set(new ItemStack(Material.DIAMOND, 2));
        }, () -> {}));
        assertEquals(7, cache.getAmount());
    }

    @Test
    void rejectedOrMissingSourceRemainsUntouched() {
        physical.set(null);
        assertEquals(0, transfer(physical::set, () -> {}));
        assertEquals(0, cache.getAmount());
        // A genuinely empty cache may accept a new item type by design.
        // Rejection applies when a positive diamond balance already exists.
        cache = new StorageCache(new ItemStack(Material.DIAMOND), 1, 1000, false);
        physical.set(new ItemStack(Material.EMERALD, 8));
        assertEquals(0, transfer(physical::set, () -> {}));
        assertEquals(1, cache.getAmount());
    }

    @Test
    void templateSnapshotFailureMustReleaseOwnershipBeforeTouchingPhysicalSource() {
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var readCalls = new AtomicInteger();
        ItemStack template = new ItemStack(Material.DIAMOND) {
            @Override
            public ItemStack clone() {
                if (failOnce.getAndSet(false)) {
                    throw new IllegalStateException("synthetic template snapshot failure");
                }
                return super.clone();
            }
        };
        cache = new StorageCache(template, 0, 1000, false);

        assertThrows(IllegalStateException.class, () ->
            StorageInputReceipt.transfer(cache, () -> {
                readCalls.incrementAndGet();
                return readCopy();
            }, physical::set, () -> {}, this::credit));

        assertEquals(0, readCalls.get(), "Template preflight failed before any source read");
        assertEquals(8, physical.get().getAmount(), "Physical source must remain untouched");
        assertEquals(0, cache.getAmount());
        assertFalse(cache.hasBlockedTransfer(), "A pre-debit snapshot exception must release the input guard");
        assertEquals(8, transfer(physical::set, () -> {}), "A later clean transaction should proceed");
        assertNull(physical.get());
        assertEquals(8, cache.getAmount());
    }

    private int transfer(java.util.function.Consumer<ItemStack> writer, Runnable afterWrite) {
        return StorageInputReceipt.transfer(cache, this::readCopy, writer, afterWrite, this::credit);
    }

    private ItemStack readCopy() {
        ItemStack item = physical.get();
        return item == null ? null : item.clone();
    }

    private int credit(ItemStack input) {
        int amount = input.getAmount();
        return amount - cache.increaseAmount(amount);
    }
}
