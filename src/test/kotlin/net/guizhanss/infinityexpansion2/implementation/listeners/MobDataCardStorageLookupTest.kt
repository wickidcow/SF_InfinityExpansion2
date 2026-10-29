package net.guizhanss.infinityexpansion2.implementation.listeners

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock

/** Uses the listener's real default storage lookup, not an injected ownership answer. */
class MobDataCardStorageLookupTest {
    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var slimefun: MockedStatic<Slimefun>
    private lateinit var controller: BlockDataController
    private lateinit var record: SlimefunBlockData
    private lateinit var barrel: Inventory
    private lateinit var location: Location
    private val queued = mutableListOf<Runnable>()
    private var ownsRegion = true

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        player.openInventory(server.createInventory(null, 9))
        val block = player.world.getBlockAt(0, 60, 0)
        block.chunk.load()
        block.type = Material.BARREL
        location = block.location
        barrel = (block.state as Container).inventory
        barrel.setItem(0, ItemStack(Material.IRON_CHESTPLATE, 13))
        slimefun = mockStatic(Slimefun::class.java, RETURNS_DEEP_STUBS)
        controller = Slimefun.getDatabaseManager().blockDataController
        `when`(controller.getBlockData(location)).thenReturn(null)
        record = mock(SlimefunBlockData::class.java)
        `when`(record.isDataLoaded).thenReturn(true)
        MobDataCardRefreshListener(
            plugin,
            refresh = { item ->
                if (item.type == Material.IRON_CHESTPLATE) item.clone().apply { type = Material.DIAMOND_CHESTPLATE }
                else null
            },
            isRefreshEnabled = { true },
            schedule = { _, task, _ -> queued.add(task); true },
            ownsLocation = { ownsRegion },
            // Intentionally omit isSlimefunBlock: exercise the production default callback.
        )
    }

    @AfterEach
    fun tearDown() {
        if (::slimefun.isInitialized) slimefun.close()
        MockBukkit.unmock()
    }

    @Test
    fun ordinaryContainerStillRefreshesAfterAnAbsentRecordLookup() {
        openAndDrain()
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 13), barrel.getItem(0))
        verify(controller).getBlockData(location)
        verifyNoMoreInteractions(controller)
    }

    @Test
    fun knownRecordPreventsAnyContainerRefreshWithoutReadingItsId() {
        `when`(controller.getBlockData(location)).thenReturn(record)
        openAndDrain()
        assertOriginal()
        verify(record).isDataLoaded
        verifyNoMoreInteractions(record)
        verify(controller).getBlockData(location)
        verifyNoMoreInteractions(controller)
    }

    @Test
    fun unloadedRecordIsLoadedButContainerRemainsUntouched() {
        `when`(controller.getBlockData(location)).thenReturn(record)
        `when`(record.isDataLoaded).thenReturn(false)
        openAndDrain()
        assertOriginal()
        verify(controller).getBlockData(location)
        verify(controller).loadBlockData(record)
        verifyNoMoreInteractions(controller)
        verify(record).isDataLoaded
        verifyNoMoreInteractions(record)
    }

    @Test
    fun failedLookupLeavesContainerUntouchedWhilePlayerRefreshStillCompletes() {
        `when`(controller.getBlockData(location)).thenThrow(IllegalStateException("record read failed"))
        player.inventory.setItem(0, ItemStack(Material.IRON_CHESTPLATE, 7))
        openAndDrain()
        assertOriginal()
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 7), player.inventory.getItem(0))
        verify(controller).getBlockData(location)
        verifyNoMoreInteractions(controller)
    }

    @Test
    fun failedLoadDoesNotAuthorizeContainerMutationOrLeaveRefreshPermanentlyQueued() {
        `when`(controller.getBlockData(location)).thenReturn(record)
        `when`(record.isDataLoaded).thenReturn(false)
        doThrow(IllegalStateException("payload read failed")).`when`(controller).loadBlockData(record)
        openAndDrain()
        assertOriginal()
        player.closeInventory()
        `when`(controller.getBlockData(location)).thenReturn(null)
        openAndDrain()
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 13), barrel.getItem(0))
    }

    @Test
    fun unreadablePayloadFlagDoesNotBecomeOrdinaryContainerPermission() {
        `when`(controller.getBlockData(location)).thenReturn(record)
        `when`(record.isDataLoaded).thenThrow(IllegalStateException("state read failed"))
        openAndDrain()
        assertOriginal()
        verify(controller).getBlockData(location)
        verifyNoMoreInteractions(controller)
    }

    @Test
    fun foreignRegionIsRejectedBeforeStorageLookup() {
        ownsRegion = false
        openAndDrain()
        assertOriginal()
        verifyNoInteractions(controller, record)
    }

    @Test
    fun virtualInventoryIsRejectedBeforeStorageLookup() {
        val virtual = server.createInventory(null, 9)
        virtual.setItem(0, ItemStack(Material.IRON_CHESTPLATE, 13))
        player.openInventory(virtual)
        drain()
        assertEquals(ItemStack(Material.IRON_CHESTPLATE, 13), virtual.getItem(0))
        verifyNoInteractions(controller, record)
    }

    private fun assertOriginal() = assertEquals(ItemStack(Material.IRON_CHESTPLATE, 13), barrel.getItem(0))
    private fun openAndDrain() { player.openInventory(barrel); drain() }
    private fun drain() { val tasks = queued.toList(); queued.clear(); tasks.forEach(Runnable::run) }
}
