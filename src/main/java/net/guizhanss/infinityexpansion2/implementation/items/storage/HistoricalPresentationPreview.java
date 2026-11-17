package net.guizhanss.infinityexpansion2.implementation.items.storage;

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

        // Name-only repair is the only proven safe path without a historical
        // locale-specific template. A same-length Chinese/dynamic/owner lore is
        // NOT proof that every line may be overwritten by a modern English line.
        // This intentionally rejects translated or extended historical lore.
        if (englishMeta.displayName() == null
                || !java.util.Objects.equals(proposedMeta.lore(), englishMeta.lore())) return null;

        // Preserve the original lore, exact typed PDC, owner, custom model,
        // storage payload, enchantments and stack quantity. Only change name.
        proposedMeta.displayName(englishMeta.displayName());
        proposed.setItemMeta(proposedMeta);
        if (proposed.getAmount() != original.getAmount()) return null;

        if (!sameNonPresentation(original, proposed)) return null;

        // No live item is mutated; the detached candidate can only be applied
        // by a separately confirmed, backed-up per-player transaction.
        return proposed;
    }
    /**
     * Preserve the complete native identity except display/lore, including
     * components which Bukkit isSimilar may ignore. Any unroundtrippable item
     * is ineligible for historical rewriting, even on an opt-in preview.
     */
    static boolean sameNonPresentation(ItemStack original, ItemStack proposed) {
        if (original == null || proposed == null
                || original.getType() != proposed.getType()
                || original.getAmount() != proposed.getAmount()) return false;
        try {
            ItemStack first = exactNativeSnapshot(original);
            ItemStack second = exactNativeSnapshot(proposed);
            if (first == null || second == null) return false;
            ItemMeta before = first.getItemMeta();
            ItemMeta after = second.getItemMeta();
            if (before == null || after == null) return false;
            before.displayName(null);
            before.lore(null);
            after.displayName(null);
            after.lore(null);
            if (!first.setItemMeta(before) || !second.setItemMeta(after)) return false;
            return java.util.Arrays.equals(first.serializeAsBytes(), second.serializeAsBytes());
        } catch (RuntimeException cannotProveIdentity) {
            return false;
        }
    }

    private static ItemStack exactNativeSnapshot(ItemStack item) {
        try {
            byte[] bytes = item.serializeAsBytes();
            ItemStack copy = ItemStack.deserializeBytes(bytes);
            return copy != null && item.getType() == copy.getType()
                    && item.getAmount() == copy.getAmount()
                    && java.util.Arrays.equals(bytes, copy.serializeAsBytes()) ? copy : null;
        } catch (RuntimeException unknownComponent) {
            return null;
        }
    }

}
