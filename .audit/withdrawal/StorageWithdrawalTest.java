package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.kyori.adventure.text.Component;
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

/** Actual Bukkit inventory API in MockBukkit; not a live player, disk save or crash-atomic transaction. */
class StorageWithdrawalTest {
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = MockBukkit.mock().addPlayer().getInventory();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void partiallyAcceptedStackIsDebitedExactlyOnce() {
        full();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 63));
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(1, StorageWithdrawal.transfer(inventory, cache, 64));
        assertEquals(99, cache.getAmount());
        assertEquals(64, inventory.getItem(0).getAmount());
    }

    @Test
    void repeatedClickAfterPartialAcceptanceCannotDuplicateItems() {
        full();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 60));
        var cache = cache(Material.DIAMOND, 100);
        for (int click = 0; click < 10; click++) {
            StorageWithdrawal.transfer(inventory, cache, 64);
        }
        assertEquals(96, cache.getAmount());
        assertEquals(160, cache.getAmount() + count(Material.DIAMOND));
    }

    @Test
    void fullInventoryRefusalDoesNotChangeCacheOrItems() {
        full();
        var before = snapshot();
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(0, StorageWithdrawal.transfer(inventory, cache, 64));
        assertEquals(100, cache.getAmount());
        assertArrayEquals(before, inventory.getStorageContents());
    }

    @Test
    void completeAcceptancePreservesOrdinaryStackWithdrawal() {
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(64, StorageWithdrawal.transfer(inventory, cache, 64));
        assertEquals(36, cache.getAmount());
        assertEquals(64, count(Material.DIAMOND));
    }

    @Test
    void requestedAmountNeverExceedsActualStock() {
        var cache = cache(Material.DIAMOND, 7);
        assertEquals(7, StorageWithdrawal.transfer(inventory, cache, 64));
        assertEquals(0, cache.getAmount());
        assertEquals(7, count(Material.DIAMOND));
    }

    @Test
    void multiplePartialSlotsConserveTotalQuantity() {
        full();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 63));
        inventory.setItem(1, new ItemStack(Material.DIAMOND, 59));
        var cache = cache(Material.DIAMOND, 80);
        assertEquals(6, StorageWithdrawal.transfer(inventory, cache, 64));
        assertEquals(74, cache.getAmount());
        assertEquals(128, count(Material.DIAMOND));
    }

    @Test
    void zeroAndNegativeRequestsAreReadOnly() {
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(0, StorageWithdrawal.transfer(inventory, cache, 0));
        assertEquals(0, StorageWithdrawal.transfer(inventory, cache, -1));
        assertEquals(100, cache.getAmount());
        assertEquals(0, count(Material.DIAMOND));
    }

    @Test
    void emptyOrInvalidExistingCacheDoesNotInventItems() {
        for (var cache : List.of(cache(Material.DIAMOND, 0), cache(Material.DIAMOND, -1),
                new StorageCache(null, 50, 100, false), cache(Material.AIR, 50))) {
            int before = cache.getAmount();
            assertEquals(0, StorageWithdrawal.transfer(inventory, cache, 64));
            assertEquals(before, cache.getAmount());
            assertTrue(Arrays.stream(inventory.getStorageContents()).allMatch(item -> item == null || item.getType().isAir()));
        }
    }

    @Test
    void exactRichItemMetadataAndStorageTemplateRemainUnchanged() {
        ItemStack template = rich("old-owner");
        ItemStack expected = template.clone();
        var cache = new StorageCache(template, 30, 500, true);
        assertEquals(7, StorageWithdrawal.transfer(inventory, cache, 7));
        assertSame(template, cache.getItemStack());
        assertEquals(expected, template);
        assertEquals(23, cache.getAmount());
        assertEquals(500, cache.getLimit());
        assertTrue(cache.getVoidExcess());
        ItemStack output = inventory.getItem(0);
        expected.setAmount(7);
        assertEquals(expected, output);
        var data = output.getItemMeta().getPersistentDataContainer();
        assertEquals(Float.floatToRawIntBits(123.4567F), Float.floatToRawIntBits(
                data.get(key("slimefun:item_charge"), PersistentDataType.FLOAT)));
        assertEquals(9_007_199_254_740_993L, data.get(key("old:count"), PersistentDataType.LONG));
        assertArrayEquals(new byte[] {0, -1, 4}, data.get(key("old:bytes"), PersistentDataType.BYTE_ARRAY));
    }

    @Test
    void differentOwnerItemsCannotProvideMergeSpace() {
        full();
        inventory.setItem(0, rich("different-owner"));
        var before = snapshot();
        var cache = new StorageCache(rich("old-owner"), 50, 100, false);
        assertEquals(0, StorageWithdrawal.transfer(inventory, cache, 20));
        assertEquals(50, cache.getAmount());
        assertArrayEquals(before, inventory.getStorageContents());
    }

    @Test
    void armorAndOffHandAreNotAdditionalStorageCapacity() {
        full();
        inventory.setItemInOffHand(new ItemStack(Material.DIAMOND, 1));
        inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(0, StorageWithdrawal.transfer(inventory, cache, 64));
        assertEquals(100, cache.getAmount());
        assertEquals(1, inventory.getItemInOffHand().getAmount());
        assertEquals(Material.DIAMOND_HELMET, inventory.getHelmet().getType());
    }

    @Test
    void mutableInputAmountsDoNotChangePartialAccounting() {
        full();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 63));
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(1, StorageWithdrawal.transfer(inputMutatingInventory(), cache, 64));
        assertEquals(99, cache.getAmount());
        assertEquals(64, count(Material.DIAMOND));
    }

    @Test
    void mutableInputAmountsDoNotChangeCompleteAccounting() {
        var cache = cache(Material.DIAMOND, 100);
        assertEquals(64, StorageWithdrawal.transfer(inputMutatingInventory(), cache, 64));
        assertEquals(36, cache.getAmount());
        assertEquals(64, count(Material.DIAMOND));
    }

    @Test
    void largeStockDoesNotOverflowOrChangeCapacityAndVoidMode() {
        for (boolean voidExcess : new boolean[] {false, true}) {
            inventory.clear();
            var cache = new StorageCache(new ItemStack(Material.DIAMOND), Integer.MAX_VALUE, Integer.MAX_VALUE, voidExcess);
            assertEquals(64, StorageWithdrawal.transfer(inventory, cache, 64));
            assertEquals(Integer.MAX_VALUE - 64, cache.getAmount());
            assertEquals(Integer.MAX_VALUE, cache.getLimit());
            assertEquals(voidExcess, cache.getVoidExcess());
        }
    }

    @Test
    void smallerVanillaStackLimitsPreservePartialWithdrawal() {
        full();
        inventory.setItem(0, new ItemStack(Material.ENDER_PEARL, 15));
        var cache = cache(Material.ENDER_PEARL, 100);
        assertEquals(1, StorageWithdrawal.transfer(inventory, cache, 16));
        assertEquals(99, cache.getAmount());
        assertEquals(16, count(Material.ENDER_PEARL));
    }

    @Test
    void unstackableItemsKeepTheirOriginalStackBehavior() {
        full();
        inventory.setItem(4, null);
        var cache = cache(Material.DIAMOND_SWORD, 2);
        assertEquals(1, StorageWithdrawal.transfer(inventory, cache, 1));
        assertEquals(1, cache.getAmount());
        assertEquals(0, StorageWithdrawal.transfer(inventory, cache, 1));
        assertEquals(1, cache.getAmount());
    }

    @Test
    void repeatedBulkOffersAccountForFinalPartialStackBeforeStopping() {
        full();
        inventory.setItem(0, null);
        inventory.setItem(1, new ItemStack(Material.DIAMOND, 62));
        var cache = cache(Material.DIAMOND, 1000);
        int accepted;
        do {
            accepted = StorageWithdrawal.transfer(inventory, cache, Math.min(64, cache.getAmount()));
        } while (accepted == 64);
        assertEquals(934, cache.getAmount());
        assertEquals(128, count(Material.DIAMOND));
    }

    @Test
    void randomizedStorageLayoutsConserveBothSides() {
        var random = new Random(0x57544844L);
        for (int sample = 0; sample < 1000; sample++) {
            inventory.clear();
            int capacity = 0;
            for (int slot = 0; slot < 36; slot++) {
                int type = random.nextInt(3);
                if (type == 0) {
                    capacity += 64;
                } else if (type == 1) {
                    int amount = 1 + random.nextInt(64);
                    inventory.setItem(slot, new ItemStack(Material.DIAMOND, amount));
                    capacity += 64 - amount;
                } else {
                    inventory.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
                }
            }
            int stock = 1 + random.nextInt(4000);
            int requested = 1 + random.nextInt(2500);
            int beforeItems = count(Material.DIAMOND);
            int expected = Math.min(stock, Math.min(requested, capacity));
            var cache = cache(Material.DIAMOND, stock);
            int actual = StorageWithdrawal.transfer(inventory, cache, requested);
            assertEquals(expected, actual, "sample=" + sample);
            assertEquals(stock - actual, cache.getAmount());
            assertEquals(beforeItems + actual, count(Material.DIAMOND));
            assertEquals(stock + beforeItems, cache.getAmount() + count(Material.DIAMOND));
        }
    }

    private Inventory inputMutatingInventory() {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(inventory, args);
                        if (method.getName().equals("addItem")) {
                            for (ItemStack input : (ItemStack[]) args[0]) input.setAmount(0);
                        }
                        return result;
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private void full() {
        for (int slot = 0; slot < 36; slot++) inventory.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
    }

    private int count(Material material) {
        return Arrays.stream(inventory.getStorageContents()).filter(item -> item != null && item.getType() == material)
                .mapToInt(ItemStack::getAmount).sum();
    }

    private ItemStack[] snapshot() {
        return Arrays.stream(inventory.getStorageContents()).map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    private static StorageCache cache(Material material, int amount) {
        return new StorageCache(new ItemStack(material), amount, Integer.MAX_VALUE, false);
    }

    private static NamespacedKey key(String name) {
        return Objects.requireNonNull(NamespacedKey.fromString(name));
    }

    private static ItemStack rich(String owner) {
        var item = new ItemStack(Material.DIAMOND);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Old named item"));
        meta.lore(List.of(Component.text("Keep the owner's lore")));
        var data = meta.getPersistentDataContainer();
        data.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "UNREGISTERED_OLD_ADDON");
        data.set(key("slimefun:owner_uuid"), PersistentDataType.STRING, owner);
        data.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        data.set(key("old:count"), PersistentDataType.LONG, 9_007_199_254_740_993L);
        data.set(key("old:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 4});
        item.setItemMeta(meta);
        return item;
    }
}
