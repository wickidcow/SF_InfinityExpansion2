package net.guizhanss.infinityexpansion2.core.commands.subcommands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class ItemMetadataDifferenceTest {

    @BeforeEach fun setup() { MockBukkit.mock() }
    @AfterEach fun teardown() { MockBukkit.unmock() }

    private fun uranium(): ItemStack = ItemStack(Material.PAPER, 1).apply {
        editMeta {
            it.displayName(Component.text("Small Chunk of Uranium", NamedTextColor.RED))
            it.lore(listOf(Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW)))
            it.persistentDataContainer.set(
                NamespacedKey("slimefun", "slimefun_item"),
                PersistentDataType.STRING,
                "SMALL_URANIUM"
            )
        }
    }

    @Test fun identicalStackIgnoresQuantityAndDoesNotMutateEitherItem() {
        val cheat = uranium()
        val quarry = cheat.clone().apply { amount = 17 }
        val beforeCheat = cheat.clone()
        val beforeQuarry = quarry.clone()
        val report = ItemMetadataDifference.inspect(cheat, quarry)
        assertTrue(report.isSimilar)
        assertEquals(64, report.mainMaxStackSize)
        assertEquals(64, report.offhandMaxStackSize)
        assertTrue(report.itemMetaSame)
        assertTrue(report.pdcSame)
        assertTrue(report.changedMetaFields.isEmpty())
        assertTrue(report.changedStackFields.isEmpty())
        assertEquals(beforeCheat, cheat)
        assertEquals(beforeQuarry, quarry)
    }

    @Test fun effectiveSingleItemStackLimitIsReportedWithoutRewritingItems() {
        val main = ItemStack(Material.NETHERITE_SWORD)
        val offhand = main.clone()
        val before = main.clone()
        val report = ItemMetadataDifference.inspect(main, offhand)

        assertTrue(report.isSimilar)
        assertEquals(1, report.mainMaxStackSize)
        assertEquals(1, report.offhandMaxStackSize)
        assertEquals(before, main)
        assertEquals(before, offhand)
    }

    @Test fun sameSlimefunIdDoesNotHideAdventureColorOrLoreMismatch() {
        val cheat = uranium()
        val quarry = uranium().apply {
            editMeta {
                it.displayName(Component.text("Small Chunk of Uranium", NamedTextColor.GOLD))
                it.lore(listOf(Component.text("Radiation Level: Moderate", NamedTextColor.RED)))
            }
        }
        val report = ItemMetadataDifference.inspect(cheat, quarry)
        assertFalse(report.isSimilar)
        assertFalse(report.nameSame)
        assertFalse(report.loreSame)
        assertFalse(report.itemMetaSame)
        assertTrue(report.pdcSame)
        assertTrue(report.changedMetaFields.isNotEmpty())
    }

    @Test fun differentOwnerKeysAreReportedWithoutExposingSecretValues() {
        val cheat = uranium()
        val quarry = uranium().apply {
            editMeta {
                it.persistentDataContainer.set(
                    NamespacedKey("addon", "owner"),
                    PersistentDataType.STRING,
                    "sensitive-value-should-not-be-printed"
                )
            }
        }
        val report = ItemMetadataDifference.inspect(cheat, quarry)
        assertFalse(report.isSimilar)
        assertFalse(report.pdcSame)
        assertTrue(report.onlyMainPdcKeys.isEmpty())
        assertEquals(listOf("addon:owner"), report.onlyOffhandPdcKeys)
        assertTrue(report.changedMetaFields.isNotEmpty())
    }


    @Test fun nestedHistoricalNameAndLoreDifferencesRemainVisibleAndReadOnly() {
        val cheat = uranium().apply {
            editMeta {
                it.displayName(Component.text("Small Chunk of Uranium", NamedTextColor.RED)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
                it.lore(listOf(Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)))
            }
        }
        val machine = cheat.clone().apply {
            editMeta {
                it.displayName(Component.text("").append(
                    Component.text("Small Chunk of Uranium", NamedTextColor.RED)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)))
                it.lore(listOf(Component.text("").append(
                    Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true))))
            }
        }
        val beforeCheat = cheat.clone()
        val beforeMachine = machine.clone()
        val report = ItemMetadataDifference.inspect(cheat, machine)
        assertFalse(report.isSimilar)
        assertFalse(report.nameSame)
        assertFalse(report.loreSame)
        assertTrue(report.pdcSame)
        assertTrue(report.onlyMainPdcKeys.isEmpty())
        assertTrue(report.onlyOffhandPdcKeys.isEmpty())
        assertTrue(report.changedMetaFields.isNotEmpty())
        assertEquals(beforeCheat, cheat)
        assertEquals(beforeMachine, machine)
    }

    @Test fun nestedHistoricalNameWithExplicitBoldMismatchIsDiagnosedWithoutRewriting() {
        // AlbionMC old machine output: nested Adventure name with bold=false.
        // Fresh /sf cheat output: flat name with bold=true. The same visible
        // name, Slimefun ID and PDC do not imply ItemStack.isSimilar.
        val cheat = uranium().apply {
            editMeta {
                it.displayName(
                    Component.text("Small Chunk of Uranium", NamedTextColor.RED)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                )
            }
        }
        val machine = cheat.clone().apply {
            editMeta {
                it.displayName(
                    Component.text("").append(
                        Component.text("Small Chunk of Uranium", NamedTextColor.RED)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                    )
                )
            }
        }
        val cheatBefore = cheat.clone()
        val machineBefore = machine.clone()
        val report = ItemMetadataDifference.inspect(cheat, machine)

        assertFalse(report.isSimilar)
        assertFalse(report.nameSame)
        assertTrue(report.loreSame)
        assertTrue(report.pdcSame)
        assertTrue(report.onlyMainPdcKeys.isEmpty())
        assertTrue(report.onlyOffhandPdcKeys.isEmpty())
        assertTrue(report.changedMetaFields.isNotEmpty())
        assertEquals(cheatBefore, cheat)
        assertEquals(machineBefore, machine)
    }

    @Test fun metadataReporterNeverNormalizesDifferentlyWrappedLore() {
        val cheat = uranium()
        val quarry = uranium().apply {
            editMeta {
                it.lore(listOf(Component.text("").append(
                    Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW))))
            }
        }
        val before = quarry.clone()
        val report = ItemMetadataDifference.inspect(cheat, quarry)
        assertFalse(report.loreSame)
        assertFalse(report.isSimilar)
        assertEquals(before, quarry)
    }
}
