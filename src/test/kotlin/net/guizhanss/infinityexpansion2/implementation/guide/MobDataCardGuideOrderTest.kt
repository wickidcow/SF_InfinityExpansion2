package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

class MobDataCardGuideOrderTest {
    @Test
    fun sortsAllCardsAlphabeticallyAcrossMobTypes() {
        val chamber = Entry.utility("chamber")
        val infuser = Entry.utility("infuser")
        val empty = Entry.utility("empty")
        val cards = mutableListOf(
            chamber, infuser, empty,
            Entry.card("zombie", "&cZombie"),
            Entry.card("wolf", "§aWolf"),
            Entry.card("sheep", "Sheep"),
            Entry.card("allay", "&bALLAY"),
            Entry.card("creeper", "Creeper"),
            Entry.card("cow", "cow"),
        )
        val order = sorter()

        assertSame(cards, order.order(cards))
        assertEquals(listOf("chamber", "infuser", "empty", "allay", "cow", "creeper", "sheep", "wolf", "zombie"), cards.map { it.id })
        assertSame(chamber, cards[0])
        assertSame(infuser, cards[1])
        assertSame(empty, cards[2])
    }

    @Test
    fun anchorsUtilitiesEvenWhenTheyAppearBetweenCards() {
        val chamber = Entry.utility("chamber")
        val empty = Entry.utility("empty")
        val addonUtility = Entry.utility("addon_utility")
        val cards = mutableListOf(
            Entry.card("zombie", "Zombie"), chamber,
            Entry.card("cow", "Cow"), empty,
            Entry.card("allay", "Allay"), addonUtility,
        )

        sorter().order(cards)

        assertEquals(listOf("allay", "chamber", "cow", "empty", "zombie", "addon_utility"), cards.map { it.id })
        assertSame(chamber, cards[1])
        assertSame(empty, cards[3])
        assertSame(addonUtility, cards[5])
    }

    @Test
    fun switchingAlphabeticalToConfigAndBackRebuildsTheSameLiveList() {
        val chamber = Entry.utility("chamber")
        val empty = Entry.utility("empty")
        val cards = mutableListOf(
            Entry.card("cow", "Cow"), chamber,
            Entry.card("zombie", "Zombie"), empty,
            Entry.card("allay", "Allay"),
        )
        val order = sorter()
        val configOrder = MobDataCardGuideOrdering(
            MobDataCardGuideSortMode.CONFIG,
            listOf("zombie", "cow", "allay"),
        )

        assertSame(cards, order.order(cards))
        assertEquals(listOf("allay", "chamber", "cow", "empty", "zombie"), cards.map { it.id })

        assertSame(cards, order.order(cards, configOrder))
        assertEquals(listOf("zombie", "chamber", "cow", "empty", "allay"), cards.map { it.id })
        assertSame(chamber, cards[1])
        assertSame(empty, cards[3])

        assertSame(cards, order.order(cards))
        assertEquals(listOf("allay", "chamber", "cow", "empty", "zombie"), cards.map { it.id })
    }

    @Test
    fun reorderedYamlWithTheSameNumberOfCardsInvalidatesCachedOrder() {
        val historical = yaml("zombie: {enabled: true}\ncow: {enabled: true}")
        val modern = yaml("wolf: {enabled: true}\nallay: {enabled: true}")
        val cards = mutableListOf(
            Entry.card("allay", "Allay"), Entry.card("cow", "Cow"),
            Entry.card("wolf", "Wolf"), Entry.card("zombie", "Zombie"),
        )
        val order = sorter()
        val configOrder = MobDataCardGuideOrdering(
            MobDataCardGuideSortMode.CONFIG,
            MobDataCardGuideOrdering.configuredSequence(historical, modern),
        )
        order.order(cards, configOrder)
        assertEquals(listOf("zombie", "cow", "wolf", "allay"), cards.map { it.id })

        val reordered = configOrder.copy(
            configuredIds = MobDataCardGuideOrdering.configuredSequence(
                yaml("cow: {enabled: true}\nzombie: {enabled: true}"),
                yaml("allay: {enabled: true}\nwolf: {enabled: true}"),
            ),
        )

        assertSame(cards, order.order(cards, reordered))
        assertEquals(listOf("cow", "zombie", "allay", "wolf"), cards.map { it.id })
    }

    @Test
    fun editsToTheSameConfiguredIdListAlsoInvalidateCachedOrder() {
        val configuredIds = mutableListOf("zombie", "cow", "allay")
        val configOrder = MobDataCardGuideOrdering(MobDataCardGuideSortMode.CONFIG, configuredIds)
        val cards = mutableListOf(
            Entry.card("cow", "Cow"), Entry.card("allay", "Allay"), Entry.card("zombie", "Zombie"),
        )
        val order = sorter()
        order.order(cards, configOrder)
        assertEquals(listOf("zombie", "cow", "allay"), cards.map { it.id })

        configuredIds.reverse()

        assertSame(cards, order.order(cards, configOrder))
        assertEquals(listOf("allay", "cow", "zombie"), cards.map { it.id })
    }

