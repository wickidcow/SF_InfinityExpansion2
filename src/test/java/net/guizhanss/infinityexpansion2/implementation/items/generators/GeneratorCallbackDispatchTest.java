package net.guizhanss.infinityexpansion2.implementation.items.generators;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataConfigWrapper;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.guizhanss.infinityexpansion2.InfinityExpansion2;
import net.guizhanss.infinityexpansion2.core.services.DebugService;
import net.guizhanss.infinityexpansion2.core.services.LocalizationService;
import net.guizhanss.infinityexpansion2.utils.storage.StoredBlockLookup;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Executes real generation bodies and core overload dispatch with isolated external IO.
 * Legacy entry points are intentionally exercised, not removed to get a green build.
 * This is not a native world, fuel-registration, energy-network or Folia simulation.
 */
@SuppressWarnings("deprecation") // Test-only calls to the ABI bridges being preserved.
class GeneratorCallbackDispatchTest {
    enum Entry { MODERN_LONG, MODERN_INT, LEGACY_LONG_BLOCK, LEGACY_INT_BLOCK, LEGACY_CONFIG }

    private final Map<Field, Object> previousServices = new LinkedHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();
    private IllegalStateException writeFailure;
    private final Map<String, String> state = new LinkedHashMap<>();
    private MockedStatic<BlockStorage> storage;
    private MockedStatic<StoredBlockLookup> lookups;
    private World world;
    private Block block;
    private Location location;
    private BlockMenu menu;
    private SlimefunBlockData data;
    private Config legacy;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var localization = mock(LocalizationService.class, invocation -> {
            if (invocation.getMethod().getName().equals("getGuiItem")) return new ItemStack(Material.PAPER);
            if (invocation.getMethod().getReturnType() == String.class) return "fixture";
            return RETURNS_DEFAULTS.answer(invocation);
        });
        replaceService("localization", localization);
        replaceService("debugService", mock(DebugService.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("generator-fixture");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        // Bukkit Location#getBlock resolves through World#getBlockAt(Location).
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        location = new Location(world, 8, 64, 8);
        when(block.getLocation()).thenReturn(location);
        when(block.getWorld()).thenReturn(world);
        menu = mock(BlockMenu.class);
        when(menu.hasViewer()).thenReturn(false);
        data = mock(SlimefunBlockData.class);
        legacy = mock(Config.class);
        state.put("progress", "1");
        storage = mockStatic(BlockStorage.class);
        lookups = mockStatic(StoredBlockLookup.class);
        lookups.when(() -> StoredBlockLookup.getInventory(location)).thenReturn(menu);
        storage.when(() -> BlockStorage.getLocationInfo(location, "progress"))
            .thenAnswer(invocation -> state.get("progress"));
        storage.when(() -> BlockStorage.addBlockInfo(eq(location), eq("progress"), anyString()))
            .thenAnswer(invocation -> {
                if (writeFailure != null) throw writeFailure;
                writes.incrementAndGet();
                state.put("progress", invocation.getArgument(2));
                return null;
            });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (lookups != null) lookups.close();
        if (storage != null) storage.close();
        for (var entry : previousServices.entrySet()) entry.getKey().set(null, entry.getValue());
        MockBukkit.unmock();
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void constantGeneratorProducesExactlyOnceForEveryHistoricalAndModernEntry(Entry entry) throws Exception {
        var generator = generator(GeneratorType.INFINITY, 123, 1, 0);
        assertEquals(123L, output(generator, entry));
        storage.verify(() -> BlockStorage.check(location), times(1));
        verify(generator, times(1)).getEnergyProduction();
        assertEquals(0, writes.get());
        verifyNoInteractions(data, legacy);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void offCadenceGeneratorDoesNotProduceOrAdvanceItsCounter(Entry entry) throws Exception {
        var generator = generator(GeneratorType.INFINITY, 123, 3, 1);
        assertEquals(0L, output(generator, entry));
        assertEquals(1, generator.getTickCount());
        verify(generator, never()).getEnergyProduction();
        assertEquals(0, writes.get());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void absentRegisteredGeneratorStillProducesNothing(Entry entry) throws Exception {
        var generator = generator(GeneratorType.INFINITY, 123, 1, 0);
        storage.when(() -> BlockStorage.check(location)).thenReturn(null);
        assertEquals(0L, output(generator, entry));
        verify(generator, never()).getEnergyProduction();
        assertEquals(0, writes.get());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void geothermalNetherMultiplierIsNotChangedByLongReturnType(Entry entry) throws Exception {
        var generator = generator(GeneratorType.GEOTHERMAL, 16_777_215, 1, 0);
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        assertEquals(33_554_430L, output(generator, entry));
        assertEquals(2_147_483_520, generator.getCapacity());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void infinityReactorAdvancesExistingProgressExactlyOnce(Entry entry) throws Exception {
        var reactor = infinity();
        assertEquals(321L, output(reactor, entry));
        assertEquals("2", state.get("progress"));
        assertEquals(1, writes.get());
        verify(menu, never()).consumeItem(anyInt());
        verify(reactor, times(1)).getEnergyProduction();
        lookups.verify(() -> StoredBlockLookup.getInventory(location), times(1));
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void singularityReactorAdvancesExistingProgressExactlyOnce(Entry entry) throws Exception {
        var reactor = singularity();
        assertEquals(654L, output(reactor, entry));
        assertEquals("2", state.get("progress"));
        assertEquals(1, writes.get());
        verify(menu, never()).consumeItem(anyInt());
        verify(reactor, times(1)).getEnergyProduction();
        lookups.verify(() -> StoredBlockLookup.getInventory(location), times(1));
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void completedReactorsKeepTheirFinalGenerationAndResetOrder(Entry entry) throws Exception {
        state.put("progress", "9");
        assertEquals(321L, output(infinity(), entry));
        assertEquals("0", state.get("progress"));
        assertEquals(1, writes.get());
        state.put("progress", "9");
        assertEquals(654L, output(singularity(), entry));
        assertEquals("0", state.get("progress"));
        assertEquals(2, writes.get());
        verify(menu, never()).consumeItem(anyInt());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void missingMenusDoNotGenerateOrResetSavedProgress(Entry entry) throws Exception {
        lookups.when(() -> StoredBlockLookup.getInventory(location)).thenReturn(null);
        assertEquals(0L, output(infinity(), entry));
        assertEquals(0L, output(singularity(), entry));
        assertEquals("1", state.get("progress"));
        assertEquals(0, writes.get());
        verifyNoInteractions(menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void failedMenuLookupPropagatesBeforeProgressOrFuelChanges(Entry entry) throws Exception {
        var failure = new IllegalStateException("menu lookup unavailable");
        lookups.when(() -> StoredBlockLookup.getInventory(location)).thenThrow(failure);
        var first = infinity();
        var second = singularity();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> output(first, entry)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> output(second, entry)));
        assertEquals("1", state.get("progress"));
        assertEquals(0, writes.get());
        verifyNoInteractions(menu);
        storage.verify(() -> BlockStorage.getLocationInfo(location, "progress"), never());
        verify(first, never()).getEnergyProduction();
        verify(second, never()).getEnergyProduction();
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void missingInitialFuelDoesNotConsumeAdvanceOrProduce(Entry entry) throws Exception {
        state.put("progress", "0");
        assertEquals(0L, output(infinity(), entry));
        assertEquals(0L, output(singularity(), entry));
        assertEquals("0", state.get("progress"));
        assertEquals(0, writes.get());
        verify(menu, never()).consumeItem(anyInt());
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void missingVoidRefillPausesWithoutAdvancingTheCheckpoint(Entry entry) throws Exception {
        state.put("progress", "4");
        assertEquals(0L, output(infinity(), entry));
        assertEquals("4", state.get("progress"));
        assertEquals(0, writes.get());
        verify(menu, never()).consumeItem(anyInt());
    }

    @Test
    void progressWriteFailureIsNotHiddenAsSuccessfulGeneration() throws Exception {
        var reactor = infinity();
        var failure = new IllegalStateException("storage write unavailable");
        // Inject at the existing write boundary, regardless of Mockito's static-stub precedence.
        writeFailure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> output(reactor, Entry.MODERN_LONG)));
        assertEquals("1", state.get("progress"));
        assertEquals(0, writes.get());
        verify(reactor, never()).getEnergyProduction();
    }

    @Test
    void modernGenerationBypassesConfigMethodsAndWrapperConstruction() throws Exception {
        List<EnergyNetProvider> providers = List.of(generator(GeneratorType.INFINITY, 123, 1, 0), infinity(), singularity());
        try (var wrappers = mockConstruction(BlockDataConfigWrapper.class)) {
            for (var provider : providers) {
                state.put("progress", "1");
                assertTrue(output(provider, Entry.MODERN_LONG) > 0L);
                assertTrue(mockingDetails(provider).getInvocations().stream().noneMatch(invocation ->
                    invocation.getMethod().getName().equals("getGeneratedOutput")
                    && invocation.getMethod().getParameterTypes()[1] == Config.class));
            }
            assertEquals(0, wrappers.constructed().size());
        }
    }

    @Test
    void unsupportedStorageContainerStillFailsBeforeAnyWorldOrInventoryLookup() throws Exception {
        List<EnergyNetProvider> providers = List.of(generator(GeneratorType.INFINITY, 123, 1, 0), infinity(), singularity());
        var unsupported = mock(ASlimefunDataContainer.class);
        storage.clearInvocations();
        for (var provider : providers) {
            assertThrows(IllegalStateException.class, () -> provider.getGeneratedOutput(location, unsupported));
            assertThrows(IllegalStateException.class, () -> provider.getGeneratedOutputLong(location, unsupported));
        }
        storage.verifyNoInteractions();
        lookups.verifyNoInteractions();
        verifyNoInteractions(menu, unsupported);
    }

    @Test
    void legacyConfigEntryRemainsCallableAndDoesNotReadOrSaveItsArgument() throws Exception {
        for (var provider : List.of(generator(GeneratorType.INFINITY, 123, 1, 0), infinity(), singularity())) {
            assertNotNull(provider.getClass().getDeclaredMethod("getGeneratedOutput", Location.class, Config.class));
            state.put("progress", "1");
            assertTrue(provider.getGeneratedOutput(location, legacy) > 0);
        }
        verifyNoInteractions(legacy);
    }

    @Test
    void tickerAdvancesOnlyOncePerUniqueCycleAndDoesNotGeneratePerBlock() throws Exception {
        var generator = generator(GeneratorType.INFINITY, 123, 1, 0);
        var ticker = ticker(generator);
        assertFalse(ticker.isSynchronized());
        ticker.update();
        ticker.update();
        assertEquals(1, generator.getTickCount());
        ticker.tick(block, generator, (ASlimefunDataContainer) data);
        ticker.tick(block, generator, data);
        ticker.tick(block, generator, legacy);
        assertEquals(1, generator.getTickCount());
        ticker.startNewTick();
        ticker.update();
        assertEquals(2, generator.getTickCount());
        storage.verify(() -> BlockStorage.check(location), never());
        verify(generator, never()).getEnergyProduction();
    }

    @Test
    void tickerModernAndConcreteCallbacksAvoidLegacyWrappersAndRejectNonBlockStorage() throws Exception {
        var generator = generator(GeneratorType.INFINITY, 123, 1, 0);
        var ticker = ticker(generator);
        try (var wrappers = mockConstruction(BlockDataConfigWrapper.class)) {
            ticker.tick(block, generator, data);
            ticker.tick(block, generator, (ASlimefunDataContainer) data);
            assertEquals(0, wrappers.constructed().size());
        }
        assertThrows(IllegalStateException.class,
            () -> ticker.tick(block, generator, mock(ASlimefunDataContainer.class)));
        assertEquals(0, generator.getTickCount());
        verifyNoInteractions(data);
    }

    @Test
    void maximumExistingReactorProductionAndCapacityRemainIntegerBounded() throws Exception {
        var reactor = infinity();
        doReturn(Integer.MAX_VALUE).when(reactor).getEnergyProduction();
        assertEquals(Integer.MAX_VALUE, reactor.getCapacity());
        assertEquals((long) Integer.MAX_VALUE, output(reactor, Entry.MODERN_LONG));
        assertEquals("2", state.get("progress"));
    }

    @Test
    void generatorUsesTheRegisteredInstancesCounterWithoutChangingItsExistingCadenceRule() throws Exception {
        var caller = generator(GeneratorType.INFINITY, 123, 2, 0);
        var registered = generator(GeneratorType.INFINITY, 456, 1, 1);
        assertEquals(0L, output(caller, Entry.MODERN_LONG));
        setField(registered, EnergyGenerator.class, "tickCount", 2);
        assertEquals(123L, output(caller, Entry.MODERN_LONG));
        assertEquals(0, caller.getTickCount());
    }

    private long output(EnergyNetProvider provider, Entry entry) {
        return switch (entry) {
            case MODERN_LONG -> provider.getGeneratedOutputLong(location, (ASlimefunDataContainer) data);
            case MODERN_INT -> provider.getGeneratedOutput(location, (ASlimefunDataContainer) data);
            case LEGACY_LONG_BLOCK -> provider.getGeneratedOutputLong(location, data);
            case LEGACY_INT_BLOCK -> provider.getGeneratedOutput(location, data);
            case LEGACY_CONFIG -> provider.getGeneratedOutput(location, legacy);
        };
    }

    private EnergyGenerator generator(GeneratorType type, int production, int rate, int count) throws Exception {
        var generator = mock(EnergyGenerator.class, CALLS_REAL_METHODS);
        setField(generator, EnergyGenerator.class, "type", type);
        setField(generator, EnergyGenerator.class, "tickCount", count);
        setField(generator, EnergyGenerator.class, "energyProductionSetting", setting(production));
        doReturn(rate).when(generator).getCustomTickRate();
        storage.when(() -> BlockStorage.check(location)).thenReturn(generator);
        clearInvocations(generator);
        return generator;
    }

    private InfinityReactor infinity() throws Exception {
        var reactor = mock(InfinityReactor.class, CALLS_REAL_METHODS);
        setField(reactor, InfinityReactor.class, "voidIngotDurationSetting", setting(4));
        setField(reactor, InfinityReactor.class, "infinityIngotDurationSetting", setting(8));
        doReturn(321).when(reactor).getEnergyProduction();
        return reactor;
    }

    private InfinitySingularityReactor singularity() throws Exception {
        var reactor = mock(InfinitySingularityReactor.class, CALLS_REAL_METHODS);
        setField(reactor, InfinitySingularityReactor.class, "infinitySingularityDurationSetting", setting(8));
        doReturn(654).when(reactor).getEnergyProduction();
        return reactor;
    }

    private IntRangeSetting setting(int value) {
        var setting = mock(IntRangeSetting.class);
        when(setting.getValue()).thenReturn(value);
        return setting;
    }

    private BlockTicker ticker(EnergyGenerator generator) throws Exception {
        var method = EnergyGenerator.class.getDeclaredMethod("onBlockTick");
        method.setAccessible(true);
        return (BlockTicker) method.invoke(generator);
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
