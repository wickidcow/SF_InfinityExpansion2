package net.guizhanss.infinityexpansion2.core.migration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real migration copy boundary; not a claim of a complete historical-world conversion. */
class LegacyMetadataPreservationTest {
    private static final NamespacedKey ID = key("slimefun", "slimefun_item");
    private static final NamespacedKey CHARGE = key("oldaddon", "charge");
    private static final NamespacedKey OWNER = key("oldaddon", "owner");
    private static final NamespacedKey COUNT = key("oldaddon", "count");
    private static final NamespacedKey BYTES = key("oldaddon", "bytes");
    private static final NamespacedKey NESTED = key("oldaddon", "nested");

    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test
    void copiesExactTypedValuesWhileKeepingTheExplicitTargetId() throws Exception {
        var source = source();
        var target = item("IE_TARGET_ID");
        copy(source, target);
        var data = target.getItemMeta().getPersistentDataContainer();
        assertEquals("IE_TARGET_ID", data.get(ID, PersistentDataType.STRING));
        assertEquals(Float.floatToIntBits(123.4567F), Float.floatToIntBits(data.get(CHARGE, PersistentDataType.FLOAT)));
        assertEquals(9_000_000_001L, data.get(COUNT, PersistentDataType.LONG));
        assertEquals("11111111-2222-3333-4444-555555555555", data.get(OWNER, PersistentDataType.STRING));
        assertArrayEquals(new byte[] {0, -1, 42, 127}, data.get(BYTES, PersistentDataType.BYTE_ARRAY));
        assertEquals(17, source.getAmount());
        assertEquals(17, target.getAmount());
    }

    @Test
    void keepsExistingTargetValuesAndTheirOriginalTypes() throws Exception {
        var source = source();
        var target = item("IE_TARGET_ID");
        var meta = target.getItemMeta();
        meta.getPersistentDataContainer().set(CHARGE, PersistentDataType.DOUBLE, 7.25D);
        target.setItemMeta(meta);
        copy(source, target);
        assertEquals(7.25D, target.getItemMeta().getPersistentDataContainer().get(CHARGE, PersistentDataType.DOUBLE));
        assertFalse(target.getItemMeta().getPersistentDataContainer().has(CHARGE, PersistentDataType.FLOAT));
        assertEquals(123.4567F, source.getItemMeta().getPersistentDataContainer().get(CHARGE, PersistentDataType.FLOAT));
    }

