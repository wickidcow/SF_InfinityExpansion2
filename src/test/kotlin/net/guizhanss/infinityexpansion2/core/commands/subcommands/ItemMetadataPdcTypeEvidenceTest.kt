package net.guizhanss.infinityexpansion2.core.commands.subcommands

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

/**
 * Existing saved owner/storage keys can have distinct primitive PDC types.
 * Doctor may report ONLY key names and type labels; no values are read,
 * printed, converted, or rewritten. No historical AlbionMC bytes are claimed.
 */
class ItemMetadataPdcTypeEvidenceTest {
    @BeforeEach fun setup() { MockBukkit.mock() }
    @AfterEach fun teardown() { MockBukkit.unmock() }

    private val sfKey = NamespacedKey("slimefun", "slimefun_item")
    private val ownerKey = NamespacedKey("legacy-evidence", "owner")
    private val ledgerKey = NamespacedKey("legacy-evidence", "ledger")

    private fun uranium(): ItemStack = ItemStack(Material.PAPER, 1).apply {
        editMeta {
            it.persistentDataContainer.set(sfKey, PersistentDataType.STRING, "SMALL_URANIUM")
        }
    }

    @Test fun sameKeyDifferentOwnerTypeIsDiagnosedWithoutExposingValuesOrChangingBytes() {
        val historical = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ownerKey, PersistentDataType.STRING,
                    "historical-private-owner-must-not-appear")
            }
        }
        val current = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ownerKey, PersistentDataType.LONG, 1234567890123L)
            }
        }
        val oldBytes = historical.serializeAsBytes()
        val currentBytes = current.serializeAsBytes()

        val report = ItemMetadataDifference.inspect(historical, current)
        val lines = ItemMetadataDifference.sourceEvidence("Main/GEO", report)
        val displayed = lines.joinToString("\n")

        assertFalse(report.isSimilar)
        assertFalse(report.pdcSame)
        assertTrue(report.onlyMainPdcKeys.isEmpty())
        assertTrue(report.onlyOffhandPdcKeys.isEmpty())
        assertEquals(listOf("legacy-evidence:owner (STRING/LONG)"), report.changedPdcTypeKeys)
        assertEquals(3, lines.size, "Preserve the existing bounded source-evidence shape")
        assertTrue(displayed.contains("legacy-evidence:owner (STRING/LONG)"))
        assertFalse(displayed.contains("historical-private-owner-must-not-appear"))
        assertFalse(displayed.contains("1234567890123"))
        assertArrayEquals(oldBytes, historical.serializeAsBytes())
        assertArrayEquals(currentBytes, current.serializeAsBytes())
    }

    @Test fun typedArrayChangesAreReportedButValuesAreNeverPrinted() {
        val historical = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ledgerKey, PersistentDataType.BYTE_ARRAY,
                    byteArrayOf(67, 12, -1))
            }
        }
        val current = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ledgerKey, PersistentDataType.LONG_ARRAY,
                    longArrayOf(1234567890123L, 99))
            }
        }
        val report = ItemMetadataDifference.inspect(historical, current)
        assertEquals(listOf("legacy-evidence:ledger (BYTE_ARRAY/LONG_ARRAY)"),
            report.changedPdcTypeKeys)
        val evidence = ItemMetadataDifference.sourceEvidence("Offhand/GEO", report).joinToString("\n")
        assertFalse(evidence.contains("1234567890123"))
        assertTrue(evidence.contains("BYTE_ARRAY/LONG_ARRAY"))
    }

    @Test fun changedValuesWithinTheSameTypeDoNotLookLikeTypeMigrations() {
        val historical = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, "private-old")
            }
        }
        val current = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, "private-new")
            }
        }
        val report = ItemMetadataDifference.inspect(historical, current)
        assertFalse(report.pdcSame)
        assertTrue(report.changedPdcTypeKeys.isEmpty())
        val evidence = ItemMetadataDifference.sourceEvidence("Main/Slimefun template", report)
            .joinToString("\n")
        assertFalse(evidence.contains("private-old"))
        assertFalse(evidence.contains("private-new"))
        assertTrue(evidence.contains("PDC=DIFFERENT"))
    }

    @Test fun missingKeyIsStillClassifiedOnlyAsKeyPresenceMismatch() {
        val historical = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(ownerKey, PersistentDataType.INTEGER, 99)
            }
        }
        val current = uranium()
        val report = ItemMetadataDifference.inspect(historical, current)
        assertEquals(listOf("legacy-evidence:owner"), report.onlyMainPdcKeys)
        assertTrue(report.changedPdcTypeKeys.isEmpty())
    }
}
