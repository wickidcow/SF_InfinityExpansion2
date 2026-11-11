package net.guizhanss.infinityexpansion2.core.commands.subcommands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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
    @Test fun nestedBoldMismatchShowsRedactedComponentShapeButNeverItemText() {
        val cheat = uranium().apply {
            editMeta {
                it.displayName(Component.text("Private Uranium Title", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, TextDecoration.State.TRUE))
            }
        }
        val machine = cheat.clone().apply {
            editMeta {
                it.displayName(Component.text("").append(
                    Component.text("Private Uranium Title", NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)))
            }
        }
        val beforeCheat = cheat.clone()
        val beforeMachine = machine.clone()
        val report = ItemMetadataDifference.inspect(cheat, machine)

        assertFalse(report.isSimilar)
        assertFalse(report.nameSame)
        assertTrue(report.pdcSame)
        assertTrue(report.componentStructureDifferences.any { it.contains("child count 0/1") })
        assertTrue(report.componentStructureDifferences.any { it.contains("bold") && it.contains("TRUE") })
        assertTrue(report.componentStructureDifferences.any { it.contains("only in offhand") && it.contains("FALSE") })
        assertTrue(report.componentStructureDifferences.none { it.contains("Private Uranium Title") })
        assertTrue(report.componentStructureDifferences.none { it.contains("Radiation Level") })
        assertEquals(beforeCheat, cheat)
        assertEquals(beforeMachine, machine)
    }

    @Test fun hoveredLoreDifferencesAreReportedWithoutExposingHoverPayload() {
        val cheat = uranium()
        val machine = cheat.clone().apply {
            editMeta {
                it.lore(listOf(Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("private-owner-payload-do-not-print")))))
            }
        }
        val report = ItemMetadataDifference.inspect(cheat, machine)

        assertFalse(report.loreSame)
        assertTrue(report.componentStructureDifferences.any { it.contains("hover differs (redacted)") })
        assertTrue(report.componentStructureDifferences.none { it.contains("private-owner-payload-do-not-print") })
        assertTrue(report.pdcSame)
    }

}
