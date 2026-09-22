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
            ?: tryProduceRareGeoResource(menu)
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

    private fun tryProduceRareGeoResource(menu: BlockMenu): ItemStack? {
        val configuredDrops = InfinityExpansion2.configService.quarryRareGeoDrops.value
        if (configuredDrops.isEmpty()) return null

        val biome = menu.location.block.biome
        val env = menu.location.world.environment
        val discoveries = Slimefun.getRegistry().geoResources.values()
            .asSequence()
            .filter { it.isObtainableFromGEOMiner }
            .filter { it.getDefaultSupply(env, biome) > 0 }
            .mapNotNull { resource ->
                val itemId = SlimefunItem.getByItem(resource.item)?.id ?: return@mapNotNull null
                val chance = configuredDrops[itemId] ?: return@mapNotNull null
                if (chance <= 0.0 || Random.nextDouble() >= chance.coerceAtMost(1.0)) {
                    return@mapNotNull null
                }
                resource.item
            }
            .toList()

        return discoveries.randomOrNull()?.clone()?.apply { amount = 1 }
    }

    private fun getProductPool(biome: Biome, env: Environment): List<ItemStack> {
        val rareGeoIds = activeRareGeoIds()
        val key = Triple(biome, env, rareGeoIds)

        return geoRecipes.getOrPut(key) {
            val pool = mutableListOf<ItemStack>()
            Slimefun.getRegistry().geoResources.values().filter { it.isObtainableFromGEOMiner }.forEach { resource ->
                val itemId = SlimefunItem.getByItem(resource.item)?.id
                if (itemId == DRACFUN_ENDER_DRACONIUM_ID || itemId in rareGeoIds) {
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
    }

    private fun activeRareGeoIds() =
        InfinityExpansion2.configService.quarryRareGeoDrops.value
            .filterValues { it > 0.0 }
            .keys
            .toSet()

    override fun getDefaultDisplayRecipes(): List<ItemStack> {
        val rareGeoIds = activeRareGeoIds()
        return Slimefun.getRegistry().geoResources.values()
            .filter { it.isObtainableFromGEOMiner }
            .map { resource ->
                val itemId = SlimefunItem.getByItem(resource.item)?.id
                resource.item.edit { amount(if (itemId in rareGeoIds) 1 else speed) }
            }
    }

    override fun getInfoItems() = listOf(
        GuiItems.tickRate(getCustomTickRate()),
        GuiItems.energyConsumptionPerTick(getEnergyConsumptionPerTick()),
        GuiItems.outputInterval(outputIntervalSetting.value)
    )

    override fun getDividerItem() = GuiItems.RECIPES

    companion object {

        private const val DRACFUN_ENDER_DRACONIUM_ID = "DRACFUN_DRACONIUM_ORE"
        private val geoRecipes = mutableMapOf<Triple<Biome, Environment, Set<String>>, List<ItemStack>>()
    }
}
