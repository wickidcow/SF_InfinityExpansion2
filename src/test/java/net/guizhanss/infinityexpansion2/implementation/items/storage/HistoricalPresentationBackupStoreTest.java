package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
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
