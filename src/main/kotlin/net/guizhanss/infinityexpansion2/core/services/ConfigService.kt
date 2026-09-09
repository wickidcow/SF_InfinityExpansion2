package net.guizhanss.infinityexpansion2.core.services

import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config
import net.guizhanss.guizhanlib.kt.minecraft.extensions.loadDoubleMap
import net.guizhanss.guizhanlib.kt.minecraft.extensions.loadEnchantmentKeyMap
import net.guizhanss.guizhanlib.kt.minecraft.extensions.loadEnumKeyMap
import net.guizhanss.guizhanlib.kt.minecraft.extensions.loadSectionMap
import net.guizhanss.guizhanlib.kt.slimefun.config.ConfigField
import net.guizhanss.guizhanlib.kt.slimefun.config.addonConfig
import net.guizhanss.guizhanlib.kt.slimefun.config.migration.configMigrations
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.config.QuarryPool
import net.guizhanss.infinityexpansion2.core.config.ResourceSynthesizerRecipe
import net.guizhanss.infinityexpansion2.utils.bukkitext.getAsSerializable
import net.guizhanss.infinityexpansion2.utils.bukkitext.getAsSerializableList
import org.bukkit.World.Environment
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment

class ConfigService(plugin: InfinityExpansion2) {

    lateinit var autoUpdate: ConfigField<Boolean>
    lateinit var lang: ConfigField<String>
    lateinit var enableResearches: ConfigField<Boolean>

    // IE1 migration / doctor options
    lateinit var migrationEnabled: ConfigField<Boolean>
    lateinit var migrationAutoBlocks: ConfigField<Boolean>
    lateinit var migrationAutoItems: ConfigField<Boolean>
    lateinit var migrationRefreshModernItems: ConfigField<Boolean>

    // debug options
    lateinit var debugEnabled: ConfigField<Boolean>
    lateinit var debugCases: ConfigField<List<String>>

    // singularity options
    lateinit var singularityCostMultiplier: ConfigField<Double>

    // gear transformer options
    lateinit var gearTransformerAllowSfItems: ConfigField<Boolean>

    // resource synthesizer options
    lateinit var resourceSynthesizerRecipes: ConfigField<List<ResourceSynthesizerRecipe>>

    // mob simulation options
    lateinit var mobSimInterval: ConfigField<Int>
    lateinit var mobSimAllowStackedCard: ConfigField<Boolean>
    lateinit var mobSimChargeCardEnergy: ConfigField<Boolean>
    lateinit var mobSimExpMultiplier: ConfigField<Double>
    lateinit var mobSimLegacyOutput: ConfigField<Boolean>

    // storage options
    lateinit var storageEnableSigns: ConfigField<Boolean>
    lateinit var storageSignUpdateInterval: ConfigField<Int>
    lateinit var storageEnableHolograms: ConfigField<Boolean>
    lateinit var storageHologramUpdateInterval: ConfigField<Int>

    // quarry options
    lateinit var quarryInterval: ConfigField<Int>
    lateinit var quarryPools: ConfigField<Map<Environment, QuarryPool>>
    lateinit var quarryOscillators: ConfigField<Map<String, Double>>

    // advanced anvil options
    lateinit var advancedAnvilMaxLevels: ConfigField<Map<Enchantment, Int>>

    // infinity gear section
    lateinit var infinityGear: ConfigField<Map<String, ConfigurationSection>>

    private val configMigrations = configMigrations {
        add(1, 2) {
            move("balance.enable-researches", "enable-researches")
            move("balance.singularity-cost-multiplier", "singularity.cost-multiplier")
            move("balance.allow-sf-item-transform", "gear-transformer.allow-sf-items")
        }
        add(2, 3) {
            move("debug", "debug.enabled")
        }
    }

