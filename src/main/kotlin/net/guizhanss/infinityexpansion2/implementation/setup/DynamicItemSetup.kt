package net.guizhanss.infinityexpansion2.implementation.setup

import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.debug.DebugCase
import net.guizhanss.infinityexpansion2.implementation.items.machines.PoweredBedrock
import net.guizhanss.infinityexpansion2.implementation.items.tools.Oscillator
import net.guizhanss.infinityexpansion2.utils.Debug
import net.guizhanss.infinityexpansion2.utils.items.toItemStack
import java.util.logging.Level

/**
 * Registers IE2 items whose ids are derived from configuration at runtime, plus restored
 * legacy items that must exist before the IE1 compatibility aliases are installed.
 *
 * These items must be available during normal plugin enable so addons which
 * consume IE2 ids (for example RSC content packs) can resolve them while they
 * are loading. A second pass is still run when Slimefun finalizes its registry
 * so custom oscillator/card definitions that depend on later addons can be
 * picked up safely.
 */
internal object DynamicItemSetup {
    private val registeredOscillators = mutableSetOf<String>()

    fun loadAvailable(finalPass: Boolean = false) {
        PoweredBedrock.register()
        loadQuarryOscillators(finalPass)
        MobSimulationSetup.loadAvailable(finalPass)
    }

    private fun loadQuarryOscillators(finalPass: Boolean) {
        InfinityExpansion2.log(Level.INFO, "Loading available oscillillators...")

        InfinityExpansion2.configService.quarryOscillators.value.forEach { (id, chance) ->
            if (id in registeredOscillators) return@forEach

            Debug.log(DebugCase.OSCILLATOR, "Loading oscillator: $id")

            val target = id.toItemStack()
            if (target.isAir()) {
                if (finalPass) {
                    InfinityExpansion2.log(
                        Level.WARNING,
                        "Skipping oscillator $id because its target item is not registered."
                    )
                }
                return@forEach
            }

            if (chance <= 0 || chance > 1) {
                if (finalPass) {
                    InfinityExpansion2.log(
                        Level.WARNING,
                        "Skipping oscillator $id because its chance must be greater than 0 and at most 1."
                    )
                }
                return@forEach
            }

            Debug.log(DebugCase.OSCILLATOR, "Registering...")
            Oscillator.register(id)
            registeredOscillators += id
        }
    }
}
