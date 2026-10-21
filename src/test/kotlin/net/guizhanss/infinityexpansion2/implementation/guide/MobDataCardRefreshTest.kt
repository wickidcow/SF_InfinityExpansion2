package net.guizhanss.infinityexpansion2.implementation.guide

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.inventory.ItemStackMock
import org.mockbukkit.mockbukkit.inventory.meta.ArmorMetaMock
import org.mockbukkit.mockbukkit.inventory.meta.components.CustomModelDataComponentMock

/** Regression cases for updating existing cards without reconstructing their identity or custom state. */
class MobDataCardRefreshTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun everyPlainChestplateUsesItsGuideCategoryMaterial() {
        val oldMaterials = listOf(
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE, Material.IRON_CHESTPLATE,
            Material.COPPER_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        )
        for (oldMaterial in oldMaterials) {
            for (group in MobDataCardGuideGroup.entries) {
                val source = ItemStack(oldMaterial)
                val before = source.clone()
                val result = MobDataCardAppearance.texture(source, group)

                assertEquals(group.material, result.type, "$oldMaterial in ${group.name}")
                assertNotSame(source, result)
                assertEquals(before, source)
            }
        }
    }

    @Test
    fun categoryAndFormattingRefreshRetainsExactVariantIdentityCountAndPersistentTypes() {
        val source = physical(Material.IRON_CHESTPLATE, "vex", "IE_MOB_DATA_CARD_VEX", 37)
        source.editMeta { meta ->
            meta.setMaxStackSize(64)
            meta.persistentDataContainer.set(OWNER, PersistentDataType.LONG, 9_000_000_001L)
            meta.persistentDataContainer.set(BYTES, PersistentDataType.BYTE_ARRAY, byteArrayOf(-1, 0, 42, 127))
        }
        val before = source.clone()
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")
        current.editMeta { meta ->
            meta.displayName(Component.text("Mob Data Card", NamedTextColor.LIGHT_PURPLE))
            meta.lore(listOf(Component.text("Vex", NamedTextColor.YELLOW)))
            meta.persistentDataContainer.set(OWNER, PersistentDataType.STRING, "template owner must not replace source")
            meta.persistentDataContainer.set(TEMPLATE_ONLY, PersistentDataType.INTEGER, 99)
        }

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.DIAMOND_CHESTPLATE, updated.type)
        assertEquals(37, updated.amount)
        assertEquals(64, updated.maxStackSize)
        assertEquals(current.itemMeta.displayName(), updated.itemMeta.displayName())
        assertEquals(current.itemMeta.lore(), updated.itemMeta.lore())
        val data = updated.itemMeta.persistentDataContainer
        assertEquals(before.itemMeta.persistentDataContainer, data)
        assertEquals("IE_MOB_DATA_CARD_VEX", data.get(SLIMEFUN_ID, PersistentDataType.STRING))
        assertEquals("vex", data.get(MOB_ID, PersistentDataType.STRING))
        assertEquals(9_000_000_001L, data.get(OWNER, PersistentDataType.LONG))
        assertFalse(data.has(OWNER, PersistentDataType.STRING))
        assertArrayEquals(byteArrayOf(-1, 0, 42, 127), data.get(BYTES, PersistentDataType.BYTE_ARRAY))
        assertFalse(data.has(TEMPLATE_ONLY))
        assertEquals(before, source)
    }

    @Test
    fun customNameRichAdditionalLoreDamageEnchantmentsAndStackLimitSurviveMaterialChange() {
        val customName = Component.text("Jason's card", NamedTextColor.GREEN).insertion("owner customization")
        val extraLore = listOf(
            Component.translatable("entity.minecraft.vex"),
            Component.text("Keep the original description", NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Additional rich text"))),
        )
        val source = physical(Material.IRON_CHESTPLATE, "vex", amount = 17)
        source.editMeta { meta ->
            meta.displayName(customName)
            meta.lore(listOf(Component.text("Vex", NamedTextColor.GRAY)) + extraLore)
            meta.setMaxStackSize(32)
            meta.isUnbreakable = true
            meta.addEnchant(Enchantment.UNBREAKING, 4, true)
            (meta as Damageable).damage = 23
        }
        val before = source.clone()
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")
        current.editMeta { it.lore(listOf(Component.text("Vex", NamedTextColor.RED))) }

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.DIAMOND_CHESTPLATE, updated.type)
        assertEquals(17, updated.amount)
        assertEquals(32, updated.maxStackSize)
        assertEquals(customName, updated.itemMeta.displayName())
        assertEquals(current.itemMeta.lore()!!.first(), updated.itemMeta.lore()!!.first())
        assertEquals(extraLore, updated.itemMeta.lore()!!.drop(1))
        assertEquals(23, (updated.itemMeta as Damageable).damage)
        assertTrue(updated.itemMeta.isUnbreakable)
        assertEquals(4, updated.itemMeta.getEnchantLevel(Enchantment.UNBREAKING))
        assertEquals(before.itemMeta.persistentDataContainer, updated.itemMeta.persistentDataContainer)
        assertEquals(before, source)
    }

    @Test
    fun blankLabelIsRepairedEvenWhenTheMaterialAlreadyMatches() {
        val source = physical(Material.DIAMOND_CHESTPLATE, "vex")
        val extra = Component.text("A preserved note", NamedTextColor.GREEN)
        source.editMeta { it.lore(listOf(Component.text("   ", NamedTextColor.GRAY), extra)) }
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(listOf(current.itemMeta.lore()!!.first(), extra), updated.itemMeta.lore())
        assertEquals(source.itemMeta.persistentDataContainer, updated.itemMeta.persistentDataContainer)
    }

    @Test
    fun intentionalCustomFirstLabelSurvivesTheCategoryChange() {
        val customLabel = Component.text("Albion's special Vex", NamedTextColor.GOLD)
            .hoverEvent(HoverEvent.showText(Component.text("Player-added label")))
        val source = physical(Material.IRON_CHESTPLATE, "vex")
        source.editMeta { it.lore(listOf(customLabel)) }
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.DIAMOND_CHESTPLATE, updated.type)
        assertEquals(listOf(customLabel), updated.itemMeta.lore())
    }

    @Test
    fun fixedItemNameIsNotHiddenByAnAddedGenericDisplayName() {
        val fixedName = Component.text("Owner's fixed card name", NamedTextColor.GREEN)
        val source = physical(Material.IRON_CHESTPLATE, "vex")
        source.editMeta {
            it.displayName(null)
            it.itemName(fixedName)
        }
        assertEquals(fixedName, source.itemMeta.itemName())
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.DIAMOND_CHESTPLATE, updated.type)
        assertEquals(fixedName, updated.itemMeta.itemName())
        assertNull(updated.itemMeta.displayName())
    }

    @Test
    fun interactiveNamesAndLabelsSurviveEvenWhenTheirVisibleTextIsStandard() {
        val source = physical(Material.IRON_CHESTPLATE, "vex")
        val name = Component.text("Mob Data Card").hoverEvent(HoverEvent.showText(Component.text("Owner note")))
        val label = Component.text("Vex").append(Component.empty().insertion("preserve nested interaction"))
        source.editMeta {
            it.displayName(name)
            it.lore(listOf(label))
        }
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.DIAMOND_CHESTPLATE, updated.type)
        assertEquals(name, updated.itemMeta.displayName())
        assertEquals(listOf(label), updated.itemMeta.lore())
    }

    @Test
    fun refreshedCardIsIdempotentAndMatchingCardIsANoop() {
        val current = physical(Material.COPPER_CHESTPLATE, "enderman")
        val source = physical(Material.IRON_CHESTPLATE, "enderman", amount = 12)
        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "enderman"))

        assertNull(MobDataCardAppearance.refreshPhysical(updated, current, "enderman"))
        assertNull(MobDataCardAppearance.refreshPhysical(current, current, "enderman"))
    }

    @Test
    fun modernNonNumericModelComponentsAndItemModelsKeepTheirArtwork() {
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")
        val modelSetters: List<(ItemStack) -> Unit> = listOf(
            { item -> item.editMeta { meta ->
                val model = meta.customModelDataComponent
                model.strings = listOf("custom-vex")
                meta.setCustomModelDataComponent(model)
            } },
            { item -> item.editMeta { meta ->
                val model = meta.customModelDataComponent
                model.flags = listOf(true)
                meta.setCustomModelDataComponent(model)
            } },
            { item -> item.editMeta { meta ->
                val model = meta.customModelDataComponent
                model.colors = listOf(Color.FUCHSIA)
                meta.setCustomModelDataComponent(model)
            } },
            { item -> item.editMeta { it.itemModel = NamespacedKey("albion", "special_vex") } },
        )
        for (setModel in modelSetters) {
            val source = componentAware(physical(Material.IRON_CHESTPLATE, "vex"))
            setModel(source)
            assertTrue(source.itemMeta.hasItemModel() || source.itemMeta.hasCustomModelDataComponent())
            val before = source.clone()

            assertNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))
            assertEquals(before.type, source.type)
            assertEquals(before.amount, source.amount)
            assertEquals(before.itemMeta, source.itemMeta)
            assertEquals(before.itemMeta.itemModel, source.itemMeta.itemModel)
            assertEquals(before.itemMeta.customModelDataComponent, source.itemMeta.customModelDataComponent)
        }
    }

    @Test
    fun modeledCardsCanRepairBlankLabelsWithoutChangingTheirArtwork() {
        val source = componentAware(physical(Material.IRON_CHESTPLATE, "vex"))
        source.editMeta { meta ->
            meta.itemModel = NamespacedKey("albion", "special_vex")
            meta.lore(listOf(Component.empty()))
        }
        assertEquals(NamespacedKey("albion", "special_vex"), source.itemMeta.itemModel)
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.IRON_CHESTPLATE, updated.type)
        assertEquals(source.itemMeta.itemModel, updated.itemMeta.itemModel)
        assertEquals(current.itemMeta.lore(), updated.itemMeta.lore())
    }

    @Test
    fun dyedLeatherNamedCarpetAndHeadsKeepTheirIntentionalArtwork() {
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")
        val sources = listOf(
            physical(Material.LEATHER_CHESTPLATE, "vex").apply {
                editMeta { (it as LeatherArmorMeta).setColor(Color.FUCHSIA) }
            },
            physical(Material.RED_CARPET, "vex"),
            physical(Material.PLAYER_HEAD, "vex"),
            physical(Material.VEX_SPAWN_EGG, "vex"),
        )
        for (source in sources) {
            val before = source.clone()
            assertNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"), source.type.name)
            assertEquals(before, source)
        }
    }

    @Test
    fun blankLegacyCarpetKeepsItsEffectiveCapacityAndSourceIdentity() {
        val source = physical(Material.BLUE_CARPET, "vex", "IE_MOB_DATA_CARD_VEX", 37)
        val note = Component.text("Keep this note").insertion("rich note")
        source.editMeta { it.lore(listOf(Component.empty(), note)) }
        val current = physical(Material.DIAMOND_CHESTPLATE, "vex")
        val before = source.clone()

        val updated = requireNotNull(MobDataCardAppearance.refreshPhysical(source, current, "vex"))

        assertEquals(Material.DIAMOND_CHESTPLATE, updated.type)
        assertEquals(37, updated.amount)
        assertEquals(64, updated.maxStackSize)
        assertEquals(before.itemMeta.persistentDataContainer, updated.itemMeta.persistentDataContainer)
        assertEquals(listOf(current.itemMeta.lore()!!.first(), note), updated.itemMeta.lore())
        assertEquals(before, source)
        assertNull(MobDataCardAppearance.refreshPhysical(updated, current, "vex"))
    }

    private fun physical(
        material: Material,
        mobId: String,
        slimefunId: String = "IE_MOB_DATA_CARD",
        amount: Int = 1,
    ): ItemStack = ItemStack(material, amount).apply {
        editMeta { meta ->
            meta.displayName(Component.text("Mob Data Card", NamedTextColor.AQUA))
            val label = mobId.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            meta.lore(listOf(Component.text(label, NamedTextColor.GRAY)))
            meta.persistentDataContainer.set(SLIMEFUN_ID, PersistentDataType.STRING, slimefunId)
            meta.persistentDataContainer.set(MOB_ID, PersistentDataType.STRING, mobId)
        }
    }

    /**
     * MockBukkit 4.110's ItemMetaMock copy constructor omits item_model and the modern
     * custom_model_data component. Its normal set/get cycle therefore deletes this fixture's
     * artwork before production code can inspect it. Supply only the missing copy behavior
     * for these component-focused cases; material/count/PDC/lore remain MockBukkit objects.
     */
    private fun componentAware(source: ItemStack): ItemStack = ComponentAwareStack(source.type, source.amount).apply {
        itemMeta = source.itemMeta
    }

    private class ComponentAwareStack(material: Material, amount: Int) : ItemStackMock(material, amount) {
        private var savedMeta: ItemMeta = ComponentAwareArmorMeta()

        override fun getItemMeta(): ItemMeta = ComponentAwareArmorMeta(savedMeta)

        override fun setItemMeta(meta: ItemMeta?): Boolean {
            savedMeta = if (meta == null) ComponentAwareArmorMeta() else ComponentAwareArmorMeta(meta)
            return true
        }

        override fun clone(): ItemStack {
            val copy = ComponentAwareStack(type, amount)
            copy.itemMeta = savedMeta
            return copy
        }

        override fun getMaxStackSize(): Int =
            if (savedMeta.hasMaxStackSize()) savedMeta.maxStackSize else type.maxStackSize
    }

    private class ComponentAwareArmorMeta : ArmorMetaMock {
        constructor() : super()

        constructor(source: ItemMeta) : super(source) {
            if (source.hasItemModel()) itemModel = source.itemModel
            if (source.hasCustomModelDataComponent()) {
                val previous = source.customModelDataComponent
                val copy = CustomModelDataComponentMock.useDefault()
                copy.floats = previous.floats.toList()
                copy.flags = previous.flags.toList()
                copy.strings = previous.strings.toList()
                copy.colors = previous.colors.toList()
                setCustomModelDataComponent(copy)
            }
        }

        override fun clone(): ArmorMetaMock = ComponentAwareArmorMeta(this)
    }

    companion object {
        private val SLIMEFUN_ID = NamespacedKey("slimefun", "slimefun_item")
        private val MOB_ID = NamespacedKey("infinityexpansion2", "mob_data_id")
        private val OWNER = NamespacedKey("otheraddon", "owner")
        private val BYTES = NamespacedKey("otheraddon", "payload")
        private val TEMPLATE_ONLY = NamespacedKey("otheraddon", "template_only")
    }
}
