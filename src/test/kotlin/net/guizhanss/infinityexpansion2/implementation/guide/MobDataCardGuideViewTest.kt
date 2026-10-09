package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Navigation operates on the registered objects without moving them between item groups. */
class MobDataCardGuideViewTest {
    @Test
    fun allMobsInterleavesTypesAlphabeticallyAndUsesIdsForEqualNames() {
        val chamber = Entry("chamber", "Chamber", null)
        val source = mutableListOf(
            card("wither", "Wither", MobDataCardGuideGroup.BOSS),
            card("vex", "Vex", MobDataCardGuideGroup.HOSTILE),
            chamber,
            card("wolf", "Wolf", MobDataCardGuideGroup.NEUTRAL),
            card("cow", "&aCow", MobDataCardGuideGroup.PASSIVE),
            card("elder_guardian", "Elder Guardian", MobDataCardGuideGroup.BOSS),
            card("allay", "Allay", MobDataCardGuideGroup.PASSIVE),
            card("enderman", "Enderman", MobDataCardGuideGroup.NEUTRAL),
            card("dynatech_vex", "§cVex", MobDataCardGuideGroup.HOSTILE),
            card("blaze", "Blaze", MobDataCardGuideGroup.HOSTILE),
            card("addon_vex", "Vex", MobDataCardGuideGroup.BOSS),
        )
        val original = source.toList()

        val all = MobDataCardGuideCategory.ALL.cards(source, ::key)

        assertEquals(
            listOf("allay", "blaze", "cow", "elder_guardian", "enderman", "addon_vex", "dynatech_vex", "vex", "wither", "wolf"),
            all.map { it.id },
        )
        assertEquals(listOf("allay", "cow"), MobDataCardGuideCategory.PASSIVE.cards(source, ::key).map { it.id })
        assertEquals(listOf("enderman", "wolf"), MobDataCardGuideCategory.NEUTRAL.cards(source, ::key).map { it.id })
        assertEquals(listOf("blaze", "dynatech_vex", "vex"), MobDataCardGuideCategory.HOSTILE.cards(source, ::key).map { it.id })
        assertEquals(listOf("elder_guardian", "addon_vex", "wither"), MobDataCardGuideCategory.BOSS.cards(source, ::key).map { it.id })
        assertEquals(original, source, "Opening a guide view must not reorder the registration list")
        all.forEach { selected -> assertSame(original.single { it.id == selected.id }, selected) }
        assertFalse(all.contains(chamber), "Utility items belong in the selector, not in the mob lists")
    }

    @Test
    fun everyKnownCardAppearsOnceInAllAndExactlyOneTypeWithoutIncludingUtilities() {
        val cards = MobDataCardGuideGroups.knownIds.map { id ->
            card(id, id.replace('_', ' '), MobDataCardGuideGroups.defaultGroup(id))
        }
        val utilities = listOf(Entry("chamber", "Chamber", null), Entry("infuser", "Infuser", null), Entry("empty", "Empty Card", null))
        val source = (cards.reversed() + utilities).toMutableList()
        val all = MobDataCardGuideCategory.ALL.cards(source, ::key)
        val typeResults = MobDataCardGuideCategory.entries.filter { it != MobDataCardGuideCategory.ALL }
            .associateWith { it.cards(source, ::key) }
        val partition = typeResults.values.flatten()

        assertEquals(cards.size, all.size)
        assertEquals(cards.map { it.id }.toSet(), all.map { it.id }.toSet())
        assertEquals(all.map { it.id }.toSet(), partition.map { it.id }.toSet())
        assertTrue(partition.groupingBy { it.id }.eachCount().values.all { it == 1 })
        typeResults.forEach { (category, selection) ->
            assertTrue(selection.all { it.group == category.group }, category.name)
            selection.forEach { selected -> assertSame(cards.single { it.id == selected.id }, selected) }
        }
        assertTrue(utilities.none { it in partition || it in all })
    }

