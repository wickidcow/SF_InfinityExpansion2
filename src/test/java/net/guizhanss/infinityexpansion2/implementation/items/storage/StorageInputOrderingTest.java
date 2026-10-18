package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Test-first source contract for the current IE2 storage input paths.
 *
 * The source audit deliberately fails on unchanged production until both
 * inventory and menu input use an authoritative debit receipt. It is not a
 * successful live-player exploit replay.
 */
class StorageInputOrderingTest {
    private static final Path SOURCE = Path.of(
        "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageUnit.kt");

    private static String section(String start, String end) throws IOException {
        String source = Files.readString(SOURCE);
        int from = source.indexOf(start);
        assertTrue(from >= 0, "Expected production entry point: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "Expected production section boundary: " + end);
        return source.substring(from, to);
    }

    @Test
    void playerDepositCannotTrustDetachedStorageContentsOrCreditBeforeVerifiedDebit() throws Exception {
        String deposit = section("InteractionMode.DEPOSIT -> {", "InteractionMode.WITHDRAW_SINGLE -> {");
        assertFalse(deposit.contains("for (item in pInv.storageContents)"),
            "Detached storageContents items must not be treated as authoritative writable slots");
        assertTrue(deposit.contains("StorageInputReceipt.transfer"),
            "Require a source-slot debit with authoritative readback before crediting IE2 cache");
    }

    @Test
    void tickerInputCannotCreditCacheBeforeMenuSlotDebitIsVerified() throws Exception {
        String ticker = section("// input item", "// output item");
        assertTrue(ticker.contains("StorageInputReceipt.transfer"),
            "Ticker must verify actual input-menu slot debit, not credit storage then call consumeItem");
        assertFalse(ticker.contains("menu.inputItem(cache, inputItem)"),
            "Previous tick order credited storage before checking a physical menu deduction");
    }

    @Test
    void detachedSnapshotModelShowsWhyMutatingReturnedStackIsNotEnough() {
        ItemStack physical = new ItemStack(Material.DIAMOND, 8);
        ItemStack detached = physical.clone();
        StorageCache cache = new StorageCache(new ItemStack(Material.DIAMOND), 0, 1000, false);

        // Equivalent to the old ordering with a defensive-copy inventory getter:
        // menu.inputItem(cache, item) followed by item.amount -= consumed.
        int consumed = detached.getAmount() - cache.increaseAmount(detached.getAmount());
        detached.setAmount(detached.getAmount() - consumed);

        assertEquals(8, physical.getAmount(), "Original physical slot remains untouched");
        assertEquals(8, cache.getAmount(), "Cache was already credited");
        assertEquals(16, physical.getAmount() + cache.getAmount(),
            "Fault model creates two claims to the same eight items");
    }
}
