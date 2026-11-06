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
import net.kyori.adventure.text.Component;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageItemPresentation;
import net.guizhanss.infinityexpansion2.utils.constant.Keys;
import org.bukkit.persistence.PersistentDataType;
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
    private Location storageLocation;

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
        // A prior disposable run may have left a completed PASS marker.
        // Clear only this phase's markers before scheduling work, so the
        // external observer cannot SIGKILL this JVM based on stale evidence.
        try {
            var directory=getDataFolder().toPath();
            Files.deleteIfExists(directory.resolve("results-"+phase+".txt"));
            Files.deleteIfExists(directory.resolve("results-"+phase+".txt.tmp"));
        } catch(Exception failure) {
            fail(failure);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this,()->{
            try { begin(); } catch(Throwable failure) { fail(failure); }
        },40L);
    }

    private void begin() {
        var world=Bukkit.getWorlds().getFirst();
        location=new Location(world,140,100,128);
        storageLocation=new Location(world,144,100,128);
        location.getChunk().load();
        storageLocation.getChunk().load();
        var forge=SlimefunItem.getById("IE_STORAGE_FORGE");
        require(forge instanceof StorageForge,"registered Forge exists in crash fixture");
        var storageUnit=SlimefunItem.getById("IE_STORAGE_UNIT_1");
        require(storageUnit instanceof StorageUnit,"registered Storage Unit exists for historical cosmetic fixture");
        if ("seed".equals(phase)) {
            require(!StoredBlockLookup.hasBlockInfo(location),"new isolated crash fixture");
            location.getBlock().setType(forge.getItem().getType());
            var controller=Slimefun.getDatabaseManager().getBlockDataController();
            var data=controller.createBlock(location,forge.getId());
            if(!data.isDataLoaded())controller.loadBlockData(data);
            data.setData("probe.marker","forge-crash-ledger");
            require(!StoredBlockLookup.hasBlockInfo(storageLocation),"new isolated historical Storage Unit");
            storageLocation.getBlock().setType(storageUnit.getItem().getType());
            var stored=controller.createBlock(storageLocation,storageUnit.getId());
            if(!stored.isDataLoaded())controller.loadBlockData(stored);
            stored.setData("probe.marker","cosmetic-repair-731");
            // Keep balance zero until its private slot has been installed; an
            // identity-less positive balance is intentionally fail-closed.
            stored.setData("stored_amount","0");
            stored.setData("void_excess","true");
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
                    if(handler!=null && handler.getClass().getName().contains("AbstractCraftingMachine")
                            && StoredBlockLookup.getInventory(storageLocation)!=null) {
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
        checkHistoricalCosmeticStorage();

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
            // Verify the oracle itself before the clean shutdown. A corrupt
            // snapshot would make a later old-or-new verdict meaningless.
            var persistedSnapshot=Files.readAllLines(snapshot,StandardCharsets.UTF_8);
            require(persistedSnapshot.size()==slots.length,
                "seed oracle contains exactly nine serialized slots");
            for(int i=0;i<slots.length;i++) {
                var expected=menu.getItemInSlot(slots[i]);
                var serialized=persistedSnapshot.get(i);
                var decoded="-".equals(serialized)?null:
                    ItemStack.deserializeBytes(Base64.getDecoder().decode(serialized));
                require(equalItem(expected,decoded),
                    "seed oracle round-trips the complete ingredient at slot "+i);
            }
            // Round-tripping item metadata alone is insufficient: a fixture
            // with a missing/empty ledger would still compare equal. Prove the
            // source snapshot carries the full historical-format balance.
            var serializedSource=persistedSnapshot.get(sourceIndex);
            require(!"-".equals(serializedSource),
                "seed oracle retains the populated source unit");
            var decodedSource=ItemStack.deserializeBytes(
                Base64.getDecoder().decode(serializedSource));
            var decodedLedger=PersistentDataAPI.get(decodedSource.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(decodedLedger!=null && decodedLedger.getAmount()==STORED_DIAMONDS
                && decodedLedger.getLimit()==((StorageUnit)source).getCapacity()
                && decodedLedger.getVoidExcess()
                && new ItemStack(Material.DIAMOND).equals(decodedLedger.getItemStack()),
                "seed oracle preserves all 731 diamonds, capacity, identity and void-excess");
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

    /**
     * An actual v2.0.17 Storage Unit record is created during seed using the
     * historical published addon JAR. Candidate JVMs must load it intact,
     * preserve the full 731 items across physical buffered output, and retain
     * the same Slimefun ID after normal restart and disposable SIGKILL.
     * Output production is accounted for; no artificial item refund is made.
     */
    private void checkHistoricalCosmeticStorage() throws Exception {
        var controller=Slimefun.getDatabaseManager().getBlockDataController();
        var data=controller.getBlockData(storageLocation);
        require(data!=null && "cosmetic-repair-731".equals(data.getData("probe.marker")),
            "historical Storage Unit block data retained");
        var menu=StoredBlockLookup.getInventory(storageLocation);
        require(menu!=null,"historical Storage Unit menu is available");
        var storedType=SlimefunItem.getById("IE_STORAGE_UNIT_1");
        var templateType=SlimefunItem.getById("IE_STORAGE_FORGE");
        require(storedType instanceof StorageUnit && templateType!=null,
            "historical storage and registered content types are available");
        Files.createDirectories(getDataFolder().toPath());
        var snapshot=getDataFolder().toPath().resolve("historical-cosmetic-template.bin");
        if ("seed".equals(phase)) {
            require(!Files.exists(snapshot),"historical cosmetic snapshot must be newly seeded");
            var wrapped=mutable(templateType.getItem());
            var meta=wrapped.getItemMeta();
            require(meta!=null && meta.displayName()!=null,
                "old registered Slimefun template has an Adventure name");
            meta.displayName(Component.text("").append(meta.displayName()));
            if(meta.lore()!=null && !meta.lore().isEmpty()) {
                var newLore=new ArrayList<Component>();
                for(var line:meta.lore())newLore.add(Component.text("").append(line));
                meta.lore(newLore);
            }
            require(wrapped.setItemMeta(meta),"old wrapped template retains its metadata");
            require(templateType.getId().equals(
                Slimefun.getItemDataService().getItemData(wrapped.getItemMeta()).orElse(null)),
                "old wrapped template retains its actual Slimefun ID");
            Files.write(snapshot,wrapped.serializeAsBytes());
            var privateDisplay=mutable(wrapped);
            privateDisplay.editMeta(m->m.getPersistentDataContainer().set(
                Keys.INSTANCE.getDISPLAY_ITEM(),PersistentDataType.BYTE,(byte)17));
            menu.replaceExistingItem(StorageUnit.ITEM_SLOT,privateDisplay);
            menu.markDirty();
            require(privateDisplay.equals(menu.getItemInSlot(StorageUnit.ITEM_SLOT)),
                "historical private display template persists before count assigned");
            // v2.0.17 and current source use the same two existing keys.
            data.setData("stored_amount","731");
            data.setData("void_excess","true");
            require("731".equals(data.getData("stored_amount")),
                "historical physical record retains exact 731-item balance");
            results.add("PASS seed: actual released IE2 2.0.17 Storage Unit has 731-item ledger and wrapped private template");
            return;
        }
        require(Files.isRegularFile(snapshot),"original native wrapped item snapshot exists");
        var historic=ItemStack.deserializeBytes(Files.readAllBytes(snapshot));
        // A SIGKILL can interrupt a markDirty flush after the in-memory
        // template was repaired. The original and canonical displays are
        // semantically equivalent; the recovered JVM must be able to retry
        // the exact native ticker safely before further output moves.
        if ("crash".equals(phase) || "verify".equals(phase)) {
            var ticker=storedType.getBlockTicker();
            require(ticker!=null && ticker.isSynchronized(),
                "registered Storage Unit uses its real synchronized ticker");
            var physicalSlots=((StorageUnit)storedType).getOutputSlots();
            require(physicalSlots.length==1,"one physical output slot before native repair");
            var beforeOutput=menu.getItemInSlot(physicalSlots[0]);
            results.add("DEBUG historical cosmetic pre-tick: stored="+data.getData("stored_amount")
                +"; buffered="+beforeOutput
                +"; private="+menu.getItemInSlot(StorageUnit.ITEM_SLOT));
            // Native Slimefun's periodic scheduler need not tick this fixture
            // before the crash probe runs. Exercise the EXACT registered
            // production BlockTicker, not a simulation or a helper shortcut.
            // An already occupied output remains protected by production logic.
            ticker.tick(storageLocation.getBlock(),storedType,data);
        }
        var currentDisplay=menu.getItemInSlot(StorageUnit.ITEM_SLOT);
        require(currentDisplay!=null && currentDisplay.getAmount()==1,
            "historical private identity template still exists");
        // The private display marker is functional PDC, not cosmetic lore.
        // Repair and crash recovery must retain its original typed byte value.
        var markerValue=currentDisplay.getItemMeta().getPersistentDataContainer().get(
            Keys.INSTANCE.getDISPLAY_ITEM(),PersistentDataType.BYTE);
        require(Byte.valueOf((byte)17).equals(markerValue),
            "historical private display marker retains exact typed PDC byte across repair/restart");
        var decoded=mutable(currentDisplay);
        decoded.editMeta(m->m.getPersistentDataContainer().remove(Keys.INSTANCE.getDISPLAY_ITEM()));
        require(StorageItemPresentation.matchesRegistered(historic,decoded),
            "historical template remains Slimefun-ID-identical without owner/model/lore loss");
        var outputSlots=((StorageUnit)storedType).getOutputSlots();
        require(outputSlots.length==1,"storage fixture retains one physical output slot");
        var output=menu.getItemInSlot(outputSlots[0]);
        int buffered=output==null||output.getType().isAir()?0:output.getAmount();
        if(buffered>0) {
            require(StorageItemPresentation.matchesRegistered(historic,output),
                "physical output retains its original Slimefun item identity");
        }
        var serialized=data.getData("stored_amount");
        require(serialized!=null,"historical stored amount is readable after restart");
        int amount=Integer.parseInt(serialized);
        require(amount>=0 && amount+buffered==731,
            "historical storage balance plus physical output must equal 731 (stored="
                +amount+", physical="+buffered+")");
        require("true".equalsIgnoreCase(data.getData("void_excess")),
            "historical Void Excess remains unchanged");
        var canonical=mutable(templateType.getItem());
        require(StorageItemPresentation.matchesRegistered(decoded,canonical),
            "saved template remains eligible only for harmless cosmetic comparison");
        // The published 2.0.17 seed starts with an empty physical output and
        // a truly empty, unstyled Adventure wrapper. On the upgraded JVM's
        // first tick the guarded repair should write the canonical template
        // before filling the output, without touching the ledger.
        require(decoded.isSimilar(canonical),
            "old wrapped private template must self-repair to canonical display on native tick");
        results.add("PASS "+phase+": historic v2.0.17 Storage Unit preserves exact 731 items, "
            +"Slimefun ID, Adventure lore and Void Excess across native restart/SIGKILL");
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
