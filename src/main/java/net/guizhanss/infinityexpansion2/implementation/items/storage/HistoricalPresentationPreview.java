package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Read-only planning primitive for a future opt-in historical presentation repair.
 *
 * <p>It never writes inventories, storage ledgers, or block state. The caller must
 * independently establish a trusted registered English template, actual Slimefun
 * identity and an administrator-confirmed backup before applying any change.
 */
public final class HistoricalPresentationPreview {
    private HistoricalPresentationPreview() {}

    /**
     * Produces an isolated candidate only when replacing name/lore leaves every
     * other Bukkit item component byte-for-byte semantically equivalent.
     * A null result is a hard rejection, not permission to migrate.
     */
    public static ItemStack candidate(ItemStack historical, ItemStack englishTemplate,
                                      String historicalId, String templateId) {
        if (historical == null || englishTemplate == null || historicalId == null
                || !historicalId.equals(templateId)
                || historical.getType() != englishTemplate.getType()
                || !historical.hasItemMeta() || !englishTemplate.hasItemMeta()) return null;

        ItemStack original = historical.clone();
        ItemStack proposed = historical.clone();
        ItemMeta englishMeta = englishTemplate.getItemMeta();
        ItemMeta proposedMeta = proposed.getItemMeta();
        if (englishMeta == null || proposedMeta == null) return null;

        // Only presentation fields may be copied; preserve the original typed PDC,
        // ownership, custom model data, storage payload, enchantments and quantity.
        proposedMeta.displayName(englishMeta.displayName());
        proposedMeta.lore(englishMeta.lore());
        proposed.setItemMeta(proposedMeta);
        if (proposed.getAmount() != original.getAmount()) return null;

        ItemStack originalNeutral = original.clone();
        ItemStack proposedNeutral = proposed.clone();
        ItemMeta before = originalNeutral.getItemMeta();
        ItemMeta after = proposedNeutral.getItemMeta();
        if (before == null || after == null) return null;
        before.displayName(null);
        before.lore(null);
        after.displayName(null);
        after.lore(null);
        originalNeutral.setItemMeta(before);
        proposedNeutral.setItemMeta(after);
        if (!originalNeutral.isSimilar(proposedNeutral)) return null;

        // No live item is mutated; the independent candidate can only be
        // considered by a separate explicit preview/backup/confirm transaction.
        return proposed;
    }
}
