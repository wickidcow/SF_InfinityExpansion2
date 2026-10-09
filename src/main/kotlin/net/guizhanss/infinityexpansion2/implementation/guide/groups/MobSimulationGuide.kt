package net.guizhanss.infinityexpansion2.implementation.guide.groups

import city.norain.slimefun4.VaultIntegration
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu
import net.guizhanss.guizhanlib.kt.minecraft.extensions.toItem
import net.guizhanss.guizhanlib.kt.minecraft.items.edit
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.menu.FlexMenu
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideCategory
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuidePage
import net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideSortMode
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobDataCard
import net.guizhanss.infinityexpansion2.utils.slimefunext.displayItem
import net.guizhanss.infinityexpansion2.utils.slimefunext.getBackButton
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.Locale

/** Display-only navigation over the original Mob Simulation registration group. */
internal class MobSimulationGuide(
    private val source: MobSimulationGroup,
    private val category: MobDataCardGuideCategory? = null,
) : FlexMenu(
    NamespacedKey(source.key.namespace, "${source.key.key}_${category?.configKey ?: "categories"}"),
    ItemStack(category?.material ?: Material.BEACON),
) {
    private fun enabledCategories() = InfinityExpansion2.configService.mobSimGuideCategories.value

    // A history entry can be reopened after a category has been disabled.
    private fun activeCategory() = category?.takeIf { it in enabledCategories() }

    override fun getGuideTitle(p: Player) = activeCategory()?.displayName ?: source.getName()

    override fun isVisible(p: Player, profile: PlayerProfile, mode: SlimefunGuideMode) = false

    override fun initPage(p: Player, profile: PlayerProfile, mode: SlimefunGuideMode, menu: ChestMenu) {
        (0..53).forEach { slot ->
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler())
        }
        menu.addItem(1, getBackButton(p)) { _, _, _, action ->
            val guide = Slimefun.getRegistry().getSlimefunGuide(mode)
            if (action.isShiftClicked) {
                guide.openMainMenu(profile, profile.guideHistory.mainMenuPage)
            } else {
                profile.guideHistory.goBack(guide)
            }
            false
        }
    }

    override fun drawPage(
        p: Player, profile: PlayerProfile, mode: SlimefunGuideMode, menu: ChestMenu, page: Int,
    ) {
        (9..44).forEach { slot ->
            menu.replaceExistingItem(slot, null)
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler())
        }
        val selected = activeCategory()
        val ordering = InfinityExpansion2.configService.mobSimGuideOrdering()
        val available = source.items.filter { isAvailable(it, p) }
        val entries = selected?.cards(available, ordering) { (it as? MobDataCard)?.guideSortKey() }
            ?: available.filter { (it as? MobDataCard)?.guideSortKey() == null }
        val listing = MobDataCardGuidePage.of(entries, page, if (selected == null) 18 else 36)
        profile.guideHistory.add(this, listing.number)

        if (selected == null) {
            enabledCategories().forEachIndexed { index, choice ->
                val count = choice.cards(available, ordering) { (it as? MobDataCard)?.guideSortKey() }.size
                val orderDescription = if (ordering.mode == MobDataCardGuideSortMode.CONFIG) "Config order" else "Sorted A-Z"
                val icon = choice.material.toItem().edit {
                    name("&b${choice.displayName}")
                    lore("&7$count mob data cards", "&7$orderDescription", "", "&eClick to browse")
                }.apply {
                    val meta = itemMeta
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                    itemMeta = meta
                }
                menu.replaceExistingItem(11 + index, icon)
                menu.addMenuClickHandler(11 + index) { _, _, _, _ ->
                    if (choice in enabledCategories()) {
                        SlimefunGuide.openItemGroup(profile, MobSimulationGuide(source, choice), mode, 1)
                    } else {
                        drawPage(p, profile, mode, menu, listing.number)
                    }
                    false
                }
            }
            (18..26).forEach { slot -> menu.replaceExistingItem(slot, ChestMenuUtils.getBackground()) }
        }

        listing.items.forEachIndexed { index, sfItem ->
            renderItem(p, profile, mode, menu, sfItem, listing.number, (if (selected == null) 27 else 9) + index, selected)
        }
        if (selected != null && listing.items.isEmpty()) {
            menu.replaceExistingItem(22, Material.BARRIER.toItem().edit {
                name("&7No mob data cards available")
            })
        }

        menu.replaceExistingItem(46, ChestMenuUtils.getPreviousButton(p, listing.number, listing.total))
        menu.addMenuClickHandler(46, ChestMenuUtils.getEmptyClickHandler())
        if (listing.number > 1) {
            menu.addMenuClickHandler(46) { _, _, _, _ ->
                SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(p)
                drawPage(p, profile, mode, menu, listing.number - 1)
                false
            }
        }
        menu.replaceExistingItem(52, ChestMenuUtils.getNextButton(p, listing.number, listing.total))
        menu.addMenuClickHandler(52, ChestMenuUtils.getEmptyClickHandler())
        if (listing.number < listing.total) {
            menu.addMenuClickHandler(52) { _, _, _, _ ->
                SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(p)
                drawPage(p, profile, mode, menu, listing.number + 1)
                false
            }
        }
    }

    private fun renderItem(
        p: Player, profile: PlayerProfile, mode: SlimefunGuideMode, menu: ChestMenu,
        sfItem: SlimefunItem, page: Int, slot: Int, renderedCategory: MobDataCardGuideCategory?,
    ) {
        val survival = mode == SlimefunGuideMode.SURVIVAL_MODE
        val permissions = Slimefun.getPermissionsService()
        val research = sfItem.research
        val locked = survival && research != null && !profile.hasUnlocked(research)

        if (survival && !permissions.hasPermission(p, sfItem)) {
            menu.replaceExistingItem(slot, ChestMenuUtils.getNoPermissionItem().edit {
                name(sfItem.itemName)
                lore(*permissions.getLore(sfItem).toTypedArray())
            })
            return
        }

        val icon = if (locked) {
            val cost = if (VaultIntegration.isEnabled()) {
                String.format(Locale.ROOT, "%.2f coins", research.currencyCost)
            } else {
                "${research.levelCost} experience levels"
            }
            ChestMenuUtils.getNotResearchedItem().edit {
                name(sfItem.itemName)
                lore("&cResearch required", "&7Cost: &b$cost", "", "&eClick to unlock")
            }
        } else {
            sfItem.item.clone()
        }
        menu.replaceExistingItem(slot, icon)
        menu.addMenuClickHandler(slot) { player, _, _, action ->
            // Menus can remain open while item/world settings or permissions change.
            if (!isAvailable(sfItem, player) || (renderedCategory != null && renderedCategory !in enabledCategories())) {
                drawPage(player, profile, mode, menu, page)
            } else if (survival && !permissions.hasPermission(player, sfItem)) {
                drawPage(player, profile, mode, menu, page)
            } else if (!survival) {
                if (player.hasPermission("slimefun.cheat.items")) {
                    val output = sfItem.item.clone()
                    output.amount = if (action.isRightClicked || action.isShiftClicked) output.maxStackSize else 1
                    player.inventory.addItem(output)
                } else {
                    Slimefun.getLocalization().sendMessage(player, "messages.no-permission", true)
                }
            } else if (research != null && !profile.hasUnlocked(research)) {
                research.unlockFromGuide(Slimefun.getRegistry().getSlimefunGuide(mode), player, profile, sfItem, this, page)
            } else {
                displayItem(profile, sfItem, mode)
            }
            false
        }
    }

    private fun isAvailable(item: SlimefunItem, player: Player) = !item.isHidden && !item.isDisabledIn(player.world)
}
