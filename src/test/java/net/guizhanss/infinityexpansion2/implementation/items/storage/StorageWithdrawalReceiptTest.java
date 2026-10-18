package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class StorageWithdrawalReceiptTest {
    private PlayerInventory physical;

    @BeforeEach
    void setUp() {
        physical = MockBukkit.mock().addPlayer().getInventory();
        for (int slot = physical.getStorageContents().length; slot < physical.getSize(); slot++) {
            physical.setItem(slot, new ItemStack(Material.BARRIER, 64));
        }
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void reservationIsSavedBeforeAnyDestinationItemIsDelivered() {
        var cache = cache(16);
        var saved = new ArrayList<Integer>();
        Inventory target = target(items -> {
            assertEquals(List.of(8), saved);
            assertEquals(8, cache.getAmount());
            assertTrue(cache.hasBlockedTransfer(), "Other operations must see the in-flight reservation");
            assertEquals(3, cache.increaseAmount(3));
            assertEquals(8, cache.getAmount());
            return physical.addItem(items);
        });
        assertEquals(8, StorageWithdrawal.transfer(cache, target, 8, () -> saved.add(cache.getAmount())));
        assertEquals(List.of(8, 8), saved);
        assertFalse(cache.hasBlockedTransfer());
        assertEquals(16, cache.getAmount() + total());
    }

    @Test
    void writeFailureRetainsReservationAndBlocksFurtherWithdrawals() {
        var cache = cache(16);
        Inventory target = target(items -> {
            physical.addItem(items);
            throw new IllegalStateException("Already delivered");
        });
        assertThrows(IllegalStateException.class, () -> StorageWithdrawal.transfer(cache, target, 8));
        assertEquals(8, cache.getAmount());
        assertEquals(8, total());
        assertTrue(cache.hasBlockedTransfer());
        assertEquals(0, StorageWithdrawal.transfer(cache, physical, 8));
        assertEquals(0, StorageWithdrawal.transferAll(cache, physical));
        assertEquals(3, cache.increaseAmount(3));
        assertEquals(16, cache.getAmount() + total());
    }

    @Test
    void failedReservationSaveNeverCallsTheDestination() {
        var cache = cache(16);
        AtomicInteger writes = new AtomicInteger();
        Inventory target = target(items -> { writes.incrementAndGet(); return physical.addItem(items); });
        assertThrows(IllegalStateException.class, () -> StorageWithdrawal.transfer(cache, target, 8,
            () -> { throw new IllegalStateException("Injected save failure"); }));
        assertEquals(0, writes.get());
        assertEquals(0, total());
        assertTrue(cache.hasBlockedTransfer());
        assertEquals(8, cache.getAmount(), "Do not refund a reservation with unverified persistence");
    }

    @Test
    void normalPartialDeliveryUsesObservedGainAndSavesOnlyTheVerifiedRefund() {
        var cache = cache(16);
        var saved = new ArrayList<Integer>();
        Inventory target = target(items -> {
            physical.addItem(new ItemStack(Material.DIAMOND, 3));
            return new HashMap<>(); // Incorrect empty remainder must not debit all eight.
        });
        assertEquals(3, StorageWithdrawal.transfer(cache, target, 8, () -> saved.add(cache.getAmount())));
        assertEquals(List.of(8, 13), saved);
        assertEquals(16, cache.getAmount() + total());
        assertFalse(cache.hasBlockedTransfer());
    }

    @Test
    void completeNoOpInsertionKeepsTheVerifiedSourceBalance() {
        var cache = cache(16);
        assertEquals(0, StorageWithdrawal.transfer(cache, target(items -> new HashMap<>()), 8));
        assertEquals(16, cache.getAmount());
        assertFalse(cache.hasBlockedTransfer());
    }

    @Test
    void sourceRestorationDuringDestinationCallbackCannotAuthorizeAnotherCopy() {
        var cache = cache(16);
        Inventory target = target(items -> {
            physical.addItem(items);
            cache.setAmount(16);
            return new HashMap<>();
        });
        assertThrows(IllegalStateException.class, () -> StorageWithdrawal.transfer(cache, target, 8));
        assertTrue(cache.hasBlockedTransfer());
        assertEquals(16, cache.getAmount() + total());
    }

    @Test
    void failingInventoryReadbackDoesNotReturnTheCreditedItemsToStorage() {
        var cache = cache(16);
        AtomicBoolean delivered = new AtomicBoolean();
        Inventory target = (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
            new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                if (method.getName().equals("getStorageContents") && delivered.get()) {
                    throw new IllegalStateException("Unreadable after insertion");
                }
                if (method.getName().equals("addItem")) delivered.set(true);
                return forward(method, args);
            });
        assertThrows(IllegalStateException.class, () -> StorageWithdrawal.transfer(cache, target, 8));
        assertEquals(16, cache.getAmount() + total());
        assertTrue(cache.hasBlockedTransfer());
    }

    @Test
    void unrelatedInventoryReplacementInvalidatesTheReceipt() {
        physical.setItem(1, new ItemStack(Material.STONE, 4));
        var cache = cache(16);
        Inventory target = target(items -> {
            var result = physical.addItem(items);
            physical.setItem(1, new ItemStack(Material.EMERALD, 4));
            return result;
        });
        assertThrows(IllegalStateException.class, () -> StorageWithdrawal.transfer(cache, target, 8));
        assertEquals(16, cache.getAmount() + total());
        assertTrue(cache.hasBlockedTransfer());
    }

    @Test
    void withdrawAllFillsOnlyStorageSlotsInOneInventoryBatch() {
        var cache = cache(5000);
        AtomicInteger insertions = new AtomicInteger();
        Inventory target = target(items -> { insertions.incrementAndGet(); return physical.addItem(items); });
        assertEquals(2304, StorageWithdrawal.transferAll(cache, target));
        assertEquals(1, insertions.get());
        assertEquals(2696, cache.getAmount());
        assertEquals(5000, total() + cache.getAmount());
        assertEquals(Material.BARRIER, physical.getItem(36).getType());
    }

    @Test
    void withdrawAllFillsPartialAndEmptySlotsWithoutEditingLiveStackReferences() {
        for (int slot = 0; slot < physical.getStorageContents().length; slot++) {
            physical.setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        physical.setItem(0, new ItemStack(Material.DIAMOND, 60));
        physical.setItem(1, null);
        var cache = cache(200);
        assertEquals(68, StorageWithdrawal.transferAll(cache, physical));
        assertEquals(132, cache.getAmount());
        assertEquals(260, cache.getAmount() + total());
    }

    @Test
    void fullInventoryDoesNotReserveOrPersistAnything() {
        for (int slot = 0; slot < physical.getStorageContents().length; slot++) {
            physical.setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        var cache = cache(16);
        AtomicInteger saves = new AtomicInteger();
        assertEquals(0, StorageWithdrawal.transfer(cache, physical, 8, saves::incrementAndGet));
        assertEquals(0, StorageWithdrawal.transferAll(cache, physical, saves::incrementAndGet));
        assertEquals(0, saves.get());
        assertEquals(16, cache.getAmount());
    }

    @Test
    void allWithdrawalPreservesTypedMetadataAndOriginalTemplate() {
        var template = new ItemStack(Material.DIAMOND, 1);
        template.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("test", "owner"), PersistentDataType.STRING, "owned-item"));
        var original = template.clone();
        var cache = new StorageCache(template, 192, 1024, false);
        assertEquals(192, StorageWithdrawal.transferAll(cache, physical));
        assertEquals(original, template);
        for (ItemStack stack : physical.getStorageContents()) {
            if (stack != null && !stack.getType().isAir()) assertTrue(original.isSimilar(stack));
        }
        assertEquals(0, cache.getAmount());
    }

    private Inventory target(Function<ItemStack[], HashMap<Integer, ItemStack>> insertion) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
            new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                if (method.getName().equals("addItem")) return insertion.apply((ItemStack[]) args[0]);
                return forward(method, args);
            });
    }

    private Object forward(java.lang.reflect.Method method, Object[] args) throws Throwable {
        try { return method.invoke(physical, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    private static StorageCache cache(int amount) {
        return new StorageCache(new ItemStack(Material.DIAMOND), amount, 10000, false);
    }

    private int total() {
        int total = 0;
        for (ItemStack item : physical.getStorageContents()) {
            if (item != null && item.getType() == Material.DIAMOND) total += item.getAmount();
        }
        return total;
    }
}
