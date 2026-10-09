package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.Material
import java.util.Locale

/** Default guide categories and the material used by each category's data cards. */
internal enum class MobDataCardGuideGroup(
    val configKey: String,
    val displayName: String,
    val material: Material,
) {
    PASSIVE("passive", "Passive Mobs", Material.IRON_CHESTPLATE),
    NEUTRAL("neutral", "Neutral Mobs", Material.COPPER_CHESTPLATE),
    HOSTILE("hostile", "Hostile Mobs", Material.DIAMOND_CHESTPLATE),
    BOSS("boss", "Boss Mobs", Material.NETHERITE_CHESTPLATE),
}

/**
 * Groups every bundled card and the known DynaTech integrations for guide display.
 *
 * Neutral includes mobs that retaliate or become aggressive under particular
 * conditions, including spiders and piglins. Mounts are grouped
 * by their own behavior without a hostile rider. Boss is a practical guide tier
 * that also includes the Warden and Elder Guardian. Unknown addon cards default
 * to Hostile until their category is explicitly configured.
 */
internal object MobDataCardGuideGroups {
    private val groupsById: Map<String, MobDataCardGuideGroup> = buildMap {
        listOf(
            "allay",
            "armadillo",
            "axolotl",
            "bat",
            "camel",
            "camel_husk",
            "cat",
            "chicken",
            "cod",
            "copper_golem",
            "cow",
            "donkey",
            "fox",
            "frog",
            "glow_squid",
            "happy_ghast",
            "horse",
            "mooshroom",
            "mule",
            "ocelot",
            "parrot",
            "pig",
            "pufferfish",
            "rabbit",
            "salmon",
            "sheep",
            "skeleton_horse",
            "sniffer",
            "snow_golem",
            "squid",
            "strider",
            "sulfur_cube",
            "tadpole",
            "tropical_fish",
            "turtle",
            "villager",
            "wandering_trader",
            "zombie_horse"
        ).forEach { put(it, MobDataCardGuideGroup.PASSIVE) }

        listOf(
            "bee",
            "cave_spider",
            "dolphin",
            "enderman",
            "goat",
            "iron_golem",
            "llama",
            "nautilus",
            "panda",
            "piglin",
            "polar_bear",
            "spider",
            "trader_llama",
            "wolf",
            "zombie_nautilus",
            "zombified_piglin"
        ).forEach { put(it, MobDataCardGuideGroup.NEUTRAL) }

        listOf(
            "blaze",
            "bogged",
            "breeze",
            "creaking",
            "creeper",
            "drowned",
            "dynatech_phantom",
            "dynatech_vex",
            "endermite",
            "evoker",
            "ghast",
            "guardian",
            "hoglin",
            "husk",
            "magma_cube",
            "parched",
            "phantom",
            "piglin_brute",
            "pillager",
            "ravager",
            "shulker",
            "silverfish",
            "skeleton",
            "slime",
            "stray",
            "vex",
            "vindicator",
            "witch",
            "wither_skeleton",
            "zoglin",
            "zombie",
            "zombie_villager"
        ).forEach { put(it, MobDataCardGuideGroup.HOSTILE) }

        listOf(
            "elder_guardian",
            "ender_dragon",
            "warden",
            "wither"
        ).forEach { put(it, MobDataCardGuideGroup.BOSS) }
    }

    internal val knownIds: Set<String> = groupsById.keys

    fun defaultGroup(id: String): MobDataCardGuideGroup =
        groupsById[id.trim().lowercase(Locale.ROOT)] ?: MobDataCardGuideGroup.HOSTILE

    fun parse(value: String?): MobDataCardGuideGroup? = when (value?.trim()?.lowercase(Locale.ROOT)) {
        "passive", "friendly" -> MobDataCardGuideGroup.PASSIVE
        "neutral" -> MobDataCardGuideGroup.NEUTRAL
        "hostile", "aggressive" -> MobDataCardGuideGroup.HOSTILE
        "boss", "bosses" -> MobDataCardGuideGroup.BOSS
        else -> null
    }
}
