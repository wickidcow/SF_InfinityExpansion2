package net.guizhanss.infinityexpansion2.utils;

import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import org.bukkit.Location;

/**
 * Preserve the historical signed-int HUD input while reading through the long energy API.
 * This presentation boundary never changes a capacity, stored balance or energy cost.
 */
public final class LegacyHudEnergy {
    private LegacyHudEnergy() { }

    public static int read(EnergyNetComponent component, Location location) {
        long charge = component.getChargeLong(location);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, charge));
    }
}
