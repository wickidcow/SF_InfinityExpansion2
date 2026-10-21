package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Conservation regression for an imported amount outside the unit's capacity. */
class StorageInvalidBalanceGuardTest {
    @Test void legacyOverfilledCacheRetainsEveryStoredItemWithoutIntWrap() {
        StorageCache cache = new StorageCache(null, Integer.MAX_VALUE, 16, false);
        assertTrue(cache.hasBlockedTransfer(), "An existing invalid count must block transfers");
        assertEquals(64, cache.increaseAmount(64));
        assertEquals(Integer.MAX_VALUE, cache.getAmount());
    }

    @Test void legacyOverfilledCacheWithVoidEnabledStillPreservesOldStock() {
        StorageCache cache = new StorageCache(null, 200, 100, true);
        assertTrue(cache.hasBlockedTransfer(), "Deliberate void excess is not permission to destroy old stock");
        assertEquals(64, cache.increaseAmount(64));
        assertEquals(200, cache.getAmount());
    }

    @Test void negativeCountAndInvalidLimitFailClosed() {
        StorageCache negative = new StorageCache(null, -4, 100, false);
        assertTrue(negative.hasBlockedTransfer());
        assertEquals(4, negative.increaseAmount(4));
        assertEquals(-4, negative.getAmount());
        StorageCache zeroCapacity = new StorageCache(null, 0, 0, false);
        assertTrue(zeroCapacity.hasBlockedTransfer());
        assertEquals(16, zeroCapacity.increaseAmount(16));
        assertEquals(0, zeroCapacity.getAmount());
    }

    @Test void invalidBalanceCannotOpenInputReceipt() {
        StorageCache invalid = new StorageCache(null, 101, 100, false);
        assertFalse(invalid.beginInputReceipt());
        assertTrue(invalid.hasBlockedTransfer());
    }

    @Test void normalCapacityAndDeliberateVoidExcessAreUnchanged() {
        StorageCache partial = new StorageCache(null, 6, 10, false);
        assertEquals(6, partial.increaseAmount(10));
        assertEquals(10, partial.getAmount());
        StorageCache voided = new StorageCache(null, 10, 10, true);
        assertEquals(0, voided.increaseAmount(64));
        assertEquals(10, voided.getAmount());
        StorageCache full = new StorageCache(null, 10, 10, false);
        assertEquals(64, full.increaseAmount(64));
        assertEquals(10, full.getAmount());
    }
}
