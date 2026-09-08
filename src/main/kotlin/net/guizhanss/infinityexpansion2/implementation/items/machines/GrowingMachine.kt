package net.guizhanss.infinityexpansion2.implementation.items.machines

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.ItemState
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.libraries.dough.inventory.InvUtils
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import net.guizhanss.infinityexpansion2.core.config.MachineSettings
import net.guizhanss.infinityexpansion2.core.items.attributes.InformationalRecipeDisplayItem
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts.AbstractTickingMachine
import net.guizhanss.infinityexpansion2.utils.items.GuiItems
import net.guizhanss.infinityexpansion2.utils.slimefunext.MutableRecipes
import net.guizhanss.infinityexpansion2.utils.slimefunext.RecipeInput
import net.guizhanss.infinityexpansion2.utils.slimefunext.RecipeOutput
import net.guizhanss.infinityexpansion2.utils.slimefunext.Recipes
import net.guizhanss.infinityexpansion2.utils.slimefunext.toDisplayRecipe
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

open class GrowingMachine(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    energyPerTick: Int,
    outputInterval: Int,
) : AbstractTickingMachine(itemGroup, itemStack, recipeType, recipe, MenuLayout.SINGLE_INPUT, energyPerTick),
    InformationalRecipeDisplayItem {

    private val outputIntervalSetting = IntRangeSetting(this, "output-interval", 1, outputInterval, 3600)

    init {
        addItemSetting(outputIntervalSetting)
    }

    private val _recipes: MutableRecipes = mutableMapOf()

    val recipes: Recipes get() = _recipes

    fun addRecipe(input: RecipeInput, output: RecipeOutput) {
        require(output.isNotEmpty()) { "Recipe output cannot be empty" }
        check(state == ItemState.UNREGISTERED) { "Cannot add recipes after the machine has been registered" }
        _recipes[input] = output
    }

    @JvmName("addRecipeVararg")
    fun addRecipe(input: RecipeInput, vararg output: ItemStack) {
        addRecipe(input, output)
    }

    protected fun addConfiguredRecipe(family: String, input: ItemStack, vararg output: ItemStack) {
        val configuredOutput = MachineSettings.configuredOutputs(family, input.type.name, output) ?: return
        if (configuredOutput.isNotEmpty()) {
            addRecipe(input, configuredOutput)
        }
    }

    protected fun addCustomConfiguredRecipes(family: String) {
        MachineSettings.customRecipes(family).forEach { (input, output) ->
            addRecipe(input, output)
        }
    }

    override fun process(b: Block, menu: BlockMenu): Boolean {
        val input = menu.getItemInSlot(inputSlots[0])
        if (input == null) {
            menu.setStatus { GuiItems.INVALID_INPUT }
            return false
        }

        val output = findRecipe(input)
        if (output == null) {
            menu.setStatus { GuiItems.INVALID_INPUT }
            return false
        }

        if (shouldProduce()) {
            val generated = output.map { it.clone() }.toTypedArray()
            if (!InvUtils.fitAll(menu.toInventory(), generated, *outputSlots)) {
                menu.setStatus { GuiItems.NO_ROOM }
                return false
            }
            generated.forEach { menu.pushItem(it, *outputSlots) }
        }

        menu.setStatus { GuiItems.PRODUCING }
        return true
    }

    private fun findRecipe(item: RecipeInput): RecipeOutput? {
        return _recipes.entries.find { (input, _) -> SlimefunUtils.isItemSimilar(item, input, false) }?.value
    }

    private fun shouldProduce() =
        tickCount % (getCustomTickRate() * outputIntervalSetting.value) == 0

    override fun getDefaultDisplayRecipes() = _recipes.flatMap { it.toPair().toDisplayRecipe() }

    override fun getInfoItems() = listOf(
        GuiItems.tickRate(getCustomTickRate()),
        GuiItems.energyConsumptionPerTick(getEnergyConsumptionPerTick()),
        GuiItems.outputInterval(outputIntervalSetting.value),
    )

    override fun getDividerItem() = GuiItems.RECIPES
}
