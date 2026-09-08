@file:Suppress("deprecation")

package net.guizhanss.infinityexpansion2.implementation.setup

import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config
import net.guizhanss.guizhanlib.common.utils.StringUtil
import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.guizhanlib.kt.slimefun.items.toItem
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.api.InfinityExpansion2API
import net.guizhanss.infinityexpansion2.api.mobsim.MobDataCardProps
import net.guizhanss.infinityexpansion2.core.debug.DebugCase
import net.guizhanss.infinityexpansion2.implementation.IEItems
import net.guizhanss.infinityexpansion2.utils.Debug
import net.guizhanss.infinityexpansion2.utils.items.toItemStack
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.MusicInstrument
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MusicInstrumentMeta
import java.util.logging.Level

/**
 * Reads the mob simulation configs and registers data cards.
 *
 * Registration is intentionally idempotent. IE2 performs an early pass during
 * plugin enable so dependent addons can resolve generated card ids, followed by
 * a final pass after Slimefun addon registration to pick up recipes which rely
 * on items supplied by later addons.
 *
 * The historical mob-simulation.yml is loaded first. The Legacy fork then loads
 * mob-simulation-modern.yml, so an administrator can override any modern default
 * simply by defining the same card id in the historical/custom file.
 */
internal object MobSimulationSetup {
    private val registeredCards = mutableSetOf<String>()
    private val randomOneCards = mutableSetOf<String>()

    internal fun usesRandomOneDrops(id: String): Boolean = id in randomOneCards

    fun loadAvailable(finalPass: Boolean = false) {
        InfinityExpansion2.log(Level.INFO, "Loading available mob simulation data cards...")
        if (!InfinityExpansion2.debugService.isEnabled(DebugCase.MOB_SIMULATION)) {
            InfinityExpansion2.log(Level.INFO, "If you encounter any issues, enabling debug mode may help.")
        }

        loadConfig(InfinityExpansion2.configService.mobSimConfig, finalPass)
        loadConfig(InfinityExpansion2.configService.modernMobSimConfig, finalPass)
    }

