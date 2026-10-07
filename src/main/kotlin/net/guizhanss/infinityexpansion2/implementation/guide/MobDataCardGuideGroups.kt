package net.guizhanss.infinityexpansion2.implementation.guide

import java.util.Locale

/** The guide's navigation order, rather than Minecraft's entity classifications. */
internal enum class MobDataCardGuideGroup {
    FRIENDLY,
    PASSIVE,
    AGGRESSIVE
}

/**
 * Groups every bundled card and the known DynaTech integrations for guide display.
 *
 * Friendly includes helpful mobs, companions and dedicated mounts. Passive holds
 * the other animals, including wild animals that can retaliate. Aggressive holds
 * combat-oriented mobs and is the fallback for unclassified addon cards.
 */
internal object MobDataCardGuideGroups {
    private val groupsById: Map<String, MobDataCardGuideGroup> = buildMap {
        listOf(
            "allay",
            "axolotl",
            "bee",
            "camel",
            "camel_husk",
            "cat",
            "copper_golem",
            "dolphin",
            "donkey",
            "happy_ghast",
            "horse",
            "iron_golem",
            "llama",
            "mule",
            "nautilus",
            "parrot",
            "skeleton_horse",
            "sniffer",
            "snow_golem",
            "strider",
            "sulfur_cube",
            "trader_llama",
            "villager",
            "wandering_trader",
            "wolf",
            "zombie_horse",
            "zombie_nautilus"
        ).forEach { put(it, MobDataCardGuideGroup.FRIENDLY) }

        listOf(
            "armadillo",
            "bat",
            "chicken",
            "cod",
            "cow",
            "fox",
            "frog",
            "glow_squid",
            "goat",
            "mooshroom",
            "ocelot",
            "panda",
            "pig",
            "polar_bear",
            "pufferfish",
            "rabbit",
            "salmon",
            "sheep",
            "squid",
            "tadpole",
            "tropical_fish",
            "turtle"
        ).forEach { put(it, MobDataCardGuideGroup.PASSIVE) }

        listOf(
            "blaze",
            "bogged",
            "breeze",
            "cave_spider",
            "creaking",
            "creeper",
            "drowned",
            "dynatech_phantom",
            "dynatech_vex",
            "elder_guardian",
            "ender_dragon",
            "enderman",
            "endermite",
            "evoker",
            "ghast",
            "guardian",
            "hoglin",
            "husk",
            "magma_cube",
            "parched",
            "phantom",
            "piglin",
            "piglin_brute",
            "pillager",
            "ravager",
            "shulker",
            "silverfish",
            "skeleton",
            "slime",
            "spider",
            "stray",
            "vex",
            "vindicator",
            "warden",
            "witch",
            "wither",
            "wither_skeleton",
            "zoglin",
            "zombie",
            "zombie_villager",
            "zombified_piglin"
        ).forEach { put(it, MobDataCardGuideGroup.AGGRESSIVE) }
    }

    internal val knownIds: Set<String> = groupsById.keys

    fun defaultGroup(id: String): MobDataCardGuideGroup =
        groupsById[id.trim().lowercase(Locale.ROOT)] ?: MobDataCardGuideGroup.AGGRESSIVE

    fun parse(value: String?): MobDataCardGuideGroup? = when (value?.trim()?.lowercase(Locale.ROOT)) {
        "friendly" -> MobDataCardGuideGroup.FRIENDLY
        "passive" -> MobDataCardGuideGroup.PASSIVE
        "aggressive" -> MobDataCardGuideGroup.AGGRESSIVE
        else -> null
    }
}
