package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import net.guizhanss.infinityexpansion2.InfinityExpansion2;
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType;
import net.guizhanss.infinityexpansion2.core.services.LocalizationService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Upgrading a storage unit in the Storage Forge must keep its stored items. */
class StorageForgeUpgradeTest {

    private final Map<Field, Object> previousServices = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var plugin = mock(InfinityExpansion2.class);
        when(plugin.getName()).thenReturn("InfinityExpansion2");
        when(plugin.namespace()).thenReturn("infinityexpansion2");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("IE2StorageForgeFixture"));
        replaceService("instance", plugin);
        replaceService("localization", mock(LocalizationService.class, invocation -> {
            if (invocation.getMethod().getName().equals("getGuiItem")) return new ItemStack(Material.PAPER);
            if (invocation.getMethod().getReturnType() == String.class) return "fixture";
            return RETURNS_DEFAULTS.answer(invocation);
        }));
    }

    @AfterEach
    void tearDown() throws Exception {
        for (var entry : previousServices.entrySet()) entry.getKey().set(null, entry.getValue());
        MockBukkit.unmock();
    }

    @Test
    void storedItemsMoveToUpgradedUnitWithNewCapacity() {
        var oldUnit = unitWith(new ItemStack(Material.COBBLESTONE), 12_345, 16_000, true);
        var output = new ItemStack(Material.BARREL);

        var result = StorageForge.transferContents(
            new ItemStack[] {new ItemStack(Material.IRON_INGOT), oldUnit, null}, output, 64_000);

        assertNotNull(result);
        var cache = read(result);
        assertNotNull(cache, "Upgraded unit must carry the old unit's contents");
        assertEquals(12_345, cache.getAmount());
        assertEquals(64_000, cache.getLimit());
        assertTrue(cache.getVoidExcess());
        assertEquals(new ItemStack(Material.COBBLESTONE), cache.getItemStack());
    }

    @Test
    void plainInputsLeaveOutputUnchanged() {
        var output = new ItemStack(Material.BARREL);
        var expected = output.clone();

        var result = StorageForge.transferContents(
            new ItemStack[] {new ItemStack(Material.IRON_INGOT), null}, output, 64_000);

        assertEquals(expected, result);
        assertNull(read(result));
    }

    @Test
    void plainInputsCannotMintAStorageLedgerFromTheRecipeOutput() {
        var preloadedOutput = unitWith(new ItemStack(Material.DIAMOND), 500, 64_000, true);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {new ItemStack(Material.IRON_INGOT)}, preloadedOutput, 64_000),
            "A preloaded recipe result must not create stored items without a source ledger");
        assertEquals(500, read(preloadedOutput).getAmount(),
            "Refusal must not mutate the original recipe output");
    }

    @Test
    void plainInputsCannotPassAnUnreadableOutputLedger() {
        var output = new ItemStack(Material.BARREL);
        var meta = output.getItemMeta();
        meta.getPersistentDataContainer().set(
            StorageUnit.Companion.getSTORAGE_KEY(),
            org.bukkit.persistence.PersistentDataType.STRING, "invalid-result-ledger");
        output.setItemMeta(meta);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {new ItemStack(Material.IRON_INGOT)}, output, 64_000),
            "A malformed result ledger must not be emitted as an upgraded unit");
        assertEquals("invalid-result-ledger", output.getItemMeta().getPersistentDataContainer().get(
            StorageUnit.Companion.getSTORAGE_KEY(), org.bukkit.persistence.PersistentDataType.STRING));
    }

    @Test
    void emptyStoragePreservesItsSeededIdentityAndSettings() {
        var template = new ItemStack(Material.DIAMOND);
        var oldUnit = unitWith(template, 0, 16_000, true);

        var result = StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000);

        assertNotNull(result);
        var cache = read(result);
        assertNotNull(cache);
        assertEquals(template, cache.getItemStack());
        assertEquals(0, cache.getAmount());
        assertEquals(64_000, cache.getLimit());
        assertTrue(cache.getVoidExcess());
    }

    @Test
    void emptyUnassignedStorageStillPreservesItsSettings() {
        var oldUnit = unitWith(null, 0, 16_000, true);

        var result = StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000);

        assertNotNull(result);
        var cache = read(result);
        assertNotNull(cache);
        assertNull(cache.getItemStack());
        assertEquals(0, cache.getAmount());
        assertEquals(64_000, cache.getLimit());
        assertTrue(cache.getVoidExcess());
    }

    @Test
    void stackedSourceOrMultipleStorageLedgersRefuseUpgrade() {
        var oldUnit = unitWith(new ItemStack(Material.DIAMOND), 123, 16_000, false);
        oldUnit.setAmount(2);
        assertNull(StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000));

        var emptyFirst = unitWith(null, 0, 16_000, false);
        var emptySecond = unitWith(null, 0, 16_000, true);
        assertNull(StorageForge.transferContents(
            new ItemStack[] {emptyFirst, emptySecond}, new ItemStack(Material.BARREL), 64_000));
    }

    @Test
    void multipleOutputsOrExistingResultLedgerRefuseUpgrade() {
        var oldUnit = unitWith(new ItemStack(Material.DIAMOND), 123, 16_000, false);
        var stackedOutput = new ItemStack(Material.BARREL, 2);
        assertNull(StorageForge.transferContents(
            new ItemStack[] {oldUnit}, stackedOutput, 64_000));

        var populatedOutput = unitWith(new ItemStack(Material.DIRT), 1, 64_000, false);
        assertNull(StorageForge.transferContents(
            new ItemStack[] {oldUnit}, populatedOutput, 64_000));
    }

    @Test
    void contentsThatDoNotFitRefuseTheCraft() {
        var oldUnit = unitWith(new ItemStack(Material.COBBLESTONE), 70_000, 100_000, false);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000));
    }

    @Test
    void twoFilledUnitsRefuseTheCraftInsteadOfLosingOne() {
        var first = unitWith(new ItemStack(Material.COBBLESTONE), 10, 16_000, false);
        var second = unitWith(new ItemStack(Material.DIRT), 20, 16_000, false);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {first, second}, new ItemStack(Material.BARREL), 64_000));
    }

    @Test
    void positiveLegacyBalanceWithoutItemIdentityRefusesUpgrade() {
        var unassigned = unitWith(null, 500, 16_000, false);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {unassigned}, new ItemStack(Material.BARREL), 64_000));
    }

    @Test
    void invalidSourceBalanceOrCapacityNeverBecomesAValidLookingUpgrade() {
        var template = new ItemStack(Material.DIAMOND);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {unitWith(template, -1, 16_000, false)},
            new ItemStack(Material.BARREL), 64_000));
        assertNull(StorageForge.transferContents(
            new ItemStack[] {unitWith(template, 16_001, 16_000, false)},
            new ItemStack(Material.BARREL), 64_000));
        assertNull(StorageForge.transferContents(
            new ItemStack[] {unitWith(template, 1, 0, false)},
            new ItemStack(Material.BARREL), 64_000));
        assertNull(StorageForge.transferContents(
            new ItemStack[] {unitWith(template, 1, 16_000, false)},
            new ItemStack(Material.BARREL), 0));
    }

    @Test
    void unreadableStorageTagRefusesCraftRatherThanDiscardingIt() {
        var oldUnit = new ItemStack(Material.BARREL);
        var meta = oldUnit.getItemMeta();
        meta.getPersistentDataContainer().set(
            StorageUnit.Companion.getSTORAGE_KEY(),
            org.bukkit.persistence.PersistentDataType.STRING, "unreadable-ledger");
        oldUnit.setItemMeta(meta);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000));
    }

    @Test
    void missingOrMistypedNestedFieldsRefuseUpgrade() {
        for (String required : new String[] {"amount", "max_amount"}) {
            var damaged = changeNestedField(
                unitWith(new ItemStack(Material.DIAMOND), 42, 16_000, true), required, false);
            assertNull(StorageForge.transferContents(
                new ItemStack[] {damaged}, new ItemStack(Material.BARREL), 64_000),
                "Missing " + required + " must not become a default balance");
        }
        for (String field : new String[] {"amount", "max_amount", "item", "void_excess"}) {
            var damaged = changeNestedField(
                unitWith(new ItemStack(Material.DIAMOND), 42, 16_000, true), field, true);
            assertNull(StorageForge.transferContents(
                new ItemStack[] {damaged}, new ItemStack(Material.BARREL), 64_000),
                "Mistyped " + field + " must not be silently replaced");
        }
    }

    @Test
    void historicallyMissingOptionalVoidFlagKeepsStock() {
        var oldUnit = changeNestedField(
            unitWith(new ItemStack(Material.DIAMOND), 42, 16_000, false), "void_excess", false);
        var upgraded = StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000);
        assertNotNull(upgraded);
        var cache = read(upgraded);
        assertNotNull(cache);
        assertEquals(42, cache.getAmount());
        assertEquals(64_000, cache.getLimit());
        assertEquals(new ItemStack(Material.DIAMOND), cache.getItemStack());
        assertFalse(cache.getVoidExcess());
    }

    @Test
    void unknownNestedLedgerFieldsRefuseUpgradeWithoutLosingTheOriginal() {
        var oldUnit = unitWith(new ItemStack(Material.DIAMOND), 42, 16_000, true);
        var meta = oldUnit.getItemMeta();
        var outer = meta.getPersistentDataContainer();
        var nested = outer.get(
            StorageUnit.Companion.getSTORAGE_KEY(), org.bukkit.persistence.PersistentDataType.TAG_CONTAINER);
        assertNotNull(nested);
        var existing = nested.getKeys().iterator().next();
        var futureKey = new org.bukkit.NamespacedKey(existing.getNamespace(), "future_owner_receipt");
        nested.set(futureKey, org.bukkit.persistence.PersistentDataType.STRING, "preserve-me");
        outer.set(StorageUnit.Companion.getSTORAGE_KEY(),
            org.bukkit.persistence.PersistentDataType.TAG_CONTAINER, nested);
        oldUnit.setItemMeta(meta);

        assertNull(StorageForge.transferContents(
            new ItemStack[] {oldUnit}, new ItemStack(Material.BARREL), 64_000),
            "Unknown future ledger fields must not be discarded by an upgrade");

        var original = oldUnit.getItemMeta().getPersistentDataContainer().get(
            StorageUnit.Companion.getSTORAGE_KEY(), org.bukkit.persistence.PersistentDataType.TAG_CONTAINER);
        assertNotNull(original);
        assertEquals("preserve-me", original.get(futureKey, org.bukkit.persistence.PersistentDataType.STRING));
    }

    private static ItemStack changeNestedField(ItemStack unit, String name, boolean mistype) {
        var meta = unit.getItemMeta();
        var outer = meta.getPersistentDataContainer();
        var nested = outer.get(
            StorageUnit.Companion.getSTORAGE_KEY(), org.bukkit.persistence.PersistentDataType.TAG_CONTAINER);
        assertNotNull(nested);
        var key = nested.getKeys().stream().filter(k -> k.getKey().equals(name)).findFirst().orElseThrow();
        if (mistype) {
            nested.set(key, org.bukkit.persistence.PersistentDataType.STRING, "corrupt");
        } else {
            nested.remove(key);
        }
        outer.set(StorageUnit.Companion.getSTORAGE_KEY(),
            org.bukkit.persistence.PersistentDataType.TAG_CONTAINER, nested);
        unit.setItemMeta(meta);
        return unit;
    }

    private static ItemStack unitWith(ItemStack stored, int amount, int limit, boolean voidExcess) {
        var unit = new ItemStack(Material.BARREL);
        var meta = unit.getItemMeta();
        PersistentDataAPI.set(meta, StorageUnit.Companion.getSTORAGE_KEY(), PersistentStorageCacheType.getTYPE(),
            new StorageCache(stored, amount, limit, voidExcess));
        unit.setItemMeta(meta);
        return unit;
    }

    private static StorageCache read(ItemStack stack) {
        return PersistentDataAPI.get(
            stack.getItemMeta(), StorageUnit.Companion.getSTORAGE_KEY(), PersistentStorageCacheType.getTYPE());
    }

    private void replaceService(String name, Object value) throws Exception {
        var field = InfinityExpansion2.class.getDeclaredField(name);
        field.setAccessible(true);
        previousServices.put(field, field.get(null));
        field.set(null, value);
    }
}
