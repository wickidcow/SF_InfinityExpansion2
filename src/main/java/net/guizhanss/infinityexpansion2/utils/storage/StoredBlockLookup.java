package net.guizhanss.infinityexpansion2.utils.storage;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nullable;
import org.bukkit.Location;

/**
 * Narrow controller-backed replacement for IE2's two legacy existence/ID reads.
 *
 * <p>A record lookup and a loaded payload are different things. Preserve the historical
 * conditional load, including its exceptions, before returning existence or the stored
 * Slimefun ID. Do not infer identity from the current block material or item registry.
 * This does not add caching, writes, chunk scans, retries or a new readiness policy.
 * Callers retain responsibility for their existing scheduling and location ownership.
 */
public final class StoredBlockLookup {
    private StoredBlockLookup() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean hasBlockInfo(Location location) {
        return resolve(location) != null;
    }

    @Nullable
    public static String getId(Location location) {
        var data = resolve(location);
        return data == null ? null : data.getSfId();
    }

    @Nullable
    private static SlimefunBlockData resolve(Location location) {
        var data = Slimefun.getDatabaseManager().getBlockDataController().getBlockData(location);
        if (data == null) {
            return null;
        }
        if (!data.isDataLoaded()) {
            Slimefun.getDatabaseManager().getBlockDataController().loadBlockData(data);
        }
        // Return the original record after the same single conditional load. A second lookup,
        // an isLoaded filter or catching an exception as 'absent' would change legacy behavior.
        return data;
    }
}