    @Test
    void preservesNestedAndUnknownAddonDataWithoutMutatingTheSource() throws Exception {
        var source = source();
        var meta = source.getItemMeta();
        var nested = meta.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        nested.set(COUNT, PersistentDataType.LONG, Long.MAX_VALUE);
        nested.set(BYTES, PersistentDataType.BYTE_ARRAY, new byte[] {-1, 0, 1});
        meta.getPersistentDataContainer().set(NESTED, PersistentDataType.TAG_CONTAINER, nested);
        source.setItemMeta(meta);
        var target = item("IE_TARGET_ID");
        copy(source, target);
        var restored = target.getItemMeta().getPersistentDataContainer().get(NESTED, PersistentDataType.TAG_CONTAINER);
        assertNotNull(restored);
        assertEquals(Long.MAX_VALUE, restored.get(COUNT, PersistentDataType.LONG));
        assertArrayEquals(new byte[] {-1, 0, 1}, restored.get(BYTES, PersistentDataType.BYTE_ARRAY));
        assertEquals("OLD_SOURCE_ID", source.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        assertEquals(17, source.getAmount());
    }

    @Test
    void repeatedCopyIsIdempotentAndKeepsTheTargetWinsPolicy() throws Exception {
        var source = source();
        var target = item("IE_TARGET_ID");
        copy(source, target);
        var before = target.getItemMeta().getPersistentDataContainer().getKeys();
        var meta = source.getItemMeta();
        meta.getPersistentDataContainer().set(COUNT, PersistentDataType.LONG, 4L);
        source.setItemMeta(meta);
        copy(source, target);
        assertEquals(before, target.getItemMeta().getPersistentDataContainer().getKeys());
        assertEquals(9_000_000_001L, target.getItemMeta().getPersistentDataContainer().get(COUNT, PersistentDataType.LONG));
    }

    @Test
    void sourceWithoutMetadataRemainsACompatibleNoOp() throws Exception {
        var target = item("IE_TARGET_ID");
        var before = target.getItemMeta();
        copy(new ItemStack(Material.PAPER), target);
        assertEquals(before, target.getItemMeta());
    }

    @Test
    void rejectedMetadataApplicationIsReportedInsteadOfSilentSuccess() {
        var source = source();
        var target = new RefusingItem();
        assertThrows(IllegalStateException.class, () -> copy(source, target));
        assertEquals("OLD_SOURCE_ID", source.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        assertFalse(target.getItemMeta().getPersistentDataContainer().has(COUNT));
    }

    @Test
    void failedNativeCopyDoesNotApplyPartialMetadataToTheTarget() {
        var source = new FailingCopyItem();
        var target = item("IE_TARGET_ID");
        var before = target.getItemMeta();
        var failure = assertThrows(IllegalStateException.class, () -> copy(source, target));
        assertEquals("injected native copy failure", failure.getMessage());
        assertEquals(before, target.getItemMeta());
        assertEquals(17, source.getAmount());
    }

    @Test
    void sourceAndTargetRemainIndependentAfterSuccessfulCopy() throws Exception {
        var source = source();
        var target = item("IE_TARGET_ID");
        copy(source, target);
        var meta = target.getItemMeta();
        meta.getPersistentDataContainer().set(BYTES, PersistentDataType.BYTE_ARRAY, new byte[] {8, 9});
        meta.getPersistentDataContainer().remove(OWNER);
        target.setItemMeta(meta);
        assertArrayEquals(new byte[] {0, -1, 42, 127}, source.getItemMeta().getPersistentDataContainer().get(BYTES, PersistentDataType.BYTE_ARRAY));
        assertTrue(source.getItemMeta().getPersistentDataContainer().has(OWNER, PersistentDataType.STRING));
    }

    private static void copy(ItemStack source, ItemStack target) throws Exception {
        var method = LegacyItemMigrator.class.getDeclaredMethod("copyPdc", ItemStack.class, ItemStack.class);
        method.setAccessible(true); // Test only: exercise the production caller, not a reimplementation.
        try {
            method.invoke(new LegacyItemMigrator(), source, target);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private static ItemStack item(String id) {
        var item = new ItemStack(Material.PAPER, 17);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack source() {
        var item = item("OLD_SOURCE_ID");
        var meta = item.getItemMeta();
        var data = meta.getPersistentDataContainer();
        data.set(CHARGE, PersistentDataType.FLOAT, 123.4567F);
        data.set(COUNT, PersistentDataType.LONG, 9_000_000_001L);
        data.set(OWNER, PersistentDataType.STRING, "11111111-2222-3333-4444-555555555555");
        data.set(BYTES, PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
        item.setItemMeta(meta);
        return item;
    }

    private static NamespacedKey key(String namespace, String value) { return new NamespacedKey(namespace, value); }

    private static final class RefusingItem extends ItemStack {
        RefusingItem() { super(Material.PAPER, 17); }
        @Override public boolean hasItemMeta() { return true; }
        @Override public boolean setItemMeta(ItemMeta meta) { return false; }
    }

    private static final class FailingCopyItem extends ItemStack {
        FailingCopyItem() { super(Material.PAPER, 17); }
        @Override public boolean hasItemMeta() { return true; }
        @Override public ItemMeta getItemMeta() {
            ItemMeta actual = super.getItemMeta();
            PersistentDataContainer data = actual.getPersistentDataContainer();
            PersistentDataContainer failing = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("copyTo")) throw new IllegalStateException("injected native copy failure");
                    return method.invoke(data, args);
                });
            return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[] {ItemMeta.class},
                (proxy, method, args) -> method.getName().equals("getPersistentDataContainer") ? failing : method.invoke(actual, args));
        }
    }
}
