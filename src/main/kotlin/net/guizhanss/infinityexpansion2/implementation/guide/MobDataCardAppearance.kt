@file:Suppress("deprecation")

package net.guizhanss.infinityexpansion2.implementation.guide

import net.guizhanss.guizhanlib.common.utils.StringUtil
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

internal data class MobDataCardResolvedAppearance(
    val name: String,
    val texture: ItemStack,
    val repairedBlankName: Boolean,
    val replacedLegacyCarpet: Boolean,
)

/**
 * Keeps explicit owner artwork authoritative while repairing the narrow broken-card signature:
 * an explicitly blank mob name paired with a plain carpet texture.
 */
internal object MobDataCardAppearance {
    fun texture(configuredTexture: ItemStack?, group: MobDataCardGuideGroup): ItemStack =
        configuredTexture?.clone() ?: ItemStack(group.material)

    fun resolve(
        id: String,
        configuredName: String?,
        configuredTexture: ItemStack?,
        group: MobDataCardGuideGroup,
    ): MobDataCardResolvedAppearance {
        val visibleName = hasVisibleText(configuredName)
        val repairedBlankName = configuredName != null && !visibleName
        val replacedLegacyCarpet = repairedBlankName && configuredTexture?.type?.isCarpetLike() == true
        val name = if (visibleName) configuredName!! else "${ChatColor.BLUE}${StringUtil.humanize(id)}"
        val resolvedTexture = texture(if (replacedLegacyCarpet) null else configuredTexture, group)
        return MobDataCardResolvedAppearance(name, resolvedTexture, repairedBlankName, replacedLegacyCarpet)
    }

    /**
     * Refresh one physical card only when it has the stale IE2 signature seen on upgraded servers:
     * a carpet base with no visible first mob-label lore line. Named carpet artwork is preserved.
     *
     * The current template wins for canonical Slimefun/mob keys. Unknown addon PDC from the old
     * stack is copied exactly, and the original stack is never mutated.
     */
    fun refreshLegacyPhysical(source: ItemStack, current: ItemStack): ItemStack? {
        if (!source.type.isCarpetLike() || current.type.isCarpetLike()) return null
        if (source.itemMeta.lore?.firstOrNull()?.let(::hasVisibleText) == true) return null

        val repaired = current.clone()
        repaired.amount = source.amount

        if (source.hasItemMeta() && repaired.hasItemMeta()) {
            val sourceMeta = source.itemMeta
            val repairedMeta = repaired.itemMeta
            sourceMeta.persistentDataContainer.copyTo(repairedMeta.persistentDataContainer, false)
            check(repaired.setItemMeta(repairedMeta)) {
                "Mob Data Card rejected preserved legacy metadata"
            }
        }

        return repaired
    }

    private fun hasVisibleText(value: String?): Boolean {
        if (value == null) return false
        val translated = ChatColor.translateAlternateColorCodes('&', value)
        return !ChatColor.stripColor(translated).orEmpty().trim().isEmpty()
    }

    private fun org.bukkit.Material.isCarpetLike() = name.endsWith("_CARPET")
}
