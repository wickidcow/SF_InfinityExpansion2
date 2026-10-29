package net.guizhanss.infinityexpansion2.implementation.guide

import net.guizhanss.guizhanlib.common.utils.StringUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import net.guizhanss.infinityexpansion2.utils.items.ModernText
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta

internal data class MobDataCardResolvedAppearance(
    val name: String,
    val texture: ItemStack,
    val repairedBlankName: Boolean,
    val replacedLegacyCarpet: Boolean,
)

/**
 * Uses the guide category for ordinary chestplate icons. Explicit custom models, trims,
 * dyed leather and non-chestplate artwork remain authoritative.
 */
internal object MobDataCardAppearance {
    fun texture(configuredTexture: ItemStack?, group: MobDataCardGuideGroup): ItemStack {
        if (configuredTexture == null) return ItemStack(group.material)
        return if (configuredTexture.type.isChestplate() && !hasCustomArtwork(configuredTexture.itemMeta)) {
            configuredTexture.withType(group.material)
        } else {
            configuredTexture.clone()
        }
    }

    fun resolve(
        id: String,
        configuredName: String?,
        configuredTexture: ItemStack?,
        group: MobDataCardGuideGroup,
    ): MobDataCardResolvedAppearance {
        val visibleName = hasVisibleText(configuredName)
        val repairedBlankName = configuredName != null && !visibleName
        val replacedLegacyCarpet = repairedBlankName && configuredTexture?.type?.isCarpetLike() == true &&
            !hasCustomArtwork(configuredTexture.itemMeta)
        val name = if (visibleName) configuredName!! else "\u00a79${StringUtil.humanize(id)}"
        val resolvedTexture = texture(if (replacedLegacyCarpet) null else configuredTexture, group)
        return MobDataCardResolvedAppearance(name, resolvedTexture, repairedBlankName, replacedLegacyCarpet)
    }

    /**
     * Refresh one physical card only when it has the stale IE2 signature seen on upgraded servers:
     * a carpet base with no visible first mob-label lore line. Named carpet artwork is preserved.
     *
     * Kept as the narrow legacy entry point; the original IDs and metadata are retained.
     */
    fun refreshLegacyPhysical(source: ItemStack, current: ItemStack): ItemStack? {
        if (!source.type.isCarpetLike() || current.type.isCarpetLike()) return null
        if (plain(source.itemMeta.lore()?.firstOrNull()).isNotBlank()) return null

        return refreshPhysical(source, current, "")
    }

    /**
     * Update only presentation on a card whose identity the caller has already validated.
     * Clone the source so unknown PDC, variant IDs, enchantments and player customizations survive.
     * No source or template is mutated, and a current card produces no replacement.
     */
    fun refreshPhysical(source: ItemStack, current: ItemStack, id: String): ItemStack? {
        if (source.type.isAir || current.type.isAir) return null
        val sourceMeta = source.itemMeta
        val currentMeta = current.itemMeta
        val sourceLore = sourceMeta.lore().orEmpty()
        val oldLabel = plain(sourceLore.firstOrNull())
        val oldCarpet = source.type.isCarpetLike() && oldLabel.isBlank()
        val changeMaterial = source.type != current.type && current.type.isChestplate() &&
            (source.type.isChestplate() || oldCarpet) && !hasCustomArtwork(sourceMeta)

        val oldName = plain(sourceMeta.displayName())
        val currentName = plain(currentMeta.displayName())
        val changeName = !sourceMeta.hasItemName() && !hasInteractiveText(sourceMeta.displayName()) &&
            (oldName.isBlank() || oldName.equals("Mob Data Card", true) || oldName == currentName) &&
            sourceMeta.displayName() != currentMeta.displayName()
        val currentLore = currentMeta.lore().orEmpty()
        val currentLabel = plain(currentLore.firstOrNull())
        val defaultLabel = StringUtil.humanize(id)
        val changeLabel = !hasInteractiveText(sourceLore.firstOrNull()) && currentLore.isNotEmpty() &&
            (oldLabel.isBlank() || oldLabel == currentLabel ||
            oldLabel.equals(defaultLabel, true)) && sourceLore.firstOrNull() != currentLore.first()
        // Chambers call this on their normal process path. Already-current cards need no clone.
        if (!changeMaterial && !changeName && !changeLabel) return null

        val updated = if (changeMaterial) source.withType(current.type) else source.clone()
        val meta = updated.itemMeta
        if (changeMaterial && updated.maxStackSize != source.maxStackSize) {
            // A carpet used to stack to 64. Changing its icon must not turn a saved stack
            // into overstacked armor or silently discard the original effective capacity.
            meta.setMaxStackSize(source.maxStackSize)
        }
        if (changeName) meta.displayName(currentMeta.displayName())
        if (changeLabel) {
            val lore = sourceLore.toMutableList()
            if (lore.isEmpty()) lore.add(currentLore.first()) else lore[0] = currentLore.first()
            meta.lore(lore)
        }

        check(updated.setItemMeta(meta)) { "Mob Data Card rejected preserved metadata" }
        check(updated.amount == source.amount &&
            updated.itemMeta.persistentDataContainer == sourceMeta.persistentDataContainer) {
            "Mob Data Card appearance refresh changed saved identity or quantity"
        }
        return updated
    }

    private fun hasCustomArtwork(meta: ItemMeta): Boolean =
        meta.hasItemModel() || meta.hasCustomModelDataComponent() ||
            (meta is ArmorMeta && meta.hasTrim()) ||
            (meta is LeatherArmorMeta && meta.color != Bukkit.getItemFactory().defaultLeatherColor)

    private fun plain(component: Component?): String =
        component?.let { PlainTextComponentSerializer.plainText().serialize(it).trim() }.orEmpty()

    private fun hasInteractiveText(component: Component?): Boolean = component != null &&
        (component.clickEvent() != null || component.hoverEvent() != null || component.insertion() != null ||
            component.children().any(::hasInteractiveText))

    private fun hasVisibleText(value: String?): Boolean = ModernText.hasVisibleLegacyText(value)

    private fun org.bukkit.Material.isCarpetLike() = name.endsWith("_CARPET")
    private fun org.bukkit.Material.isChestplate() = name.endsWith("_CHESTPLATE")
}
