package net.guizhanss.infinityexpansion2.implementation.items.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Test-first source contract; a native menu still needs its own runtime proof. */
class StorageTickerOutputOrderingTest {
    @Test void tickerOutputMustVerifyPhysicalSlotAfterPersistedSourceDebit() throws Exception {
        String file = Files.readString(Path.of(
            "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageUnit.kt"));
        int start = file.indexOf("// output item");
        int end = file.indexOf("// TODO: try to update signs", start);
        assertTrue(start >= 0 && end > start, "Ticker output boundary not found");
        String output = file.substring(start, end);
        assertTrue(output.contains("StorageOutputReceipt.transfer("),
            "A cache debit must be followed by physical output readback");
        assertFalse(output.contains("menu.pushItem(fetched, *outputSlots)"),
            "An unchecked menu push cannot certify a physical output receipt");
        assertTrue(output.contains("cache.blockUnverifiedTransfer()"),
            "An ambiguous debit must hold the cache without speculative refund");
        assertTrue(file.contains("StorageInputReceipt.transfer("),
            "Retain the existing input debit-before-credit protection");
    }
}
