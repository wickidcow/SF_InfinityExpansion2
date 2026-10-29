package net.guizhanss.infinityexpansion2.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import java.util.Random;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class LegacyHudEnergyTest {
    private final Location location = new Location(null, 1, 2, 3);

    @Test
    void ordinaryBalancesKeepTheirExactDisplayValue() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1, 10000, 2_000_000_000, Integer.MAX_VALUE}) {
            assertRead(value, value);
        }
    }

    @Test
    void largePositiveBalancesSaturateRatherThanWrap() {
        for (long value : new long[]{(long) Integer.MAX_VALUE + 1, 3_000_000_000L, Long.MAX_VALUE}) {
            assertRead(value, Integer.MAX_VALUE);
        }
    }

    @Test
    void largeNegativeBalancesRetainHistoricalSignedClamp() {
        assertRead((long) Integer.MIN_VALUE - 1, Integer.MIN_VALUE);
        assertRead(Long.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Test
    void matchesHistoricalClampAcrossDeterministicRandomBalances() {
        var random = new Random(20261028L);
        for (int i = 0; i < 256; i++) {
            long value = random.nextLong();
            int expected = value < Integer.MIN_VALUE ? Integer.MIN_VALUE
                : value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
            assertRead(value, expected);
        }
    }

    @Test
    void failedReadIsNotConvertedIntoZeroOrWrittenBack() {
        var component = mock(EnergyNetComponent.class);
        var failure = new IllegalStateException("unreadable energy");
        when(component.getChargeLong(location)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> LegacyHudEnergy.read(component, location)));
        verify(component).getChargeLong(location);
        verifyNoMoreInteractions(component);
    }

    private void assertRead(long value, int expected) {
        var component = mock(EnergyNetComponent.class);
        when(component.getChargeLong(location)).thenReturn(value);
        assertEquals(expected, LegacyHudEnergy.read(component, location));
        verify(component).getChargeLong(location);
        verifyNoMoreInteractions(component);
    }
}
