package net.guizhanss.infinityexpansion2.implementation.setup

import net.guizhanss.infinityexpansion2.api.mobsim.MobDataCardProps
import net.guizhanss.infinityexpansion2.core.IERegistry
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Tests shipped card definitions against the operator's independent SmartSpawner loot reference. */
class SmartSpawnerCardsTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
        IERegistry.itemMapping.clear()
    }

    @AfterEach
    fun tearDown() {
        IERegistry.itemMapping.clear()
        MockBukkit.unmock()
    }

    @Test
    fun everyNewMobHasAnEnabledCardAndAnUnambiguousSurvivalRecipe() {
        val reference = config("mobsim/smartspawner-new-cards-reference.yml")
        val modern = config("mob-simulation-modern.yml")
        val historical = config("mob-simulation.yml")
        assertEquals(44, reference.getKeys(false).size)

        val allCards = historical.getKeys(false).associateWith { historical.getConfigurationSection(it)!! } +
            modern.getKeys(false).associateWith { modern.getConfigurationSection(it)!! }
        reference.getKeys(false).forEach { entity ->
            val id = entity.lowercase(Locale.ROOT)
            val card = requireNotNull(modern.getConfigurationSection(id)) { "Missing card for $entity" }
            assertFalse(historical.contains(id), "$id must not replace a historical card")
            assertTrue(card.getBoolean("enabled"), id)
            assertEquals("independent", card.getString("drop-mode"), id)
            assertEquals(reference.getInt("$entity.experience"), card.getInt("experience"), id)
            assertTrue(card.getInt("energy") > 0, id)
            assertTrue(card.getString("texture") in setOf("IRON_CHESTPLATE", "DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE"), id)

            val pattern = card.getStringList("recipe.pattern")
            assertEquals(3, pattern.size, id)
            assertTrue(pattern.all { it.length == 3 }, id)
            assertEquals(1, pattern.sumOf { row -> row.count { it == 'X' } }, id)
            assertEquals('X', pattern[1][1], id)
            val ingredients = requireNotNull(card.getConfigurationSection("recipe.ingredient"))
            ingredients.getKeys(false).forEach { symbol ->
                assertEquals(1, symbol.length, id)
                val material = requireNotNull(Material.getMaterial(ingredients.getString("$symbol.item")!!))
                assertTrue(material.isItem && !material.isAir, "$id/$symbol")
                assertFalse(material.name.endsWith("_SPAWN_EGG"), "$id/$symbol")
                assertTrue(ingredients.getInt("$symbol.amount") in 1..material.maxStackSize, "$id/$symbol")
            }
            // The infuser accepts surplus ingredient quantities, so compare item identities
            // without amounts. Recipes distinguished only by their amounts would be ambiguous.
            val signature = recipeMaterials(card)
            allCards.filterKeys { it != id }.forEach { (otherId, otherCard) ->
                assertNotEquals(recipeMaterials(otherCard), signature, "$id conflicts with $otherId")
            }
        }
    }

    @Test
    fun convertedDropsPreserveEveryPerMobQuantityProbabilityAndEquipmentDamage() {
        val reference = config("mobsim/smartspawner-new-cards-reference.yml")
        val modern = config("mob-simulation-modern.yml")
        reference.getKeys(false).forEach { entity ->
            val id = entity.lowercase(Locale.ROOT)
            val source = requireNotNull(reference.getConfigurationSection("$entity.loot"))
            val actual = modern.getMapList("$id.drops").associateBy { it["item"] as String }
            assertEquals(source.getKeys(false), actual.keys, id)
            assertEquals(actual.size, modern.getMapList("$id.drops").size, "Duplicate drops for $id")
            source.getKeys(false).forEach { material ->
                val drop = requireNotNull(parse(actual.getValue(material))) { "$id/$material did not parse" }
                assertEquals(material, drop.item.type.name, id)
                assertTrue(drop.chance.isFinite() && drop.chance in 0.0..1.0, "$id/$material")
                val originalRange = range(source.getString("$material.amount")!!)
                val originalChance = source.getDouble("$material.chance") / 100.0
                val originalCount = originalRange.last - originalRange.first + 1
                val positiveCount = drop.amountRange.last - drop.amountRange.first + 1
                assertEquals(maxOf(1, originalRange.first), drop.amountRange.first, "$id/$material")
                assertEquals(originalRange.last, drop.amountRange.last, "$id/$material")
                for (quantity in 0..originalRange.last) {
                    val expected = if (quantity == 0) {
                        1.0 - originalChance + if (0 in originalRange) originalChance / originalCount else 0.0
                    } else if (quantity in originalRange) originalChance / originalCount else 0.0
                    val observed = if (quantity == 0) 1.0 - drop.chance
                        else if (quantity in drop.amountRange) drop.chance / positiveCount else 0.0
                    assertEquals(expected, observed, 1.0e-12, "$id/$material quantity=$quantity")
                }
                val sourceDamage = source.getString("$material.durability")?.let(::range)
                assertEquals(sourceDamage, drop.damageRange, "$id/$material damage")
            }
        }
    }

    @Test
    fun damageRollsUseFreshClonesAndKeepAmountRangesAndCustomMetadata() {
        val drop = requireNotNull(parse(mapOf("item" to "IRON_AXE", "amount" to "1-3", "damage" to "1-250")))
        val key = NamespacedKey("test", "owner")
        val meta = drop.item.itemMeta
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "retained")
        drop.item.itemMeta = meta
        val original = drop.item.clone()
        val props = props(drop.item)
        props.configureDropAmountRanges(listOf(drop.amountRange))
        props.configureDropDamageRanges(listOf(drop.damageRange))

        repeat(128) {
            val result = props.getDrop(0)
            assertTrue(result.amount in 1..3)
            assertTrue((result.itemMeta as Damageable).damage in 1..250)
            assertEquals("retained", result.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING))
            assertNotSame(drop.item, result)
            assertEquals(original, drop.item)
        }
        assertEquals(1..250, props.getDropDamageRange(0))
    }

    @Test
    fun fixedAndZeroDamageAreAppliedExactlyWhileApiCardsRetainExistingDamage() {
        val item = ItemStack(Material.IRON_AXE)
        val meta = item.itemMeta as Damageable
        meta.damage = 91
        item.itemMeta = meta
        val props = props(item)
        assertEquals(91, (props.getDrop(0).itemMeta as Damageable).damage)
        props.configureDropDamageRanges(listOf(17..17))
        assertEquals(17, (props.getDrop(0).itemMeta as Damageable).damage)
        props.configureDropDamageRanges(listOf(0..0))
        assertEquals(0, (props.getDrop(0).itemMeta as Damageable).damage)
        assertEquals(91, (item.itemMeta as Damageable).damage)
        assertEquals(0..0, parse(mapOf("item" to "IRON_AXE", "damage" to 0))!!.damageRange)
    }

    @Test
    fun invalidDamageDefinitionsFailClosed() {
        val invalid = listOf(-1, 1.5, "-1-2", "4-2", "0-251", "2147483648", "0-2147483648", "broken", null)
        invalid.forEach { damage ->
            assertNull(parse(mapOf("item" to "IRON_AXE", "damage" to damage)), "$damage")
        }
        assertNull(parse(mapOf("item" to "COD", "damage" to 1)))
        assertThrows(IllegalArgumentException::class.java) { props(ItemStack(Material.COD)).configureDropDamageRanges(listOf(1..2)) }
        assertThrows(IllegalArgumentException::class.java) { props(ItemStack(Material.IRON_AXE)).configureDropDamageRanges(listOf(0..251)) }
        assertThrows(IllegalArgumentException::class.java) { props(ItemStack(Material.IRON_AXE)).configureDropDamageRanges(emptyList()) }
    }

    @Test
    fun damageLimitsHonorAnExplicitMaximumOnCustomItems() {
        val item = ItemStack(Material.IRON_AXE)
        val meta = item.itemMeta as Damageable
        meta.setMaxDamage(100)
        item.itemMeta = meta
        val original = item.clone()
        IERegistry.itemMapping["CUSTOM_AXE"] = item
        val parsed = requireNotNull(parse(mapOf("item" to "CUSTOM_AXE", "damage" to "50-100")))
        val props = props(parsed.item)
        props.configureDropDamageRanges(listOf(100..100))
        val result = props.getDrop(0).itemMeta as Damageable
        assertEquals(100, result.damage)
        assertEquals(100, result.maxDamage)
        assertEquals(original, item)
        assertNull(parse(mapOf("item" to "CUSTOM_AXE", "damage" to "0-101")))
        assertThrows(IllegalArgumentException::class.java) { props.configureDropDamageRanges(listOf(101..101)) }
    }

    @Test
    fun raisedCustomMaximumIsAcceptedByTheParserAndRangeValidation() {
        val item = ItemStack(Material.IRON_AXE)
        val meta = item.itemMeta as Damageable
        meta.setMaxDamage(400)
        item.itemMeta = meta
        IERegistry.itemMapping["CUSTOM_AXE"] = item
        val parsed = requireNotNull(parse(mapOf("item" to "CUSTOM_AXE", "damage" to "250-400")))
        assertEquals(250..400, parsed.damageRange)
        assertEquals(400, (parsed.item.itemMeta as Damageable).maxDamage)
        val props = props(parsed.item)
        props.configureDropDamageRanges(listOf(400..400))
        assertEquals(400..400, props.getDropDamageRange(0))
        assertNull(parse(mapOf("item" to "CUSTOM_AXE", "damage" to "0-401")))
        assertThrows(IllegalArgumentException::class.java) { props.configureDropDamageRanges(listOf(401..401)) }
        // MockBukkit 4.110.0 ItemStackMock.setItemMeta routes through setDurability,
        // which incorrectly clamps against Material.maxDurability instead of the custom
        // maximum. Exercise real output above with a lower custom limit, and exercise
        // raised limits here before that mock-only clamp. Paper applies both components.
        // https://github.com/MockBukkit/MockBukkit/blob/v4.110.0/src/main/java/org/mockbukkit/mockbukkit/inventory/ItemStackMock.java
    }

    private fun parse(values: Map<*, *>): MobSimulationSetup.ConfiguredDrop? {
        val name = values["item"] as? String
        // Seed the production item resolver's cache with real Bukkit materials. This test
        // exercises its drop parser without booting an entire Slimefun server/registry.
        if (name != null && name !in IERegistry.itemMapping) {
            IERegistry.itemMapping[name] = ItemStack(requireNotNull(Material.getMaterial(name)))
        }
        return with(MobSimulationSetup) { values.getAsConfiguredDrop() }
    }

    private fun props(item: ItemStack) = MobDataCardProps(
        "test", "Test", ItemStack(Material.IRON_CHESTPLATE), 75, 1,
        listOf(item to 1.0), arrayOfNulls(9),
    )

    private fun config(name: String): YamlConfiguration = YamlConfiguration.loadConfiguration(
        InputStreamReader(requireNotNull(javaClass.classLoader.getResourceAsStream(name)), StandardCharsets.UTF_8)
    )

    private fun range(value: String): IntRange {
        val values = value.split('-').map(String::toInt)
        return values.first()..values.last()
    }

    private fun recipeMaterials(section: ConfigurationSection): List<String> =
        section.getStringList("recipe.pattern").joinToString("").map { symbol ->
            when (symbol) {
                'X' -> "IE_MOB_DATA_CARD_EMPTY"
                ' ' -> "AIR"
                else -> requireNotNull(section.getString("recipe.ingredient.$symbol.item"))
            }
        }
}
