package ie2breakprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Disposable native Player.breakBlock/entity-event/actual-client-placement verification. */
public final class NativeIE2BreakProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey OWNER = new NamespacedKey("ie2-break-probe", "owner");
    private static final String STORAGE_CLASS = "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit";
    private static final String CACHE_CLASS = "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache";
    private SlimefunItem unit;
    private Class<?> cacheType;
    private NamespacedKey storageKey;
    private PersistentDataType<?, ?> storageType;
    private Player player;
    private String phase;
    private int capacity;
    private int inputSlot;
    private int outputSlot;
    private Location active;
    private BlockMenu activeMenu;
    private int cancelSpawn;
    private boolean injectReentry;
    private boolean reentryVerified;
    private boolean actualPlacement;
    private boolean finished;
    private ItemStack placementShell;
    private final List<Item> drops = new ArrayList<>();
    private final List<Location> cases = new ArrayList<>();

    private Path file(String name) { return getDataFolder().toPath().getParent().resolve("native-break-" + name); }
    private Location placedLocation() { return new Location(Bukkit.getWorlds().getFirst(), 160, 100, 176); }

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("ie2.break.probe"))) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        try { phase = Files.readString(file("phase.txt")).trim(); }
        catch (Exception failure) { fail(failure); return; }
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    var plugin = Bukkit.getPluginManager().getPlugin("InfinityExpansion2");
                    if (plugin != null && plugin.isEnabled() && Slimefun.getRegistry() != null) {
                        unit = Slimefun.getRegistry().getAllSlimefunItems().stream()
                            .filter(i -> i.getClass().getName().equals(STORAGE_CLASS))
                            .sorted(Comparator.comparing(SlimefunItem::getId)).findFirst().orElse(null);
                        if (unit != null) {
                            cacheType = Class.forName(CACHE_CLASS, true, unit.getClass().getClassLoader());
                            capacity = (int) unit.getClass().getMethod("getCapacity").invoke(unit);
                            inputSlot = ((int[]) unit.getClass().getMethod("getInputSlots").invoke(unit))[0];
                            outputSlot = ((int[]) unit.getClass().getMethod("getOutputSlots").invoke(unit))[0];
                            // Reading the field avoids resolving unrelated optional SlimeHUD
                            // signatures on the Kotlin companion when SlimeHUD is absent.
                            Field storageField = unit.getClass().getDeclaredField("STORAGE_KEY");
                            storageField.setAccessible(true);
                            storageKey = (NamespacedKey) storageField.get(null);
                            Class<?> persistent = Class.forName(
                                "net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType",
                                true, unit.getClass().getClassLoader());
                            storageType = (PersistentDataType<?, ?>) persistent.getMethod("getTYPE").invoke(null);
                            cancel();
                            if (phase.equals("seed")) createCases(); else awaitRestored();
                            return;
                        }
                    }
                    if (++attempts > 120) throw new AssertionError("IE2 did not initialize");
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    private void createCases() throws Exception {
        var database = Slimefun.getDatabaseManager().getBlockDataController();
        for (int i = 0; i < 5; i++) {
            Location location = new Location(Bukkit.getWorlds().getFirst(), 160 + i * 3, 100, 160);
            location.getChunk().load();
            location.clone().add(0, -1, 0).getBlock().setType(Material.STONE);
            require(StorageCacheUtils.getBlock(location) == null, "Fixture location must be new");
            location.getBlock().setType(unit.getItem().getType());
            var data = database.createBlock(location, unit.getId());
            if (!data.isDataLoaded()) database.loadBlockData(data);
            cases.add(location);
        }
        placedLocation().getChunk().load();
        placedLocation().clone().add(0, -1, 0).getBlock().setType(Material.STONE);
        for (int x = -1; x <= 1; x++) for (int z = 1; z <= 3; z++) {
            placedLocation().clone().add(x, -1, z).getBlock().setType(Material.STONE);
        }
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    if (cases.stream().allMatch(l -> StorageCacheUtils.getMenu(l) != null)) {
                        Files.writeString(file("ready.txt"), "READY: native break fixture\n");
                        cancel();
                    } else if (++attempts > 100) throw new AssertionError("Real block menus did not initialize");
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!"IE2BreakProbe".equals(joining.getName()) || joining.getAddress() == null
            || !joining.getAddress().getAddress().isLoopbackAddress() || player != null) {
            joining.kickPlayer("Disposable loopback fixture only"); return;
        }
        player = joining;
        player.setOp(true);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.setGravity(false);
        player.teleport(new Location(Bukkit.getWorlds().getFirst(), 160.5, 101, 165.5));
        player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_PICKAXE));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { runCases(); } catch (Throwable failure) { fail(failure); }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onSpawn(ItemSpawnEvent event) {
        if (active == null || event.getLocation().distanceSquared(active) > 8) return;
        Item entity = event.getEntity();
        entity.setPickupDelay(Integer.MAX_VALUE);
        drops.add(entity);
        if (drops.size() == cancelSpawn) event.setCancelled(true);
        if (injectReentry && drops.size() == 1) {
            try {
                Object original = cache(active);
                require((boolean) cacheType.getMethod("hasBlockedTransfer").invoke(original), "Break must own cache during spawn");
                var recursive = new BlockBreakEvent(active.getBlock(), player);
                var handler = unit.getClass().getDeclaredMethod("onBreak", BlockBreakEvent.class, BlockMenu.class);
                handler.setAccessible(true);
                handler.invoke(unit, recursive, activeMenu);
                require(recursive.isCancelled(), "Injected same-cache recursive break must be refused");
                require(!(boolean) cacheType.getMethod("beginInputReceipt").invoke(original), "Cross-direction input must be blocked");
                reentryVerified = true;
            } catch (Throwable failure) { throw new IllegalStateException("Native reentry assertion failed", failure); }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer() == player && event.getBlockPlaced().getLocation().equals(placedLocation())) {
            actualPlacement = true;
        }
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() != player || !event.getMessage().equals("/breakfixture placed")) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                require(actualPlacement, "Require an actual client BlockPlaceEvent");
                verifyPlaced();
                finish("PASS: seed break-cases=5 exact-ledgers=true native-spawn-cancellation=true partial-rollback=true"
                    + " same-cache-reentry=true actual-client-placement=true");
            } catch (Throwable failure) { fail(failure); }
        }, 10L);
    }

    @SuppressWarnings("unchecked") private Map<Object, Object> caches() throws Exception {
        return (Map<Object, Object>) unit.getClass().getMethod("getCaches").invoke(unit);
    }
    private Object cache(Location location) throws Exception { return caches().get(new BlockPosition(location)); }
    private int amount(Object cache) throws Exception { return (int) cacheType.getMethod("getAmount").invoke(cache); }
    private ItemStack template(Object cache) throws Exception { return (ItemStack) cacheType.getMethod("getItemStack").invoke(cache); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private Object decode(ItemStack shell) {
        return PersistentDataAPI.get(shell.getItemMeta(), storageKey, (PersistentDataType) storageType);
    }
    private ItemStack token(String owner, int amount) {
        var stack = new ItemStack(Material.DIAMOND, amount);
        stack.editMeta(meta -> {
            meta.setDisplayName("Native IE2 break ledger");
            meta.setLore(List.of("retained metadata", owner));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner);
        });
        return stack;
    }

    private void runCases() throws Exception {
        // Each seed is explicit disposable test state; the events and item PDC are native.
        runCase(0, 0, 11, false, false, false, false);
        runCase(1, capacity, 64, true, false, false, false);
        runCase(2, 23, 11, false, true, false, false);
        runCase(3, capacity, 64, true, false, true, false);
        runCase(4, 23, 11, false, false, false, true);
        require(reentryVerified, "Actual world-spawn callback must reach reentry assertion");
        require(placementShell != null, "Need verified shell from zero-cache case");
        active = null;
        player.getInventory().clear();
        player.getInventory().setItem(0, placementShell.clone());
        player.getInventory().setHeldItemSlot(0);
        player.teleport(placedLocation().clone().add(0.5, 0, 2.5));
        player.sendMessage("NATIVEBREAK:place-ready");
    }

    private void runCase(int index, int stored, int output, boolean voidExcess,
                         boolean foreign, boolean cancelSecond, boolean reentry) throws Exception {
        active = cases.get(index);
        activeMenu = StorageCacheUtils.getMenu(active);
        Object original = cacheType.getConstructor(ItemStack.class, int.class, int.class, boolean.class)
            .newInstance(token("stored", 1), stored, capacity, voidExcess);
        caches().put(new BlockPosition(active), original);
        ItemStack physicalOutput = token(foreign ? "foreign" : "stored", output);
        ItemStack physicalInput = token("input", 3);
        activeMenu.replaceExistingItem(inputSlot, physicalInput.clone());
        activeMenu.replaceExistingItem(outputSlot, physicalOutput.clone());
        drops.clear(); cancelSpawn = cancelSecond ? 2 : 0; injectReentry = reentry;
        boolean broken = player.breakBlock(active.getBlock());
        if (cancelSecond) {
            require(!broken && active.getBlock().getType() == unit.getItem().getType(), "Cancelled spawn must cancel block break");
            require(drops.size() == 2 && drops.stream().noneMatch(Item::isValid), "Partial spawn rollback must remove exact prior entities");
            require(cache(active) == original && amount(original) == stored, "Cancelled break must retain original cache");
            require(physicalInput.equals(activeMenu.getItemInSlot(inputSlot))
                && physicalOutput.equals(activeMenu.getItemInSlot(outputSlot)), "Cancelled break must retain exact physical sources");
            require(!(boolean) cacheType.getMethod("hasBlockedTransfer").invoke(original), "Verified rollback must release ownership");
            cancelSpawn = 0; drops.clear();
            broken = player.breakBlock(active.getBlock());
        }
        require(broken && active.getBlock().getType().isAir(), "Healthy break must complete");
        long storedLedger = 0, physicalLedger = 0, inputLedger = 0, foreignLedger = 0;
        int shells = 0;
        for (Item entity : drops) {
            require(entity.isValid(), "Healthy drop receipt must survive");
            ItemStack actual = entity.getItemStack();
            Object packed = decode(actual);
            if (packed != null) {
                shells++;
                require(actual.getAmount() == 1 && token("stored", 1).isSimilar(template(packed)), "Shell must retain exact stored owner");
                require((int) cacheType.getMethod("getLimit").invoke(packed) == capacity
                    && (boolean) cacheType.getMethod("getVoidExcess").invoke(packed) == voidExcess, "Shell must retain original limit/policy");
                storedLedger += amount(packed);
                if (index == 0) { placementShell = actual.clone(); entity.remove(); }
            } else if (token("input", 1).isSimilar(actual)) inputLedger += actual.getAmount();
            else if (token("foreign", 1).isSimilar(actual)) foreignLedger += actual.getAmount();
            else if (token("stored", 1).isSimilar(actual)) physicalLedger += actual.getAmount();
            else throw new AssertionError("Unexpected drop identity: " + actual);
        }
        require(shells == 1 && inputLedger == 3, "Exactly one shell and every physical input must survive");
        require(storedLedger + physicalLedger == (long) stored + (foreign ? 0 : output), "Stored-owner quantity must be conserved");
        require(foreignLedger == (foreign ? output : 0), "Foreign owner must remain physical and unchanged");
        require(cache(active) == null, "Successful break must retire the exact runtime cache");
        getLogger().info("PASS: native break case=" + index + " stored=" + storedLedger
            + " physical=" + physicalLedger + " foreign=" + foreignLedger + " input=" + inputLedger);
    }

    private void awaitRestored() {
        placedLocation().getChunk().load();
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    if (StorageCacheUtils.getMenu(placedLocation()) != null && cache(placedLocation()) != null) {
                        verifyPlaced(); cancel();
                        finish("PASS: verify independent-JVM placed-shell total=11 exact-identity=true");
                    } else if (++attempts > 120) throw new AssertionError("Placed storage did not reload");
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    private void verifyPlaced() throws Exception {
        require(StorageCacheUtils.getSlimefunItem(placedLocation()) == unit, "Actual placed storage record must exist");
        Object restored = cache(placedLocation());
        require(restored != null && token("stored", 1).isSimilar(template(restored)), "Placed/restarted cache identity must match");
        BlockMenu menu = StorageCacheUtils.getMenu(placedLocation());
        long total = amount(restored);
        for (int slot : new int[] {inputSlot, outputSlot}) {
            ItemStack physical = menu.getItemInSlot(slot);
            if (physical != null && !physical.getType().isAir()) {
                require(token("stored", 1).isSimilar(physical), "Placed physical output must retain exact owner");
                total += physical.getAmount();
            }
        }
        require(total == 11, "Placed and restarted storage must retain exactly11 original items, found=" + total);
    }

    private void fail(Throwable failure) { failure.printStackTrace(); finish("FAIL: " + phase + " " + failure); }
    private void finish(String message) {
        if (finished) return;
        finished = true;
        try { Files.writeString(file("result.txt"), message + "\n"); }
        catch (Exception failure) { failure.printStackTrace(); }
        getLogger().info(message);
        if (player != null) player.sendMessage("NATIVEBREAK:" + (message.startsWith("PASS") ? "PASS" : "FAIL"));
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