    private fun loadConfig(cfg: Config, finalPass: Boolean) {
        cfg.keys.forEach cfgKey@{ key ->
            if (key in registeredCards) return@cfgKey

            val section = cfg.configuration.getConfigurationSection(key) ?: return@cfgKey

            // check for enabled
            if (!section.getBoolean("enabled", false)) return@cfgKey

            Debug.log(DebugCase.MOB_SIMULATION, "====================")
            Debug.log(DebugCase.MOB_SIMULATION, "Loading mob data card: $key")

            // load data
            val name = section.getString("name", "${ChatColor.BLUE}${StringUtil.humanize(key)}")!!
            val texture = section.getString("texture", "IRON_CHESTPLATE")!!.toItemStack()
            val energy = section.getInt("energy", 75).coerceIn(0, 1_000_000)
            val experience = section.getInt("experience").coerceIn(0, Int.MAX_VALUE)
            val dropMode = section.getString("drop-mode", "independent")!!.lowercase()
            val randomOne = when (dropMode) {
                "independent" -> false
                "random-one" -> true
                else -> {
                    InfinityExpansion2.log(
                        Level.WARNING,
                        "Unknown drop-mode '$dropMode' for $key; using independent rolls."
                    )
                    false
                }
            }

            Debug.log(
                DebugCase.MOB_SIMULATION,
                "name=$name, texture=$texture, energy=$energy, experience=$experience, dropMode=$dropMode"
            )

            // drops. Do not register an incomplete card during the early pass if
            // one of its configured drops belongs to an addon that loads later.
            val dropSections = section.getMapList("drops")
            val drops = dropSections.mapNotNull { it.getAsItemWithChance() }
            if (drops.size != dropSections.size) {
                if (finalPass) {
                    InfinityExpansion2.log(
                        Level.WARNING,
                        "Skipping mob data card $key because one or more configured drops could not be resolved."
                    )
                }
                return@cfgKey
            }
            Debug.log(DebugCase.MOB_SIMULATION, "drops=$drops")

            // recipe
            val recipePattern = section.getStringList("recipe.pattern")

            // validate pattern
            if (recipePattern.size != 3 || recipePattern.any { it.length != 3 }) {
                InfinityExpansion2.log(Level.WARNING, "Invalid recipe pattern for $key, must be in a 3x3 shape.")
                return@cfgKey
            }

            // find if there is 1 and only 1 X in the pattern
            if (recipePattern.sumOf { it.count { c -> c == 'X' } } != 1) {
                InfinityExpansion2.log(Level.WARNING, "Invalid recipe pattern for $key, must have only 1 'X' in the whole pattern.")
                return@cfgKey
            }

            // read the ingredients and build recipe
            val ingredients = mutableMapOf<Char, ItemStack>()
            val ingredientSection = section.getConfigurationSection("recipe.ingredient") ?: run {
                InfinityExpansion2.log(Level.WARNING, "No ingredients found for $key.")
                return@cfgKey
            }
            ingredientSection.getKeys(false).forEach { ingredient ->
                if (ingredient.length != 1) {
                    InfinityExpansion2.log(Level.WARNING, "Invalid ingredient \"$ingredient\" for $key: Must be a single character.")
                    return@cfgKey
                }
                val item = ingredientSection.getConfigurationSection(ingredient).getAsItem()
                if (item == null) {
                    if (finalPass) {
                        InfinityExpansion2.log(
                            Level.WARNING,
                            "Invalid ingredient \"$ingredient\" for $key: Item is still unavailable after addon registration."
                        )
                    }
                    return@cfgKey
                }
                ingredients[ingredient[0]] = item
            }
            Debug.log(DebugCase.MOB_SIMULATION, "pattern=$recipePattern, ingredients=$ingredients")

            val recipe = arrayOfNulls<ItemStack?>(9)
            for (i in recipePattern.indices) {
                for (j in recipePattern[i].indices) {
                    val char = recipePattern[i][j]
                    val index = i * 3 + j
                    if (char == ' ') continue
                    if (char == 'X') {
                        recipe[index] = IEItems.MOB_DATA_CARD_EMPTY.toItem()
                        continue
                    }

                    if (char !in ingredients) {
                        InfinityExpansion2.log(Level.WARNING, "Invalid recipe pattern for $key: Unknown ingredient \"$char\".")
                        return@cfgKey
                    } else {
                        recipe[index] = ingredients[char]
                    }
                }
            }

            // register the mob data card
            InfinityExpansion2API.registerMobDataCard(
                MobDataCardProps(key, name, texture, energy, experience, drops, recipe),
                InfinityExpansion2.instance
            )
            registeredCards += key
            if (randomOne) randomOneCards += key
        }
    }

    private fun Map<*, *>.getAsItem(): ItemStack? {
        val mat = this["item"] as? String ?: return null
        val amount = (this["amount"] as? Number)?.toInt() ?: 1

        val item = mat.toItemStack().let { if (it.isAir()) return null else it }.clone()
        item.amount = amount

        // Preserve the actual horn instrument so the Goat simulator can produce all eight
        // functional vanilla horn variants instead of eight identical plain GOAT_HORN stacks.
        val instrumentName = this["instrument"] as? String
        if (instrumentName != null) {
            if (item.type != Material.GOAT_HORN) return null
            val instrument = MusicInstrument.getByKey(
                NamespacedKey.minecraft(instrumentName.trim().lowercase())
            ) ?: return null
            val meta = item.itemMeta as? MusicInstrumentMeta ?: return null
            meta.instrument = instrument
            item.itemMeta = meta
        }

        return item
    }

    private fun ConfigurationSection?.getAsItem() = this?.getValues(false)?.getAsItem()

    private fun Map<*, *>.getAsItemWithChance(): Pair<ItemStack, Double>? {
        val item = this.getAsItem() ?: return null
        val chance = (this["chance"] as? Number)?.toDouble() ?: 1.0
        return item to chance
    }
}