    @Test
    fun configViewsFollowHistoricalThenModernYamlOrderWithoutGroupingAllMobs() {
        val historical = yaml("""
            wither:
              enabled: true
            cow:
              enabled: true
            zombie:
              enabled: true
            wolf:
              enabled: true
            sheep:
              enabled: true
        """)
        val modern = yaml("""
            bee:
              enabled: true
            cow:
              enabled: false
            ghast:
              enabled: true
            warden:
              enabled: true
            frog:
              enabled: true
        """)
        val source = mutableListOf(
            card("frog", "§aCustom Frog", MobDataCardGuideGroup.PASSIVE),
            card("sheep", "&dCustom Sheep", MobDataCardGuideGroup.PASSIVE),
            card("warden", "§4Custom Warden", MobDataCardGuideGroup.BOSS),
            card("wolf", "&6Custom Wolf", MobDataCardGuideGroup.NEUTRAL),
            card("zombie", "§cCustom Zombie", MobDataCardGuideGroup.HOSTILE),
            card("ghast", "&cCustom Ghast", MobDataCardGuideGroup.HOSTILE),
            card("cow", "&bCustom Cow", MobDataCardGuideGroup.PASSIVE),
            card("bee", "§eCustom Bee", MobDataCardGuideGroup.NEUTRAL),
            card("wither", "&5Custom Wither", MobDataCardGuideGroup.BOSS),
        )
        val original = source.toList()
        val names = source.associate { it.id to it.name }
        val ordering = MobDataCardGuideOrdering(
            MobDataCardGuideSortMode.CONFIG,
            MobDataCardGuideOrdering.configuredSequence(historical, modern),
        )

        val all = MobDataCardGuideCategory.ALL.cards(source, ordering, ::key)

        assertEquals(listOf("wither", "cow", "zombie", "wolf", "sheep", "bee", "ghast", "warden", "frog"), all.map { it.id })
        assertEquals(listOf("cow", "sheep", "frog"), MobDataCardGuideCategory.PASSIVE.cards(source, ordering, ::key).map { it.id })
        assertEquals(listOf("wolf", "bee"), MobDataCardGuideCategory.NEUTRAL.cards(source, ordering, ::key).map { it.id })
        assertEquals(listOf("zombie", "ghast"), MobDataCardGuideCategory.HOSTILE.cards(source, ordering, ::key).map { it.id })
        assertEquals(listOf("wither", "warden"), MobDataCardGuideCategory.BOSS.cards(source, ordering, ::key).map { it.id })
        assertEquals(original, source)
        assertEquals(names, all.associate { it.id to it.name }, "Viewing config order must not normalize stored names or colors")
        all.forEach { selected -> assertSame(original.single { it.id == selected.id }, selected) }
    }

    @Test
    fun configViewRestoresIndependentYamlOrderAfterTheLiveGroupWasAlphabeticallyRearranged() {
        val source = mutableListOf(
            card("zombie", "§cZombie", MobDataCardGuideGroup.HOSTILE),
            Entry("chamber", "Chamber", null),
            card("wolf", "&6Wolf", MobDataCardGuideGroup.NEUTRAL),
            card("cow", "§bCow", MobDataCardGuideGroup.PASSIVE),
            card("wither", "&5Wither", MobDataCardGuideGroup.BOSS),
        )
        val yamlOrder = listOf("zombie", "wolf", "cow", "wither")
        val config = MobDataCardGuideOrdering(MobDataCardGuideSortMode.CONFIG, yamlOrder)
        val alphabetical = MobDataCardGuideOrdering(MobDataCardGuideSortMode.ALPHABETICAL, yamlOrder)
        MobDataCardGuideOrder<Entry>(::key).order(source)
        assertEquals(listOf("cow", "chamber", "wither", "wolf", "zombie"), source.map { it.id })
        val alphabeticalSource = source.toList()
        val originalNames = source.associate { it.id to it.name }

        assertEquals(yamlOrder, MobDataCardGuideCategory.ALL.cards(source, config, ::key).map { it.id })
        assertEquals(listOf("cow", "wither", "wolf", "zombie"), MobDataCardGuideCategory.ALL.cards(source, alphabetical, ::key).map { it.id })
        assertEquals(yamlOrder, MobDataCardGuideCategory.ALL.cards(source, config, ::key).map { it.id })
        assertEquals(alphabeticalSource, source, "A category view must not undo another guide's backing-list order")
        assertEquals(originalNames, source.associate { it.id to it.name })
    }

