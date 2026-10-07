package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

class MobDataCardGuideOrderTest {
    @Test
    fun groupsCardsFriendlyThenPassiveThenAggressiveAlphabetically() {
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
        assertEquals(listOf("chamber", "infuser", "empty", "allay", "wolf", "cow", "sheep", "creeper", "zombie"), cards.map { it.id })
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
    fun lateAddonCardsJoinTheCorrectGroupAndTiesUseStableIds() {
        val cards = mutableListOf(Entry.card("vex", "Vex"), Entry.card("allay", "Allay"))
        val order = sorter()
        val liveList = order.order(cards)
        liveList.add(Entry.card("dynatech_vex", "&cVex"))
        liveList.add(Entry.card("dynatech_phantom", "Phantom"))
        liveList.add(Entry.card("new_addon_mob", "A Custom Mob"))

        assertSame(liveList, order.order(cards))
        assertEquals(listOf("allay", "new_addon_mob", "dynatech_phantom", "dynatech_vex", "vex"), cards.map { it.id })
        assertEquals(MobDataCardGuideGroup.AGGRESSIVE, MobDataCardGuideGroups.defaultGroup("dynatech_vex"))
        assertEquals(MobDataCardGuideGroup.AGGRESSIVE, MobDataCardGuideGroups.defaultGroup("dynatech_phantom"))
        assertEquals(MobDataCardGuideGroup.AGGRESSIVE, MobDataCardGuideGroups.defaultGroup("new_addon_mob"))
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
    fun sameSizeListEditsAndSameItemGroupOverridesInvalidateTheCachedOrder() {
        val cow = Entry.card("cow", "Cow")
        val zombie = Entry.card("zombie", "Zombie")
        val cards = mutableListOf(zombie, cow)
        val order = sorter()
        order.order(cards)
        assertEquals(listOf("cow", "zombie"), cards.map { it.id })

        zombie.group = MobDataCardGuideGroup.FRIENDLY
        order.order(cards)
        assertEquals(listOf("zombie", "cow"), cards.map { it.id })

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
        assertEquals(MobDataCardGuideGroup.FRIENDLY, MobDataCardGuideGroups.parse(" Friendly "))
        assertEquals(MobDataCardGuideGroup.PASSIVE, MobDataCardGuideGroups.parse("PASSIVE"))
        assertEquals(MobDataCardGuideGroup.AGGRESSIVE, MobDataCardGuideGroups.parse("aggressive"))
        assertNull(MobDataCardGuideGroups.parse(null))
        assertNull(MobDataCardGuideGroups.parse("neutral"))
        assertNull(MobDataCardGuideGroups.parse(""))
        assertEquals(MobDataCardGuideGroup.FRIENDLY, MobDataCardGuideGroups.defaultGroup(" IRON_GOLEM "))
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
            assertEquals(MobDataCardGuideGroup.FRIENDLY, blue.group)
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

    private class Entry(val id: String, val name: String, val card: Boolean, var group: MobDataCardGuideGroup? = null) {
        companion object {
            fun card(id: String, name: String) = Entry(id, name, true)
            fun utility(id: String) = Entry(id, id, false)
        }
    }
}
