@file:Suppress("deprecation")

package net.guizhanss.infinityexpansion2.implementation.listeners

import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.event.Event
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock

class MobDataCardRefreshListenerTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: Plugin
    private lateinit var player: PlayerMock
    private val queued = mutableListOf<Runnable>()
    private val retired = mutableListOf<Runnable>()
    private var enabled = true
    private var ownsLocation = true
    private var slimefunBlock = false

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        // MockBukkit's initial crafting view does not implement slot conversion. An opened
        // chest view provides the real InventoryClickEvent/InventoryDragEvent contracts.
        player.openInventory(server.createInventory(null, 9))
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun nativeEntitySchedulerDefersTheRefreshUntilTheNextTick() {
        MobDataCardRefreshListener(plugin, ::updated, isRefreshEnabled = { true })
        player.inventory.setItem(0, card(7))

        dispatch(held())
        assertEquals(Material.IRON_CHESTPLATE, player.inventory.getItem(0)?.type)

        server.scheduler.performOneTick()
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 7), player.inventory.getItem(0))
    }

    @Test
    fun queuedRefreshReadsCurrentSlotsAndCursorInsteadOfRestoringTheEventSnapshot() {
        listen()
        player.inventory.setItem(0, card(7))
        player.setItemOnCursor(card(5))
        dispatch(click())

        val replacement = ItemStack(Material.EMERALD, 3)
        player.inventory.setItem(0, replacement)
        player.inventory.setItem(1, card(11))
        player.setItemOnCursor(ItemStack(Material.DIRT, 2))
        drain()

        assertEquals(replacement, player.inventory.getItem(0))
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 11), player.inventory.getItem(1))
        assertEquals(ItemStack(Material.DIRT, 2), player.itemOnCursor)
    }

    @Test
    fun interactionEventsCoalesceAndAJoinAddsTheEnderChestToThePendingRequest() {
        listen()
        player.inventory.setItem(0, card(2))
        player.enderChest.setItem(0, card(9))
        player.setItemOnCursor(card(4))

        dispatch(held())
        dispatch(click())
        dispatch(drag())
        dispatch(pickup())
        dispatch(PlayerSwapHandItemsEvent(player, card(1), card(1)))
        dispatch(PlayerJoinEvent(player, "joined"))
        assertEquals(1, queued.size)
        drain()

        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 2), player.inventory.getItem(0))
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 9), player.enderChest.getItem(0))
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 4), player.itemOnCursor)
    }

    @Test
    fun cancelledTransactionsDoNotScheduleRefreshes() {
        listen()
        dispatch(held().apply { isCancelled = true })
        dispatch(click().apply { isCancelled = true })
        dispatch(drag().apply { isCancelled = true })
        dispatch(pickup().apply { isCancelled = true })
        dispatch(InventoryOpenEvent(player.openInventory).apply { isCancelled = true })
        dispatch(PlayerSwapHandItemsEvent(player, card(1), card(1)).apply { isCancelled = true })
        assertTrue(queued.isEmpty())
    }

    @Test
    fun virtualGuideTopInventoryIsUntouchedWhilePlayerCardsRefresh() {
        listen()
        val guide = server.createInventory(null, 9)
        guide.setItem(0, card(1))
        player.inventory.setItem(0, card(6))
        player.openInventory(guide)
        drain()

        assertEquals(card(1), guide.getItem(0))
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 6), player.inventory.getItem(0))
    }

    @Test
    fun ordinaryOwnedPlacedContainerRefreshesWhenOpened() {
        listen()
        val barrel = barrelInventory()
        barrel.setItem(0, card(13))
        player.openInventory(barrel)
        drain()

        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 13), barrel.getItem(0))
    }

    @Test
    fun remoteRegionAndSlimefunOwnedContainersAreSkipped() {
        listen()
        val barrel = barrelInventory()
        barrel.setItem(0, card(13))
        ownsLocation = false
        player.openInventory(barrel)
        drain()
        assertEquals(card(13), barrel.getItem(0))

        ownsLocation = true
        slimefunBlock = true
        dispatch(click())
        drain()
        assertEquals(card(13), barrel.getItem(0))
    }

    @Test
    fun aContainerClosedBeforeTheCallbackIsNeverWrittenThroughItsOldView() {
        listen()
        val barrel = barrelInventory()
        barrel.setItem(0, card(13))
        player.openInventory(barrel)
        player.closeInventory()
        drain()

        assertEquals(card(13), barrel.getItem(0))
    }

    @Test
    fun closingAnInventoryRefreshesCardsReturnedByTheCompletedCloseTransaction() {
        listen()
        player.setItemOnCursor(card(4))
        player.closeInventory()
        // Model the server returning the cursor after the close event is dispatched.
        player.setItemOnCursor(ItemStack(Material.AIR))
        player.inventory.setItem(5, card(4))
        assertEquals(1, queued.size)
        drain()

        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 4), player.inventory.getItem(5))
        assertTrue(player.itemOnCursor.type.isAir)
    }

    @Test
    fun retiredRequestsDoNotBlockLaterEvents() {
        listen()
        player.inventory.setItem(0, card(2))
        dispatch(held())
        retired.single().run()
        drain()
        assertEquals(card(2), player.inventory.getItem(0))

        dispatch(held())
        assertEquals(1, queued.size)
        drain()
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 2), player.inventory.getItem(0))
    }

    @Test
    fun quitInvalidatesQueuedWorkAndDoesNotLeaveThePlayerPending() {
        listen()
        player.inventory.setItem(0, card(2))
        dispatch(held())
        dispatch(PlayerQuitEvent(player, "quit"))
        drain()
        assertEquals(card(2), player.inventory.getItem(0))

        dispatch(held())
        drain()
        assertEquals(ItemStack(Material.DIAMOND_CHESTPLATE, 2), player.inventory.getItem(0))
    }

    @Test
    fun disabledRefreshSkipsBothNewRequestsAndAlreadyQueuedWork() {
        listen()
        player.inventory.setItem(0, card(2))
        enabled = false
        dispatch(held())
        assertTrue(queued.isEmpty())

        enabled = true
        dispatch(held())
        enabled = false
        drain()
        assertEquals(card(2), player.inventory.getItem(0))
    }

    @Test
    fun rejectedSchedulingDoesNotKeepAPlayerPermanentlyQueued() {
        var submissions = 0
        MobDataCardRefreshListener(
            plugin,
            ::updated,
            isRefreshEnabled = { true },
            schedule = { _, _, _ -> submissions++; false },
        )
        dispatch(held())
        dispatch(held())
        assertEquals(2, submissions)
    }

    private fun listen() = MobDataCardRefreshListener(
        plugin,
        ::updated,
        isRefreshEnabled = { enabled },
        schedule = { _, task, retire ->
            queued.add(task)
            retired.add(retire)
            true
        },
        ownsLocation = { ownsLocation },
        isSlimefunBlock = { slimefunBlock },
    )

    private fun updated(source: ItemStack): ItemStack? =
        if (source.type == Material.IRON_CHESTPLATE) source.clone().apply { type = Material.DIAMOND_CHESTPLATE } else null

    private fun card(amount: Int) = ItemStack(Material.IRON_CHESTPLATE, amount)

    private fun held() = PlayerItemHeldEvent(player, 0, 1)

    private fun click() = InventoryClickEvent(
        player.openInventory, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL,
    )

    private fun drag() = InventoryDragEvent(player.openInventory, null, card(1), false, mapOf(0 to card(1)))

    private fun pickup() = EntityPickupItemEvent(player, player.world.dropItem(player.location, card(1)), 0)

    private fun dispatch(event: Event) = server.pluginManager.callEvent(event)

    private fun drain() {
        val tasks = queued.toList()
        queued.clear()
        tasks.forEach(Runnable::run)
    }

    private fun barrelInventory(): Inventory {
        val block = player.world.getBlockAt(0, 60, 0)
        block.chunk.load()
        block.type = Material.BARREL
        return (block.state as Container).inventory
    }
}
