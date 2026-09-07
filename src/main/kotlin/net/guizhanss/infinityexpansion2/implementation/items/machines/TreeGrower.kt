package net.guizhanss.infinityexpansion2.implementation.items.machines

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import net.guizhanss.guizhanlib.kt.minecraft.extensions.toItem
import net.guizhanss.infinityexpansion2.core.config.MachineSettings
import net.guizhanss.infinityexpansion2.core.items.attributes.CustomWikiItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

private const val CONFIG_KEY = "tree-grower"

class TreeGrower(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    energyPerTick: Int,
    outputInterval: Int,
) : GrowingMachine(
    itemGroup,
    itemStack,
    recipeType,
    recipe,
    MachineSettings.energyPerTick(CONFIG_KEY, outputInterval, energyPerTick),
    MachineSettings.outputInterval(CONFIG_KEY, outputInterval),
), CustomWikiItem {

    override val wikiUrl = "machines/tree-grower"

    init {
        addConfiguredRecipe(
            CONFIG_KEY,
            Material.OAK_SAPLING.toItem(),
            Material.OAK_LEAVES.toItem(8),
            Material.OAK_LOG.toItem(6),
            Material.STICK.toItem(),
            Material.APPLE.toItem(),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.SPRUCE_SAPLING.toItem(),
            Material.SPRUCE_LEAVES.toItem(8),
            Material.SPRUCE_LOG.toItem(6),
            Material.STICK.toItem(2),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.DARK_OAK_SAPLING.toItem(),
            Material.DARK_OAK_LEAVES.toItem(8),
            Material.DARK_OAK_LOG.toItem(6),
            Material.APPLE.toItem(),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.BIRCH_SAPLING.toItem(),
            Material.BIRCH_LEAVES.toItem(8),
            Material.BIRCH_LOG.toItem(6),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.ACACIA_SAPLING.toItem(),
            Material.ACACIA_LEAVES.toItem(8),
            Material.ACACIA_LOG.toItem(6),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.JUNGLE_SAPLING.toItem(),
            Material.JUNGLE_LEAVES.toItem(8),
            Material.JUNGLE_LOG.toItem(6),
            Material.COCOA_BEANS.toItem(),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.WARPED_FUNGUS.toItem(),
            Material.WARPED_HYPHAE.toItem(8),
            Material.WARPED_STEM.toItem(6),
            Material.SHROOMLIGHT.toItem(),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.CRIMSON_FUNGUS.toItem(),
            Material.CRIMSON_HYPHAE.toItem(8),
            Material.CRIMSON_STEM.toItem(6),
            Material.WEEPING_VINES.toItem(),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.MANGROVE_PROPAGULE.toItem(),
            Material.MANGROVE_LEAVES.toItem(8),
            Material.MANGROVE_LOG.toItem(6),
            Material.MANGROVE_ROOTS.toItem(4),
            Material.MUDDY_MANGROVE_ROOTS.toItem(2),
        )

        addConfiguredRecipe(
            CONFIG_KEY,
            Material.CHERRY_SAPLING.toItem(),
            Material.CHERRY_LEAVES.toItem(8),
            Material.CHERRY_LOG.toItem(6),
            Material.STICK.toItem(),
        )

        // Modern vanilla trees. String lookup keeps the same build compatible with older Paper APIs.
        addOptionalRecipe(
            "PALE_OAK_SAPLING",
            "PALE_OAK_LEAVES" to 8,
            "PALE_OAK_LOG" to 6,
            "STICK" to 1,
        )
        addOptionalRecipe(
            "AZALEA",
            "AZALEA_LEAVES" to 8,
            "OAK_LOG" to 6,
            "STICK" to 1,
        )
        addOptionalRecipe(
            "FLOWERING_AZALEA",
            "FLOWERING_AZALEA_LEAVES" to 8,
            "OAK_LOG" to 6,
            "STICK" to 1,
        )

        // Minecraft 26.3 forward compatibility: Poplar saplings can grow any of three leaf colors.
        // These recipes silently activate as soon as the runtime exposes the 26.3 Material names.
        addOptionalRecipe(
            "POPLAR_SAPLING",
            "RED_POPLAR_LEAVES" to 3,
            "ORANGE_POPLAR_LEAVES" to 3,
            "YELLOW_POPLAR_LEAVES" to 3,
            "POPLAR_LOG" to 6,
            "STICK" to 1,
        )

        addCustomConfiguredRecipes(CONFIG_KEY)
    }

    private fun addOptionalRecipe(inputName: String, vararg outputs: Pair<String, Int>) {
        val input = Material.matchMaterial(inputName) ?: return
        val outputStacks = outputs.mapNotNull { (materialName, amount) ->
            Material.matchMaterial(materialName)?.let { ItemStack(it, amount) }
        }.toTypedArray()

        if (outputStacks.isNotEmpty()) {
            addConfiguredRecipe(CONFIG_KEY, ItemStack(input), *outputStacks)
        }
    }
}