    @Test
    fun configViewsAppendLateAddonCardsByNormalizedIdWhileRetainingCategoryRelativeOrder() {
        val source = mutableListOf(
            card("wither", "Wither", MobDataCardGuideGroup.BOSS),
            card("cow", "Cow", MobDataCardGuideGroup.PASSIVE),
        )
        val ordering = MobDataCardGuideOrdering(MobDataCardGuideSortMode.CONFIG, listOf("cow", "wither"))
        assertEquals(listOf("cow", "wither"), MobDataCardGuideCategory.ALL.cards(source, ordering, ::key).map { it.id })

        source.addAll(listOf(
            card("ADDON_ZETA", "&aAardvark", MobDataCardGuideGroup.PASSIVE),
            card("mod_middle", "§bBlue", MobDataCardGuideGroup.NEUTRAL),
            card("Addon_Alpha", "&cZebra", MobDataCardGuideGroup.HOSTILE),
            card("addon_beta", "§5Wolf", MobDataCardGuideGroup.BOSS),
        ))
        val original = source.toList()
        assertEquals(
            listOf("cow", "wither", "Addon_Alpha", "addon_beta", "ADDON_ZETA", "mod_middle"),
            MobDataCardGuideCategory.ALL.cards(source, ordering, ::key).map { it.id },
        )
        assertEquals(listOf("cow", "ADDON_ZETA"), MobDataCardGuideCategory.PASSIVE.cards(source, ordering, ::key).map { it.id })
        assertEquals(listOf("wither", "addon_beta"), MobDataCardGuideCategory.BOSS.cards(source, ordering, ::key).map { it.id })
        assertEquals(original, source)

        val late = card("addon_AARON", "§dLast in name order", MobDataCardGuideGroup.HOSTILE)
        source.add(late)
        val all = MobDataCardGuideCategory.ALL.cards(source, ordering, ::key)
        assertEquals(listOf("cow", "wither", "addon_AARON", "Addon_Alpha", "addon_beta", "ADDON_ZETA", "mod_middle"), all.map { it.id })
        assertEquals(listOf("addon_AARON", "Addon_Alpha"), MobDataCardGuideCategory.HOSTILE.cards(source, ordering, ::key).map { it.id })
        assertSame(late, all[2])
        assertEquals("§dLast in name order", all[2].name)
    }

    @Test
    fun lateAddRemoveReplaceAndCategoryChangesAreReadFromTheSameLiveList() {
        val cow = card("cow", "Cow", MobDataCardGuideGroup.PASSIVE)
        val zombie = card("zombie", "Zombie", MobDataCardGuideGroup.HOSTILE)
        val live = mutableListOf(zombie, cow)
        val firstSelection = MobDataCardGuideCategory.ALL.cards(live, ::key)
        val late = card("addon_alpha", "Alpha", MobDataCardGuideGroup.NEUTRAL)
        live.add(late)

        assertEquals(listOf("addon_alpha", "cow", "zombie"), MobDataCardGuideCategory.ALL.cards(live, ::key).map { it.id })
        assertSame(late, MobDataCardGuideCategory.NEUTRAL.cards(live, ::key).single())
        assertEquals(listOf("cow", "zombie"), firstSelection.map { it.id }, "Existing page snapshots remain stable")

        live.remove(cow)
        live[live.indexOf(zombie)] = card("wither", "Wither", MobDataCardGuideGroup.BOSS)
        late.group = MobDataCardGuideGroup.PASSIVE
        assertEquals(listOf("addon_alpha", "wither"), MobDataCardGuideCategory.ALL.cards(live, ::key).map { it.id })
        assertTrue(MobDataCardGuideCategory.NEUTRAL.cards(live, ::key).isEmpty())
        assertTrue(MobDataCardGuideCategory.HOSTILE.cards(live, ::key).isEmpty())
        assertSame(late, MobDataCardGuideCategory.PASSIVE.cards(live, ::key).single())
        assertEquals("wither", MobDataCardGuideCategory.BOSS.cards(live, ::key).single().id)

        live.clear()
        MobDataCardGuideCategory.entries.forEach { assertTrue(it.cards(live, ::key).isEmpty()) }
    }

    @Test
    fun missingConfigurationEnablesAllFiveCategoriesInNavigationOrder() {
        val expected = listOf(
            MobDataCardGuideCategory.ALL,
            MobDataCardGuideCategory.PASSIVE,
            MobDataCardGuideCategory.NEUTRAL,
            MobDataCardGuideCategory.HOSTILE,
            MobDataCardGuideCategory.BOSS,
        )
        assertEquals(expected, MobDataCardGuideCategory.enabledIn(null).toList())
        assertEquals(expected, MobDataCardGuideCategory.enabledIn(YamlConfiguration()).toList())
    }

