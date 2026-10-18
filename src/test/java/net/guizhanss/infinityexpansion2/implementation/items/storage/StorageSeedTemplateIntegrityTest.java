package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Injected post-credit template mutation; not a live-server exploit. */
class StorageSeedTemplateIntegrityTest {
    private static final NamespacedKey OWNER = new NamespacedKey("ie2-tests", "source_owner");

    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void teardown() { MockBukkit.unmock(); }

    private static ItemStack tagged(String owner, int amount) {
        ItemStack stack = new ItemStack(Material.PAPER, amount);
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(
            OWNER, PersistentDataType.STRING, owner));
        return stack;
    }

    @Test void newlySeededTemplateMutationCannotUseStaleCachedMetadataToPassReceipt() throws Exception {
        var cache = new StorageCache(null, 0, 1000, false);
        var physical = new AtomicReference<>(tagged("original", 8));
        Field actualTemplate = StorageCache.class.getDeclaredField("itemStack");
        actualTemplate.setAccessible(true);

        assertThrows(IllegalStateException.class, () -> StorageInputReceipt.transfer(cache,
            () -> physical.get() == null ? null : physical.get().clone(),
            physical::set, () -> {}, offered -> {
                try {
                    ItemStack newTemplate = offered.clone();
                    newTemplate.setAmount(1);
                    actualTemplate.set(cache, newTemplate);
                } catch (IllegalAccessException reflectionFailure) {
                    throw new IllegalStateException(reflectionFailure);
                }
                int requested = offered.getAmount();
                int accepted = requested - cache.increaseAmount(requested);
                assertTrue(cache.matches(offered), "Initialize existing cached metadata");
                cache.getItemStack().editMeta(meta -> meta.getPersistentDataContainer().set(
                    OWNER, PersistentDataType.STRING, "relabelled"));
                return accepted;
            }));

        assertEquals(0, cache.getAmount(), "Do not retain a claim with an unverified identity");
        assertTrue(cache.hasBlockedTransfer());
        assertEquals("relabelled", cache.getItemStack().getItemMeta()
            .getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
    }
}
