package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Issue #73: historical translated text is NOT equivalent to an English
 * registered template merely because its Slimefun ID matches.
 *
 * These are isolated metadata guards, not evidence of a successful native
 * repair, storage-block migration, or restart.
 */
class HistoricalChineseStoragePresentationTest {
    private static final NamespacedKey STORAGE = new NamespacedKey("ie2-test", "stored-ledger");
    private static final NamespacedKey OWNER = new NamespacedKey("ie2-test", "owner");

    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void teardown() { MockBukkit.unmock(); }

    private ItemStack storage(String name, String lore) {
        ItemStack item = new ItemStack(Material.BARREL, 7);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name));
            meta.lore(List.of(Component.text(lore)));
            meta.getPersistentDataContainer().set(STORAGE, PersistentDataType.LONG, 34560000L);
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "private-owner");
        });
        return item;
    }

    @Test void translatedHistoricalStorageCannotPassCosmeticCanonicalization() {
        ItemStack historical = storage("存储单元 II", "已存储：34560000");
        ItemStack english = storage("Storage Unit II", "Stored: 34560000");
        ItemStack before = historical.clone();

        assertFalse(StorageItemPresentation.equivalentPresentation(
                historical, english, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2"));
        assertEquals(before, historical, "Comparison must not mutate stored PDC, text or amount");
        assertEquals(7, historical.getAmount());
        assertEquals(34560000L, historical.getItemMeta().getPersistentDataContainer()
                .get(STORAGE, PersistentDataType.LONG));
    }

    @Test void equalEnglishPresentationCannotBypassTypedStorageLedgerDifference() {
        ItemStack original = storage("Storage Unit II", "Stored: 34560000");
        ItemStack changed = original.clone();
        changed.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(STORAGE);
            meta.getPersistentDataContainer().set(STORAGE, PersistentDataType.INTEGER, 34560000);
        });
        assertFalse(StorageItemPresentation.equivalentPresentation(
                original, changed, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2"));
        assertEquals(34560000L, original.getItemMeta().getPersistentDataContainer()
                .get(STORAGE, PersistentDataType.LONG));
    }

    @Test void cosmeticOnlyLegacyWrapperStillAllowedWithIdenticalLedger() {
        ItemStack original = storage("Storage Unit II", "Stored: 34560000");
        ItemStack wrapped = original.clone();
        wrapped.editMeta(meta -> {
            meta.displayName(Component.text("").append(Component.text("Storage Unit II")));
            meta.lore(List.of(Component.text("").append(Component.text("Stored: 34560000"))));
        });
        assertTrue(StorageItemPresentation.equivalentPresentation(
                wrapped, original, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2"));
    }
}
