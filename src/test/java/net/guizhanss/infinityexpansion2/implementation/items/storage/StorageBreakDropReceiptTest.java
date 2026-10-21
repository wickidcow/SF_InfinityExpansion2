package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Actual MockBukkit entity handles with controlled callbacks; native event proof is separate. */
class StorageBreakDropReceiptTest {
    private World world;
    private Location location;
    private final List<Item> spawned = new ArrayList<>();

    @BeforeEach void setup() {
        world = MockBukkit.mock().addSimpleWorld("storage-break");
        location = new Location(world, 0, 70, 0);
    }
    @AfterEach void teardown() { MockBukkit.unmock(); }

    private ItemStack item(int amount) {
        var stack = new ItemStack(Material.DIAMOND, amount);
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("ie2-break", "owner"), PersistentDataType.LONG, 9_000_000_001L));
        return stack;
    }

    private void spawn(ItemStack stack, Consumer<Item> capture) {
        Item entity = world.dropItem(location, stack);
        spawned.add(entity);
        capture.accept(entity);
    }

    @Test void exactEntityReceiptsKeepAllQuantitiesAndMetadata() {
        var receipt = new StorageBreakDropReceipt();
        receipt.spawn(item(11), this::spawn);
        receipt.spawn(item(23), this::spawn);
        receipt.verify();
        assertEquals(34, spawned.stream().filter(Item::isValid).mapToInt(i -> i.getItemStack().getAmount()).sum());
        assertTrue(spawned.stream().allMatch(i -> item(1).isSimilar(i.getItemStack())));
    }

    @Test void cancelledSecondSpawnRemovesEarlierReceiptBeforeSourcesCanBeRestored() {
        var receipt = new StorageBreakDropReceipt();
        receipt.spawn(item(11), this::spawn);
        var failure = assertThrows(IllegalStateException.class, () -> receipt.spawn(item(23), (stack, capture) -> {
            spawn(stack, capture);
            spawned.getLast().remove(); // Controlled ItemSpawnEvent cancellation analogue.
        }));
        assertTrue(receipt.rollback(failure));
        assertTrue(spawned.stream().noneMatch(Item::isValid));
    }

    @Test void spawnThenThrowStillHasTheExactHandleNeededForRollback() {
        var receipt = new StorageBreakDropReceipt();
        receipt.spawn(item(11), this::spawn);
        var failure = assertThrows(IllegalStateException.class, () -> receipt.spawn(item(23), (stack, capture) -> {
            spawn(stack, capture);
            throw new IllegalStateException("Injected failure after actual entity creation");
        }));
        assertEquals(2, spawned.size());
        assertTrue(receipt.rollback(failure));
        assertTrue(spawned.stream().noneMatch(Item::isValid));
    }

    @Test void changedSpawnedMetadataCannotAuthorizeSourceDeletion() {
        var receipt = new StorageBreakDropReceipt();
        var failure = assertThrows(IllegalStateException.class, () -> receipt.spawn(item(23), (stack, capture) -> {
            spawn(stack, capture);
            spawned.getLast().setItemStack(new ItemStack(Material.DIAMOND, 23));
        }));
        assertTrue(receipt.rollback(failure));
        assertTrue(spawned.stream().noneMatch(Item::isValid));
    }

    @Test void laterSpawnCannotSilentlyChangeAnEarlierQuantity() {
        var receipt = new StorageBreakDropReceipt();
        receipt.spawn(item(11), this::spawn);
        var failure = assertThrows(IllegalStateException.class, () -> receipt.spawn(item(23), (stack, capture) -> {
            spawn(stack, capture);
            spawned.getFirst().setItemStack(item(12));
        }));
        assertTrue(receipt.rollback(failure));
        assertTrue(spawned.stream().noneMatch(Item::isValid));
    }

    @Test void missingEntityCallbackCannotBeReportedAsDelivery() {
        var receipt = new StorageBreakDropReceipt();
        var failure = assertThrows(IllegalStateException.class, () -> receipt.spawn(item(23), (stack, capture) -> {}));
        assertTrue(receipt.rollback(failure));
        assertTrue(spawned.isEmpty());
    }

    @Test void duplicateEntityCannotSatisfyTwoDifferentDrops() {
        var receipt = new StorageBreakDropReceipt();
        receipt.spawn(item(11), this::spawn);
        var failure = assertThrows(IllegalStateException.class,
            () -> receipt.spawn(item(11), (stack, capture) -> capture.accept(spawned.getFirst())));
        assertTrue(receipt.rollback(failure));
        assertTrue(spawned.stream().noneMatch(Item::isValid));
    }

    @Test void invalidDropDoesNotCallWorldOrAlterSource() {
        var receipt = new StorageBreakDropReceipt();
        var called = new AtomicReference<>(false);
        assertThrows(IllegalArgumentException.class,
            () -> receipt.spawn(new ItemStack(Material.AIR), (stack, capture) -> called.set(true)));
        assertFalse(called.get());
    }
}
