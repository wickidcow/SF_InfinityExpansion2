package net.guizhanss.infinityexpansion2.utils.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.atomic.AtomicReference;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Identical fixtures exercise the actual legacy facade and the new menu lookup. */
@SuppressWarnings("deprecation") // Test oracle only: preserve the old contract deliberately.
class StoredBlockMenuLookupTest {
    enum Entry { OLD, NEW }
    private final Location location = new Location(null, -17, 64, 33);
    private MockedStatic<Slimefun> slimefun;
    private BlockDataController controller;
    private SlimefunBlockData data;
    private BlockMenu menu;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        slimefun = mockStatic(Slimefun.class, RETURNS_DEEP_STUBS);
        controller = Slimefun.getDatabaseManager().getBlockDataController();
        data = mock(SlimefunBlockData.class);
        menu = mock(BlockMenu.class);
        when(controller.getBlockData(location)).thenReturn(data);
        when(data.isDataLoaded()).thenReturn(true);
        when(data.getBlockMenu()).thenReturn(menu);
    }

    @AfterEach
    void tearDown() {
        if (slimefun != null) slimefun.close();
        MockBukkit.unmock();
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void missingRecordDoesNotLoadOrCreateAnything(Entry entry) {
        when(controller.getBlockData(location)).thenReturn(null);
        assertNull(read(entry));
        verify(controller).getBlockData(location);
        verifyNoMoreInteractions(controller);
        verifyNoInteractions(data, menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void loadedRecordReturnsTheExactMenuWithoutInspectingItsContents(Entry entry) {
        assertSame(menu, read(entry));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
        verifyNoInteractions(menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void unloadedRecordLoadsOnceBeforeReadingTheMenu(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        assertSame(menu, read(entry));
        var order = inOrder(controller, data);
        order.verify(controller).getBlockData(location);
        order.verify(data).isDataLoaded();
        order.verify(controller).loadBlockData(data);
        order.verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
        verifyNoInteractions(menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void loadedRecordWithoutMenuRemainsNull(Entry entry) {
        when(data.getBlockMenu()).thenReturn(null);
        assertNull(read(entry));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void unloadedRecordWithoutMenuDoesNotInventAnInventory(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        when(data.getBlockMenu()).thenReturn(null);
        assertNull(read(entry));
        verify(controller).getBlockData(location);
        verify(controller).loadBlockData(data);
        verify(data).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void lookupFailureDoesNotBecomeAnAbsentMenu(Entry entry) {
        var failure = new IllegalStateException("lookup failed");
        when(controller.getBlockData(location)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verifyNoMoreInteractions(controller);
        verifyNoInteractions(data, menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void readinessFailureDoesNotAttemptLoadOrRead(Entry entry) {
        var failure = new IllegalStateException("readiness failed");
        when(data.isDataLoaded()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verifyNoMoreInteractions(controller, data);
        verifyNoInteractions(menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void failedLoadDoesNotReadOrReturnTheExistingMenu(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        var failure = new IllegalStateException("load failed");
        doThrow(failure).when(controller).loadBlockData(data);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verify(controller).loadBlockData(data);
        verifyNoMoreInteractions(controller, data);
        verifyNoInteractions(menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void failedMenuReadIsNotConvertedToNoFuelOrMissingInventory(Entry entry) {
        var failure = new IllegalStateException("menu failed");
        when(data.getBlockMenu()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void repeatedCallsDoNotCacheThePreviousRecordOrMenu(Entry entry) {
        when(controller.getBlockData(location)).thenReturn(data, (SlimefunBlockData) null);
        assertSame(menu, read(entry));
        assertNull(read(entry));
        verify(controller, times(2)).getBlockData(location);
        verify(data).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void recordReplacementDuringLoadDoesNotTriggerASecondLookup(Entry entry) {
        var replacement = mock(SlimefunBlockData.class);
        var current = new AtomicReference<>(data);
        when(controller.getBlockData(location)).thenAnswer(call -> current.get());
        when(data.isDataLoaded()).thenReturn(false);
        doAnswer(call -> { current.set(replacement); return null; }).when(controller).loadBlockData(data);
        assertSame(menu, read(entry));
        verify(controller).getBlockData(location);
        verify(controller).loadBlockData(data);
        verify(data).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
        verifyNoInteractions(replacement, menu);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void menuCreatedByLoadIsReadAfterLoadEvenIfReadinessFlagStaysFalse(Entry entry) {
        var currentMenu = new AtomicReference<BlockMenu>();
        when(data.isDataLoaded()).thenReturn(false);
        when(data.getBlockMenu()).thenAnswer(call -> currentMenu.get());
        doAnswer(call -> { currentMenu.set(menu); return null; }).when(controller).loadBlockData(data);
        assertSame(menu, read(entry));
        verify(controller).getBlockData(location);
        verify(controller).loadBlockData(data);
        verify(data, times(1)).isDataLoaded();
        verify(data).getBlockMenu();
        verifyNoMoreInteractions(controller, data);
        verifyNoInteractions(menu);
    }

    private BlockMenu read(Entry entry) {
        return entry == Entry.OLD ? BlockStorage.getInventory(location) : StoredBlockLookup.getInventory(location);
    }
}
