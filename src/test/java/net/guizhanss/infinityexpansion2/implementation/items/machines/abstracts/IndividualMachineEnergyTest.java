package net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.guizhanss.infinityexpansion2.InfinityExpansion2;
import net.guizhanss.infinityexpansion2.api.mobsim.MobDataCardProps;
import net.guizhanss.infinityexpansion2.core.IERegistry;
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout;
import net.guizhanss.infinityexpansion2.core.services.ConfigService;
import net.guizhanss.infinityexpansion2.core.services.DebugService;
import net.guizhanss.infinityexpansion2.core.services.IntegrationService;
import net.guizhanss.infinityexpansion2.core.services.LocalizationService;
import net.guizhanss.infinityexpansion2.implementation.items.machines.AdvancedAnvil;
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobDataCard;
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobSimulationChamber;
import net.guizhanss.infinityexpansion2.utils.constant.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Actual anvil craft and chamber tick entry points with isolated plugin services and external IO. */
@SuppressWarnings("deprecation") // Test-only storage mock; production storage/callback APIs are not migrated here.
class IndividualMachineEnergyTest {
    private static final String CARD_ID = "energy-regression-fixture";
    private final Map<Field, Object> previousServices = new LinkedHashMap<>();
    private final Location location = new Location(null, 8, 64, 8);
    private ConfigService config;
    private MockedStatic<SlimefunItem> items;
    private MockedStatic<BlockStorage> storage;
    private BlockMenu menu;
    private Block block;
    private Player player;
    private MobDataCardProps previousCard;
    private boolean registeredCard;
    private MobDataCardProps props;
    private ItemStack card;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        player = server.addPlayer();
        var plugin = mock(InfinityExpansion2.class);
        when(plugin.getName()).thenReturn("InfinityExpansion2");
        replaceService("instance", plugin);
        var localization = mock(LocalizationService.class, call -> {
            if (call.getMethod().getName().equals("getGuiItem")) return new ItemStack(Material.PAPER);
            if (call.getMethod().getReturnType() == String.class) return "test";
            return RETURNS_DEFAULTS.answer(call);
        });
        replaceService("localization", localization);
        replaceService("debugService", mock(DebugService.class));
        replaceService("integrationService", mock(IntegrationService.class));
        config = mock(ConfigService.class, RETURNS_DEEP_STUBS);
        when(config.getMobSimRefreshCardAppearance$InfinityExpansion2().getValue()).thenReturn(false);
        when(config.getMobSimAllowStackedCard().getValue()).thenReturn(true);
        when(config.getMobSimChargeCardEnergy().getValue()).thenReturn(true);
        when(config.getMobSimInterval().getValue()).thenReturn(2);
        replaceService("configService", config);
        items = mockStatic(SlimefunItem.class);
        storage = mockStatic(BlockStorage.class);
        storage.when(() -> BlockStorage.getLocationInfo(location, "xp")).thenReturn("7");
        menu = mock(BlockMenu.class);
        when(menu.getLocation()).thenReturn(location);
        when(menu.hasViewer()).thenReturn(false);
        block = mock(Block.class);
        when(block.getLocation()).thenReturn(location);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (registeredCard) {
            if (previousCard == null) IERegistry.INSTANCE.getMobDataCards().remove(CARD_ID);
            else IERegistry.INSTANCE.getMobDataCards().put(CARD_ID, previousCard);
        }
        if (storage != null) storage.close();
        if (items != null) items.close();
        for (var entry : previousServices.entrySet()) entry.getKey().set(null, entry.getValue());
        MockBukkit.unmock();
    }

    @Test
    void anvilShortageDoesNotInspectInputsOrDebit() throws Throwable {
        var anvil = anvil(99L);
        craft(anvil);
        verify(menu, never()).getItemInSlot(anyInt());
        verify(anvil, never()).removeCharge(any(Location.class), anyLong());
        assertModernDispatch(anvil);
    }

    @Test
    void anvilExactEnergyDebitsOnlyAfterOutputIsPushed() throws Throwable {
        var anvil = anvil(100L);
        ItemStack[] input = anvilInputs();
        craft(anvil);
        assertEquals(1, input[0].getAmount());
        assertEquals(2, input[1].getAmount());
        var order = inOrder(menu, anvil);
        order.verify(menu).pushItem(any(ItemStack.class), eq(new int[]{2}));
        order.verify(anvil).removeCharge(location, 100L);
        assertModernDispatch(anvil);
    }

    @Test
    void anvilNoOutputRoomDoesNotConsumeItemsOrEnergy() throws Throwable {
        var anvil = anvil(100L);
        ItemStack[] input = anvilInputs();
        when(menu.fits(any(ItemStack.class), any(int[].class))).thenReturn(false);
        craft(anvil);
        assertEquals(2, input[0].getAmount());
        assertEquals(3, input[1].getAmount());
        verify(anvil, never()).removeCharge(any(Location.class), anyLong());
        verify(menu, never()).pushItem(any(ItemStack.class), any(int[].class));
        assertModernDispatch(anvil);
    }

    @Test
    void anvilMissingInputsDoNotDebit() throws Throwable {
        var anvil = anvil(100L);
        craft(anvil);
        verify(anvil, never()).removeCharge(any(Location.class), anyLong());
        assertModernDispatch(anvil);
    }

    @Test
    void anvilUnchangedEnchantmentsDoNotConsumeOrDebit() throws Throwable {
        var anvil = anvil(100L);
        ItemStack[] input = anvilInputs();
        input[1].removeEnchantment(Enchantment.UNBREAKING);
        craft(anvil);
        assertEquals(2, input[0].getAmount());
        assertEquals(3, input[1].getAmount());
        verify(anvil, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void anvilFailedPushDoesNotMoveTheDebitEarlier() throws Throwable {
        var anvil = anvil(100L);
        anvilInputs();
        var failure = new IllegalStateException("output failure");
        doThrow(failure).when(menu).pushItem(any(ItemStack.class), any(int[].class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> craft(anvil)));
        verify(anvil, never()).removeCharge(any(Location.class), anyLong());
    }

    @Test
    void anvilAboveIntegerBalanceKeepsConfiguredCostAndCapacity() throws Throwable {
        var anvil = anvil(3_000_000_000L);
        anvilInputs();
        assertEquals(200, anvil.getCapacity());
        craft(anvil);
        verify(anvil).removeCharge(location, 100L);
        assertModernDispatch(anvil);
    }

    @Test
    void chamberCompatibilityModeChargesBaseOnlyAndNeverDoubleDebits() throws Exception {
        when(config.getMobSimChargeCardEnergy().getValue()).thenReturn(false);
        var chamber = chamber(100L);
        tick(chamber);
        verify(chamber, times(1)).removeCharge(location, 100L);
        verify(chamber, times(1)).getChargeLong(location);
        assertModernDispatch(chamber);
    }

    @Test
    void chamberExactStackedCardDemandDebitsOnce() throws Exception {
        var chamber = chamber(160L);
        tick(chamber);
        verify(chamber).removeCharge(location, 160L);
        verify(chamber, times(2)).getChargeLong(location);
        assertEquals(3, card.getAmount());
        storage.verify(() -> BlockStorage.addBlockInfo(eq(location), anyString(), anyString()), never());
        assertModernDispatch(chamber);
    }

    @Test
    void chamberOneShortOfCardDemandDoesNotDebitOrUpdateExperience() throws Exception {
        var chamber = chamber(159L);
        tick(chamber);
        verify(chamber, never()).removeCharge(any(Location.class), anyLong());
        storage.verify(() -> BlockStorage.getLocationInfo(location, "xp"), never());
        verify(menu, never()).replaceExistingItem(eq(8), any(ItemStack.class));
        assertModernDispatch(chamber);
    }

    @Test
    void chamberDisabledStackingKeepsSingleCardCost() throws Exception {
        when(config.getMobSimAllowStackedCard().getValue()).thenReturn(false);
        var chamber = chamber(120L);
        tick(chamber);
        verify(chamber).removeCharge(location, 120L);
        assertEquals(3, card.getAmount());
        assertModernDispatch(chamber);
    }

    @Test
    void chamberCapacityGateStillPrecedesAdditionalEnergyRead() throws Exception {
        var chamber = chamber(160L);
        doReturn(150).when(chamber).getCapacity();
        tick(chamber);
        verify(chamber, times(1)).getChargeLong(location);
        verify(chamber, never()).removeCharge(any(Location.class), anyLong());
        assertModernDispatch(chamber);
    }

    @Test
    void chamberMalformedNegativeCardEnergyIsRejected() throws Exception {
        var chamber = chamber(1000L);
        when(props.getEnergy()).thenReturn(-1);
        tick(chamber);
        verify(chamber, never()).removeCharge(any(Location.class), anyLong());
        storage.verify(() -> BlockStorage.getLocationInfo(location, "xp"), never());
    }

    @Test
    void chamberHugeStackedDemandIsNotNarrowedIntoAffordableEnergy() throws Exception {
        var chamber = chamber(Long.MAX_VALUE);
        when(props.getEnergy()).thenReturn(Integer.MAX_VALUE);
        card.setAmount(64);
        tick(chamber);
        verify(chamber, never()).removeCharge(any(Location.class), anyLong());
        storage.verify(() -> BlockStorage.getLocationInfo(location, "xp"), never());
        assertModernDispatch(chamber);
    }

    @Test
    void chamberAboveIntegerBalanceKeepsCardCostWithoutChangingCapacity() throws Exception {
        var chamber = chamber(3_000_000_000L);
        assertEquals(1000, chamber.getCapacity());
        tick(chamber);
        verify(chamber).removeCharge(location, 160L);
        assertModernDispatch(chamber);
    }

    @Test
    void chamberReadFailureDoesNotGrantOutputOrDebit() throws Exception {
        var chamber = chamber(160L);
        var failure = new IllegalStateException("energy unavailable");
        doThrow(failure).when(chamber).getChargeLong(location);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tick(chamber)));
        verify(chamber, never()).removeCharge(any(Location.class), anyLong());
        verify(menu, never()).pushItem(any(ItemStack.class), any(int[].class));
    }

    private void tick(AbstractTickingMachine machine) {
        machine.tick(block, menu);
    }

    private AdvancedAnvil anvil(long balance) {
        var machine = mock(AdvancedAnvil.class, CALLS_REAL_METHODS);
        doReturn(100).when(machine).getEnergyConsumptionPerAction();
        doReturn(balance).when(machine).getChargeLong(location);
        doReturn(new int[]{0, 1}).when((AbstractTickingActionMachine) machine).getInputSlots();
        doReturn(new int[]{2}).when((AbstractTickingActionMachine) machine).getOutputSlots();
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        return machine;
    }

    private ItemStack[] anvilInputs() {
        var first = new ItemStack(Material.IRON_SWORD, 2);
        var second = new ItemStack(Material.IRON_SWORD, 3);
        second.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        when(menu.getItemInSlot(0)).thenReturn(first);
        when(menu.getItemInSlot(1)).thenReturn(second);
        when(menu.fits(any(ItemStack.class), any(int[].class))).thenReturn(true);
        return new ItemStack[]{first, second};
    }

    private void craft(AdvancedAnvil machine) throws Throwable {
        var method = AdvancedAnvil.class.getDeclaredMethod("craft", BlockMenu.class, Player.class);
        method.setAccessible(true);
        try { method.invoke(machine, menu, player); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }

    private MobSimulationChamber chamber(long balance) throws Exception {
        var machine = mock(MobSimulationChamber.class, CALLS_REAL_METHODS);
        var layout = AbstractTickingMachine.class.getDeclaredField("layout");
        layout.setAccessible(true);
        layout.set(machine, MenuLayout.Companion.getSINGLE_INPUT());
        doReturn(100).when(machine).getEnergyConsumptionPerTick();
        doReturn(1000).when(machine).getCapacity();
        doReturn(1).when(machine).getCustomTickRate();
        doReturn(1).when(machine).getTickCount(); // non-output tick: do not simulate random loot or XP grants
        doReturn(balance).when(machine).getChargeLong(location);
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        props = mock(MobDataCardProps.class);
        when(props.getEnergy()).thenReturn(20);
        when(props.getId()).thenReturn(CARD_ID);
        previousCard = IERegistry.INSTANCE.getMobDataCards().put(CARD_ID, props);
        registeredCard = true;
        card = new ItemStack(Material.PAPER, 3);
        var meta = card.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.INSTANCE.getMOB_DATA_ID(), PersistentDataType.STRING, CARD_ID);
        card.setItemMeta(meta);
        var registered = mock(MobDataCard.class);
        items.when(() -> SlimefunItem.getByItem(card)).thenReturn(registered);
        when(menu.getItemInSlot(MenuLayout.Companion.getSINGLE_INPUT().getInputSlots()[0])).thenReturn(card);
        return machine;
    }

    private void replaceService(String name, Object value) throws Exception {
        var field = InfinityExpansion2.class.getDeclaredField(name);
        field.setAccessible(true);
        previousServices.put(field, field.get(null));
        field.set(null, value);
    }

    private void assertModernDispatch(Object machine) {
        assertTrue(mockingDetails(machine).getInvocations().stream().noneMatch(invocation -> {
            var method = invocation.getMethod();
            return method.getName().equals("getCharge")
                || (method.getName().equals("removeCharge")
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[]{Location.class, int.class}));
        }));
    }
}
