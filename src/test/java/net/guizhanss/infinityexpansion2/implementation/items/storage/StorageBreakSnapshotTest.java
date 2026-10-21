package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Real cache/item/PDC preparation tests; world-spawn and restart tests remain separate. */
class StorageBreakSnapshotTest {
    private static final NamespacedKey OWNER = new NamespacedKey("ie2-break-test", "owner");

    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void teardown() { MockBukkit.unmock(); }

    private static ItemStack item(String owner, int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        item.editMeta(meta -> {
            meta.setDisplayName("Retained storage item");
            meta.setLore(java.util.List.of("original", "metadata"));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner);
        });
        return item;
    }

    @Test void fullVoidStorageReturnsAllPreviouslyDebitedPhysicalOutput() {
        var cache = new StorageCache(item("original", 1), 1000, 1000, true);
        var output = item("original", 64);
        var result = cache.snapshotPhysicalOutputForBreak(output);
        assertEquals(0, result.getSecond());
        assertEquals(1000, result.getFirst().getAmount());
        assertTrue(result.getFirst().getVoidExcess());
        assertEquals(1064L, (long) result.getFirst().getAmount() + output.getAmount() - result.getSecond());
        assertEquals(1000, cache.getAmount());
        assertEquals(64, output.getAmount());
    }

    @Test void matchingOutputUsesOnlyRealCapacityRegardlessOfVoidSetting() {
        for (boolean voidExcess : new boolean[] {false, true}) {
            var cache = new StorageCache(item("original", 1), 990, 1000, voidExcess);
            var output = item("original", 64);
            var result = cache.snapshotPhysicalOutputForBreak(output);
            assertEquals(10, result.getSecond());
            assertEquals(1000, result.getFirst().getAmount());
            assertEquals(1054L, (long) result.getFirst().getAmount() + output.getAmount() - result.getSecond());
            assertEquals(990, cache.getAmount());
            assertEquals(64, output.getAmount());
        }
    }

    @Test void zeroBalanceRetainsKnownOutputIdentityAndCount() {
        var cache = new StorageCache(item("original", 1), 0, 1000, false);
        var result = cache.snapshotPhysicalOutputForBreak(item("original", 11));
        assertEquals(11, result.getFirst().getAmount());
        assertEquals(11, result.getSecond());
        assertEquals(item("original", 1), result.getFirst().getItemStack());
        assertEquals(0, cache.getAmount());
    }

    @Test void genuinelyEmptyUntypedCacheKeepsPhysicalOutputSeparate() {
        var cache = new StorageCache(null, 0, 1000, true);
        var result = cache.snapshotPhysicalOutputForBreak(item("physical", 11));
        assertEquals(0, result.getFirst().getAmount());
        assertEquals(0, result.getSecond());
        assertNull(result.getFirst().getItemStack());
    }

    @Test void sameMaterialForeignPdcIsNeverRelabelledAsStoredStock() {
        var cache = new StorageCache(item("stored-owner", 1), 23, 1000, false);
        var output = item("other-owner", 11);
        var result = cache.snapshotPhysicalOutputForBreak(output);
        assertEquals(0, result.getSecond());
        assertEquals(23, result.getFirst().getAmount());
        assertEquals(item("stored-owner", 1), result.getFirst().getItemStack());
        assertEquals(item("other-owner", 11), output);
    }

    @Test void breakUsesLiveTemplateAfterMemoizedMetadataBecomesStale() {
        var cache = new StorageCache(item("old-owner", 1), 23, 1000, false);
        var output = item("old-owner", 11);
        assertTrue(cache.matches(output), "Prime the existing cached metadata");
        cache.getItemStack().editMeta(meta -> meta.getPersistentDataContainer().set(
            OWNER, PersistentDataType.STRING, "live-owner"));
        var result = cache.snapshotPhysicalOutputForBreak(output);
        assertEquals(0, result.getSecond());
        assertEquals(item("live-owner", 1), result.getFirst().getItemStack());
        assertEquals(item("old-owner", 11), output);
        assertEquals(23, cache.getAmount());
    }

    @Test void returnedStateIsDetachedFromBothOriginalOwners() {
        var cache = new StorageCache(item("original", 1), 23, 1000, true);
        var output = item("original", 11);
        var result = cache.snapshotPhysicalOutputForBreak(output);
        result.getFirst().getItemStack().setType(Material.EMERALD);
        result.getFirst().setAmount(0);
        assertEquals(item("original", 1), cache.getItemStack());
        assertEquals(23, cache.getAmount());
        assertEquals(item("original", 11), output);
    }

    @Test void positiveUnknownBalanceCannotBorrowTheOutputIdentity() {
        var cache = new StorageCache(null, 23, 1000, false);
        var output = item("physical", 11);
        assertThrows(IllegalStateException.class, () -> cache.snapshotPhysicalOutputForBreak(output));
        assertNull(cache.getItemStack());
        assertEquals(23, cache.getAmount());
        assertEquals(item("physical", 11), output);
    }

    @Test void invalidAndOwnedBalancesCannotBePacked() {
        for (StorageCache invalid : new StorageCache[] {
            new StorageCache(item("original", 1), -1, 1000, false),
            new StorageCache(item("original", 1), 1001, 1000, true),
            new StorageCache(item("original", 1), 0, 0, false)
        }) {
            int original = invalid.getAmount();
            assertThrows(IllegalStateException.class,
                () -> invalid.snapshotPhysicalOutputForBreak(item("original", 11)));
            assertEquals(original, invalid.getAmount());
        }
        var cache = new StorageCache(item("original", 1), 23, 1000, false);
        assertTrue(cache.beginInputReceipt());
        try {
            assertThrows(IllegalStateException.class,
                () -> cache.snapshotPhysicalOutputForBreak(item("original", 11)));
            assertEquals(23, cache.getAmount());
        } finally {
            cache.finishInputReceipt();
        }
    }

    @Test void maximumIntegerBalanceDoesNotOverflowWhenPhysicalOutputIsPacked() {
        var cache = new StorageCache(item("original", 1), Integer.MAX_VALUE - 3, Integer.MAX_VALUE, true);
        var result = cache.snapshotPhysicalOutputForBreak(item("original", 64));
        assertEquals(Integer.MAX_VALUE, result.getFirst().getAmount());
        assertEquals(3, result.getSecond());
        assertEquals((long) Integer.MAX_VALUE + 61,
            (long) result.getFirst().getAmount() + 64 - result.getSecond());
    }

    @Test void seededLedgersConserveEveryPhysicalItemWithoutMutatingSources() {
        Random random = new Random(0x1E2B4EA);
        for (int i = 0; i < 2500; i++) {
            int capacity = 1 + random.nextInt(1_000_000);
            int balance = random.nextInt(capacity + 1);
            int physical = 1 + random.nextInt(64);
            boolean sameOwner = random.nextBoolean();
            var cache = new StorageCache(item("stored", 1), balance, capacity, random.nextBoolean());
            var output = item(sameOwner ? "stored" : "foreign", physical);
            ItemStack beforeOutput = output.clone();
            var result = cache.snapshotPhysicalOutputForBreak(output);
            int remainder = physical - result.getSecond();
            assertTrue(remainder >= 0 && remainder <= physical);
            assertEquals((long) balance + physical, (long) result.getFirst().getAmount() + remainder);
            assertEquals(balance, cache.getAmount());
            assertEquals(beforeOutput, output);
            assertEquals(item("stored", 1), result.getFirst().getItemStack());
            if (!sameOwner) assertEquals(physical, remainder);
        }
    }
}
