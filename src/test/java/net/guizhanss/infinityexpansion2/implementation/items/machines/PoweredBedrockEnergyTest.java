package net.guizhanss.infinityexpansion2.implementation.items.machines;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Executes the real existing anonymous ticker. Only its external energy IO and block are mocked. */
@SuppressWarnings("deprecation") // The legacy callback stays public; this test deliberately invokes that boundary.
class PoweredBedrockEnergyTest {
    private final Location location = new Location(null, 1, 64, 1);
    private final AtomicReference<Material> material = new AtomicReference<>(Material.NETHERITE_BLOCK);
    private PoweredBedrock machine;
    private BlockTicker ticker;
    private Block block;
    private Config legacyData;

    @BeforeEach
    void setUp() throws Exception {
        machine = mock(PoweredBedrock.class, CALLS_REAL_METHODS);
        var energy = PoweredBedrock.class.getDeclaredField("energyPerTick");
        energy.setAccessible(true);
        energy.setInt(machine, 10_000);
        doReturn(20_000L).when(machine).getChargeLong(location);
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        block = mock(Block.class);
        when(block.getLocation()).thenReturn(location);
        when(block.getType()).thenAnswer(call -> material.get());
        doAnswer(call -> { material.set(call.getArgument(0)); return null; }).when(block).setType(any(Material.class));
        legacyData = mock(Config.class);
        var constructor = Class.forName(PoweredBedrock.class.getName() + "$1").getDeclaredConstructor(PoweredBedrock.class);
        constructor.setAccessible(true);
        ticker = (BlockTicker) constructor.newInstance(machine);
    }

    @Test
    void initialAndEighthPassDoNotReadOrDebit() {
        ticker.tick(block, machine, legacyData);
        for (int i = 0; i < 8; i++) ticker.uniqueTick();
        ticker.tick(block, machine, legacyData);
        verify(machine, never()).getChargeLong(any(Location.class));
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        verify(block, never()).setType(any(Material.class));
    }

    @Test
    void sevenOfEightPassesDebitExactlyOnceEach() {
        for (int i = 0; i < 8; i++) {
            ticker.uniqueTick();
            ticker.tick(block, machine, legacyData);
        }
        verify(machine, times(7)).getChargeLong(location);
        verify(machine, times(7)).removeCharge(location, 10_000L);
        verify(block, times(1)).setType(Material.BEDROCK);
        assertModernDispatch();
    }

    @Test
    void exactPowerTransformsBeforeDebiting() {
        doReturn(10_000L).when(machine).getChargeLong(location);
        tickOnce();
        var order = inOrder(block, machine);
        order.verify(machine).getChargeLong(location);
        order.verify(block).setType(Material.BEDROCK);
        order.verify(machine).removeCharge(location, 10_000L);
        assertModernDispatch();
    }

    @Test
    void alreadyPoweredBlockContinuesConsumingEnergy() {
        material.set(Material.BEDROCK);
        tickOnce();
        verify(block, never()).setType(any(Material.class));
        verify(machine).removeCharge(location, 10_000L);
        assertModernDispatch();
    }

    @Test
    void depletedPoweredBlockRevertsWithoutAnotherDebit() {
        material.set(Material.BEDROCK);
        doReturn(9_999L).when(machine).getChargeLong(location);
        tickOnce();
        assertEquals(Material.NETHERITE_BLOCK, material.get());
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertModernDispatch();
    }

    @Test
    void depletedAlreadyUnpoweredBlockRetainsExistingClampedDebitPath() {
        doReturn(0L).when(machine).getChargeLong(location);
        tickOnce();
        verify(block, never()).setType(any(Material.class));
        verify(machine).removeCharge(location, 10_000L);
        assertModernDispatch();
    }

    @Test
    void aboveIntegerRangeBalanceDoesNotChangeCapacityOrCost() {
        doReturn(3_000_000_000L).when(machine).getChargeLong(location);
        assertEquals(20_000, machine.getCapacity());
        assertEquals(10_000, machine.getEnergyConsumptionPerTick());
        tickOnce();
        assertEquals(Material.BEDROCK, material.get());
        verify(machine).removeCharge(location, 10_000L);
        assertModernDispatch();
    }

    @Test
    void unreadableChargeCannotTransformOrDebit() {
        var failure = new IllegalStateException("failed energy read");
        doThrow(failure).when(machine).getChargeLong(location);
        assertSame(failure, assertThrows(IllegalStateException.class, this::tickOnce));
        verify(block, never()).setType(any(Material.class));
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void tickerSynchronizationAndUnusedLegacyConfigRemainUnchanged() {
        assertTrue(ticker.isSynchronized());
        tickOnce();
        verifyNoInteractions(legacyData);
        assertModernDispatch();
    }

    private void tickOnce() {
        ticker.uniqueTick();
        ticker.tick(block, machine, legacyData);
    }

    private void assertModernDispatch() {
        assertTrue(mockingDetails(machine).getInvocations().stream().noneMatch(invocation -> {
            var method = invocation.getMethod();
            return method.getName().equals("getCharge")
                || (method.getName().equals("removeCharge")
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[]{Location.class, int.class}));
        }));
    }
}
