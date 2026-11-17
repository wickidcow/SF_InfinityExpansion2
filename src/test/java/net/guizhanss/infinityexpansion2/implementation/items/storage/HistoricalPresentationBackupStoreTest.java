package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class HistoricalPresentationBackupStoreTest {
    private static final NamespacedKey BALANCE = new NamespacedKey("ie2-test", "ledger");
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("ie2-test", "owner");

    @TempDir Path root;

    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void teardown() { MockBukkit.unmock(); }

    private ItemStack historical() {
        ItemStack stack = new ItemStack(Material.BARREL, 7);
        stack.editMeta(meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("存储单元 II"));
            meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Custom original lore")));
            meta.getPersistentDataContainer().set(BALANCE, PersistentDataType.LONG, 34_560_000L);
            meta.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, "original-owner");
        });
        return stack;
    }

    private ItemStack english(ItemStack original) {
        ItemStack changed = original.clone();
        changed.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("Storage Unit II")));
        return changed;
    }

    @Test void backupIsDurableByteExactAndOwnerScoped() throws Exception {
        UUID owner = UUID.randomUUID();
        ItemStack old = historical();
        ItemStack next = english(old);
        Path directory = root.resolve("backups");
        UUID id = HistoricalPresentationBackupStore.save(directory, owner, old, next);
        var loaded = HistoricalPresentationBackupStore.load(directory, owner, id);
        assertEquals(old, loaded.original());
        assertEquals(next, loaded.proposed());
        assertArrayEquals(old.serializeAsBytes(), loaded.original().serializeAsBytes());
        assertEquals(34_560_000L, loaded.original().getItemMeta().getPersistentDataContainer()
                .get(BALANCE, PersistentDataType.LONG));
        assertEquals("original-owner", loaded.proposed().getItemMeta().getPersistentDataContainer()
                .get(OWNER_KEY, PersistentDataType.STRING));
        assertThrows(IOException.class, () -> HistoricalPresentationBackupStore.load(
                directory, UUID.randomUUID(), id));
        // A second backup for the same owner must coexist, never overwrite.
        UUID second = HistoricalPresentationBackupStore.save(directory, owner, old, next);
        assertNotEquals(id, second);
        assertEquals(old, HistoricalPresentationBackupStore.load(directory, owner, second).original());
    }

    @Test void rejectsCorruptAndMissingBackupBeforeAnItemCanChange() throws Exception {
        UUID owner = UUID.randomUUID();
        Path directory = root.resolve("backups");
        UUID id = HistoricalPresentationBackupStore.save(
                directory, owner, historical(), english(historical()));
        Path data = directory.resolve(owner.toString()).resolve(id + ".bin");
        byte[] bytes = Files.readAllBytes(data);
        bytes[bytes.length - 1] ^= 0x1;
        Files.write(data, bytes);
        assertThrows(IOException.class, () -> HistoricalPresentationBackupStore.load(directory, owner, id));
        assertThrows(IOException.class, () -> HistoricalPresentationBackupStore.load(
                directory, owner, UUID.randomUUID()));
    }

    @Test void checksumValidButChangedTypedLedgerRecordCannotBeLoaded() throws Exception {
        UUID owner = UUID.randomUUID();
        ItemStack old = historical();
        ItemStack changed = english(old);
        changed.editMeta(meta ->
                meta.getPersistentDataContainer().set(BALANCE, PersistentDataType.LONG, 34_560_010L));
        Path directory = root.resolve("backups");
        UUID id = HistoricalPresentationBackupStore.save(
                directory, owner, old, english(old));
        Path file = directory.resolve(owner.toString()).resolve(id + ".bin");

        // Model an old or untrusted backup that has a *matching* public digest.
        // A SHA-256 checksum is not a substitute for validating the typed item
        // identity and non-presentation components on every rollback read.
        byte[] oldBytes = old.serializeAsBytes();
        byte[] changedBytes = changed.serializeAsBytes();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(oldBytes);
        sha.update(changedBytes);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x49453250);
            out.writeInt(1);
            out.writeLong(owner.getMostSignificantBits());
            out.writeLong(owner.getLeastSignificantBits());
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
            out.writeInt(oldBytes.length);
            out.write(oldBytes);
            out.writeInt(changedBytes.length);
            out.write(changedBytes);
            out.write(sha.digest());
        }
        Files.write(file, bytes.toByteArray());
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.load(directory, owner, id),
                "Even checksum-valid records must not authorize changed storage ledger data");
    }

    @Test void rollbackRetainsButRetiresBackupToPreventReplay() throws Exception {
        UUID owner = UUID.randomUUID();
        ItemStack old = historical(), next = english(old);
        Path directory = root.resolve("backups");
        UUID id = HistoricalPresentationBackupStore.save(directory, owner, old, next);
        HistoricalPresentationBackupStore.markRolledBack(directory, owner, id);
        assertThrows(IOException.class, () -> HistoricalPresentationBackupStore.load(directory, owner, id));
        assertTrue(Files.isRegularFile(directory.resolve(owner.toString()).resolve(id + ".rolledback")));
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.markRolledBack(directory, owner, id));
    }

    @Test void backupCannotAuthorizeModifiedOwnerOrTypedLedgerDespiteMatchingQuantity() throws Exception {
        UUID owner = UUID.randomUUID();
        Path dir = root.resolve("backups");
        ItemStack original = historical();

        ItemStack changedLedger = english(original);
        changedLedger.editMeta(meta ->
                meta.getPersistentDataContainer().set(BALANCE, PersistentDataType.LONG, 34_560_001L));
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.save(dir, owner, original, changedLedger));

        ItemStack changedOwner = english(original);
        changedOwner.editMeta(meta ->
                meta.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, "different-owner"));
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.save(dir, owner, original, changedOwner));

        ItemStack changedType = english(original);
        changedType.setType(Material.CHEST);
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.save(dir, owner, original, changedType));

        // All attempts were rejected before writing any owner-scoped record.
        Path ownerDir = dir.resolve(owner.toString());
        assertFalse(Files.exists(ownerDir), "A refused repair must not create backup records");
    }

    @Test void rejectsMismatchedAmountsAndUnavailableOwnerDirectories() throws Exception {
        UUID owner = UUID.randomUUID();
        ItemStack old = historical();
        ItemStack next = english(old);
        next.setAmount(6);
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.save(root.resolve("backups"), owner, old, next));
        assertThrows(IOException.class, () ->
                HistoricalPresentationBackupStore.load(root.resolve("does-not-exist"), owner, UUID.randomUUID()));
    }
}
