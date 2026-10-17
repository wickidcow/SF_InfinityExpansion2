package net.guizhanss.infinityexpansion2.implementation.items.storage;

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

import static org.junit.jupiter.api.Assertions.*;

class StorageTemplatePersistenceTest {
    private final AtomicReference<ItemStack> slot = new AtomicReference<>();
    private ItemStack encoded;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        encoded = new ItemStack(Material.DIAMOND, 1);
        encoded.editMeta(meta -> {
            meta.getPersistentDataContainer().set(new NamespacedKey("storage-test", "owner"),
                PersistentDataType.LONG, 9_000_000_001L);
            meta.getPersistentDataContainer().set(new NamespacedKey("storage-test", "display"),
                PersistentDataType.BYTE, (byte) 17);
        });
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void templateWriteDoesNotRequireAViewerAndPreservesTypedMetadata() {
        var original = encoded.clone();
        var dirty = new AtomicInteger();
        assertTrue(StorageTemplatePersistence.update(encoded, this::readCopy, slot::set, dirty::incrementAndGet));
        assertEquals(original, encoded);
        assertNotSame(encoded, slot.get());
        assertEquals(original, slot.get());
        assertEquals(1, dirty.get());
    }

    @Test
    void unchangedTemplateDoesNotRewriteOrMarkDirty() {
        slot.set(encoded.clone());
        var writes = new AtomicInteger();
        var dirty = new AtomicInteger();
        assertFalse(StorageTemplatePersistence.update(encoded, this::readCopy,
            value -> writes.incrementAndGet(), dirty::incrementAndGet));
        assertEquals(0, writes.get());
        assertEquals(0, dirty.get());
    }

    @Test
    void ignoredWriterCannotAuthorizeAStoredBalance() {
        var dirty = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> StorageTemplatePersistence.update(
            encoded, this::readCopy, ignored -> {}, dirty::incrementAndGet));
        assertNull(slot.get());
        assertEquals(0, dirty.get());
    }

    @Test
    void writerCannotChangeTheExpectedIdentityToMakeItsMutationPass() {
        assertThrows(IllegalStateException.class, () -> StorageTemplatePersistence.update(
            encoded, this::readCopy, value -> {
                value.setType(Material.EMERALD);
                slot.set(value);
            }, () -> {}));
        assertEquals(Material.DIAMOND, encoded.getType());
    }

    @Test
    void writeThenThrowDoesNotRetryOrAlterTheSourceTemplate() {
        var writes = new AtomicInteger();
        var original = encoded.clone();
        assertThrows(IllegalStateException.class, () -> StorageTemplatePersistence.update(
            encoded, this::readCopy, value -> {
                writes.incrementAndGet();
                slot.set(value);
                throw new IllegalStateException("Injected write-then-throw");
            }, () -> {}));
        assertEquals(1, writes.get());
        assertEquals(original, encoded);
    }

    @Test
    void dirtyCallbackMustNotUndoTheVerifiedTemplate() {
        assertThrows(IllegalStateException.class, () -> StorageTemplatePersistence.update(
            encoded, this::readCopy, slot::set, () -> slot.set(null)));
    }

    @Test
    void malformedTemplateQuantityIsRejectedBeforeAnyWrite() {
        encoded.setAmount(2);
        assertThrows(IllegalStateException.class, () -> StorageTemplatePersistence.update(
            encoded, this::readCopy, slot::set, () -> {}));
        assertNull(slot.get());
    }

    @Test
    void unknownLegacyBalanceIsDistinctFromGenuinelyEmptyStorage() {
        var orphan = new StorageCache(null, 128, 1000, false);
        assertTrue(orphan.hasUnassignedItems());
        assertEquals(128, orphan.getAmount());
        assertNull(orphan.getItemStack());
        assertFalse(new StorageCache(null, 0, 1000, false).hasUnassignedItems());
        assertFalse(new StorageCache(new ItemStack(Material.DIAMOND), 128, 1000, false).hasUnassignedItems());
    }

    private ItemStack readCopy() {
        ItemStack current = slot.get();
        return current == null ? null : current.clone();
    }
}
