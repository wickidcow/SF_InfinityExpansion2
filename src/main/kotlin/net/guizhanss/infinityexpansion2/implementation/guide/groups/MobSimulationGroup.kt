package net.guizhanss.infinityexpansion2.implementation.guide.groups

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideOrder
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobDataCard
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

/** Uses the normal live group list while organizing only its card entries for display. */
internal class MobSimulationGroup(key: NamespacedKey, item: ItemStack) : SubGroup(key, item) {
    private val order = MobDataCardGuideOrder<SlimefunItem> { (it as? MobDataCard)?.guideSortKey() }

    @Synchronized
    override fun getItems(): MutableList<SlimefunItem> = order.order(super.getItems())

    @Synchronized
    override fun add(item: SlimefunItem) = super.add(item)

    @Synchronized
    override fun remove(item: SlimefunItem) = super.remove(item)
}
