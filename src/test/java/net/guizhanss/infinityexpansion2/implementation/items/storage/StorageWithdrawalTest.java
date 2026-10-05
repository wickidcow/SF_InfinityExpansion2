package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
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

class StorageWithdrawalTest {
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = MockBukkit.mock().addPlayer().getInventory();
        // MockBukkit 4.110.0 addItem scans equipment slots too; keep them unavailable.
        for (int slot = inventory.getStorageContents().length; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Material.BARRIER, 64));
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void partiallyAcceptedStackIsDeductedFromStorage() {
        fill(Material.STONE, 64);
        inventory.setItem(0, new ItemStack(Material.STONE, 60));
        var cache = cache(Material.STONE, 100);

        assertEquals(4, StorageWithdrawal.transfer(cache, inventory, 64));

        assertEquals(96, cache.getAmount());
        assertEquals(64, inventory.getItem(0).getAmount());
    }

    @Test
    void repeatedClicksOnAFullInventoryCannotDuplicateItems() {
        fill(Material.STONE, 64);
        inventory.setItem(0, new ItemStack(Material.STONE, 60));
        var cache = cache(Material.STONE, 100);
        int before = total(Material.STONE) + cache.getAmount();

        for (int attempt = 0; attempt < 20; attempt++) {
            StorageWithdrawal.transfer(cache, inventory, 64);
            assertEquals(before, total(Material.STONE) + cache.getAmount());
        }
        assertEquals(96, cache.getAmount());
    }

    @Test
    void fullInventoryKeepsEveryStoredItem() {
        fill(Material.DIRT, 64);
        var cache = cache(Material.STONE, 100);

        assertEquals(0, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(100, cache.getAmount());
        assertEquals(0, total(Material.STONE));
    }

    @Test
    void emptyInventoryReceivesOneRequestedStack() {
        var cache = cache(Material.STONE, 100);

        assertEquals(64, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(36, cache.getAmount());
        assertEquals(64, total(Material.STONE));
    }

    @Test
    void transferCannotExceedStoredQuantity() {
        var cache = cache(Material.STONE, 9);

        assertEquals(9, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(0, cache.getAmount());
        assertEquals(9, total(Material.STONE));
    }

    @Test
    void sixteenItemStacksKeepTheirLimitAndPartialAccounting() {
        fill(Material.ENDER_PEARL, 16);
        inventory.setItem(0, new ItemStack(Material.ENDER_PEARL, 13));
        var cache = cache(Material.ENDER_PEARL, 100);

        assertEquals(3, StorageWithdrawal.transfer(cache, inventory, 16));
        assertEquals(97, cache.getAmount());
        assertEquals(16, inventory.getItem(0).getAmount());
    }

    @Test
    void repeatedStackTransfersStopWithUnacceptedItemsStillStored() {
        fill(Material.DIRT, 64);
        inventory.setItem(0, null);
        inventory.setItem(1, new ItemStack(Material.STONE, 60));
        var cache = cache(Material.STONE, 200);

        assertEquals(64, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(4, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(0, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(132, cache.getAmount());
        assertEquals(128, total(Material.STONE));
    }

    @Test
    void transferPreservesStoredMetadataAndDoesNotModifyTheTemplate() {
        var template = new ItemStack(Material.PAPER, 17);
        var meta = template.getItemMeta();
        meta.displayName(Component.text("Player's named item"));
        meta.lore(List.of(Component.translatable("item.minecraft.paper")));
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("slimefun:slimefun_item"), PersistentDataType.STRING, "OLD_ITEM_ID");
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("oldaddon:stored_value"), PersistentDataType.LONG, 9_000_000_001L);
        template.setItemMeta(meta);
        var original = template.clone();
        var cache = new StorageCache(template, 100, 1000, false);
        fill(Material.DIRT, 64);
        var partial = template.clone();
        partial.setAmount(60);
        inventory.setItem(0, partial);

        assertEquals(4, StorageWithdrawal.transfer(cache, inventory, 64));
        assertEquals(original, template);
        assertEquals(original.getItemMeta(), inventory.getItem(0).getItemMeta());
        assertEquals(96, cache.getAmount());
    }

    @Test
    void accountingDoesNotDependOnWhetherAddItemMutatesItsArgument() {
        fill(Material.STONE, 64);
        inventory.setItem(0, new ItemStack(Material.STONE, 60));
        var cache = cache(Material.STONE, 100);
        Inventory mutating = (Inventory) Proxy.newProxyInstance(
            Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                if (!method.getName().equals("addItem")) throw new AssertionError(method.getName());
                var offered = (ItemStack[]) args[0];
                var leftovers = inventory.addItem(offered);
                offered[0].setAmount(leftovers.values().stream().mapToInt(ItemStack::getAmount).sum());
                return leftovers;
            });

        assertEquals(4, StorageWithdrawal.transfer(cache, mutating, 64));
        assertEquals(96, cache.getAmount());
        assertEquals(1, cache.getItemStack().getAmount());
    }

    @Test
    void emptyStorageAndNonpositiveRequestsDoNotChangeTheInventory() {
        var cache = cache(Material.STONE, 100);
        assertEquals(0, StorageWithdrawal.transfer(cache, inventory, 0));
        assertEquals(0, StorageWithdrawal.transfer(cache, inventory, -1));
        assertEquals(100, cache.getAmount());
        assertEquals(0, StorageWithdrawal.transfer(cache(Material.STONE, 0), inventory, 64));
        assertEquals(0, StorageWithdrawal.transfer(new StorageCache(null, 0, 1000, false), inventory, 64));
        assertEquals(0, total(Material.STONE));
    }

    private static StorageCache cache(Material material, int amount) {
        return new StorageCache(new ItemStack(material), amount, 1000, false);
    }

    private void fill(Material material, int amount) {
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            inventory.setItem(slot, new ItemStack(material, amount));
        }
    }

    private int total(Material material) {
        int amount = 0;
        for (var item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) amount += item.getAmount();
        }
        return amount;
    }
}
