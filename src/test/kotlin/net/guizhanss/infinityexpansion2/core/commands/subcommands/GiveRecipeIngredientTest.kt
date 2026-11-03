package net.guizhanss.infinityexpansion2.core.commands.subcommands

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.mockbukkit.mockbukkit.MockBukkit

/** Regression for /infinityexpansion2 giverecipe with placeholder-only recipes. */
class GiveRecipeIngredientTest {

    @BeforeEach
    fun setUpBukkit() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDownBukkit() {
        MockBukkit.unmock()
    }

    @Test
    fun nullRecipeSlotsDoNotReachInventoryPush() {
        assertTrue(usableRecipeIngredients(arrayOfNulls<ItemStack>(9)).isEmpty())
    }

    @Test
    fun airAndZeroAmountPlaceholdersDoNotReachInventoryPush() {
        val recipe = arrayOf<ItemStack?>(
            ItemStack(Material.AIR),
            ItemStack(Material.CAVE_AIR),
            ItemStack(Material.DIAMOND, 0),
            null,
        )

        assertTrue(usableRecipeIngredients(recipe).isEmpty())
    }

    @Test
    fun normalRecipePreservesExactIngredientStacksAndOrder() {
        val iron = ItemStack(Material.IRON_INGOT, 23)
        val diamond = ItemStack(Material.DIAMOND, 5)
        val recipe = arrayOf<ItemStack?>(
            null,
            iron,
            ItemStack(Material.AIR),
            diamond,
            null,
        )

        assertEquals(listOf(iron, diamond), usableRecipeIngredients(recipe))
        assertEquals(23, iron.amount)
        assertEquals(5, diamond.amount)
    }
}
