package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Test-only cross-direction fault injection. Not a live-player exploit replay. */
class StorageCrossDirectionTest {
    private ServerMock server;
    private StorageCache cache;
    private AtomicReference<ItemStack> physical;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        cache = new StorageCache(new ItemStack(Material.DIAMOND), 16, 1000, false);
        physical = new AtomicReference<>(new ItemStack(Material.DIAMOND, 8));
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    private ItemStack readCopy() {
        ItemStack item = physical.get();
        return item == null ? null : item.clone();
    }
    private int credit(ItemStack item) {
        int amount = item.getAmount();
        return amount - cache.increaseAmount(amount);
    }
    private int deposit(java.util.function.Consumer<ItemStack> writer,
                        Runnable afterDebit,
                        java.util.function.Function<ItemStack,Integer> callback) {
        return StorageInputReceipt.transfer(cache, this::readCopy, writer, afterDebit, callback);
    }

    @Test void restoredSourceAfterAlreadyCreditedCallbackRequiresStickyConservativeHold() {
        assertThrows(IllegalStateException.class, () -> deposit(physical::set, () -> {}, offered -> {
            int acknowledged = credit(offered);
            physical.set(new ItemStack(Material.DIAMOND, 8));
            return acknowledged;
        }));
        assertTrue(cache.hasBlockedTransfer(), "Restored debited source requires a sticky hold");
        assertEquals(16, cache.getAmount(), "Do not leave a second eight-item cache claim");
    }

    @Test void writeThenThrowMustBlockLaterSourceRetries() {
        AtomicInteger writes = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> deposit(value -> {
            writes.incrementAndGet();
            physical.set(value);
            throw new IllegalStateException("setter wrote then threw");
        }, () -> {}, this::credit));
        assertTrue(cache.hasBlockedTransfer());
        assertEquals(16, cache.getAmount());
        assertEquals(0, deposit(value -> writes.incrementAndGet(), () -> {}, this::credit));
        assertEquals(1, writes.get(), "Blocked retry must not write physical source");
    }

    @Test void inputCreditCallbackCannotWithdrawFromSameCache() {
        var inventory = server.addPlayer().getInventory();
        assertEquals(8, deposit(physical::set, () -> {}, item -> {
            assertEquals(0, StorageWithdrawal.transfer(cache, inventory, 8));
            return credit(item);
        }));
        assertEquals(24, cache.getAmount());
        assertNull(physical.get());
        assertEquals(0, inventory.getItem(0) == null ? 0 : inventory.getItem(0).getAmount());
    }

    @Test void withdrawalPersistCallbackCannotDebitPhysicalInputSlot() {
        var inventory = server.addPlayer().getInventory();
        AtomicInteger writes = new AtomicInteger();
        int received = StorageWithdrawal.transfer(cache, inventory, 8, () -> {
            int nested = deposit(value -> {
                writes.incrementAndGet();
                physical.set(value);
            }, () -> {}, this::credit);
            assertEquals(0, nested);
        });
        assertEquals(8, received);
        assertEquals(0, writes.get());
        assertEquals(8, physical.get().getAmount());
        assertEquals(8, cache.getAmount());
        assertEquals(8, inventory.getItem(0).getAmount());
    }

    @Test void inputCallbackCannotMakeExtraDirectCacheCredit() {
        assertEquals(8, deposit(physical::set, () -> {}, item -> {
            assertEquals(1, cache.increaseAmount(1));
            return credit(item);
        }));
        assertEquals(24, cache.getAmount());
        assertNull(physical.get());
        assertFalse(cache.hasBlockedTransfer());
    }

    @Test void restoredSourceMustRejectSubsequentRetryWithoutEvenReadingWriter() {
        assertThrows(IllegalStateException.class, () -> deposit(physical::set, () -> {}, offered -> {
            int acknowledged = credit(offered);
            physical.set(new ItemStack(Material.DIAMOND, 8));
            return acknowledged;
        }));
        assertTrue(cache.hasBlockedTransfer());
        AtomicInteger subsequentWrites = new AtomicInteger();
        int returned = deposit(next -> {
            subsequentWrites.incrementAndGet();
            physical.set(next);
        }, () -> {}, this::credit);
        assertEquals(0, returned);
        assertEquals(0, subsequentWrites.get());
        assertEquals(8, physical.get().getAmount());
        assertEquals(16, cache.getAmount());
    }

    @Test void sameCacheRejectedInputMustNotPreventUnrelatedCacheReceipt() {
        var otherCache = new StorageCache(new ItemStack(Material.DIAMOND), 5, 1000, false);
        var otherPhysical = new AtomicReference<>(new ItemStack(Material.DIAMOND, 3));
        assertEquals(8, deposit(physical::set, () -> {}, original -> {
            int unrelated = StorageInputReceipt.transfer(otherCache,
                () -> otherPhysical.get() == null ? null : otherPhysical.get().clone(),
                otherPhysical::set, () -> {}, offered -> {
                    int amount = offered.getAmount();
                    return amount - otherCache.increaseAmount(amount);
                });
            assertEquals(3, unrelated);
            return credit(original);
        }));
        assertEquals(24, cache.getAmount());
        assertEquals(8, otherCache.getAmount());
        assertNull(physical.get());
        assertNull(otherPhysical.get());
    }

    @Test void ignoredSourceSetterMustFailClosedEvenIfPhysicalItemsSurvive() {
        assertThrows(IllegalStateException.class, () -> deposit(ignored -> {}, () -> {}, this::credit));
        assertTrue(cache.hasBlockedTransfer());
        assertEquals(8, physical.get().getAmount());
        assertEquals(16, cache.getAmount());
    }
}
