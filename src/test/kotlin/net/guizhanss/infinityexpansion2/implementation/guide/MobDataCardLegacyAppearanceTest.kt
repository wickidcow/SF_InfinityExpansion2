@file:Suppress("deprecation")

package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class MobDataCardLegacyAppearanceTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun blankConfiguredNameAndCarpetUseTheCategoryDefaultWithoutMutatingTheSource() {
        val carpet = ItemStack(Material.BLUE_CARPET, 9)
        val original = carpet.clone()

        val resolved = MobDataCardAppearance.resolve(
            "trader_llama",
            "   ",
            carpet,
            MobDataCardGuideGroup.NEUTRAL,
        )

        assertEquals("${ChatColor.BLUE}Trader Llama", resolved.name)
        assertEquals(Material.COPPER_CHESTPLATE, resolved.texture.type)
        assertEquals(1, resolved.texture.amount)
        assertTrue(resolved.repairedBlankName)
        assertTrue(resolved.replacedLegacyCarpet)
        assertEquals(original, carpet)
    }

    @Test
    fun namedCarpetCustomizationRemainsAuthoritative() {
        val carpet = ItemStack(Material.RED_CARPET, 3)
        val resolved = MobDataCardAppearance.resolve(
            "vex",
            "&dCustom Vex",
            carpet,
            MobDataCardGuideGroup.HOSTILE,
        )

        assertEquals("&dCustom Vex", resolved.name)
        assertEquals(carpet, resolved.texture)
        assertNotSame(carpet, resolved.texture)
        assertFalse(resolved.repairedBlankName)
        assertFalse(resolved.replacedLegacyCarpet)
    }

    @Test
    fun blankNameDoesNotReplaceNonCarpetCustomArtwork() {
        val head = ItemStack(Material.PLAYER_HEAD)
        val resolved = MobDataCardAppearance.resolve(
            "allay",
            "&7",
            head,
            MobDataCardGuideGroup.PASSIVE,
        )

        assertEquals("${ChatColor.BLUE}Allay", resolved.name)
        assertEquals(head, resolved.texture)
        assertNotSame(head, resolved.texture)
        assertTrue(resolved.repairedBlankName)
        assertFalse(resolved.replacedLegacyCarpet)
    }

    @Test
    fun stalePhysicalCarpetIsRebuiltAndKeepsAmountAndUnknownPersistentData() {
        val source = physical(Material.BLUE_CARPET, "", "IE_MOB_DATA_CARD_OLD", "vex", 37)
        val sourceBefore = source.clone()
        val sourceMeta = source.itemMeta
        sourceMeta.persistentDataContainer.set(ADDON_COUNT, PersistentDataType.LONG, 9_000_000_001L)
        sourceMeta.persistentDataContainer.set(ADDON_BYTES, PersistentDataType.BYTE_ARRAY, byteArrayOf(-1, 0, 42, 127))
        source.itemMeta = sourceMeta

        val current = physical(Material.DIAMOND_CHESTPLATE, "Vex", "IE_MOB_DATA_CARD", "vex", 1)
        val repaired = requireNotNull(MobDataCardAppearance.refreshLegacyPhysical(source, current))

        assertEquals(Material.DIAMOND_CHESTPLATE, repaired.type)
        assertEquals(37, repaired.amount)
        assertEquals("Vex", visibleFirstLore(repaired))

        val data = repaired.itemMeta.persistentDataContainer
        assertEquals("IE_MOB_DATA_CARD", data.get(SLIMEFUN_ID, PersistentDataType.STRING))
        assertEquals("vex", data.get(MOB_ID, PersistentDataType.STRING))
        assertEquals(9_000_000_001L, data.get(ADDON_COUNT, PersistentDataType.LONG))
        assertArrayEquals(byteArrayOf(-1, 0, 42, 127), data.get(ADDON_BYTES, PersistentDataType.BYTE_ARRAY))

        assertEquals(sourceBefore.type, source.type)
        assertEquals(sourceBefore.amount, source.amount)
        assertEquals("IE_MOB_DATA_CARD_OLD", source.itemMeta.persistentDataContainer.get(SLIMEFUN_ID, PersistentDataType.STRING))
    }

    @Test
    fun namedPhysicalCarpetIsNotRewritten() {
        val source = physical(Material.RED_CARPET, "Custom Vex Artwork", "IE_MOB_DATA_CARD", "vex", 8)
        val current = physical(Material.DIAMOND_CHESTPLATE, "Vex", "IE_MOB_DATA_CARD", "vex", 1)

        assertNull(MobDataCardAppearance.refreshLegacyPhysical(source, current))
    }

    @Test
    fun currentConfiguredCarpetIsNotRewrittenEvenWhenOldLabelIsBlank() {
        val source = physical(Material.RED_CARPET, "", "IE_MOB_DATA_CARD", "vex", 8)
        val current = physical(Material.RED_CARPET, "Vex", "IE_MOB_DATA_CARD", "vex", 1)

        assertNull(MobDataCardAppearance.refreshLegacyPhysical(source, current))
    }

    private fun physical(material: Material, mobLabel: String, slimefunId: String, mobId: String, amount: Int) =
        ItemStack(material, amount).apply {
            val meta = itemMeta
            meta.setDisplayName("${ChatColor.AQUA}Mob Data Card")
            meta.lore = listOf("${ChatColor.GRAY}$mobLabel")
            meta.persistentDataContainer.set(SLIMEFUN_ID, PersistentDataType.STRING, slimefunId)
            meta.persistentDataContainer.set(MOB_ID, PersistentDataType.STRING, mobId)
            itemMeta = meta
        }

    private fun visibleFirstLore(item: ItemStack): String =
        ChatColor.stripColor(item.itemMeta.lore?.firstOrNull()).orEmpty().trim()

    companion object {
        private val SLIMEFUN_ID = NamespacedKey("slimefun", "slimefun_item")
        private val MOB_ID = NamespacedKey("infinityexpansion2", "mob_data_id")
        private val ADDON_COUNT = NamespacedKey("oldaddon", "count")
        private val ADDON_BYTES = NamespacedKey("oldaddon", "bytes")
    }
}
