package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Single-use, in-memory guard for an explicitly confirmed presentation-only
 * repair. No inventory, block, file or database is modified by this class.
 *
 * <p>Callers MUST durably back up the original ItemStack before invoking
 * confirm, recheck the live inventory slot and commit on its owning thread,
 * then verify the saved world after restart. This is not itself a migration.
 */
public final class HistoricalPresentationTransaction {
    private final ItemStack backup;
    private final ItemStack proposed;
    private final String confirmation;
    private boolean consumed;
    private boolean rolledBack;

    private HistoricalPresentationTransaction(ItemStack original, ItemStack candidate) {
        this.backup = original.clone();
        this.proposed = candidate.clone();
        this.confirmation = UUID.randomUUID().toString();
    }

    /** Returns null when the preview cannot preserve all non-presentation data. */
    public static HistoricalPresentationTransaction preview(
            ItemStack historical, ItemStack registeredEnglish, String historicalId, String registeredId) {
        ItemStack candidate = HistoricalPresentationPreview.candidate(
                historical, registeredEnglish, historicalId, registeredId);
        if (candidate == null) {
            // Only populated storage shells with the exact known four-line
            // Chinese layout and a valid typed ledger can use this fallback.
            // Never generalize it to owner lore or arbitrary translated items.
            candidate = HistoricalStorageLorePreview.candidate(
                    historical, registeredEnglish, historicalId, registeredId);
        }
        return candidate == null ? null : new HistoricalPresentationTransaction(historical, candidate);
    }

    public String confirmationToken() {
        return confirmation;
    }

    /** Detached backup for the caller's durable, per-item backup file. */
    public ItemStack backup() {
        return backup.clone();
    }

    /** Detached read-only preview. */
    public ItemStack proposed() {
        return proposed.clone();
    }

    /**
     * Confirm once against the EXACT item originally previewed. A changed
     * slot, amount, storage ledger or token rejects without modifying anything.
     * The caller must persist the backup BEFORE using this returned clone.
     */
    public synchronized ItemStack confirm(String token, ItemStack live) {
        if (consumed || rolledBack || !Objects.equals(confirmation, token) || !backup.equals(live)) return null;
        consumed = true;
        return proposed.clone();
    }

    /** Restore only when the live item is EXACTLY the result of this repair. */
    public synchronized ItemStack rollback(ItemStack live) {
        if (!consumed || rolledBack || !proposed.equals(live)) return null;
        rolledBack = true;
        return backup.clone();
    }
}