    @Test
    fun everyCategoryFlagIsIndependentAndCanBeReenabledWithoutRecreatingTheConfig() {
        val section = YamlConfiguration().createSection("categories")
        val flags = linkedMapOf(
            "all" to MobDataCardGuideCategory.ALL,
            "passive" to MobDataCardGuideCategory.PASSIVE,
            "neutral" to MobDataCardGuideCategory.NEUTRAL,
            "hostile" to MobDataCardGuideCategory.HOSTILE,
            "boss" to MobDataCardGuideCategory.BOSS,
        )

        flags.forEach { (configKey, category) ->
            section.set(configKey, false)
            assertEquals(flags.values.toSet() - category, MobDataCardGuideCategory.enabledIn(section), configKey)
            section.set(configKey, true)
            assertEquals(flags.values.toSet(), MobDataCardGuideCategory.enabledIn(section), configKey)
        }
        flags.keys.forEach { section.set(it, false) }
        assertTrue(MobDataCardGuideCategory.enabledIn(section).isEmpty())
        section.set("all", true)
        assertEquals(setOf(MobDataCardGuideCategory.ALL), MobDataCardGuideCategory.enabledIn(section))
    }

    @Test
    fun hidingTypeButtonsDoesNotRemoveTheirCardsFromAllMobsOrMutateRegistration() {
        val section = YamlConfiguration()
        section.set("boss", false)
        section.set("hostile", false)
        val source = mutableListOf(
            card("wither", "Wither", MobDataCardGuideGroup.BOSS),
            card("zombie", "Zombie", MobDataCardGuideGroup.HOSTILE),
        )
        val original = source.toList()

        assertEquals(
            setOf(MobDataCardGuideCategory.ALL, MobDataCardGuideCategory.PASSIVE, MobDataCardGuideCategory.NEUTRAL),
            MobDataCardGuideCategory.enabledIn(section),
        )
        assertEquals(listOf("wither", "zombie"), MobDataCardGuideCategory.ALL.cards(source, ::key).map { it.id })
        assertEquals(original, source)
    }

    @Test
    fun emptyAndExactlyFullPagesHaveValidOneBasedNavigation() {
        for (requested in listOf(Int.MIN_VALUE, 0, 1, 2, Int.MAX_VALUE)) {
            val empty = MobDataCardGuidePage.of(emptyList<String>(), requested, 36)
            assertEquals(1, empty.number)
            assertEquals(1, empty.total)
            assertTrue(empty.items.isEmpty())
        }
        val fullItems = (1..36).toList()
        val full = MobDataCardGuidePage.of(fullItems, 2, 36)
        assertEquals(1, full.number)
        assertEquals(1, full.total)
        assertEquals(fullItems, full.items)
    }

    @Test
    fun thirtySevenCardsHaveNoOverlapOrOmissionAndAStalePageClampsAfterShrink() {
        val items = (1..37).toList()
        val first = MobDataCardGuidePage.of(items, 0, 36)
        val second = MobDataCardGuidePage.of(items, Int.MAX_VALUE, 36)
        assertEquals(1, first.number)
        assertEquals(2, first.total)
        assertEquals((1..36).toList(), first.items)
        assertEquals(2, second.number)
        assertEquals(2, second.total)
        assertEquals(listOf(37), second.items)
        assertEquals(items, first.items + second.items)

        val shrunk = MobDataCardGuidePage.of(items.take(3), second.number, 36)
        assertEquals(1, shrunk.number)
        assertEquals(1, shrunk.total)
        assertEquals(listOf(1, 2, 3), shrunk.items)
        val cleared = MobDataCardGuidePage.of(emptyList<Int>(), second.number, 36)
        assertEquals(1, cleared.number)
        assertEquals(1, cleared.total)
        assertTrue(cleared.items.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { MobDataCardGuidePage.of(items, 1, 0) }
    }

    private fun card(id: String, name: String, group: MobDataCardGuideGroup) = Entry(id, name, group)

    private fun key(entry: Entry): MobDataCardGuideKey? =
        entry.group?.let { MobDataCardGuideKey.of(entry.id, entry.name, it) }

    private fun yaml(source: String) = YamlConfiguration().apply { loadFromString(source.trimIndent()) }

    private class Entry(val id: String, val name: String, var group: MobDataCardGuideGroup?)
}
