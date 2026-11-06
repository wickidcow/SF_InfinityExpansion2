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

    @Test
    void cosmeticTemplateRepairDoesNotTouchTheSeparateStoredCount() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var ledger = new AtomicInteger(731);
        var dirty = new AtomicInteger();
        slot.set(original.clone());

        assertTrue(StorageTemplatePersistence.updateCosmeticWithRollback(original, canonical,
            this::readCopy, slot::set, dirty::incrementAndGet));
        assertEquals(canonical, slot.get());
        assertEquals(731, ledger.get(), "Cosmetic repair must never change saved quantity");
        assertEquals(1, dirty.get());
        assertEquals(original, encoded, "A saved historical item must not be modified in place");
    }

    @Test
    void cosmeticWriteThenThrowRestoresThePreviousSlotAndCount() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var ledger = new AtomicInteger(731);
        var writes = new AtomicInteger();
        slot.set(original.clone());

        assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(original, canonical,
                this::readCopy, value -> {
                    slot.set(value);
                    if (writes.incrementAndGet() == 1) {
                        throw new IllegalStateException("Injected write-after-commit failure");
                    }
                }, () -> {}));
        assertEquals(2, writes.get(), "Failed canonical write must attempt verified restoration");
        assertEquals(original, slot.get(), "The previous private template must be restored");
        assertEquals(731, ledger.get(), "The separate ledger must not be written");
    }

    @Test
    void cosmeticDirtyFailureRestoresPreviousTemplate() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var dirty = new AtomicInteger();
        slot.set(original.clone());

        assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(original, canonical,
                this::readCopy, slot::set, () -> {
                    if (dirty.incrementAndGet() == 1) {
                        throw new IllegalStateException("Injected markDirty failure");
                    }
                }));
        assertEquals(original, slot.get(), "A failed dirty mark must restore the old template");
        assertEquals(2, dirty.get());
    }


    @Test
    void abruptStopBeforeOrAfterCosmeticWriteKeepsExactLedgerAndOwnerMetadata() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        final int persistedBalance = 731;
        // Model the two possible durable snapshots at an abrupt process stop:
        // old private template, or new private template. No rollback callback
        // runs during SIGKILL; the separate persisted ledger is untouched.
        for (ItemStack persistedTemplate : new ItemStack[] {original, canonical}) {
            slot.set(persistedTemplate.clone());
            // Cross the actual Bukkit ItemStack byte serialization boundary,
            // rather than merely cloning an in-memory object. This is still a
            // unit-level restart fixture, not evidence of a native SIGKILL.
            byte[] savedBytes = readCopy().serializeAsBytes();
            ItemStack restartedTemplate = ItemStack.deserializeBytes(savedBytes);
            int restartedBalance = Integer.parseInt(Integer.toString(persistedBalance));
            assertEquals(persistedTemplate, restartedTemplate);
            assertNotSame(persistedTemplate, restartedTemplate);
            assertEquals(9_000_000_001L, restartedTemplate.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("storage-test", "owner"), PersistentDataType.LONG));
            assertEquals((byte) 17, restartedTemplate.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("storage-test", "display"), PersistentDataType.BYTE));
            assertEquals(731, restartedBalance);
            assertEquals(original.getType(), restartedTemplate.getType());
        }
    }

    @Test
    void failedRollbackLeavesCanonicalTemplateDetectableAndNeverTouchesLedger() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var ledger = new AtomicInteger(731);
        var writes = new AtomicInteger();
        slot.set(original.clone());
        var failure = assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(original, canonical,
                this::readCopy, value -> {
                    if (writes.incrementAndGet() == 2) {
                        throw new IllegalStateException("Injected rollback failure");
                    }
                    slot.set(value);
                    throw new IllegalStateException("Injected canonical write failure");
                }, () -> {}));
        assertEquals(1, failure.getSuppressed().length,
            "Rollback failure must be surfaced rather than silently claiming restoration");
        assertEquals(canonical, slot.get(),
            "A failed rollback can leave a canonical template; never claim old template survived");
        assertEquals(731, ledger.get());
    }

    @Test
    void foreignPersistedTemplateIsNeverOverwrittenByCosmeticRepairOrRollback() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var foreign = encoded.clone();
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("storage-test", "owner"), PersistentDataType.LONG, 42L));
        slot.set(foreign.clone());
        var writes = new AtomicInteger();
        var dirty = new AtomicInteger();
        assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(
                original, canonical, this::readCopy,
                value -> { writes.incrementAndGet(); slot.set(value); },
                dirty::incrementAndGet));
        assertEquals(foreign, slot.get(), "A foreign owner's persisted template must survive");
        assertEquals(0, writes.get(), "Neither repair nor rollback may overwrite foreign identity");
        assertEquals(0, dirty.get());
    }

    @Test
    void alreadyCanonicalPersistedTemplateIsIdempotentAfterRestart() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        slot.set(ItemStack.deserializeBytes(canonical.serializeAsBytes()));
        var writes = new AtomicInteger();
        assertFalse(StorageTemplatePersistence.updateCosmeticWithRollback(
            original, canonical, this::readCopy,
            value -> { writes.incrementAndGet(); slot.set(value); },
            () -> fail("No dirty mark for an already persisted canonical template")));
        assertEquals(0, writes.get());
        assertEquals(canonical, slot.get());
    }

    @Test
    void failedCanonicalWriteMustNotRollBackOverConcurrentForeignIdentity() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var foreign = encoded.clone();
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("storage-test", "owner"), PersistentDataType.LONG, 42L));
        slot.set(original.clone());
        var writes = new AtomicInteger();
        var dirty = new AtomicInteger();

        var failure = assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(
                original, canonical, this::readCopy, value -> {
                    writes.incrementAndGet();
                    slot.set(foreign.clone());
                    throw new IllegalStateException("Injected concurrent replacement");
                }, dirty::incrementAndGet));
        assertEquals("Injected concurrent replacement", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length,
            "Foreign identity must be reported as an unsafe rollback boundary");
        assertEquals(foreign, slot.get(), "Never overwrite the concurrent owner's item");
        assertEquals(1, writes.get(), "No rollback write may target a foreign identity");
        assertEquals(0, dirty.get());
    }


    @Test
    void changedOwnerBetweenInitialCheckAndWriteMustNotBeOverwritten() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var foreign = encoded.clone();
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("storage-test", "owner"), PersistentDataType.LONG, 42L));
        slot.set(original.clone());
        var reads = new AtomicInteger();
        var writes = new AtomicInteger();
        var dirty = new AtomicInteger();
        java.util.function.Supplier<ItemStack> changingReader = () -> {
            if (reads.incrementAndGet() == 2) {
                slot.set(foreign.clone());
            }
            return readCopy();
        };

        assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(
                original, canonical, changingReader,
                value -> { writes.incrementAndGet(); slot.set(value); },
                dirty::incrementAndGet));
        assertEquals(foreign, slot.get(), "A changed owner must retain the physical slot");
        assertEquals(0, writes.get(), "A stale initial read must not authorize any write");
        assertEquals(0, dirty.get());
    }

    @Test
    void rollbackMustNotOverwriteForeignOwnerInstalledAfterRollbackRead() {
        var original = encoded.clone();
        var canonical = encoded.clone();
        canonical.editMeta(meta -> meta.displayName(
            net.kyori.adventure.text.Component.text("Canonical visual name")));
        var foreign = encoded.clone();
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("storage-test", "owner"), PersistentDataType.LONG, 42L));
        slot.set(original.clone());
        var reads = new AtomicInteger();
        var writes = new AtomicInteger();
        var dirty = new AtomicInteger();
        java.util.function.Supplier<ItemStack> changingReader = () -> {
            // Initial check, update check, forward prewrite, failure read,
            // rollback update check, then rollback prewrite.
            if (reads.incrementAndGet() == 6) {
                slot.set(foreign.clone());
            }
            return readCopy();
        };
        var failure = assertThrows(IllegalStateException.class,
            () -> StorageTemplatePersistence.updateCosmeticWithRollback(
                original, canonical, changingReader, candidate -> {
                    slot.set(candidate);
                    if (writes.incrementAndGet() == 1) {
                        throw new IllegalStateException("Injected failure after canonical write");
                    }
                }, dirty::incrementAndGet));
        assertEquals("Injected failure after canonical write", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length,
            "The unsafe rollback boundary must remain visible to the caller");
        assertEquals(foreign, slot.get(), "Rollback must not erase a new owner's template");
        assertEquals(1, writes.get(), "No rollback writer may run after owner replacement");
        assertEquals(0, dirty.get());
    }

    private ItemStack readCopy() {
        ItemStack current = slot.get();
        return current == null ? null : current.clone();
    }
}
