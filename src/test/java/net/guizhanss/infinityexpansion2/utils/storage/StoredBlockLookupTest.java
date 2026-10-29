package net.guizhanss.infinityexpansion2.utils.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Run the same fixtures through the real old facade and the new controller boundary. */
@SuppressWarnings("deprecation") // Only the historical oracle is deprecated, never the production helper.
class StoredBlockLookupTest {
    enum Entry {
        OLD_EXISTS, NEW_EXISTS, OLD_ID, NEW_ID;
        boolean existence() { return this == OLD_EXISTS || this == NEW_EXISTS; }
    }

    private final Location location = new Location(null, -17, 64, 33);
    private MockedStatic<Slimefun> slimefun;
    private BlockDataController controller;
    private SlimefunBlockData data;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        slimefun = mockStatic(Slimefun.class, RETURNS_DEEP_STUBS);
        controller = Slimefun.getDatabaseManager().getBlockDataController();
        data = mock(SlimefunBlockData.class);
        when(controller.getBlockData(location)).thenReturn(data);
        when(data.isDataLoaded()).thenReturn(true);
        when(data.getSfId()).thenReturn("MISSING_ADDON_WITHER_PROOF_LEGACY");
    }

    @AfterEach
    void tearDown() {
        if (slimefun != null) slimefun.close();
        MockBukkit.unmock();
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void absentRecordDoesNotLoadCreateOrInspectPayload(Entry entry) {
        when(controller.getBlockData(location)).thenReturn(null);
        assertEquals(entry.existence() ? Boolean.FALSE : null, read(entry));
        verify(controller).getBlockData(location);
        verifyNoMoreInteractions(controller);
        verifyNoInteractions(data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void loadedRecordRetainsStoredIdentityWithoutRegistryOrGenericIdData(Entry entry) {
        assertPresent(entry);
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verifyIdRead(entry);
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void unloadedRecordIsLoadedOnceBeforeReturningExistenceOrIdentity(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        assertPresent(entry);
        var order = inOrder(controller, data);
        order.verify(controller).getBlockData(location);
        order.verify(data).isDataLoaded();
        order.verify(controller).loadBlockData(data);
        if (!entry.existence()) order.verify(data).getSfId();
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void loadThatLeavesFlagFalseDoesNotAddRetriesOrChangeExistence(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        assertPresent(entry);
        verify(controller, times(1)).getBlockData(location);
        verify(controller, times(1)).loadBlockData(data);
        verify(data, times(1)).isDataLoaded();
        verifyIdRead(entry);
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void lookupFailureIsNotConvertedToAbsent(Entry entry) {
        var failure = new IllegalStateException("record unavailable");
        when(controller.getBlockData(location)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verifyNoMoreInteractions(controller);
        verifyNoInteractions(data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void readinessFailurePropagatesWithoutLoadingOrReadingIdentity(Entry entry) {
        var failure = new IllegalStateException("readiness unavailable");
        when(data.isDataLoaded()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void payloadLoadFailureIsNotAnEmptySuccessfulRead(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        var failure = new IllegalStateException("payload unavailable");
        doThrow(failure).when(controller).loadBlockData(data);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verify(controller).loadBlockData(data);
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void existenceDoesNotDependOnReadingAnId(Entry entry) {
        var failure = new IllegalStateException("identity unreadable");
        when(data.getSfId()).thenThrow(failure);
        if (entry.existence()) {
            assertEquals(Boolean.TRUE, read(entry));
        } else {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> read(entry)));
        }
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verifyIdRead(entry);
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void everyCallResolvesAgainRatherThanCachingAFormerRecord(Entry entry) {
        when(controller.getBlockData(location)).thenReturn(data, null);
        assertPresent(entry);
        assertEquals(entry.existence() ? Boolean.FALSE : null, read(entry));
        verify(controller, times(2)).getBlockData(location);
        verify(data).isDataLoaded();
        verifyIdRead(entry);
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void nullStoredIdDoesNotTurnAnExistingRecordIntoAnAbsentRecord(Entry entry) {
        when(data.getSfId()).thenReturn(null);
        assertEquals(entry.existence() ? Boolean.TRUE : null, read(entry));
        verify(controller).getBlockData(location);
        verify(data).isDataLoaded();
        verifyIdRead(entry);
        verifyNoMoreInteractions(controller, data);
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void loadKeepsOriginalRecordRatherThanPerformingASecondLookup(Entry entry) {
        when(data.isDataLoaded()).thenReturn(false);
        var replacement = mock(SlimefunBlockData.class);
        doAnswer(call -> {
            when(controller.getBlockData(location)).thenReturn(replacement);
            return null;
        }).when(controller).loadBlockData(data);
        assertPresent(entry);
        // The re-stubbing above itself records a lookup invocation; verify the replacement
        // was never consulted instead of counting that setup interaction as production IO.
        verify(controller).loadBlockData(data);
        verify(data).isDataLoaded();
        verifyIdRead(entry);
        verifyNoInteractions(replacement);
    }

    private void verifyIdRead(Entry entry) {
        if (entry.existence()) verify(data, never()).getSfId();
        else verify(data).getSfId();
    }

    private void assertPresent(Entry entry) {
        assertEquals(entry.existence() ? Boolean.TRUE : "MISSING_ADDON_WITHER_PROOF_LEGACY", read(entry));
    }

    private Object read(Entry entry) {
        return switch (entry) {
            case OLD_EXISTS -> BlockStorage.hasBlockInfo(location);
            case NEW_EXISTS -> StoredBlockLookup.hasBlockInfo(location);
            case OLD_ID -> BlockStorage.getLocationInfo(location, "id");
            case NEW_ID -> StoredBlockLookup.getId(location);
        };
    }
}
