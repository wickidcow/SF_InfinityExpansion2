package net.guizhanss.infinityexpansion2.core.config

import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

/**
 * Accesses the standalone machine-settings.yml file.
 *
 * The file only overrides built-in values. Any missing path falls back to the
 * values compiled into IE2 so an older machine-settings.yml remains safe after
 * an addon update.
 */
object MachineSettings {

    private val config
        get() = InfinityExpansion2.configService.machineSettingsConfig.configuration

    fun energyPerTick(family: String, defaultOutputInterval: Int, defaultValue: Int): Int {
        val path = "$family.tiers.${tierKey(defaultOutputInterval)}.energy-per-tick"
        return config.getInt(path, defaultValue).coerceIn(1, 1_000_000_000)
    }

    fun outputInterval(family: String, defaultOutputInterval: Int): Int {
        val path = "$family.tiers.${tierKey(defaultOutputInterval)}.output-interval"
        return config.getInt(path, defaultOutputInterval).coerceIn(1, 3600)
    }

    fun configuredOutputs(
        family: String,
        inputName: String,
        defaults: Array<out ItemStack>,
    ): Array<ItemStack>? {
        val basePath = "$family.recipes.$inputName"
        if (!config.getBoolean("$basePath.enabled", true)) {
            return null
        }

        val outputSection = config.getConfigurationSection("$basePath.outputs")
        val result = mutableListOf<ItemStack>()
        val defaultMaterialNames = mutableSetOf<String>()

        defaults.forEach { defaultStack ->
            val materialName = defaultStack.type.name
            defaultMaterialNames += materialName
            val amount = outputSection?.getConfiguredAmount(materialName, defaultStack.amount) ?: defaultStack.amount
            if (amount > 0) {
                result += defaultStack.clone().apply { this.amount = amount }
            }
        }

        // Permit additional vanilla outputs without requiring a new addon build.
        outputSection?.getKeys(false)
            ?.filterNot { it in defaultMaterialNames }
            ?.forEach { materialName ->
                val amount = outputSection.getInt(materialName, 0).coerceIn(0, 64)
                if (amount <= 0) return@forEach

                val material = Material.matchMaterial(materialName)
                if (material == null) {
                    warn("Unknown output material '$materialName' in $basePath.outputs")
                    return@forEach
                }
                result += ItemStack(material, amount)
            }

        return result.toTypedArray()
    }

    fun customRecipes(family: String): List<Pair<ItemStack, Array<ItemStack>>> {
        val section = config.getConfigurationSection("$family.custom-recipes") ?: return emptyList()

        return section.getKeys(false).mapNotNull { inputName ->
            val recipeSection = section.getConfigurationSection(inputName) ?: return@mapNotNull null
            if (!recipeSection.getBoolean("enabled", false)) return@mapNotNull null

            val inputMaterial = Material.matchMaterial(inputName)
            if (inputMaterial == null) {
                warn("Unknown custom recipe input material '$inputName' in $family.custom-recipes")
                return@mapNotNull null
            }

            val outputSection = recipeSection.getConfigurationSection("outputs")
            if (outputSection == null) {
                warn("Custom recipe '$family.custom-recipes.$inputName' has no outputs section")
                return@mapNotNull null
            }

            val outputs = outputSection.getKeys(false).mapNotNull output@{ materialName ->
                val amount = outputSection.getInt(materialName, 0).coerceIn(0, 64)
                if (amount <= 0) return@output null

                val material = Material.matchMaterial(materialName)
                if (material == null) {
                    warn("Unknown output material '$materialName' in $family.custom-recipes.$inputName.outputs")
                    return@output null
                }

                ItemStack(material, amount)
            }.toTypedArray()

            if (outputs.isEmpty()) {
                warn("Custom recipe '$family.custom-recipes.$inputName' has no valid outputs")
                return@mapNotNull null
            }

            ItemStack(inputMaterial) to outputs
        }
    }

    private fun ConfigurationSection.getConfiguredAmount(path: String, defaultValue: Int): Int =
        getInt(path, defaultValue).coerceIn(0, 64)

    private fun tierKey(defaultOutputInterval: Int) = when (defaultOutputInterval) {
        300 -> "tier-1"
        60 -> "tier-2"
        30 -> "tier-3"
        10 -> "tier-4"
        else -> "default-$defaultOutputInterval"
    }

    private fun warn(message: String) {
        InfinityExpansion2.log(Level.WARNING, "machine-settings.yml: $message")
    }
}
