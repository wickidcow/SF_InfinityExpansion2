package verification;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache;
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
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Cow;
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
        recordLocation.getChunk().load(); menuLocation.getChunk().load();
        var controller=Slimefun.getDatabaseManager().getBlockDataController();
        if (phase.equals("seed")) {
            require(!StoredBlockLookup.hasBlockInfo(recordLocation),"new record fixture");
            require(!StoredBlockLookup.hasBlockInfo(menuLocation),"new menu fixture");
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
        }
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    if (StoredBlockLookup.getInventory(menuLocation)!=null) {
                        cancel(); checkNative(); finish();
                    } else if (++attempts>100) {
                        cancel(); fail(new AssertionError("Native workbench inventory did not load"));
                    }
                } catch(Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this,1L,5L);
    }

    private void checkNative() throws Exception {
        var controller=Slimefun.getDatabaseManager().getBlockDataController();
        require(StoredBlockLookup.hasBlockInfo(recordLocation),"unknown stored record exists");
        require("IE2_NATIVE_UNKNOWN_ID".equals(StoredBlockLookup.getId(recordLocation)),"stored identity not inferred from registry");
        require("preserve-unknown-record".equals(controller.getBlockData(recordLocation).getData("probe.marker")),"unknown record payload retained");
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
            active=BowDamageEvents.splash(attacker,victim,
                DamageSource.builder(DamageType.EXPLOSION).withCausingEntity(attacker).build(),12.5);
            Bukkit.getPluginManager().callEvent(active);
            require(preflights==1,"one cancellable preflight notification");
            require(active.isCancelled() && active.getDamage()==8.0,"protection listener can cancel and edit damage");
            require(!active.isCritical(),"historical noncritical semantics");
            require(victim.getHealth()==health,"preflight does not apply damage by itself");
        } finally { active=null; attacker.remove(); victim.remove(); }
        require(ModernText.actionBar("§x§1§2§a§b§3§4RGB").color().equals(TextColor.color(0x12ab34)),"native Adventure RGB preserved");
        results.add("PASS "+phase+": native metadata, registry, oscillator, cancellable event, stored identity and menu persistence");
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
            var instrument=((MusicInstrumentMeta)item.getItemMeta()).getInstrument();
            require(instrument!=null && instrument.equals(RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT).get(NamespacedKey.minecraft(id))),"configured instrument retained "+id);
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
