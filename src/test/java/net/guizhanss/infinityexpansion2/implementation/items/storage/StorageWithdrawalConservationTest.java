package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/** Conservation tests inject destination faults; they are not a real player exploit replay. */
class StorageWithdrawalConservationTest {
    private PlayerInventory physical;

    @BeforeEach
    void setUp() {
        physical = MockBukkit.mock().addPlayer().getInventory();
        for (int slot = physical.getStorageContents().length; slot < physical.getSize(); slot++) {
            physical.setItem(slot, new ItemStack(Material.BARRIER, 64));
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void completeDestinationWriteThenThrowCannotLeaveTheSameEightItemsInStorage() {
        var cache = cache(16);
        Inventory target = target(items -> {
            physical.addItem(items);
            throw new IllegalStateException("Destination wrote all eight then threw");
        });
        attempt(cache, target, 8);
        assertEquals(8, delivered());
        assertEquals(16, cache.getAmount() + delivered(),
            "A destination exception must not leave credited items available in storage again");
    }

    @Test
    void partialDestinationWriteThenThrowCannotIncreaseTotalOwnedItems() {
        var cache = cache(16);
        Inventory target = target(items -> {
            physical.addItem(new ItemStack(Material.DIAMOND, 4));
            throw new IllegalStateException("Destination wrote four then threw without updating offer");
        });
        attempt(cache, target, 8);
        assertEquals(4, delivered());
        assertTrue(cache.getAmount() + delivered() <= 16,
            "Partial ambiguous delivery must not manufacture items through an unchanged source balance");
        assertTrue(cache.getAmount() >= 0);
    }

    @Test
    void claimedFullRemainderCannotRefundItemsThatReachedPhysicalInventory() {
        var cache = cache(16);
        Inventory target = target(items -> {
            physical.addItem(items[0].clone());
            var claimed = new HashMap<Integer, ItemStack>();
            claimed.put(0, new ItemStack(Material.DIAMOND, 8));
            return claimed;
        });
        attempt(cache, target, 8);
        assertEquals(8, delivered());
        assertEquals(16, cache.getAmount() + delivered(),
            "Trust the actual inventory, not a contradictory leftover report");
    }

    @Test
    void inflatedLeftoversCannotIncreaseTheStorageCounter() {
        var cache = cache(16);
        Inventory target = target(items -> {
            var claimed = new HashMap<Integer, ItemStack>();
            claimed.put(0, new ItemStack(Material.DIAMOND, 9));
            return claimed;
        });
        attempt(cache, target, 8);
        assertEquals(0, delivered());
        assertTrue(cache.getAmount() <= 16, "Negative transferred quantities must never increase storage");
        assertTrue(cache.getAmount() >= 0);
    }

    @Test
    void recursiveDestinationCannotWithdrawTheSameCacheBalanceTwice() {
        var cache = cache(8);
        AtomicBoolean enter = new AtomicBoolean(true);
        Inventory[] target = new Inventory[1];
        target[0] = target(items -> {
            if (enter.getAndSet(false)) {
                StorageWithdrawal.transfer(cache, target[0], 8);
            }
            return physical.addItem(items);
        });
        attempt(cache, target[0], 8);
        assertTrue(cache.getAmount() >= 0, "Reentrant delivery must not make the stored counter negative");
        assertTrue(delivered() <= 8, "Only the eight original items may reach the inventory");
        assertEquals(8, cache.getAmount() + delivered());
    }

    private Inventory target(Function<ItemStack[], HashMap<Integer, ItemStack>> insertion) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
            new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                if (method.getName().equals("addItem")) return insertion.apply((ItemStack[]) args[0]);
                try {
                    return method.invoke(physical, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            });
    }

    private static void attempt(StorageCache cache, Inventory target, int amount) {
        try {
            StorageWithdrawal.transfer(cache, target, amount);
        } catch (IllegalStateException expectedContainment) {
            // A fault may be rejected. The physical/cache ledger must remain safe in either outcome.
        }
    }

    private static StorageCache cache(int amount) {
        return new StorageCache(new ItemStack(Material.DIAMOND), amount, 1024, false);
    }

    private int delivered() {
        int total = 0;
        for (ItemStack item : physical.getStorageContents()) {
            if (item != null && item.getType() == Material.DIAMOND) total += item.getAmount();
        }
        return total;
    }
}
