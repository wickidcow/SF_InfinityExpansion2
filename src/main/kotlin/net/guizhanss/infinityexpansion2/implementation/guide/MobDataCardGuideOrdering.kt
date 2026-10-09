package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.configuration.ConfigurationSection
import java.util.Locale

internal enum class MobDataCardGuideSortMode {
    ALPHABETICAL,
    CONFIG;

    companion object {
        fun parse(value: String?): MobDataCardGuideSortMode? = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "alphabetical" -> ALPHABETICAL
            "config", "yaml" -> CONFIG
            else -> null
        }
    }
}

/** The configured sequence comes from the files, independently of the live guide list. */
internal data class MobDataCardGuideOrdering(
    val mode: MobDataCardGuideSortMode = MobDataCardGuideSortMode.ALPHABETICAL,
    val configuredIds: List<String> = emptyList(),
) {
    val comparator: Comparator<MobDataCardGuideKey>
        get() = when (mode) {
            MobDataCardGuideSortMode.ALPHABETICAL -> naturalOrder()
            MobDataCardGuideSortMode.CONFIG -> {
                val ranks = configuredIds.map { it.trim().lowercase(Locale.ROOT) }
                    .distinct().withIndex().associate { it.value to it.index }
                compareBy<MobDataCardGuideKey> { ranks[it.id] ?: Int.MAX_VALUE }.thenBy { it.id }
            }
        }

    companion object {
        /**
         * Historical/custom keys take precedence, including disabled cards. Modern
         * defaults follow in file order; an overridden key must not get a second rank.
         */
        fun configuredSequence(historical: ConfigurationSection, modern: ConfigurationSection): List<String> {
            val historicalIds = historical.getKeys(false)
            return historicalIds.toList() + modern.getKeys(false).filterNot { it in historicalIds }
        }
    }
}
