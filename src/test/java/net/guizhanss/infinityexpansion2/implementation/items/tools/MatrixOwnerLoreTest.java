package net.guizhanss.infinityexpansion2.implementation.items.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Real metadata calls used by InfinityMatrix; no plugin services or player data are started. */
class MatrixOwnerLoreTest {
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test
    void bindsAnOlderItemWithNoLoreWithoutFailing() {
        ItemMeta meta = meta();
        assertNull(meta.lore());
        assertDoesNotThrow(() -> MatrixOwnerLore.update(meta, OWNER));
        assertEquals(List.of(line(OWNER)), meta.lore());
    }

    @Test
    void clearsTheDisplayOwnerOnAnOlderItemWithNoLore() {
        ItemMeta meta = meta();
        assertDoesNotThrow(() -> MatrixOwnerLore.update(meta, null));
        assertEquals(List.of(line(null)), meta.lore());
    }

    @Test
    void richPrefixIsNotFlattenedWhileAddingAnOwnerLine() {
        ItemMeta meta = meta();
        var original = richLore();
        meta.lore(original);
        MatrixOwnerLore.update(meta, OWNER);
        assertEquals(original, meta.lore().subList(0, original.size()));
        assertEquals(line(OWNER), meta.lore().get(original.size()));
    }

    @Test
    void onlyTheFirstOwnerLineIsReplaced() {
        ItemMeta meta = meta();
        var original = new ArrayList<>(richLore());
        original.add(1, Component.text("Owner: previous", NamedTextColor.RED));
        original.add(Component.text("Owner: addon annotation"));
        meta.lore(original);
        MatrixOwnerLore.update(meta, OWNER);
        var expected = new ArrayList<>(original);
        expected.set(1, line(OWNER));
        assertEquals(expected, meta.lore());
    }

    @Test
    void splitOwnerComponentsAreMatchedWithoutChangingOtherLines() {
        ItemMeta meta = meta();
        Component split = Component.text("Own").append(Component.text("er: previous"));
        meta.lore(List.of(split, richLore().get(1)));
        MatrixOwnerLore.update(meta, null);
        assertEquals(List.of(line(null), richLore().get(1)), meta.lore());
    }

    @Test
    void originalCaseAndPrefixMatchingSemanticsRemainIntact() {
        ItemMeta meta = meta();
        var original = List.of(Component.text("owner: lowercase"), Component.text(" Owner: space"),
            Component.text("Addon Owner: suffix"));
        meta.lore(original);
        MatrixOwnerLore.update(meta, OWNER);
        assertEquals(original, meta.lore().subList(0, 3));
        assertEquals(line(OWNER), meta.lore().get(3));
    }

    @Test
    void repeatedBindAndUnbindAreIdempotent() {
        ItemMeta meta = meta(); meta.lore(richLore());
        for (int i = 0; i < 50; i++) {
            MatrixOwnerLore.update(meta, OWNER);
            MatrixOwnerLore.update(meta, null);
        }
        assertEquals(richLore().size() + 1, meta.lore().size());
        assertEquals(richLore(), meta.lore().subList(0, richLore().size()));
        assertEquals(line(null), meta.lore().get(richLore().size()));
    }

    @Test
    void ownerAndItemPersistentKeysAndModelsAreNeverChangedByThePresentationHelper() {
        var item = new ItemStack(Material.NETHER_STAR, 17);
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "INFINITY_MATRIX");
        pdc.set(key("infinityexpansion2:owner"), PersistentDataType.STRING, OWNER.toString());
        pdc.set(key("oldaddon:count"), PersistentDataType.LONG, 9_000_000_001L);
        pdc.set(key("oldaddon:charge"), PersistentDataType.FLOAT, 123.4567F);
        var model = meta.getCustomModelDataComponent();
        model.setFloats(List.of(12345.25F)); model.setStrings(List.of("legacy-model"));
        meta.setCustomModelDataComponent(model);
        meta.displayName(Component.text("Player's custom name"));
        meta.setUnbreakable(true);
        ItemMeta before = meta.clone();
        MatrixOwnerLore.update(meta, null);
        item.setItemMeta(meta);
        before.lore(null); meta.lore(null);
        assertEquals(before, meta);
        assertEquals(17, item.getAmount());
        assertEquals(Material.NETHER_STAR, item.getType());
    }

    @Test
    void missingOwnershipMarkersAreNotInventedByLoreUpdates() {
        ItemMeta meta = meta();
        var keys = java.util.Set.copyOf(meta.getPersistentDataContainer().getKeys());
        MatrixOwnerLore.update(meta, OWNER);
        assertEquals(keys, meta.getPersistentDataContainer().getKeys());
    }

    @Test
    @SuppressWarnings("deprecation") // Differential fixture for the former String-lore algorithm.
    void legacyRepresentableLoreMatchesTheFormerAlgorithm() {
        var random = new Random(72111L);
        for (int attempt = 0; attempt < 250; attempt++) {
            ItemMeta old = meta();
            var lines = new ArrayList<String>();
            for (int count = random.nextInt(8); count > 0; count--)
                lines.add("\u00a7" + "0123456789abcdef".charAt(random.nextInt(16)) + "line-" + count);
            if (random.nextBoolean()) lines.add(random.nextInt(lines.size() + 1), "\u00a7cOwner: prior");
            // The old algorithm required a non-null lore list. Null is covered by the new regression tests.
            if (lines.isEmpty()) lines.add("\u00a77Description");
            old.setLore(lines);
            var candidate = old.clone();
            UUID owner = random.nextBoolean() ? OWNER : null;
            var legacy = new ArrayList<>(old.getLore());
            int index = -1;
            for (int i = 0; i < legacy.size(); i++) {
                if (org.bukkit.ChatColor.stripColor(legacy.get(i)).startsWith("Owner:")) { index = i; break; }
            }
            String replacement = "\u00a7bOwner: \u00a7f" + (owner == null ? "None" : owner);
            if (index == -1) legacy.add(replacement); else legacy.set(index, replacement);
            old.setLore(legacy);
            MatrixOwnerLore.update(candidate, owner);
            assertEquals(old.lore(), candidate.lore(), "Historical owner layout " + attempt);
        }
    }

    private static ItemMeta meta() { return new ItemStack(Material.NETHER_STAR).getItemMeta(); }
    private static NamespacedKey key(String name) { return java.util.Objects.requireNonNull(NamespacedKey.fromString(name)); }
    private static Component line(UUID owner) {
        return LegacyComponentSerializer.legacySection().deserialize("\u00a7bOwner: \u00a7f" + (owner == null ? "None" : owner));
    }
    private static List<Component> richLore() {
        return List.of(Component.translatable("item.minecraft.nether_star"),
            Component.text("Original", NamedTextColor.GOLD).font(Key.key("oldaddon:font"))
                .insertion("existing").hoverEvent(HoverEvent.showText(Component.text("Keep this"))));
    }
}
