package net.guizhanss.infinityexpansion2.implementation.guide

import java.util.Locale

/** Presentation metadata only; it never changes a card's identity or stored item. */
internal data class MobDataCardGuideKey(
    val group: MobDataCardGuideGroup,
    val name: String,
    val id: String,
) : Comparable<MobDataCardGuideKey> {
    override fun compareTo(other: MobDataCardGuideKey): Int =
        compareValuesBy(this, other, { it.group.ordinal }, { it.name }, { it.id })

    companion object {
        private val colors = Regex("(?i)(?:[&§]x(?:[&§][0-9a-f]){6}|[&§]#[0-9a-f]{6}|[&§][0-9a-fk-or])")

        fun of(id: String, name: String, group: MobDataCardGuideGroup? = null): MobDataCardGuideKey {
            val normalizedId = id.trim().lowercase(Locale.ROOT)
            val plainName = colors.replace(name, "").trim().ifEmpty { normalizedId.replace('_', ' ') }
            return MobDataCardGuideKey(
                group ?: MobDataCardGuideGroups.defaultGroup(normalizedId),
                plainName.lowercase(Locale.ROOT),
                normalizedId,
            )
        }
    }
}

/**
 * Reorders only card positions in the existing mutable guide list. Registration and
 * infuser recipe lists are separate and are never touched. Keep the same list object
 * so addons using ItemGroup.getItems().add/remove retain the normal live-list API.
 */
internal class MobDataCardGuideOrder<T : Any>(private val keyOf: (T) -> MobDataCardGuideKey?) {
    private var lastItems: List<T> = emptyList()
    private var lastKeys: List<MobDataCardGuideKey?> = emptyList()

    @Synchronized
    fun order(items: MutableList<T>): MutableList<T> {
        val keys = items.map(keyOf)
        if (items.size == lastItems.size && keys == lastKeys &&
            items.indices.all { items[it] === lastItems[it] }) {
            return items
        }

        val cards = items.indices.mapNotNull { index ->
            keys[index]?.let { key -> items[index] to key }
        }.sortedBy { it.second }.iterator()

        items.indices.forEach { index ->
            if (keys[index] != null) items[index] = cards.next().first
        }

        lastItems = items.toList()
        lastKeys = lastItems.map(keyOf)
        return items
    }
}