    @Test
    fun historicalKeysTakePrecedenceOverModernDefaultsEvenWhenDisabled() {
        val historical = yaml(
            """
            wolf:
              enabled: false
              name: Custom Wolf
            cow:
              enabled: true
            """.trimIndent(),
        )
        val modern = yaml(
            """
            allay:
              enabled: true
            wolf:
              enabled: true
            zombie:
              enabled: true
            cow:
              enabled: true
            """.trimIndent(),
        )

        val configuredIds = MobDataCardGuideOrdering.configuredSequence(historical, modern)

        assertEquals(listOf("wolf", "cow", "allay", "zombie"), configuredIds)
        // Disabled/unavailable cards keep their config rank without creating guide entries.
        val cards = mutableListOf(Entry.card("zombie", "Zombie"), Entry.card("allay", "Allay"), Entry.card("cow", "Cow"))
        sorter().order(cards, MobDataCardGuideOrdering(MobDataCardGuideSortMode.CONFIG, configuredIds))
        assertEquals(listOf("cow", "allay", "zombie"), cards.map { it.id })
    }

    @Test
    fun configOrderAppendsLateAddonCardsByNormalizedIdIndependentlyOfNameOrGroup() {
        val cards = mutableListOf(Entry.card("cow", "Aardvark"), Entry.card("zombie", "Zebra"))
        val configOrder = MobDataCardGuideOrdering(MobDataCardGuideSortMode.CONFIG, listOf(" ZOMBIE ", "cow"))
        val order = sorter()
        val liveList = order.order(cards, configOrder)
        liveList.add(Entry.card("ZZZ_ADDON", "&aA First Name").apply { group = MobDataCardGuideGroup.PASSIVE })
        liveList.add(Entry.card("dynatech_vex", "&cVex"))
        liveList.add(Entry.card(" addon_aurora ", "Z Last Name").apply { group = MobDataCardGuideGroup.BOSS })

        assertSame(liveList, order.order(cards, configOrder))
        val expected = listOf("zombie", "cow", " addon_aurora ", "dynatech_vex", "ZZZ_ADDON")
        assertEquals(expected, cards.map { it.id })

        cards.reverse()
        order.order(cards, configOrder)
        assertEquals(expected, cards.map { it.id })
    }

    @Test
    fun bothOrderingModesPreserveCustomizedNamesAndCardObjects() {
        val cow = Entry.card("cow", "§x§F§F§0§0§A§A  Zebra &lCow ")
        val zombie = Entry.card("zombie", "&#00aaffA Custom Zombie")
        val cards = mutableListOf(cow, zombie)
        val originalNames = cards.associate { it.id to it.name }
        val order = sorter()

        order.order(cards)
        assertEquals(listOf("zombie", "cow"), cards.map { it.id })
        assertSame(zombie, cards[0])
        assertSame(cow, cards[1])
        assertEquals(originalNames, cards.associate { it.id to it.name })

        order.order(cards, MobDataCardGuideOrdering(MobDataCardGuideSortMode.CONFIG, listOf("cow", "zombie")))
        assertSame(cow, cards[0])
        assertSame(zombie, cards[1])
        assertEquals(originalNames, cards.associate { it.id to it.name })
    }

    @Test
    fun sortModeParsingDefaultsOnlyWhenTheCallerChoosesTo() {
        assertEquals(MobDataCardGuideSortMode.ALPHABETICAL, MobDataCardGuideOrdering().mode)
        assertEquals(MobDataCardGuideSortMode.ALPHABETICAL, MobDataCardGuideSortMode.parse(" Alphabetical "))
        assertEquals(MobDataCardGuideSortMode.CONFIG, MobDataCardGuideSortMode.parse("CONFIG"))
        assertEquals(MobDataCardGuideSortMode.CONFIG, MobDataCardGuideSortMode.parse(" yaml "))
        assertNull(MobDataCardGuideSortMode.parse(null))
        assertNull(MobDataCardGuideSortMode.parse(""))
        assertNull(MobDataCardGuideSortMode.parse("registration"))
    }

    @Test
    fun lateAddonCardsJoinTheCorrectGroupAndTiesUseStableIds() {
        val cards = mutableListOf(Entry.card("vex", "Vex"), Entry.card("allay", "Allay"))
        val order = sorter()
        val liveList = order.order(cards)
        liveList.add(Entry.card("dynatech_vex", "&cVex"))
        liveList.add(Entry.card("dynatech_phantom", "Phantom"))
        liveList.add(Entry.card("new_addon_mob", "A Custom Mob"))

        assertSame(liveList, order.order(cards))
        assertEquals(listOf("new_addon_mob", "allay", "dynatech_phantom", "dynatech_vex", "vex"), cards.map { it.id })
        assertEquals(MobDataCardGuideGroup.HOSTILE, MobDataCardGuideGroups.defaultGroup("dynatech_vex"))
        assertEquals(MobDataCardGuideGroup.HOSTILE, MobDataCardGuideGroups.defaultGroup("dynatech_phantom"))
        assertEquals(MobDataCardGuideGroup.HOSTILE, MobDataCardGuideGroups.defaultGroup("new_addon_mob"))
    }

