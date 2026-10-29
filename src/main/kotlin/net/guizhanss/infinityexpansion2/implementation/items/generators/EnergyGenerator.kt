package net.guizhanss.infinityexpansion2.implementation.items.generators

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker
import net.guizhanss.guizhanlib.common.utils.StringUtil
import net.guizhanss.guizhanlib.kt.slimefun.utils.getSfItem
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.debug.DebugCase
import net.guizhanss.infinityexpansion2.core.items.attributes.CustomTickRateMachine
import net.guizhanss.infinityexpansion2.core.items.attributes.CustomWikiItem
import net.guizhanss.infinityexpansion2.core.items.attributes.EnergyProducer
import net.guizhanss.infinityexpansion2.core.items.attributes.InformationalRecipeDisplayItem
import net.guizhanss.infinityexpansion2.utils.Debug
import net.guizhanss.infinityexpansion2.utils.constant.Strings
import net.guizhanss.infinityexpansion2.utils.items.GuiItems
import net.guizhanss.infinityexpansion2.utils.slimefunext.powerPerTickToSecond
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

class EnergyGenerator(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    private val type: GeneratorType,
    defaultProduction: Int,
) : SlimefunItem(itemGroup, itemStack, recipeType, recipe), EnergyNetProvider, CustomTickRateMachine, EnergyProducer,
    InformationalRecipeDisplayItem, CustomWikiItem {

    override val wikiUrl: String
        get() = "generators/" + when (type) {
            GeneratorType.HYDROELECTRIC -> "hydro"
            GeneratorType.GEOTHERMAL -> "geothermal"
            GeneratorType.SOLAR -> "solar"
            GeneratorType.LUNAR -> "void"
            GeneratorType.INFINITY -> "infinity"
        }

    private val tickRateSetting = IntRangeSetting(this, "tick-rate", 1, 1, 3600)
    private val energyProductionSetting =
        IntRangeSetting(this, "energy-production", 1, defaultProduction, 16_777_215) // 2 ^ 24 - 1

    var tickCount: Int = 0
        private set

    init {
        addItemSetting(tickRateSetting, energyProductionSetting)
        addItemHandler(onBlockClick(), onBlockTick())
    }

    override fun getCapacity() =
        (energyProductionSetting.value.toLong() * 128L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    override fun getEnergyProduction() = energyProductionSetting.value

    override fun getCustomTickRate() = tickRateSetting.value

    // Preserve the block-only contract: this migration does not enable universal storage.
    override fun getGeneratedOutput(l: Location, data: ASlimefunDataContainer): Int {
        check(data is SlimefunBlockData) { "You must implement getGeneratedOutput for ${data.javaClass.name}" }
        return generateAt(l)
    }

    override fun getGeneratedOutputLong(l: Location, data: ASlimefunDataContainer): Long =
        getGeneratedOutput(l, data).toLong()

    // Old compiled callers still resolve the exact Config descriptor, without recursion.
    @Deprecated("Compatibility bridge; use getGeneratedOutputLong(Location, ASlimefunDataContainer)")
    override fun getGeneratedOutput(l: Location, data: me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config): Int =
        generateAt(l)

    private fun generateAt(l: Location): Int {
        val sfItem = l.getSfItem()
        if (sfItem !is EnergyGenerator) {
            Debug.log(DebugCase.ENERGY_GENERATOR, "Generator instance is invalid at: $l")
            return 0
        }
        if (sfItem.tickCount % getCustomTickRate() != 0) return 0
        return type.generate(l.world!!, l.block, sfItem, getEnergyProduction())
    }

    private fun onBlockTick() = object : BlockTicker() {
        override fun tick(b: Block, item: SlimefunItem, data: ASlimefunDataContainer) {
            check(data is SlimefunBlockData) { "BlockTicker is non-universal but item storage is universal" }
            // Generation remains in EnergyNetProvider, not this per-block callback.
        }

        override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
            // Concrete block callers must also avoid allocating a legacy Config wrapper.
        }

        @Deprecated("Compatibility bridge; use the storage-container ticker overload")
        override fun tick(b: Block, item: SlimefunItem, config: me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config) {
            // Retain the historical no-op callback for already compiled callers.
        }

        override fun uniqueTick() {
            tickCount++
        }

        override fun isSynchronized() = false
    }

    private fun onBlockClick() = BlockUseHandler { e ->
        if (e.clickedBlock.isEmpty) return@BlockUseHandler
        val sfItem = e.slimefunBlock.get()
        if (sfItem !is EnergyGenerator) return@BlockUseHandler
        val p = e.player
        val block = e.clickedBlock.get()
        val integration = InfinityExpansion2.integrationService

        p.sendMessage("", integration.getTranslatedItemName(p, sfItem.item))
        integration.sendMessage(p, "generators.type", StringUtil.humanize(type.name))
        integration.sendMessage(p, "generators.tick-rate", getCustomTickRate())

        val generation = type.generate(block.world, block, sfItem, getEnergyProduction())
        integration.sendMessage(
            p,
            "generators.working",
            if (generation > 0) Strings.CHECK else Strings.CROSS
        )
        if (generation > 0) {
            integration.sendMessage(
                p,
                "generators.production",
                generation,
                powerPerTickToSecond(generation, getCustomTickRate())
            )
        } else {
            integration.sendMessage(
                p,
                "generators.expected-production",
                getEnergyProduction(),
                powerPerTickToSecond(getEnergyProduction(), getCustomTickRate())
            )
        }
    }

    override fun getInfoItems() = listOf(
        GuiItems.tickRate(getCustomTickRate()),
        GuiItems.energyProductionPerTick(getEnergyProduction())
    )
}
