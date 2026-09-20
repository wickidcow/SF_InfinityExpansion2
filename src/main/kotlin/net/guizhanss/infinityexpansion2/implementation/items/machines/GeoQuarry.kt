package net.guizhanss.infinityexpansion2.implementation.items.machines

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import net.guizhanss.guizhanlib.kt.minecraft.items.edit
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.items.attributes.InformationalRecipeDisplayItem
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts.AbstractTickingMachine
import net.guizhanss.infinityexpansion2.utils.items.GuiItems
import org.bukkit.World.Environment
import org.bukkit.block.Biome
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

class GeoQuarry(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    energyPerTick: Int,
    outputInterval: Int,
    val speed: Int, // the amount of output
) : AbstractTickingMachine(itemGroup, itemStack, recipeType, recipe, MenuLayout.OUTPUT_ONLY, energyPerTick),
    InformationalRecipeDisplayItem {

    private val outputIntervalSetting = IntRangeSetting(this, "output-interval", 1, outputInterval, 3600)

    init {
        addItemSetting(outputIntervalSetting)
    }

    override fun process(b: Block, menu: BlockMenu): Boolean {
        menu.setStatus { GuiItems.PRODUCING }
        if (!shouldProduce()) return true

        val output = tryProduceDracFunEnderDraconium(menu)
            ?: produce(menu)?.edit { amount(speed) }
            ?: return true

        if (!menu.fits(output, *outputSlots)) {
            menu.setStatus { GuiItems.NO_ROOM }
            return false
        }

        menu.pushItem(output, *outputSlots)
        return true
    }

    private fun shouldProduce() =
        tickCount % (getCustomTickRate() * outputIntervalSetting.value) == 0

    private fun produce(menu: BlockMenu): ItemStack? {
        val biome = menu.location.block.biome
        val env = menu.location.world.environment

        return getProductPool(biome, env).randomOrNull()
    }

    private fun tryProduceDracFunEnderDraconium(menu: BlockMenu): ItemStack? {
        if (menu.location.world.environment != Environment.THE_END) return null

        val ore = SlimefunItem.getById(DRACFUN_ENDER_DRACONIUM_ID) ?: return null
        val chance = InfinityExpansion2.configService.quarryDracFunEnderDraconiumChance.value
        if (chance <= 0.0 || Random.nextDouble() >= chance) return null

        return ore.item.clone().apply { amount = 1 }
    }

    private fun getProductPool(biome: Biome, env: Environment): List<ItemStack> =
        geoRecipes.getOrPut(Pair(biome, env)) {
            val pool = mutableListOf<ItemStack>()
            Slimefun.getRegistry().geoResources.values().filter { it.isObtainableFromGEOMiner }.forEach { resource ->
                if (SlimefunItem.getByItem(resource.item)?.id == DRACFUN_ENDER_DRACONIUM_ID) {
                    return@forEach
                }

                val supply = resource.getDefaultSupply(env, biome)
                if (supply > 0) {
                    repeat(supply) {
                        pool.add(resource.item)
                    }
                }
            }
            pool
        }

    override fun getDefaultDisplayRecipes() =
        Slimefun.getRegistry().geoResources.values().filter { it.isObtainableFromGEOMiner }
            .map { it.item.edit { amount(speed) } }

    override fun getInfoItems() = listOf(
        GuiItems.tickRate(getCustomTickRate()),
        GuiItems.energyConsumptionPerTick(getEnergyConsumptionPerTick()),
        GuiItems.outputInterval(outputIntervalSetting.value)
    )

    override fun getDividerItem() = GuiItems.RECIPES

    companion object {

        private const val DRACFUN_ENDER_DRACONIUM_ID = "DRACFUN_DRACONIUM_ORE"
        private val geoRecipes = mutableMapOf<Pair<Biome, Environment>, List<ItemStack>>()
    }
}
