package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Fault-injected output receipts. They do not establish crash or live-player proof. */
class StorageOutputReceiptTest {
    private static final NamespacedKey OWNER = new NamespacedKey("ie2-output-test", "owner");
    private AtomicReference<ItemStack> physical;
    private AtomicInteger cachedAmount;
    private AtomicBoolean held;
    private ItemStack template;

    @BeforeEach void setup() {
        MockBukkit.mock();
        template = new ItemStack(Material.DIAMOND, 1);
        template.editMeta(meta -> meta.getPersistentDataContainer().set(
            OWNER, PersistentDataType.STRING, "server-a"));
        physical = new AtomicReference<>();
        cachedAmount = new AtomicInteger(16);
        held = new AtomicBoolean();
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private ItemStack physicalCopy() {
        ItemStack value = physical.get();
        return value == null ? null : value.clone();
    }
    private ItemStack reserve(int n) {
        cachedAmount.addAndGet(-n);
        ItemStack result = template.clone();
        result.setAmount(n);
        return result;
    }
    private void insert(ItemStack item) { physical.set(item.clone()); }
    private int run(java.util.function.Consumer<ItemStack> writer, Runnable dirty) {
        return StorageOutputReceipt.transfer(
            template, 16, () -> template, this::physicalCopy, this::reserve,
            cachedAmount::get, writer, dirty, () -> held.set(true));
    }

    @Test void emptyOutputReceivesExactlyPersistedStockAndIdentity() {
        assertEquals(16, run(this::insert, () -> {}));
        assertEquals(0, cachedAmount.get());
        assertEquals(16, physical.get().getAmount());
        assertTrue(template.isSimilar(physical.get()));
        assertFalse(held.get());
    }
    @Test void ignoredWriterCannotPretendToDeliverStock() {
        assertThrows(IllegalStateException.class, () -> run(ignored -> {}, () -> {}));
        assertTrue(held.get());
        assertEquals(0, cachedAmount.get());
        assertNull(physical.get());
    }
    @Test void writerWriteThenThrowNeverAutoRefundsDeliveredStock() {
        assertThrows(IllegalStateException.class, () -> run(item -> {
            insert(item);
            throw new IllegalStateException("written then threw");
        }, () -> {}));
        assertTrue(held.get());
        assertEquals(0, cachedAmount.get());
        assertEquals(16, physical.get().getAmount());
    }
    @Test void dirtyCallbackCannotReplaceOutputWithWrongPdc() {
        assertThrows(IllegalStateException.class, () -> run(this::insert, () ->
            physical.set(new ItemStack(Material.DIAMOND, 16))));
        assertTrue(held.get());
        assertEquals(0, cachedAmount.get());
    }
    @Test void sourceRestoreBeforeOutputCreditIsRejected() {
        assertThrows(IllegalStateException.class, () -> StorageOutputReceipt.transfer(
            template, 16, () -> template, this::physicalCopy, n -> {
                ItemStack returned = reserve(n);
                cachedAmount.set(16);
                return returned;
            }, cachedAmount::get, this::insert, () -> {}, () -> held.set(true)));
        assertTrue(held.get());
        assertNull(physical.get());
    }
    @Test void sourceRestoreAfterPhysicalCreditRequiresHold() {
        assertThrows(IllegalStateException.class, () -> run(this::insert, () -> cachedAmount.set(16)));
        assertTrue(held.get());
        assertEquals(16, cachedAmount.get());
        assertEquals(16, physical.get().getAmount());
    }
    @Test void fullOrForeignOutputMustNotDebitSource() {
        ItemStack full = template.clone();
        full.setAmount(64);
        physical.set(full);
        assertEquals(0, run(this::insert, () -> {}));
        assertEquals(16, cachedAmount.get());
        assertFalse(held.get());
        physical.set(new ItemStack(Material.DIAMOND, 32));
        assertEquals(0, run(this::insert, () -> {}));
        assertEquals(16, cachedAmount.get());
        assertFalse(held.get());
    }
    @Test void partialOutputOnlyFillsItsExistingPhysicalCapacity() {
        ItemStack partial = template.clone();
        partial.setAmount(60);
        physical.set(partial);
        assertEquals(4, run(item -> {
            ItemStack current = physicalCopy();
            current.setAmount(current.getAmount() + item.getAmount());
            physical.set(current);
        }, () -> {}));
        assertEquals(12, cachedAmount.get());
        assertEquals(64, physical.get().getAmount());
        assertFalse(held.get());
    }
    @Test void dirtyCallbackCannotRelabelLiveSourceTemplate() {
        assertThrows(IllegalStateException.class, () -> run(this::insert, () ->
            template.editMeta(meta -> meta.getPersistentDataContainer().set(
                OWNER, PersistentDataType.STRING, "server-b"))));
        assertTrue(held.get());
        assertEquals(0, cachedAmount.get());
        assertEquals(16, physical.get().getAmount());
        assertFalse(template.isSimilar(physical.get()));
    }
    @Test void emptyStoredAmountCannotManufactureOutput() {
        assertEquals(0, StorageOutputReceipt.transfer(
            template, 0, () -> template, this::physicalCopy, this::reserve,
            cachedAmount::get, this::insert, () -> {}, () -> held.set(true)));
        assertNull(physical.get());
        assertFalse(held.get());
    }
}
