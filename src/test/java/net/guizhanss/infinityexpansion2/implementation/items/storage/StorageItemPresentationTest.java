package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Identity-safe, presentation-only tests. Real registered Slimefun IDs are checked by the native runs. */
class StorageItemPresentationTest {
    private static final NamespacedKey OWNER = new NamespacedKey("ie2-test", "owner");

    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void teardown() { MockBukkit.unmock(); }

    private ItemStack item(boolean legacy, String owner) {
        ItemStack item = new ItemStack(Material.PAPER, 17);
        item.editMeta(meta -> {
            meta.displayName(legacy
                    ? Component.text("").append(Component.text("Aluminum Singularity", NamedTextColor.GRAY))
                    : Component.text("Aluminum Singularity", NamedTextColor.GRAY));
            meta.lore(List.of(legacy
                    ? Component.text("").append(Component.text("Requirement: 64", NamedTextColor.AQUA))
                    : Component.text("Requirement: 64", NamedTextColor.AQUA)));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner);
        });
        return item;
    }

    @Test void equalPlainTextAndAllOtherMetadataCanUseLegacyComponents() {
        ItemStack legacy = item(true, "owner-a");
        ItemStack modern = item(false, "owner-a");
        ItemStack before = legacy.clone();
        assertFalse(legacy.isSimilar(modern));
        assertTrue(StorageItemPresentation.equivalentPresentation(legacy, modern, "IE_ALUMINUM_SINGULARITY",
                "IE_ALUMINUM_SINGULARITY"));
        assertEquals(before, legacy, "Presentation matching must never mutate actual stored items");
    }

    @Test void differentSlimefunIdsAreNotInterchangeable() {
        assertFalse(StorageItemPresentation.equivalentPresentation(
                item(true, "same"), item(false, "same"), "IE_ALUMINUM_SINGULARITY", "IE_GOLD_SINGULARITY"));
        assertFalse(StorageItemPresentation.equivalentPresentation(
                item(true, "same"), item(false, "same"), null, null));
    }

    @Test void ownerPdcAndActualLoreContentMustRemainStrict() {
        ItemStack old = item(true, "owner-a");
        assertFalse(StorageItemPresentation.equivalentPresentation(
                old, item(false, "owner-b"), "ID", "ID"));
        ItemStack altered = item(false, "owner-a");
        altered.editMeta(meta -> meta.lore(List.of(Component.text("Requirement: 65"))));
        assertFalse(StorageItemPresentation.equivalentPresentation(old, altered, "ID", "ID"));
    }

    @Test void customModelOrLoreLengthCannotBeSilentlyDiscarded() {
        ItemStack old = item(true, "owner-a");
        ItemStack model = item(false, "owner-a");
        model.editMeta(meta -> {
            var data = meta.getCustomModelDataComponent();
            data.setFloats(List.of(123f));
            meta.setCustomModelDataComponent(data);
        });
        assertTrue(model.getItemMeta().hasCustomModelDataComponent(),
                "A model-component regression must exercise a real model value");
        assertFalse(StorageItemPresentation.equivalentPresentation(old, model, "ID", "ID"));
        ItemStack shortened = item(false, "owner-a");
        shortened.editMeta(meta -> meta.lore(List.of()));
        assertFalse(StorageItemPresentation.equivalentPresentation(old, shortened, "ID", "ID"));
    }
}
