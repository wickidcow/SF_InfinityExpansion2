package net.guizhanss.infinityexpansion2.implementation.guide

import org.bukkit.inventory.ItemStack

/** Category materials are defaults only; an administrator's explicit artwork always wins. */
internal object MobDataCardAppearance {
    fun texture(configuredTexture: ItemStack?, group: MobDataCardGuideGroup): ItemStack =
        configuredTexture?.clone() ?: ItemStack(group.material)
}
