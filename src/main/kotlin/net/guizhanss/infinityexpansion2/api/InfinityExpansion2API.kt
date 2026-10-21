package net.guizhanss.infinityexpansion2.api

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.api.mobsim.MobDataCardProps
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardAppearance
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideGroup
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideGroups
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobDataCard
import net.guizhanss.infinityexpansion2.utils.SlimefunReflection
import net.guizhanss.infinityexpansion2.utils.slimefunext.displayItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

/**
 * This class provides API endpoints to access InfinityExpansion2's features.
 * Use any other classes outside api package is not recommended, as they are subject to change.
 */
object InfinityExpansion2API {

    /**
     * Open the InfinityExpansion2 guide of the specific [SlimefunItem] for the [Player].
     */
    @JvmStatic
    fun openGuide(p: Player, sfItem: SlimefunItem, item: ItemStack? = null) {
        PlayerProfile.get(p) { profile ->
            displayItem(profile, sfItem, SlimefunGuideMode.SURVIVAL_MODE, item)
        }
    }

    /**
     * Register a mob data card.
     */
    @JvmStatic
    fun registerMobDataCard(props: MobDataCardProps, addon: SlimefunAddon) =
        registerMobDataCard(props, addon, null)

    internal fun registerMobDataCard(
        props: MobDataCardProps,
        addon: SlimefunAddon,
        guideGroup: MobDataCardGuideGroup?,
    ) {
        // create item
        val group = guideGroup ?: MobDataCardGuideGroups.defaultGroup(props.id)
        val texture = MobDataCardAppearance.texture(props.texture, group)
        val displayItem = MobDataCard.buildDisplayItem(props.id, props.name, texture)

        // If another addon already registered the same IE2 mob data card, keep the
        // existing registration instead of throwing a Slimefun IdConflictException.
        // This is expected for integrations such as DynaTech, which may provide a
        // card before IE2's final config pass sees the same id.
        val existing = SlimefunItem.getById(displayItem.itemId)
        if (existing is MobDataCard) {
            InfinityExpansion2.log(
                Level.INFO,
                "Skipping mob data card ${props.id}: Slimefun item id ${displayItem.itemId} is already registered by ${existing.addon.name}."
            )
            return
        }

        // register item
        val card = MobDataCard.create(displayItem, props.recipe, props).apply { this.guideGroup = guideGroup }
        card.register(addon)
        // in case autoloading is not yet enabled but item is registered
        if (!SlimefunReflection.isAutoLoadingEnabled()) {
            card.load()
        }
    }
}
