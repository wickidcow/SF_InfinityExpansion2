package net.guizhanss.infinityexpansion2.implementation.items.machines

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import net.guizhanss.guizhanlib.kt.minecraft.extensions.toItem
import net.guizhanss.guizhanlib.minecraft.utils.compatibility.MaterialX
import net.guizhanss.infinityexpansion2.core.config.MachineSettings
import net.guizhanss.infinityexpansion2.core.items.attributes.CustomWikiItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

private const val CONFIG_KEY = "flower-grower"

class FlowerGrower(
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

    override val wikiUrl = "machines/flower-grower"

    init {
        addConfiguredRecipe(CONFIG_KEY, MaterialX.SHORT_GRASS.toItem(), MaterialX.SHORT_GRASS.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.FERN.toItem(), Material.FERN.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.DEAD_BUSH.toItem(), Material.DEAD_BUSH.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.DANDELION.toItem(), Material.DANDELION.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.POPPY.toItem(), Material.POPPY.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.BLUE_ORCHID.toItem(), Material.BLUE_ORCHID.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.ALLIUM.toItem(), Material.ALLIUM.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.AZURE_BLUET.toItem(), Material.AZURE_BLUET.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.RED_TULIP.toItem(), Material.RED_TULIP.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.ORANGE_TULIP.toItem(), Material.ORANGE_TULIP.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.WHITE_TULIP.toItem(), Material.WHITE_TULIP.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.PINK_TULIP.toItem(), Material.PINK_TULIP.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.OXEYE_DAISY.toItem(), Material.OXEYE_DAISY.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.CORNFLOWER.toItem(), Material.CORNFLOWER.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.LILY_OF_THE_VALLEY.toItem(), Material.LILY_OF_THE_VALLEY.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.WITHER_ROSE.toItem(), Material.WITHER_ROSE.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.PINK_PETALS.toItem(), Material.PINK_PETALS.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.SPORE_BLOSSOM.toItem(), Material.SPORE_BLOSSOM.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.WEEPING_VINES.toItem(), Material.WEEPING_VINES.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.TWISTING_VINES.toItem(), Material.TWISTING_VINES.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.VINE.toItem(), Material.VINE.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.TALL_GRASS.toItem(), Material.TALL_GRASS.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.LARGE_FERN.toItem(), Material.LARGE_FERN.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.SUNFLOWER.toItem(), Material.SUNFLOWER.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.LILAC.toItem(), Material.LILAC.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.ROSE_BUSH.toItem(), Material.ROSE_BUSH.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.PEONY.toItem(), Material.PEONY.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.CHORUS_FLOWER.toItem(), Material.CHORUS_FLOWER.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.GLOW_LICHEN.toItem(), Material.GLOW_LICHEN.toItem(4))
        addConfiguredRecipe(CONFIG_KEY, Material.LILY_PAD.toItem(), Material.LILY_PAD.toItem(4))

        // Newer vanilla vegetation. Runtime lookup lets IE2 keep building on older APIs.
        listOf(
            "TORCHFLOWER",
            "PITCHER_PLANT",
            "MOSS_CARPET",
            "PALE_MOSS_CARPET",
            "PALE_HANGING_MOSS",
            "HANGING_ROOTS",
            "SMALL_DRIPLEAF",
            "BIG_DRIPLEAF",
            "OPEN_EYEBLOSSOM",
            "CLOSED_EYEBLOSSOM",
            "FIREFLY_BUSH",
            "BUSH",
            "WILDFLOWERS",
            "CACTUS_FLOWER",
            "LEAF_LITTER",
            "SHORT_DRY_GRASS",
            "TALL_DRY_GRASS",
            // Minecraft 26.3 forward compatibility.
            "SHELF_MUSHROOM",
            "RED_SHRUB",
        ).forEach { addOptionalSelfRecipe(it, 4) }

        addCustomConfiguredRecipes(CONFIG_KEY)
    }

    private fun addOptionalSelfRecipe(materialName: String, amount: Int) {
        val material = Material.matchMaterial(materialName) ?: return
        addConfiguredRecipe(CONFIG_KEY, ItemStack(material), ItemStack(material, amount))
    }
}
