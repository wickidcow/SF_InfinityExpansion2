package net.guizhanss.infinityexpansion2.implementation.items.storage;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Compatibility for purely cosmetic Adventure component-tree encodings.
 *
 * <p>Never discard unknown/custom lore text, item owner data, PDC, model values,
 * or other functional components. A Slimefun ID is required before entering the
 * presentation-only fallback; vanilla and unidentified items remain strict.
 */
public final class StorageItemPresentation {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private StorageItemPresentation() {}

    public static boolean matchesRegistered(ItemStack oldItem, ItemStack newItem) {
        if (oldItem == null || newItem == null || oldItem.getType() != newItem.getType()
                || !oldItem.hasItemMeta() || !newItem.hasItemMeta()) return false;
        ItemMeta oldMeta = oldItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();
        if (oldMeta == null || newMeta == null) return false;
        String oldId = Slimefun.getItemDataService().getItemData(oldMeta).orElse(null);
        String newId = Slimefun.getItemDataService().getItemData(newMeta).orElse(null);
        return equivalentPresentation(oldItem, newItem, oldId, newId);
    }

    /**
     * Tests the exact PDC-confirmed identity supplied by the caller, then checks
     * every other ItemStack component by neutralizing only display name and lore
     * on independent clones. Text and lore line count must be exactly equal.
     */
    public static boolean equivalentPresentation(ItemStack oldItem, ItemStack newItem, String oldId, String newId) {
        if (oldItem == null || newItem == null || oldItem.getType() != newItem.getType()
                || oldId == null || !Objects.equals(oldId, newId)) return false;
        ItemMeta oldMeta = oldItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();
        if (oldMeta == null || newMeta == null) return false;
        if (!sameText(oldMeta.displayName(), newMeta.displayName())
                || !sameLore(oldMeta.lore(), newMeta.lore())
                || !sameCustomModelData(oldMeta, newMeta)) return false;
        ItemStack first = oldItem.clone();
        ItemStack second = newItem.clone();
        ItemMeta firstMeta = first.getItemMeta();
        ItemMeta secondMeta = second.getItemMeta();
        firstMeta.displayName(null);
        secondMeta.displayName(null);
        firstMeta.lore(null);
        secondMeta.lore(null);
        first.setItemMeta(firstMeta);
        second.setItemMeta(secondMeta);
        return first.isSimilar(second);
    }

    public static ItemStack canonicalIfCosmetic(ItemStack storedTemplate) {
        // Do not query Slimefun's optional live item-data service for ordinary
        // vanilla materials or empty/unadorned input templates. These never need
        // cosmetic migration and must preserve their historical ticker behavior.
        if (storedTemplate == null || storedTemplate.getType().isAir() || !storedTemplate.hasItemMeta()) return null;
        ItemMeta meta = storedTemplate.getItemMeta();
        if (meta == null) return null;
        String id = Slimefun.getItemDataService().getItemData(meta).orElse(null);
        if (id == null) return null;
        SlimefunItem registered = SlimefunItem.getById(id);
        if (registered == null) return null;
        ItemStack canonical = registered.getItem().clone();
        canonical.setAmount(storedTemplate.getAmount());
        if (storedTemplate.isSimilar(canonical) || !matchesRegistered(storedTemplate, canonical)) return null;
        return canonical;
    }

    private static boolean sameCustomModelData(ItemMeta first, ItemMeta second) {
        if (first.hasCustomModelDataComponent() != second.hasCustomModelDataComponent()) return false;
        if (!first.hasCustomModelDataComponent()) return true;
        var old = first.getCustomModelDataComponent();
        var now = second.getCustomModelDataComponent();
        return Objects.equals(old.getFloats(), now.getFloats())
                && Objects.equals(old.getBooleans(), now.getBooleans())
                && Objects.equals(old.getStrings(), now.getStrings())
                && Objects.equals(old.getColors(), now.getColors());
    }

    public static boolean sameText(Component first, Component second) {
        if (first == null || second == null) return first == second;
        return PLAIN.serialize(first).equals(PLAIN.serialize(second));
    }

    public static boolean sameLore(List<Component> first, List<Component> second) {
        if (first == null || second == null) return first == second;
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            if (!sameText(first.get(i), second.get(i))) return false;
        }
        return true;
    }
}
