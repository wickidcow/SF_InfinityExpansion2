package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Test-only source-callback regressions; not live exploit replay. */
class StorageInputIncidentBaselineTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void sourceReadMustRejectExistingIdentitySubstitutionBeforePhysicalDebit() {
        var cache = new StorageCache(new ItemStack(Material.DIAMOND), 16, 1000, false);
        var originalSource = new AtomicReference<>(new ItemStack(Material.EMERALD, 8));
        var first = new AtomicBoolean(true);
        assertThrows(IllegalStateException.class, () -> StorageInputReceipt.transfer(
            cache, () -> {
                if (first.getAndSet(false)) cache.getItemStack().setType(Material.EMERALD);
                return originalSource.get() == null ? null : originalSource.get().clone();
            }, originalSource::set, () -> {}, item -> {
                int amount = item.getAmount();
                return amount - cache.increaseAmount(amount);
            }));
        assertEquals(16, cache.getAmount());
        assertNotNull(originalSource.get());
        assertEquals(8, originalSource.get().getAmount());
        assertTrue(cache.hasBlockedTransfer());
    }

    @Test void postCreditVoidPolicyMutationMustHoldTheUnverifiedInput() {
        var cache = new StorageCache(new ItemStack(Material.DIAMOND), 16, 20, false);
        var source = new AtomicReference<>(new ItemStack(Material.DIAMOND, 8));
        assertThrows(IllegalStateException.class, () -> StorageInputReceipt.transfer(
            cache,
            () -> source.get() == null ? null : source.get().clone(),
            source::set, () -> {}, item -> {
                int amount = item.getAmount();
                int accepted = amount - cache.increaseAmount(amount);
                cache.setVoidExcess(true);
                return accepted;
            }));
        assertEquals(16, cache.getAmount());
        assertEquals(4, source.get().getAmount());
        assertTrue(cache.hasBlockedTransfer());
    }
}
