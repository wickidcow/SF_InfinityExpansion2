package net.guizhanss.infinityexpansion2.implementation.guide

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

/** Category defaults apply only when no texture was configured; every explicit texture is preserved. */
class MobDataCardAppearanceTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun missingTexturesReceiveFreshCategoryDefaultsWithoutSharedState() {
        val expected = linkedMapOf(
            MobDataCardGuideGroup.PASSIVE to Material.IRON_CHESTPLATE,
            MobDataCardGuideGroup.NEUTRAL to Material.COPPER_CHESTPLATE,
            MobDataCardGuideGroup.HOSTILE to Material.DIAMOND_CHESTPLATE,
            MobDataCardGuideGroup.BOSS to Material.NETHERITE_CHESTPLATE,
        )
        expected.forEach { (group, material) ->
            val first = MobDataCardAppearance.texture(null, group)
            assertEquals(ItemStack(material), first, group.name)
            assertEquals(1, first.amount)

            first.amount = 17
            val meta = first.itemMeta
            meta.displayName(Component.text("Caller customization"))
            meta.persistentDataContainer.set(MOB_ID, PersistentDataType.STRING, "caller_mob")
            first.itemMeta = meta

            val second = MobDataCardAppearance.texture(null, group)
            assertNotSame(first, second)
            assertEquals(ItemStack(material), second, "$group must create an independent default")
            assertTrue(second.itemMeta.persistentDataContainer.keys.isEmpty())
        }
    }

    @Test
    fun everyExplicitChestplateKeepsItsMaterialRichMetadataDamageAndAmountForEveryCategory() {
        val materials = listOf(
            Material.IRON_CHESTPLATE,
            Material.COPPER_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE,
            Material.NETHERITE_CHESTPLATE,
            Material.LEATHER_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE,
            Material.CHAINMAIL_CHESTPLATE,
        )
        materials.forEach { material ->
            val source = textured(material)
            val damage = source.itemMeta as Damageable
            damage.damage = 23
            source.itemMeta = damage
            val original = source.clone()
            MobDataCardGuideGroup.entries.forEach { group ->
                val result = MobDataCardAppearance.texture(source, group)
                assertNotSame(source, result)
                assertEquals(original, result, "$material/$group")
                assertEquals(material, result.type)
                assertEquals(17, result.amount)
                assertEquals(original.itemMeta, result.itemMeta, "$material/$group metadata")
                assertEquals(23, (result.itemMeta as Damageable).damage)
                assertPersistentData(result)
                assertEquals(original, source)
            }
        }
    }

    @Test
    fun explicitNonChestplateTexturesKeepTheirArtworkAndMetadataForEveryCategory() {
        val materials = listOf(Material.PLAYER_HEAD, Material.PAPER, Material.COW_SPAWN_EGG)
        materials.forEach { material ->
            val source = textured(material)
            val original = source.clone()
            MobDataCardGuideGroup.entries.forEach { group ->
                val result = MobDataCardAppearance.texture(source, group)
                assertNotSame(source, result)
                assertEquals(original, result, "$material/$group custom texture")
                assertPersistentData(result)
                assertEquals(original, source)
            }
        }
    }

    @Test
    fun laterOutputChangesCannotAlterTheSourceOrAnotherCategoryTemplate() {
        val source = textured(Material.IRON_CHESTPLATE)
        val original = source.clone()
        val neutral = MobDataCardAppearance.texture(source, MobDataCardGuideGroup.NEUTRAL)
        val boss = MobDataCardAppearance.texture(source, MobDataCardGuideGroup.BOSS)
        val originalBoss = boss.clone()

        neutral.amount = 1
        val meta = neutral.itemMeta
        meta.displayName(Component.text("Changed by a later caller"))
        meta.persistentDataContainer.set(MOB_ID, PersistentDataType.STRING, "different_mob")
        meta.persistentDataContainer.remove(ADDON_COUNT)
        meta.lore(emptyList())
        neutral.itemMeta = meta

        assertEquals(original, source)
        assertEquals(originalBoss, boss)
        assertPersistentData(source)
        assertPersistentData(boss)
    }

    private fun textured(material: Material): ItemStack = ItemStack(material, 17).apply {
        val meta = itemMeta
        meta.displayName(Component.text("Custom card name", NamedTextColor.AQUA).insertion("preserved name"))
        meta.lore(listOf(
            Component.translatable("entity.minecraft.enderman"),
            Component.text("Original addon description", NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Preserve rich lore"))),
        ))
        meta.isUnbreakable = true
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        val model = meta.customModelDataComponent
        model.floats = listOf(12345.25F)
        model.strings = listOf("custom-card-model")
        meta.setCustomModelDataComponent(model)
        meta.persistentDataContainer.set(SLIMEFUN_ID, PersistentDataType.STRING, "IE_MOB_DATA_CARD")
        meta.persistentDataContainer.set(MOB_ID, PersistentDataType.STRING, "enderman")
        meta.persistentDataContainer.set(ADDON_COUNT, PersistentDataType.LONG, 9_000_000_001L)
        meta.persistentDataContainer.set(ADDON_BYTES, PersistentDataType.BYTE_ARRAY, byteArrayOf(-1, 0, 42, 127))
        itemMeta = meta
    }

    private fun assertPersistentData(item: ItemStack) {
        val data = item.itemMeta.persistentDataContainer
        assertEquals(setOf(SLIMEFUN_ID, MOB_ID, ADDON_COUNT, ADDON_BYTES), data.keys)
        assertEquals("IE_MOB_DATA_CARD", data.get(SLIMEFUN_ID, PersistentDataType.STRING))
        assertEquals("enderman", data.get(MOB_ID, PersistentDataType.STRING))
        assertEquals(9_000_000_001L, data.get(ADDON_COUNT, PersistentDataType.LONG))
        assertFalse(data.has(ADDON_COUNT, PersistentDataType.INTEGER))
        assertArrayEquals(byteArrayOf(-1, 0, 42, 127), data.get(ADDON_BYTES, PersistentDataType.BYTE_ARRAY))
    }

    companion object {
        private val SLIMEFUN_ID = NamespacedKey("slimefun", "slimefun_item")
        private val MOB_ID = NamespacedKey("infinityexpansion2", "mob_data_id")
        private val ADDON_COUNT = NamespacedKey("oldaddon", "count")
        private val ADDON_BYTES = NamespacedKey("oldaddon", "bytes")
    }
}
