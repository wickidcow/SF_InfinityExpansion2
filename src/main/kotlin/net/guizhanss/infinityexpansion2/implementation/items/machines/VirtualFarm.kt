package net.guizhanss.infinityexpansion2.implementation.items.machines

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import net.guizhanss.guizhanlib.kt.minecraft.extensions.toItem
import net.guizhanss.infinityexpansion2.core.config.MachineSettings
import net.guizhanss.infinityexpansion2.core.items.attributes.CustomWikiItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

private const val CONFIG_KEY = "virtual-farm"

class VirtualFarm(
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

    override val wikiUrl = "machines/virtual-farm"

    init {
        addConfiguredRecipe(CONFIG_KEY, Material.WHEAT_SEEDS.toItem(), Material.WHEAT.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.CARROT.toItem(), Material.CARROT.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.POTATO.toItem(), Material.POTATO.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.BEETROOT_SEEDS.toItem(), Material.BEETROOT.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.PUMPKIN_SEEDS.toItem(), Material.PUMPKIN.toItem())
        addConfiguredRecipe(CONFIG_KEY, Material.MELON_SEEDS.toItem(), Material.MELON.toItem())
        addConfiguredRecipe(CONFIG_KEY, Material.SUGAR_CANE.toItem(), Material.SUGAR_CANE.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.COCOA_BEANS.toItem(), Material.COCOA_BEANS.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.CACTUS.toItem(), Material.CACTUS.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.BAMBOO.toItem(), Material.BAMBOO.toItem(6))
        addConfiguredRecipe(CONFIG_KEY, Material.CHORUS_FLOWER.toItem(), Material.CHORUS_FRUIT.toItem(6))
        addConfiguredRecipe(CONFIG_KEY, Material.NETHER_WART.toItem(), Material.NETHER_WART.toItem(2))
        addConfiguredRecipe(CONFIG_KEY, Material.SWEET_BERRIES.toItem(), Material.SWEET_BERRIES.toItem(2))

        // Modern farmables and renewable vegetation.
        addOptionalRecipe("GLOW_BERRIES", "GLOW_BERRIES", 2)
        addOptionalRecipe("TORCHFLOWER_SEEDS", "TORCHFLOWER", 2)
        addOptionalRecipe("PITCHER_POD", "PITCHER_PLANT", 2)
        addOptionalRecipe("KELP", "KELP", 2)
        addOptionalRecipe("SEA_PICKLE", "SEA_PICKLE", 2)
        addOptionalRecipe("MOSS_BLOCK", "MOSS_BLOCK", 2)
        addOptionalRecipe("PALE_MOSS_BLOCK", "PALE_MOSS_BLOCK", 2)

        addCustomConfiguredRecipes(CONFIG_KEY)
    }

    private fun addOptionalRecipe(inputName: String, outputName: String, amount: Int) {
        val input = Material.matchMaterial(inputName) ?: return
        val output = Material.matchMaterial(outputName) ?: return
        addConfiguredRecipe(CONFIG_KEY, ItemStack(input), ItemStack(output, amount))
    }
}
