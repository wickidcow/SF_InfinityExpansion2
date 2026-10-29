package net.guizhanss.infinityexpansion2.implementation.guide.groups

import io.github.thebusybiscuit.slimefun4.api.researches.Research
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Display-only regression checks; no server, inventory or player data is created. */
class DisplaySubGroupResearchCostTest {
    @Test
    fun keepsExactLegacyTextForZeroOrdinaryAndMaximumLevelCosts() {
        for (levels in listOf(0, 1, 30, 32767, Int.MAX_VALUE)) {
            val research = research(levels, 19.5)

            assertEquals("&7Cost: &b$levels Level(s)", DisplaySubGroup.researchCostLore(research))
            assertEquals(levels, research.levelCost)
        }
    }

    @Test
    fun usesXpLevelCostRatherThanTheIndependentCurrencyCost() {
        val research = research(37, 9876.5)

        assertEquals("&7Cost: &b37 Level(s)", DisplaySubGroup.researchCostLore(research))
    }

    @Test
    fun readsUpdatedCostInsteadOfCachingOrRewritingTheResearch() {
        val research = research(5, 123.0)
        val key = research.key
        assertEquals("&7Cost: &b5 Level(s)", DisplaySubGroup.researchCostLore(research))

        research.levelCost = 41

        assertEquals("&7Cost: &b41 Level(s)", DisplaySubGroup.researchCostLore(research))
        assertEquals(41, research.levelCost)
        assertEquals(key, research.key)
    }

    private fun research(levels: Int, currency: Double): Research = Research(
        NamespacedKey("ie2test", "research_cost"), 1, "Research cost regression", levels, currency
    )
}
