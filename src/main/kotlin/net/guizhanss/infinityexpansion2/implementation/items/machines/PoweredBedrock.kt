package net.guizhanss.infinityexpansion2.implementation.items.machines

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.items.attributes.EnergyTickingConsumer
import net.guizhanss.infinityexpansion2.implementation.IEItems
import net.guizhanss.infinityexpansion2.implementation.guide.IEItemGroups
import net.guizhanss.infinityexpansion2.implementation.recipes.IERecipeTypes
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

/**
 * IE1's Powered Bedrock, restored for IE2.
 *
 * The placed block is a netherite block while unpowered and becomes bedrock while it can
 * sustain its energy cost. The ticker intentionally preserves IE1's 7-of-8 tick cadence
 * and the v140 state/power-consumption fix so a block cannot remain stuck as bedrock after
 * its charge is exhausted.
 */
class PoweredBedrock(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    private val energyPerTick: Int,
) : SlimefunItem(itemGroup, itemStack, recipeType, recipe), EnergyNetComponent, EnergyTickingConsumer {

    init {
        addItemHandler(object : BlockTicker() {
            private var tickCount = 0L

            override fun isSynchronized() = true

            override fun uniqueTick() {
                tickCount++
            }

            override fun tick(b: Block, item: SlimefunItem, data: Config) {
                // Preserve the final IE1 cadence: one ticker pass out of every eight is skipped.
                if (tickCount % 8L == 0L) return

                val location = b.location
                val charge = getCharge(location, data)

                if (charge < energyPerTick) {
                    if (b.type != Material.NETHERITE_BLOCK) {
                        // IE1 v140 fix: revert first, then stop. Do not charge again during
                        // the state transition or the block can get stuck powered.
                        b.type = Material.NETHERITE_BLOCK
                        return
                    }
                } else if (b.type != Material.BEDROCK) {
                    b.type = Material.BEDROCK
                }

                // IE1 v140 moved this outside the state-change branch so powered bedrock
                // continuously consumes charge instead of only charging when it transforms.
                removeCharge(location, energyPerTick)
            }
        })
    }

    override fun getEnergyConsumptionPerTick() = energyPerTick

    override fun getEnergyComponentType() = EnergyNetComponentType.CONSUMER

    override fun getCapacity() = energyPerTick * 2

    companion object {
        const val ID = "IE_POWERED_BEDROCK"
        private const val ENERGY = 10_000
        private var registered = false

        fun register() {
            if (registered || SlimefunItem.getById(ID) != null) {
                registered = true
                return
            }

            val itemStack = SlimefunItemStack(
                ID,
                Material.NETHERITE_BLOCK,
                "&4Powered Bedrock",
                "&7When powered, transforms into bedrock",
                "&7Will revert once unpowered or broken",
            )

            val c = IEItems.COMPRESSED_COBBLESTONE_5
            val p = IEItems.MACHINE_PLATE
            val v = IEItems.VOID_INGOT
            val e = SlimefunItems.ENERGIZED_CAPACITOR
            val core = IEItems.INFINITY_MACHINE_CORE
            val circuit = IEItems.INFINITY_MACHINE_CIRCUIT

            val recipe = arrayOf<ItemStack?>(
                c, c, c, c, c, c,
                c, p, v, v, p, c,
                c, v, e, e, v, c,
                c, v, core, circuit, v, c,
                c, p, v, v, p, c,
                c, c, c, c, c, c,
            )

            PoweredBedrock(
                IEItemGroups.MACHINES,
                itemStack,
                IERecipeTypes.INFINITY_WORKBENCH,
                recipe,
                ENERGY,
            ).register(InfinityExpansion2.instance)

            registered = true
        }
    }
}
