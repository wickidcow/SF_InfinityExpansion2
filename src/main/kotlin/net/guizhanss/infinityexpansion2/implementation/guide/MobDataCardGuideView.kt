package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection

/** Navigation choices are separate from card registration and machine availability. */
internal enum class MobDataCardGuideCategory(val group: MobDataCardGuideGroup?) {
    ALL(null),
    PASSIVE(MobDataCardGuideGroup.PASSIVE),
    NEUTRAL(MobDataCardGuideGroup.NEUTRAL),
    HOSTILE(MobDataCardGuideGroup.HOSTILE),
    BOSS(MobDataCardGuideGroup.BOSS);

    val configKey: String get() = group?.configKey ?: "all"
    val displayName: String get() = group?.displayName ?: "All Mobs"
    val material: Material get() = group?.material ?: Material.BOOK

    /** Read the live source afresh; no card is registered again or assigned to a second owner. */
    fun <T : Any> cards(items: List<T>, keyOf: (T) -> MobDataCardGuideKey?): List<T> =
        cards(items, MobDataCardGuideOrdering(), keyOf)

    fun <T : Any> cards(
        items: List<T>, ordering: MobDataCardGuideOrdering, keyOf: (T) -> MobDataCardGuideKey?,
    ): List<T> {
        val comparator = ordering.comparator
        return items.mapNotNull { item ->
            keyOf(item)?.takeIf { group == null || it.group == group }?.let { item to it }
        }.sortedWith { left, right -> comparator.compare(left.second, right.second) }.map { it.first }
    }

    companion object {
        fun enabledIn(section: ConfigurationSection?): Set<MobDataCardGuideCategory> =
            entries.filterTo(linkedSetOf()) { section?.getBoolean(it.configKey, true) ?: true }
    }
}

/** A history page may outlive a change in the visible cards or category configuration. */
internal data class MobDataCardGuidePage<T>(val number: Int, val total: Int, val items: List<T>) {
    companion object {
        fun <T> of(items: List<T>, requestedPage: Int, pageSize: Int): MobDataCardGuidePage<T> {
            require(pageSize > 0)
            val pages = if (items.isEmpty()) 1 else (items.size - 1) / pageSize + 1
            val page = requestedPage.coerceIn(1, pages)
            val start = (page - 1) * pageSize
            return MobDataCardGuidePage(page, pages, items.subList(start, minOf(start + pageSize, items.size)))
        }
    }
}
