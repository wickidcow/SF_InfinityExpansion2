package net.guizhanss.infinityexpansion2.utils.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Explicit, checked metadata transfer when presentation changes an item's material. */
public final class ModernItems {
    private ModernItems() { throw new IllegalStateException("Utility class"); }

    public static ItemStack withType(ItemStack source, Material material) {
        var result = source.withType(material);
        var original = source.getItemMeta();
        if (original != null) {
            // Convert the metadata subtype explicitly (for example carpet -> armor).
            // Do not rely on every ItemStack implementation accepting the old subtype.
            var converted = Bukkit.getItemFactory().asMetaFor(original, material);
            if (!result.setItemMeta(converted)
                || !result.getItemMeta().getPersistentDataContainer().equals(original.getPersistentDataContainer())) {
                throw new IllegalStateException("Material conversion rejected preserved item identity");
            }
        }
        if (result.getAmount() != source.getAmount()) {
            throw new IllegalStateException("Material conversion changed item quantity");
        }
        return result;
    }

    public static boolean hasCustomModel(ItemMeta meta) {
        // Preserve imported/alternative metadata implementations that still expose legacy
        // integer artwork only through the public serialized form. This is read-only and
        // reached only for a potential material change, not an already-current card tick.
        return meta.hasCustomModelDataComponent() || meta.serialize().containsKey("custom-model-data");
    }
}
