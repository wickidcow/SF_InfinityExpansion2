package verification;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout;
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageForge;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit;
import net.guizhanss.infinityexpansion2.utils.storage.StoredBlockLookup;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Disposable three-stage Forge crash-oracle. Seed shuts down cleanly; crash
 * intentionally remains running until the Python harness SIGKILLs its process;
 * verify accepts ONLY a complete old transaction or a complete new one.
 *
 * This is not a production test plugin and does not certify every interruption
 * instruction or historical server-world format.
 */
public final class NativeForgeCrashProbe extends JavaPlugin {
    private static final int STORED_DIAMONDS = 731;
    private final List<String> results = new ArrayList<>();
    private int assertions;
    private int failures;
    private String phase;
    private Location location;

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("ie2.forge.crash"))) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        phase=System.getProperty("ie2.forge.phase", "");
        if (!List.of("seed","crash","verify").contains(phase)) {
            fail(new IllegalArgumentException("Unknown crash probe phase"));
            return;
        }
        Bukkit.getScheduler().runTaskLater(this,()->{
            try { begin(); } catch(Throwable failure) { fail(failure); }
        },40L);
    }

    private void begin() {
        var world=Bukkit.getWorlds().getFirst();
        location=new Location(world,140,100,128);
        location.getChunk().load();
        var forge=SlimefunItem.getById("IE_STORAGE_FORGE");
        require(forge instanceof StorageForge,"registered Forge exists in crash fixture");
        if ("seed".equals(phase)) {
            require(!StoredBlockLookup.hasBlockInfo(location),"new isolated crash fixture");
            location.getBlock().setType(forge.getItem().getType());
            var controller=Slimefun.getDatabaseManager().getBlockDataController();
            var data=controller.createBlock(location,forge.getId());
            if(!data.isDataLoaded())controller.loadBlockData(data);
            data.setData("probe.marker","forge-crash-ledger");
        }
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    var menu=StoredBlockLookup.getInventory(location);
                    var handler=menu==null?null:menu.getMenuClickHandler(((StorageForge)forge).getCraftSlot());
                    // A saved menu may expose its decorative button handler
                    // before the owning-region callback is restored. Do not
                    // click the placeholder and mistake delayed setup for a
                    // failed craft. Fail after a bounded wait if it never heals.
                    if(handler!=null && handler.getClass().getName().contains("AbstractCraftingMachine")) {
                        cancel();
                        runPhase((StorageForge)forge,menu);
                        finish();
                    } else if(++attempts>100) {
                        cancel();
                        fail(new AssertionError("Real Forge click handler not restored on loaded menu; observed="
                            +(handler==null?"null":handler.getClass().getName())));
                    }
                } catch(Throwable failure) {cancel();fail(failure);}
            }
        }.runTaskTimer(this,1L,5L);
    }

    private void runPhase(StorageForge forge, BlockMenu menu) throws Exception {
        require("IE_STORAGE_FORGE".equals(StoredBlockLookup.getId(location)),
            "crash Forge block identity survives restart");
        var marker=Slimefun.getDatabaseManager().getBlockDataController().getBlockData(location);
        require("forge-crash-ledger".equals(marker.getData("probe.marker")),
            "crash Forge database marker persists");

        var source=SlimefunItem.getById("IE_STORAGE_UNIT_1");
        var target=SlimefunItem.getById("IE_STORAGE_UNIT_2");
        require(source instanceof StorageUnit && target instanceof StorageUnit,
            "registered real Storage Unit tiers exist");
        var recipe=forge.getRecipes().stream()
            .filter(r->SlimefunItem.getByItem(r.getOutput())==target).findFirst()
            .orElseThrow(()->new AssertionError("tier-2 Forge recipe missing"));
        var slots=MenuLayout.Companion.getCRAFTING_DEFAULT().getInputSlots();
        int outputSlot=MenuLayout.Companion.getCRAFTING_DEFAULT().getOutputSlots()[0];
        require(slots.length==9 && recipe.getInput().length==9,"real nine-slot Forge recipe");
        var snapshot=getDataFolder().toPath().resolve("precrash-inputs.txt");
        if("seed".equals(phase)) {
            var ingredients=Arrays.stream(recipe.getInput())
                .map(NativeForgeCrashProbe::mutable).toArray(ItemStack[]::new);
            int sourceIndex=-1;
            for(int i=0;i<ingredients.length;i++) {
                if(ingredients[i]!=null && SlimefunItem.getByItem(ingredients[i])==source) {
                    require(sourceIndex<0,"only one source unit");
                    sourceIndex=i;
                }
            }
            require(sourceIndex>=0,"source unit is in registered recipe");
            var old=mutable(source.getItem());
            var meta=old.getItemMeta();
            PersistentDataAPI.set(meta,StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentStorageCacheType.getTYPE(),
                new StorageCache(new ItemStack(Material.DIAMOND),STORED_DIAMONDS,
                    ((StorageUnit)source).getCapacity(),true));
            require(old.setItemMeta(meta),"populated source PDC saved in old unit");
            ingredients[sourceIndex]=old;
            for(int i=0;i<slots.length;i++)menu.replaceExistingItem(slots[i],ingredients[i]);
            menu.replaceExistingItem(outputSlot,new ItemStack(Material.AIR));
            require(recipe.check(Arrays.stream(slots).mapToObj(menu::getItemInSlot)
                .toArray(ItemStack[]::new),false),"native Forge seed matches original recipe");
            getDataFolder().mkdirs();
            List<String> encoded=new ArrayList<>();
            for(int slot:slots) {
                var item=menu.getItemInSlot(slot);
                encoded.add(item==null||item.getType().isAir() ? "-"
                    :Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
            // Publish the seed oracle only when every serialized slot is ready.
            // A truncated fixture must never be accepted as historical state.
            var temporarySnapshot=snapshot.resolveSibling(snapshot.getFileName()+".tmp");
            Files.write(temporarySnapshot,encoded,StandardCharsets.UTF_8);
            Files.move(temporarySnapshot,snapshot,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            menu.markDirty();
            require(isEmpty(menu.getItemInSlot(outputSlot)),"seed output remains empty");
            var seedHandler=menu.getMenuClickHandler(forge.getCraftSlot());
            require(seedHandler!=null,"newly registered Forge has the craft button handler");
            results.add("DEBUG seed callback="+seedHandler.getClass().getName()
                +"; menuLocked="+menu.locked());
            results.add("PASS seed: persisted full nine-input Forge recipe with 731 old-unit diamonds");
            return;
        }

        require(Files.isRegularFile(snapshot),"clean-seed inventory snapshot exists");
        var expectedLines=Files.readAllLines(snapshot,StandardCharsets.UTF_8);
        require(expectedLines.size()==slots.length,"all nine old recipe snapshots retained");
        var oldInputs=new ItemStack[slots.length];
        int sourceIndex=-1;
        for(int i=0;i<slots.length;i++) {
            oldInputs[i]="-".equals(expectedLines.get(i))?null:
                ItemStack.deserializeBytes(Base64.getDecoder().decode(expectedLines.get(i)));
            if(oldInputs[i]!=null && SlimefunItem.getByItem(oldInputs[i])==source)sourceIndex=i;
        }
        require(sourceIndex>=0,"old fixture retains the registered source ID");
        var expectedSource=oldInputs[sourceIndex];
        if("crash".equals(phase)) {
            for(int i=0;i<slots.length;i++)
                require(equalItem(oldInputs[i],menu.getItemInSlot(slots[i])),
                    "precrash source ingredient persisted intact: "+i);
            require(isEmpty(menu.getItemInSlot(outputSlot)),"precrash output is empty");
            var reread=Arrays.stream(slots).mapToObj(menu::getItemInSlot)
                .toArray(ItemStack[]::new);
            require(recipe.check(reread,false),
                "saved nine-slot Forge recipe still matches after clean server restart");
            var wrapped=ItemStackWrapper.wrapArray(reread);
            var registeredMatches=forge.getRecipes().stream()
                .filter(candidate->candidate.check(wrapped,false)).toList();
            require(!registeredMatches.isEmpty(),
                "production wrapped recipe matching accepts the reloaded input");
            require(registeredMatches.getFirst()==recipe,
                "production wrapped recipe selects the same Storage Unit tier");
            var recoveredCache=PersistentDataAPI.get(menu.getItemInSlot(slots[sourceIndex]).getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(recoveredCache!=null && recoveredCache.getAmount()==STORED_DIAMONDS,
                "stored source PDC still decodes to all 731 diamonds");
            var player=probePlayer();
            require(recipe.check(player),"test player can use the registered upgrade result");
            var prepare=StorageForge.class.getDeclaredMethod(
                "prepareOutput",ItemStack[].class,ItemStack.class);
            prepare.setAccessible(true);
            var prepared=(ItemStack)prepare.invoke(forge,reread,mutable(recipe.getOutput()));
            require(prepared!=null,
                "production Forge preflight accepts PDC ledger after saved-menu reload");
            require(upgraded(prepared,(StorageUnit)target),
                "prepared restarted Forge result retains exact stock and Slimefun identity");
            require(menu.fits(prepared,outputSlot),
                "physical output slot accepts the prepared ledger before craft");
            var handler=menu.getMenuClickHandler(forge.getCraftSlot());
            require(handler!=null,"registered Forge button callback exists");
            results.add("DEBUG restarted callback="+handler.getClass().getName()
                +"; menuLocked="+menu.locked());
            require(handler.getClass().getName().contains("AbstractCraftingMachine"),
                "hydrated Forge menu must restore the registered craft handler, not a placeholder");
            require(!handler.onClick(player,forge.getCraftSlot(),
                    menu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "real crafting button handles crash fixture");
            var actualOutput=menu.getItemInSlot(outputSlot);
            results.add("DEBUG postclick: output="+actualOutput+"; firstInput="
                +menu.getItemInSlot(slots[sourceIndex])+"; target="+target.getId());
            require(upgraded(actualOutput,(StorageUnit)target),
                "real craft creates exactly 731 backed diamonds");
            for(int slot:slots)require(isEmpty(menu.getItemInSlot(slot)),
                "real craft consumes every input before crash");
            results.add("PASS crash: real Forge click completed in memory; SIGKILL requested before clean shutdown");
            return;
        }

        var out=menu.getItemInSlot(outputSlot);
        var in=menu.getItemInSlot(slots[sourceIndex]);
        boolean oldPresent=equalItem(expectedSource,in);
        boolean newPresent=upgraded(out,(StorageUnit)target);
        require(oldPresent!=newPresent,
            "SIGKILL restart must keep exactly one 731-diamond ledger, never neither or both");
        if(oldPresent) {
            require(isEmpty(out),"rolled-back old transaction must not retain another output");
            for(int i=0;i<slots.length;i++)
                require(equalItem(oldInputs[i],menu.getItemInSlot(slots[i])),
                    "rolled-back old transaction must retain all inputs: "+i);
            // A rolled-back transaction must remain craftable exactly once.
            // This checks retry conservation after an interrupted prior craft;
            // it is not a substitute for fault injection inside commitCraft.
            var retryHandler=menu.getMenuClickHandler(forge.getCraftSlot());
            require(retryHandler!=null && retryHandler.getClass().getName().contains("AbstractCraftingMachine"),
                "rollback retry uses the registered Forge handler");
            require(!retryHandler.onClick(probePlayer(),forge.getCraftSlot(),
                    menu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "rollback retry click is handled");
            require(upgraded(menu.getItemInSlot(outputSlot),(StorageUnit)target),
                "rollback retry delivers exactly one backed 731-diamond output");
            for(int slot:slots)require(isEmpty(menu.getItemInSlot(slot)),
                "rollback retry consumes each restored ingredient exactly once");
            // Clicking a second time must not mint another ledger.
            require(!retryHandler.onClick(probePlayer(),forge.getCraftSlot(),
                    menu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "repeated rollback retry click is handled");
            require(upgraded(menu.getItemInSlot(outputSlot),(StorageUnit)target),
                "second retry cannot duplicate the backed output");
            for(int slot:slots)require(isEmpty(menu.getItemInSlot(slot)),
                "second retry cannot restore consumed ingredients");
            results.add("PASS verify: complete pre-craft ledger recovered; retry commits once without duplicate");
        } else {
            for(int slot:slots)require(isEmpty(menu.getItemInSlot(slot)),
                "committed new output must have consumed every old ingredient");
            // The committed-output branch must be idempotent too: a repeated
            // registered click cannot mint a second ledger from empty inputs.
            var committedHandler=menu.getMenuClickHandler(forge.getCraftSlot());
            require(committedHandler!=null && committedHandler.getClass().getName().contains("AbstractCraftingMachine"),
                "committed restart retains the real Forge handler");
            require(!committedHandler.onClick(probePlayer(),forge.getCraftSlot(),
                    menu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "committed restart repeated click is handled");
            require(upgraded(menu.getItemInSlot(outputSlot),(StorageUnit)target),
                "committed restart repeated click preserves exactly one backed output");
            for(int slot:slots)require(isEmpty(menu.getItemInSlot(slot)),
                "committed restart repeated click cannot restore consumed ingredients");
            results.add("PASS verify: complete new 731-diamond ledger recovered; repeated click cannot duplicate");
        }
    }

    private boolean upgraded(ItemStack item, StorageUnit target) {
        if(item==null || SlimefunItem.getByItem(item)!=target || item.getAmount()!=1)return false;
        var cache=PersistentDataAPI.get(item.getItemMeta(),StorageUnit.Companion.getSTORAGE_KEY(),
            PersistentStorageCacheType.getTYPE());
        return cache!=null && cache.getAmount()==STORED_DIAMONDS
            && cache.getLimit()==target.getCapacity() && cache.getVoidExcess()
            && new ItemStack(Material.DIAMOND).equals(cache.getItemStack());
    }

    private static boolean equalItem(ItemStack a, ItemStack b) {
        return (a==null||a.getType().isAir())?(b==null||b.getType().isAir()):a.equals(b);
    }
    private static boolean isEmpty(ItemStack item) {
        return item==null||item.getType().isAir()||item.getAmount()<=0;
    }
    private static ItemStack mutable(ItemStack original) {
        if(original==null)return null;
        var copy=new ItemStack(original.getType(),original.getAmount());
        if(original.hasItemMeta())copy.setItemMeta(original.getItemMeta());
        return copy;
    }
    private Player probePlayer() {
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class},(proxy,method,args)->{
                return switch(method.getName()) {
                    case "getName" -> "NativeForgeCrashProbe";
                    case "getUniqueId" -> new UUID(0,731);
                    case "getWorld" -> location.getWorld();
                    case "getLocation" -> location.clone();
                    case "getServer" -> Bukkit.getServer();
                    case "hasPermission","isOp","isOnline" -> true;
                    case "getLocale" -> "en_us";
                    case "locale" -> java.util.Locale.US;
                    case "equals" -> proxy==args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "NativeForgeCrashProbe(player-interface)";
                    default -> {
                        var type=method.getReturnType();
                        if(type==boolean.class)yield false;
                        if(type==int.class)yield 0;
                        if(type==long.class)yield 0L;
                        if(type==float.class)yield 0f;
                        if(type==double.class)yield 0d;
                        if(type==byte.class)yield (byte)0;
                        if(type==short.class)yield (short)0;
                        if(type==char.class)yield (char)0;
                        yield null;
                    }
                };
            });
    }
    private void require(boolean condition,String text) {
        assertions++;
        if(!condition)throw new AssertionError(text);
    }
    private void fail(Throwable failure) {
        failures++;
        results.add("FAIL "+failure);
        getLogger().log(Level.SEVERE,"Native Forge crash probe failure",failure);
        finish();
    }
    private void finish() {
        try {
            getDataFolder().mkdirs();
            results.add("Assertions: "+assertions+"; failures: "+failures);
            // The external SIGKILL harness polls for this file. Publish a
            // complete result atomically so it never observes a partial PASS
            // and terminates the JVM before all assertions are recorded.
            var directory=getDataFolder().toPath();
            var destination=directory.resolve("results-"+phase+".txt");
            var temporary=directory.resolve("results-"+phase+".txt.tmp");
            Files.write(temporary,results,StandardCharsets.UTF_8);
            Files.move(temporary,destination,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception failure) {
            getLogger().log(Level.SEVERE,"Cannot persist crash-probe results",failure);
        }
        // In the crash phase ONLY the Python harness is allowed to SIGKILL this
        // exact disposable JVM after observing and validating the result file.
        if(!"crash".equals(phase))Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5L);
    }
}
