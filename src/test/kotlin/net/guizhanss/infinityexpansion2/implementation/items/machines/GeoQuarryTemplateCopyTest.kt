package net.guizhanss.infinityexpansion2.implementation.items.machines

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

/** Item-source clone isolation, not authorization to normalize old saved items. */
class GeoQuarryTemplateCopyTest {

    @BeforeEach fun setup() { MockBukkit.mock() }
    @AfterEach fun teardown() { MockBukkit.unmock() }

    private val idKey = NamespacedKey("slimefun", "slimefun_item")
    private val ownerKey = NamespacedKey("geo-probe", "owner")
    private val ledgerKey = NamespacedKey("geo-probe", "ledger")
    private val bytesKey = NamespacedKey("geo-probe", "extra_bytes")

    private fun uranium(historical: Boolean): ItemStack = ItemStack(Material.PLAYER_HEAD, 1).apply {
        editMeta { meta ->
            val readable = Component.text("Small Chunk of Uranium", NamedTextColor.RED)
            meta.displayName(if (historical) {
                Component.text("").append(readable.decoration(TextDecoration.BOLD, false))
            } else {
                readable.decoration(TextDecoration.BOLD, true)
            })
            meta.lore(listOf(
                Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)))
            meta.persistentDataContainer.set(idKey, PersistentDataType.STRING, "SMALL_URANIUM")
            meta.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, "unchanged-owner")
            meta.persistentDataContainer.set(ledgerKey, PersistentDataType.LONG, 9876543210123L)
            meta.persistentDataContainer.set(bytesKey, PersistentDataType.BYTE_ARRAY, byteArrayOf(-1, 0, 42))
        }
    }

    @Test fun registeredGEOItemIsNeverReusedForQuantityEdits() {
        val registered = uranium(false)
        val originalBytes = registered.serializeAsBytes()
        val guide = copyGeoQuarryTemplate(registered, 5)
        val produced = copyGeoQuarryTemplate(registered, 17)

        assertNotSame(registered, guide)
        assertNotSame(registered, produced)
        assertNotSame(guide, produced)
        assertEquals(1, registered.amount)
        assertEquals(5, guide.amount)
        assertEquals(17, produced.amount)
        assertTrue(registered.isSimilar(guide))
        assertTrue(registered.isSimilar(produced))
        assertArrayEquals(originalBytes, registered.serializeAsBytes())
        assertArrayEquals(registered.itemMeta.persistentDataContainer.serializeToBytes(),
            produced.itemMeta.persistentDataContainer.serializeToBytes())
    }

    @Test fun changingBorrowedOutputNeverChangesTheTemplateOrEarlierOutput() {
        val registered = uranium(false)
        val unchangedSourceBytes = registered.serializeAsBytes()
        val first = copyGeoQuarryTemplate(registered, 1)
        val firstBytes = first.serializeAsBytes()
        val second = copyGeoQuarryTemplate(registered, 1)
        second.editMeta { meta ->
            meta.displayName(Component.text("custom-new-owner-label", NamedTextColor.GOLD))
            meta.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, "second-owner")
        }

        assertArrayEquals(unchangedSourceBytes, registered.serializeAsBytes())
        assertArrayEquals(firstBytes, first.serializeAsBytes())
        assertFalse(first.isSimilar(second))
        assertEquals("unchanged-owner",
            registered.itemMeta.persistentDataContainer.get(ownerKey, PersistentDataType.STRING))
        assertEquals(9876543210123L,
            registered.itemMeta.persistentDataContainer.get(ledgerKey, PersistentDataType.LONG))
    }

    @Test fun historicalAdventureDifferenceRemainsDistinctDespiteSameSlimefunId() {
        val old = uranium(true)
        val fresh = uranium(false)
        val oldBytes = old.serializeAsBytes()
        val newBytes = fresh.serializeAsBytes()
        val oldOutput = copyGeoQuarryTemplate(old, 12)
        val freshOutput = copyGeoQuarryTemplate(fresh, 12)

        assertFalse(old.isSimilar(fresh), "Same Slimefun ID never authorizes erasing rich name differences")
        assertFalse(oldOutput.isSimilar(freshOutput))
        assertEquals("SMALL_URANIUM",
            oldOutput.itemMeta.persistentDataContainer.get(idKey, PersistentDataType.STRING))
        assertEquals("SMALL_URANIUM",
            freshOutput.itemMeta.persistentDataContainer.get(idKey, PersistentDataType.STRING))
        assertEquals(old.itemMeta.displayName(), oldOutput.itemMeta.displayName())
        assertEquals(fresh.itemMeta.displayName(), freshOutput.itemMeta.displayName())
        assertEquals(12, oldOutput.amount)
        assertEquals(12, freshOutput.amount)
        assertArrayEquals(oldBytes, old.serializeAsBytes())
        assertArrayEquals(newBytes, fresh.serializeAsBytes())
    }

    @Test fun unrelatedSlimefunIdAndTypedOwnerNeverBecomeSameItem() {
        val legitimate = uranium(false)
        val differentId = uranium(false).apply {
            editMeta { it.persistentDataContainer.set(idKey, PersistentDataType.STRING, "OTHER_ID") }
        }
        val differentOwner = uranium(false).apply {
            editMeta { it.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, "other-owner") }
        }

        assertFalse(copyGeoQuarryTemplate(legitimate, 1).isSimilar(
            copyGeoQuarryTemplate(differentId, 1)))
        assertFalse(copyGeoQuarryTemplate(legitimate, 1).isSimilar(
            copyGeoQuarryTemplate(differentOwner, 1)))
    }
}
