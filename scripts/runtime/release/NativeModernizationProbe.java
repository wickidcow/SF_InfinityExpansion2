package verification;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryKey;
import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.UUID;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout;
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType;
import net.guizhanss.infinityexpansion2.core.recipes.MachineRecipe;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageItemPresentation;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageForge;
import net.guizhanss.infinityexpansion2.implementation.items.machines.GeoQuarry;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit;
import net.guizhanss.infinityexpansion2.implementation.items.tools.Oscillator;
import net.guizhanss.infinityexpansion2.utils.events.BowDamageEvents;
import net.guizhanss.infinityexpansion2.utils.items.ModernText;
import net.guizhanss.infinityexpansion2.utils.storage.StoredBlockLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Disposable native metadata, cancellable-event, live-record and clean-restart verification. */
public final class NativeModernizationProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey OWNER=new NamespacedKey("probe","owner");
    private static final NamespacedKey BYTES=new NamespacedKey("probe","bytes");
    private final List<String> results=new ArrayList<>();
    private int assertions;
    private int failures;
    private int preflights;
    private String phase;
    private Location recordLocation;
    private Location menuLocation;
    private Location forgeLocation;
    private Location quarryLocation;
    private EntityDamageByEntityEvent active;

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("ie2.release.probe"))) {
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        phase=System.getProperty("ie2.release.phase", "");
        if (!phase.equals("seed") && !phase.equals("verify")) { fail(new IllegalStateException("Unknown phase")); return; }
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try { begin(); } catch(Throwable failure) { fail(failure); }
        },40L);
    }

    private void begin() throws Exception {
        require(Bukkit.getPluginManager().getPlugin("InfinityExpansion2").isEnabled(),"IE2 enabled");
        var world=Bukkit.getWorlds().getFirst();
        recordLocation=new Location(world,128,100,128);
        menuLocation=new Location(world,132,100,128);
        forgeLocation=new Location(world,136,100,128);
        quarryLocation=new Location(world,140,100,128);
        recordLocation.getChunk().load(); menuLocation.getChunk().load(); forgeLocation.getChunk().load(); quarryLocation.getChunk().load();
        var controller=Slimefun.getDatabaseManager().getBlockDataController();
        if (phase.equals("seed")) {
            require(!StoredBlockLookup.hasBlockInfo(recordLocation),"new record fixture");
            require(!StoredBlockLookup.hasBlockInfo(menuLocation),"new menu fixture");
            require(!StoredBlockLookup.hasBlockInfo(forgeLocation),"new Storage Forge fixture");
            require(!StoredBlockLookup.hasBlockInfo(quarryLocation),"new GEO Quarry fixture");
            recordLocation.getBlock().setType(Material.STONE);
            var record=controller.createBlock(recordLocation,"IE2_NATIVE_UNKNOWN_ID");
            if (!record.isDataLoaded()) controller.loadBlockData(record);
            record.setData("probe.marker","preserve-unknown-record");
            var item=SlimefunItem.getById("IE_INFINITY_WORKBENCH");
            require(item!=null,"real workbench registered");
            menuLocation.getBlock().setType(item.getItem().getType());
            var data=controller.createBlock(menuLocation,item.getId());
            if (!data.isDataLoaded()) controller.loadBlockData(data);
            data.setData("probe.marker","preserve-menu-record");
            var forge=SlimefunItem.getById("IE_STORAGE_FORGE");
            require(forge instanceof StorageForge,"registered Storage Forge exists for click fixture");
            forgeLocation.getBlock().setType(forge.getItem().getType());
            var forgeData=controller.createBlock(forgeLocation,forge.getId());
            if (!forgeData.isDataLoaded()) controller.loadBlockData(forgeData);
            var quarry=SlimefunItem.getById("IE_GEO_QUARRY");
            require(quarry instanceof GeoQuarry,"registered IE2 GEO Quarry available for output fixture");
            quarryLocation.getBlock().setType(quarry.getItem().getType());
            var quarryData=controller.createBlock(quarryLocation,quarry.getId());
            if (!quarryData.isDataLoaded()) controller.loadBlockData(quarryData);
        }
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    if (StoredBlockLookup.getInventory(menuLocation)!=null
                        && StoredBlockLookup.getInventory(forgeLocation)!=null
                        && StoredBlockLookup.getInventory(quarryLocation)!=null) {
                        cancel(); checkNative(); finish();
                    } else if (++attempts>100) {
                        cancel(); fail(new AssertionError("Native workbench, Storage Forge or GEO Quarry inventory did not load"));
                    }
                } catch(Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this,1L,5L);
    }

    @SuppressWarnings("deprecation") // Native historical oracle, never packaged production code.
    private void checkNative() throws Exception {
        var controller=Slimefun.getDatabaseManager().getBlockDataController();
        // The core deliberately omits unknown IDs from its cache after chunk reload.
        // Keep legacy lookup parity; the Python harness independently proves that the
        // actual SQLite record and payload survive both clean shutdowns unchanged.
        if (phase.equals("seed")) {
            require(StoredBlockLookup.hasBlockInfo(recordLocation),"unknown stored record exists before reload");
            require("IE2_NATIVE_UNKNOWN_ID".equals(StoredBlockLookup.getId(recordLocation)),"stored identity not inferred from registry");
            require("preserve-unknown-record".equals(controller.getBlockData(recordLocation).getData("probe.marker")),"unknown record payload retained");
        } else {
            require(StoredBlockLookup.hasBlockInfo(recordLocation)==BlockStorage.hasBlockInfo(recordLocation),"unknown-ID reload visibility matches historical facade");
            require(Objects.equals(StoredBlockLookup.getId(recordLocation),BlockStorage.getLocationInfo(recordLocation,"id")),"unknown-ID reload identity matches historical facade");
        }
        require("IE_INFINITY_WORKBENCH".equals(StoredBlockLookup.getId(menuLocation)),"menu record identity retained");
        require("preserve-menu-record".equals(controller.getBlockData(menuLocation).getData("probe.marker")),"menu payload retained");
        var menu=StoredBlockLookup.getInventory(menuLocation);
        require(menu==controller.getBlockData(menuLocation).getBlockMenu(),"lookup returns authoritative native menu");
        var snapshot=getDataFolder().toPath().resolve("preserved-item.bin");
        if (phase.equals("seed")) {
            var item=new ItemStack(Material.PAPER,37);
            var meta=item.getItemMeta();
            var rich=Component.text("Owner RGB",TextColor.color(0x12ab34))
                .hoverEvent(HoverEvent.showText(Component.translatable("entity.minecraft.vex"))).insertion("keep insertion");
            meta.lore(List.of(rich,Component.translatable("item.minecraft.diamond")));
            meta.getPersistentDataContainer().set(OWNER,PersistentDataType.LONG,9_000_000_001L);
            meta.getPersistentDataContainer().set(BYTES,PersistentDataType.BYTE_ARRAY,new byte[]{-1,0,42,127});
            meta.setItemModel(new NamespacedKey("probe","artwork"));
            var original=meta.clone();
            var cache=new StorageCache(new ItemStack(Material.STONE),96,1024,false);
            cache.addLore(meta);
            require(meta.lore().subList(0,2).equals(original.lore()),"actual storage lore preserves rich components");
            require(meta.getPersistentDataContainer().equals(original.getPersistentDataContainer()),"actual storage lore retains typed PDC");
            require(cache.getAmount()==96 && cache.getLimit()==1024,"lore does not alter stored balance");
            require(item.setItemMeta(meta),"native metadata accepted");
            require(item.getAmount()==37,"stack quantity retained");
            getDataFolder().mkdirs(); Files.write(snapshot,item.serializeAsBytes());
            // Slot zero is a physical workbench input, not a regenerated UI icon.
            menu.replaceExistingItem(0,item.clone()); menu.markDirty();
            require(menu.getItemInSlot(0).equals(item),"native menu physical write retained");
        } else {
            require(Files.isRegularFile(snapshot),"previous-boot snapshot exists");
            var expected=ItemStack.deserializeBytes(Files.readAllBytes(snapshot));
            var actual=menu.getItemInSlot(0);
            require(actual!=null && expected.equals(actual),"live database/menu item survived clean server restart");
            require(actual.getAmount()==37,"restart preserves quantity");
            require(Long.valueOf(9_000_000_001L).equals(actual.getItemMeta().getPersistentDataContainer().get(OWNER,PersistentDataType.LONG)),"restart preserves long PDC");
            require(actual.getItemMeta().lore().getFirst().hoverEvent()!=null,"restart preserves hover event");
            require("keep insertion".equals(actual.getItemMeta().lore().getFirst().insertion()),"restart preserves insertion");
        }
        checkNativeCosmeticPresentation();
        checkNativeGeoQuarryRegisteredTemplateParity();
        checkNativeForgeLedger(menu);
        checkRegisteredForgeClick();
        var oscillator=Oscillator.Companion.getItem("DIAMOND");
        require(oscillator.getType()==Material.DIAMOND,"native oscillator material conversion");
        require("DIAMOND".equals(Oscillator.Companion.getTarget(oscillator)),"oscillator target identity retained");
        require(oscillator.getItemMeta().lore()!=null && oscillator.getItemMeta().lore().size()==2,"oscillator component lore available");
        checkHorns();
        var location=menuLocation.clone().add(0,3,0);
        var attacker=location.getWorld().spawn(location,Cow.class);
        var victim=location.getWorld().spawn(location.clone().add(2,0,0),Cow.class);
        try {
            double health=victim.getHealth();
            var source=BowDamageEvents.explosionSource(attacker);
            require(source.getDirectEntity()==attacker,"native explosion source retains direct entity");
            require(source.getCausingEntity()==attacker,"native explosion source retains causing entity");
            active=BowDamageEvents.splash(attacker,victim,source,12.5);
            Bukkit.getPluginManager().callEvent(active);
            require(preflights==1,"one cancellable preflight notification");
            require(active.isCancelled() && active.getDamage()==8.0,"protection listener can cancel and edit damage");
            require(!active.isCritical(),"historical noncritical semantics");
            require(victim.getHealth()==health,"preflight does not apply damage by itself");
        } finally { active=null; attacker.remove(); victim.remove(); }
        require(ModernText.actionBar("§x§1§2§a§b§3§4RGB").color().equals(TextColor.color(0x12ab34)),"native Adventure RGB preserved");
        results.add("PASS "+phase+": native metadata, registry, oscillator, cancellable event, stored identity and menu persistence");
    }


    /**
     * Run the real released-core Slimefun ItemDataService and Paper ItemMeta
     * through the cosmetic fallback. MockBukkit alone cannot show whether
     * native Slimefun item templates or Adventure wrappers round-trip safely.
     */
    private void checkNativeCosmeticPresentation() {
        var sf=SlimefunItem.getById("IE_STORAGE_FORGE");
        require(sf!=null,"real registered item exists for native cosmetic test");
        var canonical=mutableRecipeStack(sf.getItem());
        var original=mutableRecipeStack(canonical);
        var metadata=original.getItemMeta();
        require(metadata!=null && metadata.displayName()!=null,
            "native registered item retains a named item meta");
        require(sf.getId().equals(Slimefun.getItemDataService().getItemData(metadata).orElse(null)),
            "registered template has the real Slimefun ID in native ItemDataService");
        var oldName=metadata.displayName();
        metadata.displayName(Component.text("").append(oldName));
        var lore=metadata.lore();
        if(lore!=null && !lore.isEmpty()) {
            var wrapped=new ArrayList<Component>();
            for(var line:lore)wrapped.add(Component.text("").append(line));
            metadata.lore(wrapped);
        }
        require(original.setItemMeta(metadata),"native wrapped component metadata accepted");
        require(StorageItemPresentation.matchesRegistered(original,canonical),
            "native Slimefun ID and semantically equal Adventure wrappers can match");
        var repaired=StorageItemPresentation.canonicalIfCosmetic(original);
        if(original.isSimilar(canonical)) {
            require(repaired==null,"identical templates should avoid a needless write");
        } else {
            require(repaired!=null && repaired.isSimilar(canonical),
                "native historical component wrappers repair to an exact independent template");
        }
        require(sf.getId().equals(Slimefun.getItemDataService().getItemData(original.getItemMeta()).orElse(null)),
            "native repair preflight must not alter the original item-data identity");
        var changed=mutableRecipeStack(canonical);
        changed.editMeta(meta->meta.displayName(oldName.hoverEvent(
            HoverEvent.showText(Component.text("protected semantic data")))));
        require(!StorageItemPresentation.matchesRegistered(changed,canonical),
            "native hover-event differences must never be normalized");
        var owned=mutableRecipeStack(original);
        owned.editMeta(meta->meta.getPersistentDataContainer().set(OWNER,PersistentDataType.LONG,731L));
        require(!StorageItemPresentation.matchesRegistered(owned,canonical),
            "native owner PDC difference must never be normalized");
        results.add("PASS "+phase+": native registered Slimefun ID, Adventure and PDC cosmetic safety");
    }

    /**
     * Recipe inputs may be Slimefun ItemStackWrapper instances. Their clone() is
     * still immutable, so a native crafting rehearsal must copy metadata onto a
     * genuine mutable Bukkit stack before placing it in a live inventory.
     */
    private static ItemStack mutableRecipeStack(ItemStack original) {
        if (original == null) return null;
        var copy = new ItemStack(original.getType(), original.getAmount());
        if (original.hasItemMeta()) copy.setItemMeta(original.getItemMeta());
        return copy;
    }

    /**
     * Real registered IE2 Storage Unit PDC upgrade preflight and live menu/database
     * restart. This is NOT an end-to-end Forge click/ingredient-consumption test.
     * Never present it as transaction or interrupted-shutdown evidence.
     */
    /**
     * An owner-reported SMALL_URANIUM discrepancy is not proven by textual
     * ordering in Minecraft's copied /give command. Compare native items:
     * /sf cheat uses the SlimefunItem registry template, while the IE2 GEO
     * Quarry draws from the registered GEOResource.getItem() template.
     *
     * Do not auto-replace external GEO-resource outputs by current Slimefun
     * templates: custom metadata may be deliberate and protected.
     */
    private void checkNativeGeoQuarryRegisteredTemplateParity() throws Exception {
        var registered = SlimefunItem.getById("SMALL_URANIUM");
        require(registered != null, "Slimefun SMALL_URANIUM registered for cheat vs GEO comparison");
        GEOResource uranium = null;
        for (GEOResource resource : Slimefun.getRegistry().getGEOResources().values()) {
            if ("uranium".equals(resource.getKey().getKey())
                    && "slimefun".equals(resource.getKey().getNamespace())) {
                require(uranium == null, "Only one slimefun:uranium GEO resource registered");
                uranium = resource;
            }
        }
        require(uranium != null, "Native slimefun:uranium GEO resource exists");
        ItemStack cheat = registered.getItem().clone();
        ItemStack produced = uranium.getItem().clone();
        produced.setAmount(1); // The GeoQuarry output-path clone and quantity.
        cheat.setAmount(1);
        require(cheat.getType() == Material.PLAYER_HEAD && produced.getType() == Material.PLAYER_HEAD,
            "Both uranium paths retain the actual player-head material");
        String cheatId = Slimefun.getItemDataService()
            .getItemData(cheat.getItemMeta()).orElse(null);
        String producedId = Slimefun.getItemDataService()
            .getItemData(produced.getItemMeta()).orElse(null);
        require("SMALL_URANIUM".equals(cheatId) && cheatId.equals(producedId),
            "Cheat and registered GEO uranium contain identical non-null Slimefun ID");
        var cheatMeta = cheat.getItemMeta();
        var producedMeta = produced.getItemMeta();
        boolean pdcSame = cheatMeta.getPersistentDataContainer()
            .equals(producedMeta.getPersistentDataContainer());
        boolean nameSame = Objects.equals(cheatMeta.displayName(), producedMeta.displayName());
        boolean loreSame = Objects.equals(cheatMeta.lore(), producedMeta.lore());
        boolean materialSame = cheat.getType() == produced.getType();
        require(cheat.isSimilar(produced),
            "Native cheat vs GEO uranium item differs beyond copied-component order"
            + " (material=" + materialSame
            + ", PDC=" + pdcSame + ", name=" + nameSame
            + ", lore=" + loreSame + ", ItemMeta=" + cheatMeta.equals(producedMeta) + ")");
        ItemStack restored = ItemStack.deserializeBytes(produced.serializeAsBytes());
        require(cheat.isSimilar(restored),
            "GEO uranium loses registered item identity after native Paper byte serialization");
        require(produced.isSimilar(restored),
            "GEO uranium item itself changes after native Paper byte serialization");
        require(cheat.getAmount() == 1 && produced.getAmount() == 1
                && restored.getAmount() == 1,
            "Cheat, GEO and serialized uranium retain exact one-item amount");

        // Exercise the actual registered GeoQuarry output slots and the same
        // BlockMenu.pushItem path used by production, not an invented chest.
        var quarryItem=SlimefunItem.getById("IE_GEO_QUARRY");
        require(quarryItem instanceof GeoQuarry,"real IE2 GEO Quarry exists for output fixture");
        require("IE_GEO_QUARRY".equals(StoredBlockLookup.getId(quarryLocation)),
            "GEO Quarry fixture retains registered block identity");
        var quarryMenu=StoredBlockLookup.getInventory(quarryLocation);
        require(quarryMenu!=null,"real GEO Quarry output BlockMenu loaded");
        int[] outputSlots=MenuLayout.Companion.getOUTPUT_ONLY().getOutputSlots();
        require(outputSlots.length>0,"GEO Quarry declares physical output slots");
        var snapshot=getDataFolder().toPath().resolve("native-geo-uranium.bin");
        if (phase.equals("seed")) {
            for (int slot:outputSlots) {
                var prior=quarryMenu.getItemInSlot(slot);
                require(prior==null || prior.getType().isAir(),
                    "GEO Quarry fixture starts with no physical output");
            }
            require(quarryMenu.fits(produced,outputSlots),
                "Real GEO Quarry output accepts registered uranium without discarding it");
            var remainder=quarryMenu.pushItem(produced.clone(),outputSlots);
            require(remainder==null || remainder.getType().isAir() || remainder.getAmount()==0,
                "Real GEO Quarry output push leaves no uninserted item");
            quarryMenu.markDirty();
        }
        int count=0;
        ItemStack inSlot=null;
        for (int slot:outputSlots) {
            var candidate=quarryMenu.getItemInSlot(slot);
            if (candidate==null || candidate.getType().isAir()) continue;
            require("SMALL_URANIUM".equals(Slimefun.getItemDataService()
                .getItemData(candidate.getItemMeta()).orElse(null)),
                "No unrelated item appeared in GEO Quarry fixture");
            count+=candidate.getAmount();
            require(candidate.getAmount()>0 && candidate.isSimilar(cheat),
                "Actual GEO Quarry output slot differs from /sf cheat identity after push/reload"
                    + " (slot="+slot+", meta="+candidate.getItemMeta().equals(cheat.getItemMeta())+")");
            inSlot=candidate;
        }
        require(count==1 && inSlot!=null,
            "Exactly one physical SMALL_URANIUM remains in GEO Quarry output across restart");
        if (phase.equals("seed")) {
            getDataFolder().mkdirs();
            Files.write(snapshot,inSlot.serializeAsBytes());
        } else {
            require(Files.isRegularFile(snapshot),"GEO Quarry output baseline from prior boot exists");
            var fromPriorBoot=ItemStack.deserializeBytes(Files.readAllBytes(snapshot));
            require(inSlot.isSimilar(fromPriorBoot) && fromPriorBoot.getAmount()==1,
                "GEO Quarry physical output changed metadata after clean server restart");
        }
        results.add("PASS " + phase
            + ": real SMALL_URANIUM cheat, registered GEO resource and actual"
            + " GeoQuarry output slot match before/after persisted menu restart");
    }

    private void checkNativeForgeLedger(me.mrCookieSlime.Slimefun.api.inventory.BlockMenu menu) throws Exception {
        var sourceType=SlimefunItem.getById("IE_STORAGE_UNIT_1");
        var targetType=SlimefunItem.getById("IE_STORAGE_UNIT_2");
        require(sourceType instanceof StorageUnit,"real source Storage Unit registered");
        require(targetType instanceof StorageUnit,"real target Storage Unit registered");
        var sourceCapacity=((StorageUnit)sourceType).getCapacity();
        var targetCapacity=((StorageUnit)targetType).getCapacity();
        require(sourceCapacity>731 && targetCapacity>sourceCapacity,"registered Forge capacities increase");
        var snapshot=getDataFolder().toPath().resolve("native-forge-upgrade.bin");
        if (phase.equals("seed")) {
            var source=sourceType.getItem().clone();
            var meta=source.getItemMeta();
            PersistentDataAPI.set(meta,StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentStorageCacheType.getTYPE(),
                new StorageCache(new ItemStack(Material.DIAMOND),731,sourceCapacity,true));
            require(source.setItemMeta(meta),"native old Storage Unit accepts PDC");
            var sourceBefore=source.clone();
            // Validate the production Forge's registered nine-slot upgrade recipe,
            // not only a synthetic one-ingredient MachineRecipe fixture.
            var registeredForge=SlimefunItem.getById("IE_STORAGE_FORGE");
            require(registeredForge instanceof StorageForge,"real Storage Forge machine registered");
            var registeredRecipe=((StorageForge)registeredForge).getRecipes().stream()
                .filter(r->SlimefunItem.getByItem(r.getOutput())==targetType)
                .findFirst().orElseThrow(()->new AssertionError("registered tier-2 Forge recipe missing"));
            var registeredInput=java.util.Arrays.stream(registeredRecipe.getInput())
                .map(NativeModernizationProbe::mutableRecipeStack).toArray(ItemStack[]::new);
            require(registeredInput.length==9,"registered Forge recipe retains nine input slots");
            int storageIndex=-1;
            for(int i=0;i<registeredInput.length;i++) {
                if(registeredInput[i]!=null && SlimefunItem.getByItem(registeredInput[i])==sourceType) {
                    require(storageIndex<0,"registered Forge recipe has only one source Storage Unit");
                    storageIndex=i;
                }
            }
            require(storageIndex>=0,"registered Forge recipe contains source Storage Unit");
            registeredInput[storageIndex]=source.clone();
            require(registeredRecipe.check(registeredInput,false),
                "registered non-strict Forge recipe matches populated storage and all real ingredients");
            var prepare=StorageForge.class.getDeclaredMethod("prepareOutput",ItemStack[].class,ItemStack.class);
            prepare.setAccessible(true);
            var prepared=(ItemStack)prepare.invoke(registeredForge,registeredInput,mutableRecipeStack(registeredRecipe.getOutput()));
            require(prepared!=null,"registered Forge prepareOutput accepts the real upgrade recipe");
            var preparedLedger=PersistentDataAPI.get(prepared.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(preparedLedger!=null && preparedLedger.getAmount()==731
                && preparedLedger.getLimit()==targetCapacity && preparedLedger.getVoidExcess()
                && new ItemStack(Material.DIAMOND).equals(preparedLedger.getItemStack()),
                "registered Forge prepareOutput retains item identity, stock, capacity and settings");
            require(prepare.invoke(registeredForge,registeredInput,new ItemStack(Material.DIRT))==null,
                "registered Forge refuses populated storage when recipe output is not a Storage Unit");
            require(source.equals(sourceBefore),"registered Forge preflight never mutates source unit");
            var upgraded=StorageForge.transferContents(
                new ItemStack[]{source},targetType.getItem().clone(),targetCapacity);
            require(upgraded!=null,"native registered Storage Unit upgrades without emptying");
            require(source.equals(sourceBefore),"Forge preflight does not mutate old unit");
            var cache=PersistentDataAPI.get(upgraded.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(cache!=null,"upgraded unit has readable storage ledger");
            require(cache.getAmount()==731 && cache.getLimit()==targetCapacity,
                "native upgraded balance and target capacity retained");
            require(cache.getVoidExcess() && new ItemStack(Material.DIAMOND).equals(cache.getItemStack()),
                "native upgraded identity and Void Excess retained");
            require(StorageForge.transferContents(new ItemStack[]{source},
                targetType.getItem().clone(),100)==null,"insufficient target capacity refuses upgrade");
            require(source.equals(sourceBefore),"refused upgrade leaves original storage untouched");
            // Two independently backed inputs must never be collapsed into one
            // upgraded unit: that would discard one ledger or clone its stock.
            var secondLedger=source.clone();
            require(StorageForge.transferContents(new ItemStack[]{source,secondLedger},
                targetType.getItem().clone(),targetCapacity)==null,
                "two backed source ledgers refuse a single-result upgrade");
            require(source.equals(sourceBefore) && secondLedger.equals(sourceBefore),
                "multiple-ledger refusal preserves both exact source PDC items");
            // An output already bearing a ledger is not an empty destination.
            require(StorageForge.transferContents(new ItemStack[]{source},
                upgraded.clone(),targetCapacity)==null,
                "preloaded backed result cannot overwrite or duplicate its ledger");
            require(source.equals(sourceBefore),
                "preloaded-output refusal preserves the original ledger");
            Files.write(snapshot,upgraded.serializeAsBytes());
            menu.replaceExistingItem(1,upgraded.clone());menu.markDirty();
            require(upgraded.equals(menu.getItemInSlot(1)),"upgraded PDC in real persistent menu");
            // Test the production recipe-consume and real BlockMenu push/persistence
            // sequence; this remains a rehearsal, not a GUI craft click.
            var transactionSnapshot=getDataFolder().toPath().resolve("native-forge-transaction.bin");
            menu.replaceExistingItem(2,source.clone());
            var actualInput=new ItemStack[]{menu.getItemInSlot(2)};
            var recipe=MachineRecipe.Companion.of(
                new ItemStack[]{sourceType.getItem().clone()},targetType.getItem().clone());
            require(recipe.check(actualInput,false),
                "production non-strict Forge recipe matches populated storage PDC");
            var staged=StorageForge.transferContents(actualInput,recipe.getOutput().clone(),targetCapacity);
            require(staged!=null,"native recipe transaction preflight retains stored stock");
            var failedSource=actualInput[0].clone();
            require(StorageForge.transferContents(actualInput,recipe.getOutput().clone(),100)==null,
                "native recipe transaction refuses insufficient target capacity");
            require(failedSource.equals(actualInput[0]),"failed native transaction preserves input");
            recipe.consume(actualInput);
            require(actualInput[0].getAmount()==0,"production recipe consumes exactly one old unit");
            var consumedSlot=menu.getItemInSlot(2);
            require(consumedSlot==null || consumedSlot.getType().isAir() || consumedSlot.getAmount()<=0,
                "production recipe consumption clears the authoritative native menu input");
            var expectedOutput=staged.clone();
            menu.pushItem(staged,52);
            menu.markDirty();
            require(expectedOutput.equals(menu.getItemInSlot(52)),
                "production menu push retains the complete upgraded ledger");
            Files.write(transactionSnapshot,expectedOutput.serializeAsBytes());
        } else {
            require(Files.isRegularFile(snapshot),"prior JVM native Forge item snapshot exists");
            var expected=ItemStack.deserializeBytes(Files.readAllBytes(snapshot));
            var persisted=menu.getItemInSlot(1);
            require(persisted!=null && persisted.equals(expected),
                "upgraded unit survives real Slimefun menu/database restart");
            var cache=PersistentDataAPI.get(persisted.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(cache!=null && cache.getAmount()==731 && cache.getLimit()==targetCapacity,
                "restarted native storage amount and capacity retained");
            require(cache.getVoidExcess() && new ItemStack(Material.DIAMOND).equals(cache.getItemStack()),
                "restarted native item identity and Void Excess retained");
            require(SlimefunItem.getByItem(persisted)==targetType,
                "restarted upgraded unit retains registered Slimefun identity");
            var transactionSnapshot=getDataFolder().toPath().resolve("native-forge-transaction.bin");
            require(Files.isRegularFile(transactionSnapshot),"native recipe transaction snapshot exists");
            var transactionExpected=ItemStack.deserializeBytes(Files.readAllBytes(transactionSnapshot));
            var sourceAfterRestart=menu.getItemInSlot(2);
            require(sourceAfterRestart==null || sourceAfterRestart.getType().isAir()
                || sourceAfterRestart.getAmount()<=0,
                "native restart must not restore the already-consumed old storage unit");
            var transactionPersisted=menu.getItemInSlot(52);
            require(transactionPersisted!=null && transactionPersisted.equals(transactionExpected),
                "production consume/push rehearsal output survives clean server restart");
            var transactionCache=PersistentDataAPI.get(transactionPersisted.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(transactionCache!=null && transactionCache.getAmount()==731
                && transactionCache.getLimit()==targetCapacity && transactionCache.getVoidExcess()
                && new ItemStack(Material.DIAMOND).equals(transactionCache.getItemStack()),
                "native consume/push rehearsal preserves identity, stock and settings after restart");
        }
        results.add("PASS "+phase+": native Storage Forge PDC and consume/push rehearsal across real menu restart (not a craft click)");
    }

    /**
     * Dispatch the registered Forge's real crafting-button callback on a native
     * BlockMenu using all nine production recipe slots. No Bukkit network
     * InventoryClickEvent is emitted; this proves the installed handler path,
     * consumption and clean restart, NOT an interrupted-operation rollback.
     */
    private void checkRegisteredForgeClick() throws Exception {
        var sourceType=SlimefunItem.getById("IE_STORAGE_UNIT_1");
        var targetType=SlimefunItem.getById("IE_STORAGE_UNIT_2");
        var forge=(StorageForge)SlimefunItem.getById("IE_STORAGE_FORGE");
        require(sourceType instanceof StorageUnit && targetType instanceof StorageUnit,
            "registered Forge click Storage Unit tiers exist");
        var forgeMenu=StoredBlockLookup.getInventory(forgeLocation);
        require(forgeMenu!=null,"real registered Forge menu is persisted");
        require("IE_STORAGE_FORGE".equals(StoredBlockLookup.getId(forgeLocation)),
            "Forge click executes on an actual registered Forge block");
        var handler=forgeMenu.getMenuClickHandler(forge.getCraftSlot());
        require(handler!=null,"Forge crafting button has a live registered click handler");
        var layout=MenuLayout.Companion.getCRAFTING_DEFAULT();
        var slots=layout.getInputSlots();
        require(slots.length==9,"Forge click uses all nine physical recipe slots");
        int outputSlot=layout.getOutputSlots()[0];
        var recipe=forge.getRecipes().stream()
            .filter(r->SlimefunItem.getByItem(r.getOutput())==targetType)
            .findFirst().orElseThrow(()->new AssertionError("registered Forge upgrade recipe missing"));
        var ingredients=java.util.Arrays.stream(recipe.getInput())
            .map(NativeModernizationProbe::mutableRecipeStack).toArray(ItemStack[]::new);
        require(ingredients.length==slots.length,"Forge click recipe/layout dimensions agree");
        int sourceIndex=-1;
        for(int i=0;i<ingredients.length;i++) {
            if(ingredients[i]!=null && SlimefunItem.getByItem(ingredients[i])==sourceType) {
                require(sourceIndex<0,"Forge click recipe has one source unit");
                sourceIndex=i;
            }
        }
        require(sourceIndex>=0,"Forge click recipe locates source Storage Unit");
        var snapshot=getDataFolder().toPath().resolve("native-forge-click-output.bin");
        var sourceCapacity=((StorageUnit)sourceType).getCapacity();
        var targetCapacity=((StorageUnit)targetType).getCapacity();
        if (phase.equals("seed")) {
            var source=mutableRecipeStack(sourceType.getItem());
            var meta=source.getItemMeta();
            PersistentDataAPI.set(meta,StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentStorageCacheType.getTYPE(),
                new StorageCache(new ItemStack(Material.DIAMOND),731,sourceCapacity,true));
            require(source.setItemMeta(meta),"Forge click source unit contains the stored ledger");
            // A zero-balance unit may still carry an assigned item identity and
            // void-excess preference. The upgrade must preserve both, not
            // normalize it to a fresh unassigned unit.
            var emptyAssigned=mutableRecipeStack(sourceType.getItem());
            var emptyMeta=emptyAssigned.getItemMeta();
            PersistentDataAPI.set(emptyMeta,StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentStorageCacheType.getTYPE(),
                new StorageCache(new ItemStack(Material.DIAMOND),0,sourceCapacity,true));
            require(emptyAssigned.setItemMeta(emptyMeta),"zero-balance Forge fixture keeps assigned identity");
            var emptyUpgrade=StorageForge.transferContents(
                new ItemStack[]{emptyAssigned},mutableRecipeStack(targetType.getItem()),targetCapacity);
            require(emptyUpgrade!=null,"zero-balance assigned Forge unit upgrades safely");
            var emptyLedger=PersistentDataAPI.get(emptyUpgrade.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(emptyLedger!=null && emptyLedger.getAmount()==0
                && emptyLedger.getLimit()==targetCapacity && emptyLedger.getVoidExcess()
                && new ItemStack(Material.DIAMOND).equals(emptyLedger.getItemStack()),
                "zero-balance upgrade preserves item identity, capacity and void-excess");
            // An upgrade with a destination too small for the persisted stock
            // must refuse without rewriting the source ledger or minting output.
            var sourceBeforeCapacityRefusal=source.clone();
            require(StorageForge.transferContents(new ItemStack[]{source},
                mutableRecipeStack(targetType.getItem()),730)==null,
                "Forge refuses target capacity below persisted 731-item balance");
            require(source.equals(sourceBeforeCapacityRefusal),
                "capacity refusal leaves source PDC and stack unchanged");
            // A malformed legacy ledger can report a negative balance or more
            // items than its own declared capacity. Neither is permission to
            // silently normalize stock during a tier upgrade.
            for (int invalidAmount : new int[]{-1, sourceCapacity+1}) {
                var invalid=mutableRecipeStack(sourceType.getItem());
                var invalidMeta=invalid.getItemMeta();
                PersistentDataAPI.set(invalidMeta,StorageUnit.Companion.getSTORAGE_KEY(),
                    PersistentStorageCacheType.getTYPE(),
                    new StorageCache(new ItemStack(Material.DIAMOND),invalidAmount,sourceCapacity,true));
                require(invalid.setItemMeta(invalidMeta),
                    "malformed legacy Forge fixture retains its exact ledger");
                var beforeInvalid=invalid.clone();
                require(StorageForge.transferContents(new ItemStack[]{invalid},
                    mutableRecipeStack(targetType.getItem()),targetCapacity)==null,
                    "Forge refuses malformed persisted source amount "+invalidAmount);
                require(invalid.equals(beforeInvalid),
                    "malformed ledger refusal leaves source PDC unchanged "+invalidAmount);
            }
            // An unknown nested field may belong to a future writer or another
            // addon. The Forge must not silently discard it when reconstructing
            // a legacy storage ledger during a tier upgrade.
            var future=source.clone();
            var futureMeta=future.getItemMeta();
            var futurePdc=futureMeta.getPersistentDataContainer();
            var rawLedger=futurePdc.get(StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentDataType.TAG_CONTAINER);
            require(rawLedger!=null,"future-field fixture has a nested ledger");
            rawLedger.set(new NamespacedKey("ie2","future_ledger_field"),
                PersistentDataType.STRING,"keep-me");
            futurePdc.set(StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentDataType.TAG_CONTAINER,rawLedger);
            require(future.setItemMeta(futureMeta),
                "future-field fixture retains nested unknown PDC field");
            var futureBefore=future.clone();
            require(StorageForge.transferContents(new ItemStack[]{future},
                mutableRecipeStack(targetType.getItem()),targetCapacity)==null,
                "Forge refuses to erase unknown nested storage metadata");
            require(future.equals(futureBefore),
                "unknown-field refusal preserves exact original PDC");
            // Historical storage fields must retain their exact PDC types. A
            // decoder default must not convert missing stock/capacity into an
            // apparently empty unit or discard a malformed optional setting.
            for (String missing : new String[]{"amount","max_amount"}) {
                var malformed=source.clone();
                var malformedMeta=malformed.getItemMeta();
                var malformedPdc=malformedMeta.getPersistentDataContainer();
                var nested=malformedPdc.get(StorageUnit.Companion.getSTORAGE_KEY(),
                    PersistentDataType.TAG_CONTAINER);
                require(nested!=null,"missing-field fixture has nested ledger");
                nested.remove(new NamespacedKey(StorageUnit.Companion.getSTORAGE_KEY().getNamespace(),missing));
                malformedPdc.set(StorageUnit.Companion.getSTORAGE_KEY(),
                    PersistentDataType.TAG_CONTAINER,nested);
                require(malformed.setItemMeta(malformedMeta),
                    "missing-field fixture retains malformed ledger "+missing);
                var unchanged=malformed.clone();
                require(StorageForge.transferContents(new ItemStack[]{malformed},
                    mutableRecipeStack(targetType.getItem()),targetCapacity)==null,
                    "Forge refuses missing historical storage field "+missing);
                require(malformed.equals(unchanged),
                    "missing-field refusal preserves exact original ledger "+missing);
            }
            var malformedFlag=source.clone();
            var flagMeta=malformedFlag.getItemMeta();
            var flagPdc=flagMeta.getPersistentDataContainer();
            var flagLedger=flagPdc.get(StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentDataType.TAG_CONTAINER);
            require(flagLedger!=null,"wrong-type fixture has nested ledger");
            flagLedger.set(new NamespacedKey(StorageUnit.Companion.getSTORAGE_KEY().getNamespace(),"void_excess"),
                PersistentDataType.STRING,"true");
            flagPdc.set(StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentDataType.TAG_CONTAINER,flagLedger);
            require(malformedFlag.setItemMeta(flagMeta),
                "wrong-type fixture retains malformed optional flag");
            var flagBefore=malformedFlag.clone();
            require(StorageForge.transferContents(new ItemStack[]{malformedFlag},
                mutableRecipeStack(targetType.getItem()),targetCapacity)==null,
                "Forge refuses wrong-type persisted void-excess setting");
            require(malformedFlag.equals(flagBefore),
                "wrong-type optional field refusal preserves original ledger");
            ingredients[sourceIndex]=source;
            for(int i=0;i<slots.length;i++) {
                forgeMenu.replaceExistingItem(slots[i],ingredients[i]);
            }
            require(recipe.check(java.util.Arrays.stream(slots)
                .mapToObj(forgeMenu::getItemInSlot).toArray(ItemStack[]::new),false),
                "all nine real Forge menu slots match the registered upgrade recipe");
            var player=forgeClickProbePlayer();
            // A populated ledger without item identity must not disappear when
            // the real button is clicked. The recipe itself still matches by
            // non-strict identity; only the Forge safety preflight rejects it.
            var corrupt=mutableRecipeStack(source);
            var corruptMeta=corrupt.getItemMeta();
            PersistentDataAPI.set(corruptMeta,StorageUnit.Companion.getSTORAGE_KEY(),
                PersistentStorageCacheType.getTYPE(),
                new StorageCache(null,731,sourceCapacity,true));
            require(corrupt.setItemMeta(corruptMeta),"corrupt native Forge fixture retains missing identity");
            forgeMenu.replaceExistingItem(slots[sourceIndex],corrupt);
            require(recipe.check(java.util.Arrays.stream(slots)
                .mapToObj(forgeMenu::getItemInSlot).toArray(ItemStack[]::new),false),
                "identity-less old ledger still reaches Forge safety preflight");
            var beforeRefusal=java.util.Arrays.stream(slots)
                .mapToObj(forgeMenu::getItemInSlot)
                .map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
            require(!handler.onClick(player,forge.getCraftSlot(),
                forgeMenu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "invalid-ledger Forge button remains non-movable");
            for(int i=0;i<slots.length;i++) {
                require(java.util.Objects.equals(beforeRefusal[i],forgeMenu.getItemInSlot(slots[i])),
                    "invalid ledger click must not consume any input: "+i);
            }
            var rejected=forgeMenu.getItemInSlot(outputSlot);
            require(rejected==null || rejected.getType().isAir(),
                "invalid ledger click does not mint an empty upgrade");
            forgeMenu.replaceExistingItem(slots[sourceIndex],source);
            // A stacked source with a populated ledger represents multiple
            // backed units. The upgrade must not consume one and leave a clone.
            var stackedSource=source.clone();
            stackedSource.setAmount(2);
            forgeMenu.replaceExistingItem(slots[sourceIndex],stackedSource);
            require(!handler.onClick(player,forge.getCraftSlot(),
                forgeMenu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "stacked-ledger Forge button remains non-movable");
            require(stackedSource.equals(forgeMenu.getItemInSlot(slots[sourceIndex])),
                "stacked-ledger refusal preserves both source units and their PDC");
            for(int i=0;i<slots.length;i++) {
                if(i!=sourceIndex)require(java.util.Objects.equals(ingredients[i],forgeMenu.getItemInSlot(slots[i])),
                    "stacked-ledger refusal preserves other ingredients: "+i);
            }
            require(forgeMenu.getItemInSlot(outputSlot)==null
                    || forgeMenu.getItemInSlot(outputSlot).getType().isAir(),
                "stacked-ledger refusal creates no upgraded output");
            forgeMenu.replaceExistingItem(slots[sourceIndex],source);
            // A full unrelated output slot must also refuse the transaction
            // before consuming the source or other recipe ingredients.
            var blockedOutput=new ItemStack(Material.BEDROCK,64);
            forgeMenu.replaceExistingItem(outputSlot,blockedOutput);
            require(!handler.onClick(player,forge.getCraftSlot(),
                forgeMenu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "blocked-output Forge button remains non-movable");
            require(blockedOutput.equals(forgeMenu.getItemInSlot(outputSlot)),
                "blocked Forge click retains the original output stack");
            for(int i=0;i<slots.length;i++) {
                require(java.util.Objects.equals(ingredients[i],forgeMenu.getItemInSlot(slots[i])),
                    "blocked Forge click leaves every recipe input untouched: "+i);
            }
            // An occupied slot with a byte-identical upgraded ledger must
            // not merge two backed Storage Units into an indistinguishable stack.
            var existingLedger=StorageForge.transferContents(
                new ItemStack[]{source},mutableRecipeStack(targetType.getItem()),targetCapacity);
            require(existingLedger!=null,"existing backed output unit can be prepared independently");
            forgeMenu.replaceExistingItem(outputSlot,existingLedger.clone());
            require(!handler.onClick(player,forge.getCraftSlot(),
                forgeMenu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "occupied backed output click remains non-movable");
            require(existingLedger.equals(forgeMenu.getItemInSlot(outputSlot)),
                "occupied backed output is not stacked or overwritten");
            for(int i=0;i<slots.length;i++) {
                require(java.util.Objects.equals(ingredients[i],forgeMenu.getItemInSlot(slots[i])),
                    "occupied backed output preserves all stored input ingredients: "+i);
            }
            forgeMenu.replaceExistingItem(outputSlot,new ItemStack(Material.AIR));
            // Fault injection: simulate a stale recipe quantity after matching,
            // so the storage-only commit sees a debit it cannot fulfill.
            // The actual registered click below still uses the original recipe.
            var staleRecipeInput=java.util.Arrays.stream(recipe.getInput())
                .map(NativeModernizationProbe::mutableRecipeStack).toArray(ItemStack[]::new);
            int shortIndex=-1;
            for(int i=0;i<staleRecipeInput.length;i++) {
                if(staleRecipeInput[i]!=null && i!=sourceIndex) {
                    shortIndex=i;
                    break;
                }
            }
            require(shortIndex>=0,"Forge fault fixture has a non-ledger ingredient");
            staleRecipeInput[shortIndex].setAmount(ingredients[shortIndex].getAmount()+1);
            var staleRecipe=net.guizhanss.infinityexpansion2.core.recipes.MachineRecipe.Companion
                .of(staleRecipeInput,mutableRecipeStack(recipe.getOutput()));
            var backedOutput=StorageForge.transferContents(
                new ItemStack[]{source},mutableRecipeStack(targetType.getItem()),targetCapacity);
            require(backedOutput!=null,"Forge fault fixture prepares a backed target");
            var commit=StorageForge.class.getDeclaredMethod("commitCraft",BlockMenu.class,
                net.guizhanss.infinityexpansion2.core.recipes.MachineRecipe.class,
                ItemStack[].class,ItemStack.class);
            commit.setAccessible(true);
            var staleMenuInputs=java.util.Arrays.stream(slots)
                .mapToObj(forgeMenu::getItemInSlot).toArray(ItemStack[]::new);
            require(!((Boolean)commit.invoke(forge,forgeMenu,staleRecipe,staleMenuInputs,backedOutput)),
                "Forge refuses a partially fulfillable stale ingredient debit");
            require(forgeMenu.getItemInSlot(outputSlot)==null
                    || forgeMenu.getItemInSlot(outputSlot).getType().isAir(),
                "failed Forge ingredient debit cannot mint a backed output");
            for(int i=0;i<slots.length;i++) {
                require(java.util.Objects.equals(ingredients[i],forgeMenu.getItemInSlot(slots[i])),
                    "failed Forge debit restores exact source and ingredient snapshot: "+i);
            }
            // This is the same handler registered by onNewInstance(), not a
            // reflected prepareOutput() invocation or synthetic recipe consume.
            require(!handler.onClick(player,forge.getCraftSlot(),
                forgeMenu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "registered Forge button rejects moving the button item");
            var actual=forgeMenu.getItemInSlot(outputSlot);
            require(actual!=null && SlimefunItem.getByItem(actual)==targetType,
                "registered Forge click produces the correct new Storage Unit");
            var ledger=PersistentDataAPI.get(actual.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(ledger!=null && ledger.getAmount()==731
                && ledger.getLimit()==targetCapacity && ledger.getVoidExcess()
                && new ItemStack(Material.DIAMOND).equals(ledger.getItemStack()),
                "registered Forge click conserves identity, count, capacity and settings");
            for(int i=0;i<slots.length;i++) {
                var remaining=forgeMenu.getItemInSlot(slots[i]);
                require(remaining==null || remaining.getType().isAir() || remaining.getAmount()==0,
                    "registered Forge click consumes each required input exactly once: "+i);
            }
            // Repeated clicks must not re-consume ingredients or create a
            // second populated ledger after the first successful upgrade.
            require(!handler.onClick(player,forge.getCraftSlot(),
                forgeMenu.getItemInSlot(forge.getCraftSlot()),new ClickAction(false,false)),
                "repeated Forge click remains non-movable");
            require(actual.equals(forgeMenu.getItemInSlot(outputSlot)),
                "repeated Forge click does not mint extra stored stock");
            for(int i=0;i<slots.length;i++) {
                var remaining=forgeMenu.getItemInSlot(slots[i]);
                require(remaining==null || remaining.getType().isAir() || remaining.getAmount()==0,
                    "repeated Forge click cannot restore a consumed ingredient: "+i);
            }
            forgeMenu.markDirty();
            Files.write(snapshot,actual.serializeAsBytes());
        } else {
            require(Files.isRegularFile(snapshot),"prior registered Forge click snapshot exists");
            var expected=ItemStack.deserializeBytes(Files.readAllBytes(snapshot));
            var actual=forgeMenu.getItemInSlot(outputSlot);
            require(actual!=null && actual.equals(expected),
                "registered Forge click output survives clean native database restart");
            for(int i=0;i<slots.length;i++) {
                var remaining=forgeMenu.getItemInSlot(slots[i]);
                require(remaining==null || remaining.getType().isAir() || remaining.getAmount()==0,
                    "registered Forge click inputs remain consumed after restart: "+i);
            }
            var ledger=PersistentDataAPI.get(actual.getItemMeta(),
                StorageUnit.Companion.getSTORAGE_KEY(),PersistentStorageCacheType.getTYPE());
            require(ledger!=null && ledger.getAmount()==731
                && ledger.getLimit()==targetCapacity && ledger.getVoidExcess()
                && new ItemStack(Material.DIAMOND).equals(ledger.getItemStack()),
                "registered Forge click keeps stored identity/stock/settings after restart");
        }
        results.add("PASS "+phase+": registered Storage Forge click handler, real slots and clean database restart (not an interrupted craft)");
    }

    /** A minimal permissible player interface for the disposable native click callback. */
    private Player forgeClickProbePlayer() {
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class},(proxy,method,args)->{
                return switch(method.getName()) {
                    case "getName" -> "NativeForgeProbe";
                    case "getUniqueId" -> new UUID(0,731);
                    case "getWorld" -> forgeLocation.getWorld();
                    case "getLocation" -> forgeLocation.clone();
                    case "getServer" -> Bukkit.getServer();
                    case "hasPermission","isOp","isOnline" -> true;
                    case "getLocale" -> "en_us";
                    case "locale" -> java.util.Locale.US;
                    case "equals" -> proxy==args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "NativeForgeProbe(player-interface)";
                    default -> {
                        var type=method.getReturnType();
                        if (type==boolean.class) yield false;
                        if (type==int.class) yield 0;
                        if (type==long.class) yield 0L;
                        if (type==float.class) yield 0f;
                        if (type==double.class) yield 0d;
                        if (type==byte.class) yield (byte)0;
                        if (type==short.class) yield (short)0;
                        if (type==char.class) yield (char)0;
                        yield null;
                    }
                };
            });
    }

    private void checkHorns() throws Exception {
        var type=Class.forName("net.guizhanss.infinityexpansion2.implementation.setup.MobSimulationSetup");
        var instance=type.getField("INSTANCE").get(null);
        var method=java.util.Arrays.stream(type.getMethods()).filter(m->m.getName().startsWith("getAsConfiguredDrop") && m.getParameterCount()==1).findFirst().orElseThrow();
        for (String sound:List.of("ponder","sing","seek","feel","admire","call","yearn","dream")) {
            String id=sound+"_goat_horn";
            Object drop=method.invoke(instance,Map.of("item","GOAT_HORN","instrument",id,"amount",1));
            require(drop!=null,"configured horn resolves "+id);
            var item=(ItemStack)drop.getClass().getMethod("getItem").invoke(drop);
            // The effective component includes the material default; ItemMeta may omit an
            // unchanged default instrument in newer Paper versions.
            var registry=RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT);
            var instrument=item.getData(DataComponentTypes.INSTRUMENT);
            var actualKey=instrument==null?null:registry.getKey(instrument);
            require(NamespacedKey.minecraft(id).equals(actualKey),"configured effective instrument retained "+id+"; actual="+actualKey+"; meta="+item.getItemMeta().serialize());
        }
    }

    @EventHandler public void onPreflight(EntityDamageByEntityEvent event) {
        if (event==active) { preflights++;event.setDamage(8.0);event.setCancelled(true); }
    }
    private void require(boolean condition,String text) { assertions++;if(!condition)throw new AssertionError(text); }
    private void fail(Throwable failure) { failures++;results.add("FAIL "+failure);getLogger().log(java.util.logging.Level.SEVERE,"Modernization probe failure",failure);finish(); }
    private void finish() {
        try { getDataFolder().mkdirs();results.add("Assertions: "+assertions+"; failures: "+failures);Files.write(getDataFolder().toPath().resolve("results-"+phase+".txt"),results); }
        catch(Exception failure) { getLogger().log(java.util.logging.Level.SEVERE,"Cannot save results",failure); }
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5L);
    }
}
