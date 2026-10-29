package net.guizhanss.infinityexpansion2.implementation.items;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataConfigWrapper;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalData;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.guizhanss.infinityexpansion2.InfinityExpansion2;
import net.guizhanss.infinityexpansion2.core.services.DebugService;
import net.guizhanss.infinityexpansion2.core.services.LocalizationService;
import net.guizhanss.infinityexpansion2.implementation.items.machines.PoweredBedrock;
import net.guizhanss.infinityexpansion2.implementation.items.sfextension.NetherStarReactor;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Real anonymous tickers and core dispatch, with external storage/menu/energy IO isolated. */
@SuppressWarnings("deprecation") // Deliberately exercise retained historical API descriptors.
class TickerCallbackDispatchTest {
    enum Entry { LEGACY_CONFIG, CONCRETE_BLOCK, NEUTRAL_CONTAINER }

    private final Map<Field, Object> previousServices = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> slots = new HashMap<>();
    private final Map<String, String> saved = new HashMap<>();
    private MockedStatic<BlockStorage> storage;
    private RuntimeException inventoryFailure;
    private Location location;
    private Block block;
    private BlockMenu menu;
    private SlimefunBlockData data;
    private Config legacy;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        var world = server.addSimpleWorld("ticker-fixture");
        block = world.getBlockAt(8, 64, 8);
        block.setType(Material.NETHERITE_BLOCK);
        location = block.getLocation();
        var plugin = mock(InfinityExpansion2.class);
        when(plugin.getName()).thenReturn("InfinityExpansion2");
        when(plugin.getNamespace()).thenReturn("infinityexpansion2");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("IE2TickerFixture"));
        replaceService("instance", plugin);
        var localization = mock(LocalizationService.class, invocation -> {
            if (invocation.getMethod().getName().equals("getGuiItem")) return new ItemStack(Material.PAPER);
            if (invocation.getMethod().getReturnType() == String.class) return "fixture";
            return RETURNS_DEFAULTS.answer(invocation);
        });
        replaceService("localization", localization);
        replaceService("debugService", mock(DebugService.class));
        menu = mock(BlockMenu.class);
        when(menu.getLocation()).thenReturn(location);
        when(menu.hasViewer()).thenReturn(false);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> slots.get(call.getArgument(0)));
        data = mock(SlimefunBlockData.class);
        legacy = mock(Config.class);
        storage = mockStatic(BlockStorage.class);
        storage.when(() -> BlockStorage.getInventory(location)).thenAnswer(call -> {
            if (inventoryFailure != null) throw inventoryFailure;
            return menu;
        });
        storage.when(() -> BlockStorage.addBlockInfo(eq(location), anyString(), anyString()))
            .thenAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null) storage.close();
        for (var entry : previousServices.entrySet()) entry.getKey().set(null, entry.getValue());
        MockBukkit.unmock();
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void bedrockRetainsSevenOfEightCadenceWithoutDuplicateDebit(Entry entry) throws Exception {
        var machine = bedrock();
        var ticker = ticker(machine);
        for (int i = 0; i < 8; i++) {
            ticker.startNewTick();
            ticker.update();
            ticker.update(); // The second update in a cycle must not advance uniqueTick.
            tick(ticker, machine, entry);
        }
        verify(machine, times(7)).getChargeLong(location);
        verify(machine, times(7)).removeCharge(location, 10_000L);
        assertEquals(Material.BEDROCK, block.getType());
        assertEquals(20_000, machine.getCapacity());
        verifyNoInteractions(data, legacy);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void depletedBedrockRevertsWithoutAnotherDebit(Entry entry) throws Exception {
        var machine = bedrock();
        var ticker = ticker(machine);
        block.setType(Material.BEDROCK);
        doReturn(9_999L).when(machine).getChargeLong(location);
        ticker.update();
        tick(ticker, machine, entry);
        assertEquals(Material.NETHERITE_BLOCK, block.getType());
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void bedrockInitialAndEighthPassRemainIdle(Entry entry) throws Exception {
        var machine = bedrock();
        var ticker = ticker(machine);
        tick(ticker, machine, entry);
        for (int i = 0; i < 8; i++) ticker.uniqueTick();
        tick(ticker, machine, entry);
        verify(machine, never()).getChargeLong(any(Location.class));
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertEquals(Material.NETHERITE_BLOCK, block.getType());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void bedrockReadFailureDoesNotTransformOrDebit(Entry entry) throws Exception {
        var machine = bedrock();
        var ticker = ticker(machine);
        var failure = new IllegalStateException("energy read failed");
        doThrow(failure).when(machine).getChargeLong(location);
        ticker.update();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tick(ticker, machine, entry)));
        verify(machine, never()).removeCharge(any(Location.class), anyLong());
        assertEquals(Material.NETHERITE_BLOCK, block.getType());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void netherReactorCountsOnlyUniqueCyclesNotBlocks(Entry entry) throws Exception {
        var machine = mock(NetherStarReactor.class, CALLS_REAL_METHODS);
        var ticker = ticker(machine);
        for (int i = 0; i < 8; i++) {
            ticker.startNewTick();
            ticker.update();
            ticker.update();
            tick(ticker, machine, entry);
            tick(ticker, machine, entry);
        }
        var field = NetherStarReactor.class.getDeclaredField("sfTickCount");
        field.setAccessible(true);
        assertEquals(8, field.getInt(machine));
        verify(machine, never()).extraTick(any(Location.class));
        verify(machine, never()).getEnergyProduction();
        storage.verifyNoInteractions();
        verifyNoInteractions(data, legacy, menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void missingStorageCacheDoesNotTouchInventoryOrPersistEmptyState(Entry entry) throws Exception {
        var machine = unit(null);
        tick(ticker(machine), machine, entry);
        verify(menu, never()).getItemInSlot(anyInt());
        verify(menu, never()).markDirty();
        assertTrue(saved.isEmpty());
        assertTrue(machine.getCaches().isEmpty());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void unassignedStorageIdentityKeepsItsBalanceAndStopsTransfers(Entry entry) throws Exception {
        var cache = new StorageCache(null, 23, 1024, false);
        var machine = unit(cache);
        tick(ticker(machine), machine, entry);
        assertEquals(23, cache.getAmount());
        assertNull(cache.getItemStack());
        verify(menu, never()).getItemInSlot(anyInt());
        verify(menu, never()).markDirty();
        assertTrue(saved.isEmpty());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void quarantinedStorageRemainsUntouched(Entry entry) throws Exception {
        var cache = new StorageCache(new ItemStack(Material.STONE), 96, 1024, false);
        cache.blockUnverifiedTransfer$InfinityExpansion2();
        var machine = unit(cache);
        tick(ticker(machine), machine, entry);
        assertEquals(96, cache.getAmount());
        assertTrue(cache.hasBlockedTransfer());
        verify(menu, never()).getItemInSlot(anyInt());
        assertTrue(saved.isEmpty());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void storageProducesOneStackWithOneConservedDebit(Entry entry) throws Exception {
        var template = new ItemStack(Material.STONE);
        var cache = new StorageCache(template, 96, 1024, false);
        var machine = unit(cache);
        var before = cache.getItemStack().clone();
        int output = machine.getOutputSlots()[0];
        doAnswer(call -> {
            assertEquals("32", saved.get(StorageUnit.BS_AMOUNT), "Reserve and persist before output credit");
            slots.put(output, ((ItemStack) call.getArgument(0)).clone());
            return null;
        }).when(menu).pushItem(any(ItemStack.class), any(int[].class));
        tick(ticker(machine), machine, entry);
        assertEquals(32, cache.getAmount());
        assertEquals(64, slots.get(output).getAmount());
        assertEquals(96, cache.getAmount() + slots.get(output).getAmount());
        assertEquals(before, cache.getItemStack());
        verify(menu, times(1)).pushItem(any(ItemStack.class), eq(machine.getOutputSlots()));
        verify(menu, times(1)).markDirty();
        assertFalse(cache.hasBlockedTransfer());
        verifyNoInteractions(data, legacy);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void fullOutputDoesNotDebitStorageOrDuplicateItems(Entry entry) throws Exception {
        var cache = new StorageCache(new ItemStack(Material.STONE), 96, 1024, false);
        var machine = unit(cache);
        slots.put(machine.getOutputSlots()[0], new ItemStack(Material.STONE, 64));
        tick(ticker(machine), machine, entry);
        assertEquals(96, cache.getAmount());
        verify(menu, never()).pushItem(any(ItemStack.class), any(int[].class));
        verify(menu, never()).markDirty();
        assertTrue(saved.isEmpty());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void failedPhysicalOutputKeepsReservationAndQuarantineInsteadOfRefunding(Entry entry) throws Exception {
        var cache = new StorageCache(new ItemStack(Material.STONE), 96, 1024, false);
        var machine = unit(cache);
        int output = machine.getOutputSlots()[0];
        doAnswer(call -> {
            slots.put(output, ((ItemStack) call.getArgument(0)).clone());
            throw new IllegalStateException("write succeeded before destination threw");
        }).when(menu).pushItem(any(ItemStack.class), any(int[].class));
        tick(ticker(machine), machine, entry);
        assertEquals(32, cache.getAmount());
        assertEquals(64, slots.get(output).getAmount());
        assertEquals("32", saved.get(StorageUnit.BS_AMOUNT));
        assertTrue(cache.hasBlockedTransfer());
        verify(menu, times(1)).pushItem(any(ItemStack.class), any(int[].class));
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void storageLookupFailureDoesNotBecomeAnEmptySuccessfulTick(Entry entry) throws Exception {
        var cache = new StorageCache(new ItemStack(Material.STONE), 96, 1024, false);
        var machine = unit(cache);
        var ticker = ticker(machine);
        inventoryFailure = new IllegalStateException("inventory unavailable");
        assertSame(inventoryFailure, assertThrows(IllegalStateException.class, () -> tick(ticker, machine, entry)));
        assertEquals(96, cache.getAmount());
        verify(menu, never()).getItemInSlot(anyInt());
        assertTrue(saved.isEmpty());
    }

    @Test
    void bothModernPathsAvoidConfigDispatchAndWrapperAllocation() throws Exception {
        for (var machine : machines()) {
            var ticker = spy(ticker(machine));
            ticker.update();
            try (var wrappers = mockConstruction(BlockDataConfigWrapper.class)) {
                tick(ticker, machine, Entry.NEUTRAL_CONTAINER);
                tick(ticker, machine, Entry.CONCRETE_BLOCK);
                assertEquals(0, wrappers.constructed().size());
            }
            assertTrue(mockingDetails(ticker).getInvocations().stream().noneMatch(call ->
                call.getMethod().getName().equals("tick")
                    && call.getMethod().getParameterTypes()[2] == Config.class));
        }
        verifyNoInteractions(data, legacy);
    }

    @Test
    void coreRejectsUnsupportedNeutralStorageBeforeAnyMachineWork() throws Exception {
        for (var machine : machines()) {
            var ticker = ticker(machine);
            var unsupported = mock(ASlimefunDataContainer.class);
            var error = assertThrows(IllegalStateException.class,
                () -> ticker.tick(block, machine, unsupported));
            assertEquals("BlockTicker is non-universal but item storage is universal", error.getMessage());
            verifyNoInteractions(unsupported);
        }
        storage.verifyNoInteractions();
        verifyNoInteractions(menu);
        assertEquals(Material.NETHERITE_BLOCK, block.getType());
    }

    @Test
    void historicalUniversalOverloadRemainsAnInheritedNoOp() throws Exception {
        for (var machine : machines()) {
            var ticker = ticker(machine);
            var universal = mock(SlimefunUniversalData.class);
            ticker.tick(block, machine, universal);
            verifyNoInteractions(universal);
        }
        storage.verifyNoInteractions();
        verifyNoInteractions(menu);
    }

    @Test
    void ownershipFlagsAndLegacyDescriptorsStayAvailable() throws Exception {
        for (var machine : machines()) {
            var ticker = ticker(machine);
            assertEquals(!(machine instanceof NetherStarReactor), ticker.isSynchronized());
            assertFalse(ticker.isUniversal());
            assertEquals(BlockTicker.class, ticker.getIdentifier());
            assertNotNull(ticker.getClass().getDeclaredMethod("tick", Block.class, SlimefunItem.class, Config.class));
            assertNotNull(ticker.getClass().getDeclaredMethod("tick", Block.class, SlimefunItem.class, SlimefunBlockData.class));
        }
    }

    private List<SlimefunItem> machines() throws Exception {
        return List.of(bedrock(), mock(NetherStarReactor.class, CALLS_REAL_METHODS), unit(null));
    }

    private PoweredBedrock bedrock() throws Exception {
        var machine = mock(PoweredBedrock.class, CALLS_REAL_METHODS);
        setField(machine, PoweredBedrock.class, "energyPerTick", 10_000);
        doReturn(20_000L).when(machine).getChargeLong(location);
        doNothing().when(machine).removeCharge(any(Location.class), anyLong());
        return machine;
    }

    private StorageUnit unit(StorageCache cache) throws Exception {
        var machine = mock(StorageUnit.class, CALLS_REAL_METHODS);
        Map<BlockPosition, StorageCache> caches = new HashMap<>();
        if (cache != null) caches.put(new BlockPosition(location), cache);
        setField(machine, StorageUnit.class, "_caches", caches);
        return machine;
    }

    private BlockTicker ticker(SlimefunItem machine) throws Exception {
        var type = machine instanceof PoweredBedrock ? PoweredBedrock.class
            : machine instanceof NetherStarReactor ? NetherStarReactor.class : StorageUnit.class;
        var suffix = machine instanceof NetherStarReactor ? "$preRegister$1" : "$1";
        var constructor = Class.forName(type.getName() + suffix).getDeclaredConstructor(type);
        constructor.setAccessible(true);
        return (BlockTicker) constructor.newInstance(machine);
    }

    private void tick(BlockTicker ticker, SlimefunItem machine, Entry entry) {
        switch (entry) {
            case LEGACY_CONFIG -> ticker.tick(block, machine, legacy);
            case CONCRETE_BLOCK -> ticker.tick(block, machine, data);
            case NEUTRAL_CONTAINER -> ticker.tick(block, machine, (ASlimefunDataContainer) data);
        }
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void replaceService(String name, Object value) throws Exception {
        var field = InfinityExpansion2.class.getDeclaredField(name);
        field.setAccessible(true);
        previousServices.put(field, field.get(null));
        field.set(null, value);
    }
}