    @Test
    fun theSameLiveListStillSupportsRemoveReplaceAndClearAfterSorting() {
        val allay = Entry.card("allay", "Allay")
        val zombie = Entry.card("zombie", "Zombie")
        val backing = mutableListOf(zombie, allay)
        val order = sorter()
        val view = order.order(backing)

        assertTrue(view.remove(allay))
        assertEquals(listOf(zombie), backing)
        view[0] = Entry.card("cow", "Cow")
        assertEquals("cow", backing.single().id)
        assertSame(backing, order.order(view))
        view.clear()
        assertTrue(backing.isEmpty())
        assertSame(backing, order.order(backing))
    }

    @Test
    fun sameSizeListEditsInvalidateOrderAndTypeOverridesKeepAlphabeticOrder() {
        val cow = Entry.card("cow", "Cow")
        val zombie = Entry.card("zombie", "Zombie")
        val cards = mutableListOf(zombie, cow)
        val order = sorter()
        order.order(cards)
        assertEquals(listOf("cow", "zombie"), cards.map { it.id })

        zombie.group = MobDataCardGuideGroup.PASSIVE
        order.order(cards)
        assertEquals(listOf("cow", "zombie"), cards.map { it.id })
        assertEquals(MobDataCardGuideGroup.PASSIVE, MobDataCardGuideKey.of(zombie.id, zombie.name, zombie.group).group)

        zombie.group = null
        order.order(cards)
        assertEquals(listOf("cow", "zombie"), cards.map { it.id })

        cards[0] = Entry.card("wither", "Wither")
        order.order(cards)
        assertEquals(listOf("wither", "zombie"), cards.map { it.id })
        cards.reverse()
        order.order(cards)
        assertEquals(listOf("wither", "zombie"), cards.map { it.id })
    }

    @Test
    fun guideGroupsAreExplicitAndEveryBundledCardHasADefault() {
        val bundledIds = config("mob-simulation.yml").getKeys(false) + config("mob-simulation-modern.yml").getKeys(false)
        assertEquals(88, bundledIds.size)
        assertEquals(bundledIds + setOf("dynatech_vex", "dynatech_phantom"), MobDataCardGuideGroups.knownIds)
        assertEquals(MobDataCardGuideGroup.PASSIVE, MobDataCardGuideGroups.parse(" Friendly "))
        assertEquals(MobDataCardGuideGroup.PASSIVE, MobDataCardGuideGroups.parse("PASSIVE"))
        assertEquals(MobDataCardGuideGroup.HOSTILE, MobDataCardGuideGroups.parse("aggressive"))
        assertNull(MobDataCardGuideGroups.parse(null))
        assertEquals(MobDataCardGuideGroup.NEUTRAL, MobDataCardGuideGroups.parse("neutral"))
        assertEquals(MobDataCardGuideGroup.BOSS, MobDataCardGuideGroups.parse("boss"))
        assertEquals(MobDataCardGuideGroup.BOSS, MobDataCardGuideGroups.parse(" BOSSes "))
        assertNull(MobDataCardGuideGroups.parse("unknown"))
        assertNull(MobDataCardGuideGroups.parse(""))
        assertEquals(MobDataCardGuideGroup.NEUTRAL, MobDataCardGuideGroups.defaultGroup(" IRON_GOLEM "))
    }

    @Test
    fun displayNameSortingStripsFormattingAndDoesNotDependOnServerLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val blue = MobDataCardGuideKey.of("IRON_GOLEM", "&x&0&0&A&A&F&FIron Golem")
            assertEquals("iron golem", blue.name)
            assertEquals("iron_golem", blue.id)
            assertEquals("allay", MobDataCardGuideKey.of("allay", "&#00aaffAllay").name)
            assertEquals("snow golem", MobDataCardGuideKey.of("snow_golem", "§b").name)
            assertEquals(MobDataCardGuideGroup.NEUTRAL, blue.group)
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun sorter() = MobDataCardGuideOrder<Entry> { entry ->
        if (entry.card) MobDataCardGuideKey.of(entry.id, entry.name, entry.group) else null
    }

    private fun config(name: String): YamlConfiguration = YamlConfiguration.loadConfiguration(
        InputStreamReader(requireNotNull(javaClass.classLoader.getResourceAsStream(name)), StandardCharsets.UTF_8)
    )

    private fun yaml(contents: String) = YamlConfiguration().apply { loadFromString(contents) }

    private class Entry(val id: String, val name: String, val card: Boolean, var group: MobDataCardGuideGroup? = null) {
        companion object {
            fun card(id: String, name: String) = Entry(id, name, true)
            fun utility(id: String) = Entry(id, id, false)
        }
    }
}
