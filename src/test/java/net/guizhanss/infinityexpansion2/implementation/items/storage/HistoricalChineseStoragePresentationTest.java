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
    @Test void identicalVisibleEnglishCannotOverrideHistoricalOwner() {
        ItemStack original = storage("Storage Unit II", "Stored: 34560000");
        ItemStack differentOwner = original.clone();
        differentOwner.editMeta(meta -> meta.getPersistentDataContainer()
                .set(OWNER, PersistentDataType.STRING, "different-owner"));

        assertFalse(StorageItemPresentation.equivalentPresentation(
                original, differentOwner, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2"));
        assertEquals(original.getItemMeta().getPersistentDataContainer()
                .get(OWNER, PersistentDataType.STRING), "private-owner");
    }

    @Test void dynamicLoreAndDifferentSlimefunIdRemainIneligible() {
        ItemStack historical = storage("存储单元 II", "已存储：34560000");
        ItemStack english = storage("Storage Unit II", "Stored: 34560000");
        english.editMeta(meta -> meta.lore(List.of(
                Component.text("Stored: 34560000"), Component.text("Capacity: 34560000"))));

        assertFalse(StorageItemPresentation.equivalentPresentation(
                historical, english, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2"));
        assertFalse(StorageItemPresentation.equivalentPresentation(
                historical, english, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_3"));
        assertFalse(StorageItemPresentation.equivalentPresentation(
                historical, english, null, "IE_STORAGE_UNIT_2"));
    }

    @Test void previewChangesOnlyEnglishPresentationAndLeavesOriginalUntouched() {
        ItemStack historical = storage("存储单元 II", "已存储：34560000");
        ItemStack english = storage("Storage Unit II", "Stored: 34560000");
        ItemStack snapshot = historical.clone();

        ItemStack candidate = HistoricalPresentationPreview.candidate(
                historical, english, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2");

        assertNotNull(candidate);
        assertEquals(snapshot, historical);
        assertEquals(7, candidate.getAmount());
        assertEquals(english.getItemMeta().displayName(), candidate.getItemMeta().displayName());
        assertEquals(english.getItemMeta().lore(), candidate.getItemMeta().lore());
        assertEquals(34560000L, candidate.getItemMeta().getPersistentDataContainer()
                .get(STORAGE, PersistentDataType.LONG));
        assertEquals("private-owner", candidate.getItemMeta().getPersistentDataContainer()
                .get(OWNER, PersistentDataType.STRING));
        assertEquals(snapshot.getItemMeta().getPersistentDataContainer().getKeys(),
                candidate.getItemMeta().getPersistentDataContainer().getKeys());
    }

    @Test void previewRejectsIdentityMismatchWithoutMutatingHistoricalItem() {
        ItemStack historical = storage("存储单元 II", "已存储：34560000");
        ItemStack english = storage("Storage Unit II", "Stored: 34560000");
        ItemStack snapshot = historical.clone();

        assertNull(HistoricalPresentationPreview.candidate(
                historical, english, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_3"));
        assertNull(HistoricalPresentationPreview.candidate(
                historical, english, null, "IE_STORAGE_UNIT_2"));
        assertEquals(snapshot, historical);
    }

    @Test void transactionRequiresExactConfirmationAndUnchangedLiveLedger() {
        ItemStack original = storage("存储单元 II", "已存储：34560000");
        ItemStack english = storage("Storage Unit II", "Stored: 34560000");
        HistoricalPresentationTransaction transaction = HistoricalPresentationTransaction.preview(
                original, english, "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2");
        assertNotNull(transaction);
        ItemStack backedUp = transaction.backup();
        ItemStack modified = original.clone();
        modified.editMeta(meta -> meta.getPersistentDataContainer()
                .set(STORAGE, PersistentDataType.LONG, 34560001L));

        assertNull(transaction.confirm("incorrect-token", original));
        assertNull(transaction.confirm(transaction.confirmationToken(), modified));
        assertEquals(original, backedUp);
        ItemStack repaired = transaction.confirm(transaction.confirmationToken(), original);
        assertNotNull(repaired);
        assertEquals(34560000L, repaired.getItemMeta().getPersistentDataContainer()
                .get(STORAGE, PersistentDataType.LONG));
        assertEquals(7, repaired.getAmount());
        assertNull(transaction.confirm(transaction.confirmationToken(), original));
        assertNull(transaction.rollback(modified));
        assertEquals(backedUp, transaction.rollback(repaired));
        assertNull(transaction.rollback(repaired));
        assertEquals("存储单元 II", ((net.kyori.adventure.text.TextComponent)
                original.getItemMeta().displayName()).content());
    }

    @Test void transactionBackupAndPreviewAreDetachedFromCallers() {
        ItemStack original = storage("存储单元 II", "已存储：34560000");
        HistoricalPresentationTransaction transaction = HistoricalPresentationTransaction.preview(
                original, storage("Storage Unit II", "Stored: 34560000"),
                "IE_STORAGE_UNIT_2", "IE_STORAGE_UNIT_2");
        assertNotNull(transaction);
        transaction.backup().setAmount(1);
        transaction.proposed().setAmount(2);
        assertEquals(7, transaction.backup().getAmount());
        assertEquals(7, transaction.proposed().getAmount());
        assertEquals(7, transaction.confirm(transaction.confirmationToken(), original).getAmount());
    }

}