    private val config = addonConfig(plugin, "config.yml", configMigrations) {
        autoUpdate = boolean("auto-update", false)
        lang = string("lang", InfinityExpansion2.DEFAULT_LANG)
        enableResearches = boolean("enable-researches", false)
        migrationEnabled = boolean("migration.enabled", true)
        migrationAutoBlocks = boolean("migration.auto-migrate-blocks", false)
        migrationAutoItems = boolean("migration.auto-migrate-items", false)
        migrationRefreshModernItems = boolean("migration.refresh-modern-items-on-join", true)
        debugEnabled = boolean("debug.enabled", false)
        debugCases = custom { it.getStringList("debug.cases") }
        singularityCostMultiplier = double("singularity.cost-multiplier", 1.0, 0.0, 1000.0)
        gearTransformerAllowSfItems = boolean("gear-transformer.allow-sf-items", false)
        resourceSynthesizerRecipes =
            custom { it.getMapList("resource-synthesizer.recipes").getAsSerializableList<ResourceSynthesizerRecipe>() }
        mobSimInterval = int("mob-simulation.output-interval", 20, 1, 3600)
        mobSimAllowStackedCard = boolean("mob-simulation.allow-stacked-card", false)
        mobSimChargeCardEnergy = boolean("mob-simulation.charge-card-energy", false)
        mobSimExpMultiplier = double("mob-simulation.exp-multiplier", 1.0, 0.0, 1000.0)
        mobSimLegacyOutput = boolean("mob-simulation.legacy-output", false)
        storageEnableSigns = boolean("storage.enable-signs", false)
        storageSignUpdateInterval = int("storage.sign-update-interval", 20, 1, 3600)
        storageEnableHolograms = boolean("storage.enable-holograms", false)
        storageHologramUpdateInterval = int("storage.hologram-update-interval", 20, 1, 3600)
        quarryInterval = int("quarry.output-interval", 10, 1, 3600)
        quarryOscillators = custom { it.getConfigurationSection("quarry.oscillators").loadDoubleMap() }
        quarryPools = custom {
            it.getConfigurationSection("quarry.pools")
                .loadEnumKeyMap<Environment, QuarryPool>({ obj -> (obj as ConfigurationSection).getAsSerializable() })
        }
        advancedAnvilMaxLevels = custom {
            it.getConfigurationSection("advanced-anvil.max-levels").loadEnchantmentKeyMap()
        }
        infinityGear = custom {
            it.getConfigurationSection("infinity-gear").loadSectionMap()
        }
    }

    internal val mobSimConfig = Config(plugin, "mob-simulation.yml")
    // Kept separate from the historical file so existing servers receive new default cards
    // without overwriting or re-serializing their customized mob-simulation.yml.
    internal val modernMobSimConfig = Config(plugin, "mob-simulation-modern.yml")
    internal val machineSettingsConfig = Config(plugin, "machine-settings.yml")

    init {
        if (!mobSimConfig.file.exists()) {
            plugin.saveResource("mob-simulation.yml", false)
        }
        if (!modernMobSimConfig.file.exists()) {
            plugin.saveResource("mob-simulation-modern.yml", false)
        }
        if (!machineSettingsConfig.file.exists()) {
            plugin.saveResource("machine-settings.yml", false)
        }
        reload()
        migrateModernMobSimulationTextures()
    }

    /**
     * The first Legacy modern-card release used spawn eggs as card textures. Preserve arbitrary
     * administrator texture choices, but upgrade the exact shipped spawn-egg defaults to IE2's
     * historical armor-based difficulty language.
     */
    private fun migrateModernMobSimulationTextures() {
        val textureMigrations = mapOf(
            "goat" to ("GOAT_SPAWN_EGG" to "IRON_CHESTPLATE"),
            "frog" to ("FROG_SPAWN_EGG" to "IRON_CHESTPLATE"),
            "sniffer" to ("SNIFFER_SPAWN_EGG" to "IRON_CHESTPLATE"),
            "armadillo" to ("ARMADILLO_SPAWN_EGG" to "IRON_CHESTPLATE"),
            "breeze" to ("BREEZE_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "warden" to ("WARDEN_SPAWN_EGG" to "NETHERITE_CHESTPLATE"),
            "creaking" to ("CREAKING_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "shulker" to ("SHULKER_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "phantom" to ("PHANTOM_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "drowned" to ("DROWNED_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "hoglin" to ("HOGLIN_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "zombified_piglin" to ("ZOMBIFIED_PIGLIN_SPAWN_EGG" to "DIAMOND_CHESTPLATE"),
            "rabbit" to ("RABBIT_SPAWN_EGG" to "IRON_CHESTPLATE")
        )

        var changed = false
        textureMigrations.forEach { (id, textures) ->
            val path = "$id.texture"
            if (modernMobSimConfig.configuration.getString(path) == textures.first) {
                modernMobSimConfig.configuration.set(path, textures.second)
                changed = true
            }
        }

        if (changed) {
            modernMobSimConfig.save()
        }
    }

    fun reload() {
        config.reload()
        mobSimConfig.reload()
        modernMobSimConfig.reload()
        machineSettingsConfig.reload()
    }
}
