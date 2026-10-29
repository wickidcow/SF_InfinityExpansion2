package net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import java.util.Arrays;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executes production machine entry points with mocked inventories and energy IO.
 * Core container tests execute the real default long-energy methods separately.
 * This does not simulate a live server, connected player, or region scheduler.
 */
class MachineEnergyAccessTest {
    private final Location location = new Location(null, 4, 64, 8);
    private final Block block = mock(Block.class);
    private final BlockMenu menu = mock(BlockMenu.class);
    private final Player player = mock(Player.class);

    @BeforeEach
    void prepareMenu() {
        when(menu.getLocation()).thenReturn(location);
        when(menu.hasViewer()).thenReturn(false);
    }

    @Test
    void tickingInsufficientPowerDoesNotProcessOrDebit() {
        var machine = ticking(100, 99, true);
        machine.tick(block, menu);
        verify(machine).getChargeLong(location);
        verify(machine, never()).process(block, menu);
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void tickingExactPowerProcessesAndDebitsOnceThroughLongOverload() {
        var machine = ticking(100, 100, true);
        machine.tick(block, menu);
        verify(machine).process(block, menu);
        verify(machine).removeCharge(location, 100L);
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void tickingMaximumConfiguredCostKeepsCapacityAndDebit() {
        var machine = ticking(1_000_000_000, 2_000_000_000L, true);
        assertEquals(2_000_000_000, machine.getCapacity());
        machine.tick(block, menu);
        verify(machine).removeCharge(location, 1_000_000_000L);
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void tickingOffCadenceDoesNotProcessOrDebit() {
        var machine = ticking(100, 200, true);
        doReturn(2).when(machine).getCustomTickRate();
        doReturn(1).when(machine).getTickCount();
        machine.tick(block, menu);
        verify(machine, never()).process(block, menu);
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void tickingRejectedWorkIsNotCharged() {
        var machine = ticking(100, 200, false);
        machine.tick(block, menu);
        verify(machine).process(block, menu);
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void tickingExceptionDoesNotChargeOrHideFailure() {
        var machine = ticking(100, 200, true);
        var failure = new IllegalStateException("processing failed");
        doThrow(failure).when(machine).process(block, menu);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> machine.tick(block, menu)));
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void actionInsufficientPowerDoesNotProcessOrDebit() {
        var machine = action(100, 99, true);
        machine.tick(block, menu);
        verify(machine).getChargeLong(location);
        verify(machine, never()).process(block, menu);
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void actionExactPowerDebitsOnceAfterSuccessfulProcess() {
        var machine = action(100, 100, true);
        machine.tick(block, menu);
        var order = inOrder(machine);
        order.verify(machine).process(block, menu);
        order.verify(machine).removeCharge(location, 100L);
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void actionRejectedWorkIsNotCharged() {
        var machine = action(100, 200, false);
        machine.tick(block, menu);
        verify(machine).process(block, menu);
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void actionOffCadenceDoesNotEvenReadEnergy() {
        var machine = action(100, 200, true);
        doReturn(2).when(machine).getCustomTickRate();
        doReturn(1).when(machine).getTickCount();
        machine.tick(block, menu);
        verify(machine, never()).getChargeLong(any(Location.class));
        verify(machine, never()).process(block, menu);
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void actionMaximumCostKeepsCapacityAndDebit() {
        var machine = action(1_000_000_000, 2_000_000_000L, true);
        assertEquals(2_000_000_000, machine.getCapacity());
        machine.tick(block, menu);
        verify(machine).removeCharge(location, 1_000_000_000L);
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void craftingCapacityStillEqualsConfiguredActionCost() {
        var machine = crafting(1_000_000_000, 0);
        assertEquals(1_000_000_000, machine.getCapacity());
    }

    @Test
    void craftingInsufficientPowerDoesNotEnterRecipeValidation() {
        var machine = crafting(100, 99);
        machine.craft(menu, player);
        verify(machine).getChargeLong(location);
        verify(machine, never()).getInputSlots();
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void craftingExactPowerEntersExistingRecipePathWithoutEarlyDebit() {
        var machine = crafting(100, 100);
        // Stop at the unchanged parent recipe boundary rather than simulating a craft.
        var marker = new BoundaryReached();
        doThrow(marker).when(machine).getInputSlots();
        assertSame(marker, assertThrows(BoundaryReached.class, () -> machine.craft(menu, player)));
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void successfulCraftDispatchesLongDebitBeforeExistingNotifications() {
        var machine = crafting(100, 100);
        // Observe the debit signature, stopping before the unchanged plugin notification.
        var marker = new BoundaryReached();
        doThrow(marker).when(machine).removeCharge(location, 100L);
        assertSame(marker, assertThrows(BoundaryReached.class,
            () -> machine.onSuccessfulCraft(menu, mock(ItemStack.class), player)));
        verify(machine).removeCharge(location, 100L);
        assertNoDeprecatedEnergyCalls(machine);
    }

    @Test
    void realCoreLongDebitPreservesAmountAndClampsAtZero() {
        var component = coreComponent();
        var data = loadedData("300");
        component.removeCharge(location, 100L, data);
        verify(data).setData("energy-charge", "200");
        var small = loadedData("25");
        component.removeCharge(location, 100L, small);
        verify(small).setData("energy-charge", "0");
    }

    @Test
    void realCoreDoesNotDebitUnloadedOrPendingRemovalContainers() {
        var component = coreComponent();
        var unloaded = loadedData("300");
        when(unloaded.isDataLoaded()).thenReturn(false);
        component.removeCharge(location, 100L, unloaded);
        verify(unloaded, never()).setData(anyString(), anyString());
        var removing = loadedData("300");
        when(removing.isPendingRemove()).thenReturn(true);
        component.removeCharge(location, 100L, removing);
        verify(removing, never()).setData(anyString(), anyString());
    }

    @Test
    void realCoreLongReadDoesNotTruncateStoredBalanceOrRewriteData() {
        var component = coreComponent();
        var data = loadedData("3000000000");
        assertEquals(3_000_000_000L, component.getChargeLong(location, data));
        verify(data, never()).setData(anyString(), anyString());
    }

    private AbstractTickingMachine ticking(int cost, long balance, boolean processed) {
        var machine = mock(AbstractTickingMachine.class, CALLS_REAL_METHODS);
        doReturn(cost).when(machine).getEnergyConsumptionPerTick();
        doReturn(1).when(machine).getCustomTickRate();
        doReturn(0).when(machine).getTickCount();
        doReturn(balance).when(machine).getChargeLong(location);
        doReturn(processed).when(machine).process(block, menu);
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        return machine;
    }

    private AbstractTickingActionMachine action(int cost, long balance, boolean processed) {
        var machine = mock(AbstractTickingActionMachine.class, CALLS_REAL_METHODS);
        doReturn(cost).when(machine).getEnergyConsumptionPerAction();
        doReturn(1).when(machine).getCustomTickRate();
        doReturn(0).when(machine).getTickCount();
        doReturn(balance).when(machine).getChargeLong(location);
        doReturn(processed).when(machine).process(block, menu);
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        return machine;
    }

    private AbstractEnergyCraftingMachine crafting(int cost, long balance) {
        var machine = mock(AbstractEnergyCraftingMachine.class, CALLS_REAL_METHODS);
        doReturn(cost).when(machine).getEnergyConsumptionPerAction();
        doReturn(balance).when(machine).getChargeLong(location);
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        return machine;
    }

    private EnergyNetComponent coreComponent() {
        var component = mock(EnergyNetComponent.class, CALLS_REAL_METHODS);
        doReturn(4_000_000_000L).when(component).getCapacityLong();
        doReturn(EnergyNetComponentType.CONSUMER).when(component).getEnergyComponentType();
        return component;
    }

    private ASlimefunDataContainer loadedData(String charge) {
        var data = mock(ASlimefunDataContainer.class);
        when(data.isDataLoaded()).thenReturn(true);
        when(data.isPendingRemove()).thenReturn(false);
        when(data.getData("energy-charge")).thenReturn(charge);
        return data;
    }

    private void assertNoDeprecatedEnergyCalls(Object machine) {
        assertTrue(mockingDetails(machine).getInvocations().stream().noneMatch(invocation -> {
            var method = invocation.getMethod();
            return method.getName().equals("getCharge")
                || (method.getName().equals("removeCharge")
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[]{Location.class, int.class}));
        }), "Machine must use the long energy API, not an integer compatibility overload");
    }

    private static final class BoundaryReached extends RuntimeException { }
}
