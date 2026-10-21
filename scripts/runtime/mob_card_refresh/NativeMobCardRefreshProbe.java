package verification;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import kotlin.Pair;
import net.guizhanss.infinityexpansion2.api.InfinityExpansion2API;
import net.guizhanss.infinityexpansion2.api.mobsim.MobDataCardProps;
import net.guizhanss.infinityexpansion2.core.migration.LegacyItemMigrator;
import net.guizhanss.infinityexpansion2.implementation.items.mobsim.MobDataCard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/** Isolated native Paper assertions; never install on a live server (the probe stops the JVM). */
public final class NativeMobCardRefreshProbe extends JavaPlugin implements SlimefunAddon {
    private static final NamespacedKey SF = new NamespacedKey("slimefun", "slimefun_item");
    private static final NamespacedKey MOB = new NamespacedKey("infinityexpansion2", "mob_data_id");
    private static final NamespacedKey OWNER = new NamespacedKey("probe", "owner");
    private static final NamespacedKey BYTES = new NamespacedKey("probe", "bytes");
    private static final Component EXTRA = Component.text("Owner's extra lore", NamedTextColor.GOLD)
        .hoverEvent(HoverEvent.showText(Component.text("Keep rich components")));
    private final List<String> results = new ArrayList<>();
    private int failures;
    private int assertions;
    private Method refresh;

    @Override public JavaPlugin getJavaPlugin() { return this; }
    @Override public String getBugTrackerURL() { return null; }

    @Override public void onEnable() {
        try {
            register("probe_api_hostile", null);
            register("probe_api_neutral", "NEUTRAL");
            register("probe_api_boss", "BOSS");
            refresh = Arrays.stream(MobDataCard.Companion.getClass().getMethods())
                .filter(m -> m.getName().startsWith("refreshAppearance") && m.getParameterCount() == 1)
                .findFirst().orElseThrow();
            Bukkit.getScheduler().runTaskLater(this, this::runProbe, 40L);
        } catch (Throwable failure) {
            fail("probe setup", failure);
            Bukkit.getScheduler().runTask(this, this::finish);
        }
    }

    private void register(String id, String category) throws Exception {
        ItemStack[] recipe = new ItemStack[9];
        recipe[0] = new ItemStack(Material.DIRT);
        recipe[4] = SlimefunItem.getById("IE_MOB_DATA_CARD_EMPTY").getItem().clone();
        MobDataCardProps props = new MobDataCardProps(id, id, new ItemStack(Material.IRON_CHESTPLATE),
            75, 1, List.of(new Pair<>(new ItemStack(Material.DIRT), 1.0)), recipe);
        if (category == null) {
            InfinityExpansion2API.registerMobDataCard(props, this);
        } else {
            Class<?> groups = Class.forName("net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideGroup");
            Object group = Arrays.stream(groups.getEnumConstants()).filter(e -> e.toString().equals(category)).findFirst().orElseThrow();
            Method register = Arrays.stream(InfinityExpansion2API.class.getMethods())
                .filter(m -> m.getName().startsWith("registerMobDataCard") && m.getParameterCount() == 3)
                .findFirst().orElseThrow();
            register.invoke(InfinityExpansion2API.INSTANCE, props, this, group);
        }
    }

    private void runProbe() {
        run("registered guide and crafted output categories", () -> {
            Class<?> registry = Class.forName("net.guizhanss.infinityexpansion2.core.IERegistry");
            Map<?, ?> cards = (Map<?, ?>) registry.getMethod("getMobDataCards").invoke(registry.getField("INSTANCE").get(null));
            Class<?> groups = Class.forName("net.guizhanss.infinityexpansion2.implementation.guide.MobDataCardGuideGroups");
            Object groupResolver = groups.getField("INSTANCE").get(null);
            Method configured = Arrays.stream(MobDataCard.class.getMethods()).filter(m -> m.getName().startsWith("getGuideGroup")).findFirst().orElseThrow();
            int checked = 0;
            for (Object entry : cards.keySet()) {
                String id = entry.toString();
                if (id.isEmpty()) continue;
                MobDataCard card = MobDataCard.Companion.getMobDataCard(id);
                check(card != null, "registered card " + id);
                Object group = configured.invoke(card);
                if (group == null) group = groups.getMethod("defaultGroup", String.class).invoke(groupResolver, id);
                Material material = (Material) group.getClass().getMethod("getMaterial").invoke(group);
                if (card.getItem().getType().name().endsWith("_CHESTPLATE")) {
                    check(card.getItem().getType() == material, id + " guide category");
                    check(card.getRecipeOutput().getType() == material, id + " output category");
                }
                check(card.getItem().getItemMeta().hasDisplayName(), id + " card name");
                check(card.getItem().getItemMeta().lore() != null && !card.getItem().getItemMeta().lore().isEmpty(), id + " mob label");
                checked++;
            }
            check(checked > 60, "sufficient registered cards: " + checked);
            results.add("Registered cards checked: " + checked);
        });
        run("API addon wrong iron icon normalizes hostile guide and output", () -> category("probe_api_hostile", Material.DIAMOND_CHESTPLATE));
        run("explicit internal neutral category normalizes guide and output", () -> category("probe_api_neutral", Material.COPPER_CHESTPLATE));
        run("explicit internal boss category normalizes guide and output", () -> category("probe_api_boss", Material.NETHERITE_CHESTPLATE));
        run("config guide-group override controls guide and output", () -> category("probe_config_neutral", Material.COPPER_CHESTPLATE));
        for (boolean generic : new boolean[]{true, false}) {
            run((generic ? "generic" : "variant") + " card identity and metadata", () -> {
                ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, generic);
                source.setAmount(23);
                ItemMeta meta = source.getItemMeta();
                meta.lore(List.of(Component.text("Zombie"), EXTRA));
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                meta.setUnbreakable(true);
                source.setItemMeta(meta);
                ItemStack before = source.clone();
                ItemStack updated = refresh(source);
                preserved(source, updated, Material.DIAMOND_CHESTPLATE);
                check(updated.getItemMeta().getEnchantLevel(Enchantment.UNBREAKING) == 3, "enchantment retained");
                check(updated.getItemMeta().isUnbreakable(), "unbreakable retained");
                check(updated.getItemMeta().lore().get(1).equals(EXTRA), "extra rich lore retained");
                check(source.equals(before), "source unchanged");
                check(refresh(updated) == null, "idempotent");
            });
        }
        run("stack64 blank carpet uses category and retains capacity", () -> {
            ItemStack source = stale("enderman", Material.BLUE_CARPET, true);
            source.setAmount(64);
            ItemMeta meta = source.getItemMeta();
            meta.displayName(Component.empty());
            meta.lore(List.of(Component.empty(), EXTRA));
            source.setItemMeta(meta);
            ItemStack updated = refresh(source);
            preserved(source, updated, Material.COPPER_CHESTPLATE);
            check(updated.getMaxStackSize() == 64, "effective stack capacity retained");
            check(updated.getItemMeta().lore().get(1).equals(EXTRA), "carpet extra lore retained");
            check(!updated.getItemMeta().displayName().equals(Component.empty()), "card title filled");
            check(refresh(updated) == null, "carpet idempotent");
        });
        run("custom item model preserved", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            ItemMeta meta = source.getItemMeta();
            meta.setItemModel(new NamespacedKey("probe", "custom_card"));
            source.setItemMeta(meta);
            ItemStack before = source.clone();
            check(refresh(source) == null, "custom model no-op");
            check(source.equals(before), "custom model source unchanged");
        });
        run("custom model data preserved", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            ItemMeta meta = source.getItemMeta();
            CustomModelDataComponent model = meta.getCustomModelDataComponent();
            model.setStrings(List.of("custom-card"));
            model.setFloats(List.of(1234.5F));
            meta.setCustomModelDataComponent(model);
            source.setItemMeta(meta);
            check(refresh(source) == null, "custom model component no-op");
        });
        run("player title and custom label retained while category changes", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            ItemMeta meta = source.getItemMeta();
            Component name = Component.text("Jason's card", NamedTextColor.AQUA);
            Component label = Component.text("Custom monster label", NamedTextColor.GREEN);
            meta.displayName(name);
            meta.lore(List.of(label, EXTRA));
            source.setItemMeta(meta);
            ItemStack updated = refresh(source);
            preserved(source, updated, Material.DIAMOND_CHESTPLATE);
            check(updated.getItemMeta().displayName().equals(name), "player title preserved");
            check(updated.getItemMeta().lore().equals(List.of(label, EXTRA)), "custom lore preserved");
        });
        run("fixed modern item name is not shadowed by a generic title", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            ItemMeta meta = source.getItemMeta();
            Component fixed = Component.text("Fixed custom simulator", NamedTextColor.AQUA);
            meta.displayName(null);
            meta.itemName(fixed);
            source.setItemMeta(meta);
            ItemStack updated = refresh(source);
            preserved(source, updated, Material.DIAMOND_CHESTPLATE);
            check(!updated.getItemMeta().hasDisplayName(), "no added display name shadows fixed name");
            check(updated.getItemMeta().itemName().equals(fixed), "fixed item name preserved");
            check(refresh(updated) == null, "fixed name idempotent");
        });
        run("interactive canonical-looking title and label survive", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            ItemMeta meta = source.getItemMeta();
            Component name = Component.text("Mob Data Card").hoverEvent(HoverEvent.showText(Component.text("Owner note")));
            Component label = Component.text("Zombie").insertion("owner-label");
            meta.displayName(name);
            meta.lore(List.of(label, EXTRA));
            source.setItemMeta(meta);
            ItemStack updated = refresh(source);
            preserved(source, updated, Material.DIAMOND_CHESTPLATE);
            check(updated.getItemMeta().displayName().equals(name), "interactive title preserved");
            check(updated.getItemMeta().lore().equals(List.of(label, EXTRA)), "interactive mob label preserved");
            check(refresh(updated) == null, "interactive components idempotent");
        });
        run("existing generic card modernization preserves label and custom metadata", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            source.setAmount(17);
            ItemMeta meta = source.getItemMeta();
            Component customName = Component.text("Jason's simulator", NamedTextColor.AQUA);
            meta.displayName(customName);
            List<Component> originalLore = new ArrayList<>(meta.lore());
            originalLore.add(EXTRA);
            meta.lore(originalLore);
            source.setItemMeta(meta);
            LegacyItemMigrator migrator = new LegacyItemMigrator();
            LegacyItemMigrator.Result result = migrator.migrate(source, true);
            check(result != null && result.getChanged(), "modernization reports change");
            ItemStack updated = result.getItem();
            preserved(source, updated, Material.DIAMOND_CHESTPLATE);
            check(updated.getItemMeta().displayName().equals(customName), "modernization custom name");
            check(updated.getItemMeta().lore().equals(originalLore), "modernization retains mob label and extra lore");
            check(!migrator.migrate(updated, true).getChanged(), "modernization idempotent");
        });
        run("current registered template is a no-op", () -> check(refresh(template("zombie")) == null, "current no-op"));
        run("vanilla chestplate is a no-op", () -> check(refresh(new ItemStack(Material.IRON_CHESTPLATE)) == null, "vanilla no-op"));
        run("mob-only metadata cannot refresh a vanilla item", () -> {
            ItemStack source = new ItemStack(Material.IRON_CHESTPLATE);
            set(source, MOB, "zombie");check(refresh(source) == null, "missing Slimefun ID no-op");
        });
        run("different Slimefun item with a mob field is a no-op", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            set(source, SF, "ELECTRIC_MOTOR");check(refresh(source) == null, "different Slimefun item no-op");
        });
        run("missing mob ID is a no-op", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            ItemMeta meta = source.getItemMeta();meta.getPersistentDataContainer().remove(MOB);source.setItemMeta(meta);
            check(refresh(source) == null, "missing ID no-op");
        });
        run("unknown mob ID is a no-op", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, true);
            set(source, MOB, "probe_unknown");check(refresh(source) == null, "unknown mob no-op");
        });
        run("mismatched variant and mob ID is a no-op", () -> {
            ItemStack source = stale("zombie", Material.IRON_CHESTPLATE, false);
            set(source, MOB, "enderman");check(refresh(source) == null, "mismatched variant no-op");
        });
        run("legacy IE1 alias is a no-op", () -> {
            ItemStack source = stale("enderman", Material.IRON_CHESTPLATE, false);
            set(source, SF, "ENDERMEN_DATA_CARD");
            check(SlimefunItem.getByItem(source) instanceof MobDataCard, "legacy alias actually resolves to a card");
            check(refresh(source) == null, "legacy alias no-op");
            check(source.getItemMeta().getPersistentDataContainer().get(SF, PersistentDataType.STRING).equals("ENDERMEN_DATA_CARD"), "legacy identity unchanged");
        });
        finish();
    }

    private void category(String id, Material expected) {
        MobDataCard card = MobDataCard.Companion.getMobDataCard(id);
        check(card != null, id + " registered");
        check(card.getItem().getType() == expected, id + " guide texture");
        check(card.getRecipeOutput().getType() == expected, id + " output texture");
    }
    private ItemStack template(String id) { return new ItemStack(Objects.requireNonNull(MobDataCard.Companion.getMobDataCard(id)).getItem()); }
    private ItemStack stale(String id, Material material, boolean generic) {
        ItemStack item = template(id).withType(material);
        ItemMeta meta = item.getItemMeta();
        if (generic) meta.getPersistentDataContainer().set(SF, PersistentDataType.STRING, "IE_MOB_DATA_CARD");
        meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 9000000001L);
        meta.getPersistentDataContainer().set(BYTES, PersistentDataType.BYTE_ARRAY, new byte[]{-1, 0, 42, 127});
        item.setItemMeta(meta);
        return item;
    }
    private static void set(ItemStack item, NamespacedKey key, String value) { ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,value);item.setItemMeta(meta); }
    private ItemStack refresh(ItemStack item) throws Exception { return (ItemStack) refresh.invoke(MobDataCard.Companion, item); }
    private void preserved(ItemStack source, ItemStack updated, Material material) {
        check(updated != null, "replacement produced");
        check(updated.getType() == material, "target material");
        check(updated.getAmount() == source.getAmount(), "stack amount retained");
        check(updated.getItemMeta().getPersistentDataContainer().equals(source.getItemMeta().getPersistentDataContainer()), "all typed persistent metadata retained");
    }
    private void check(boolean condition, String detail) { assertions++;if (!condition) throw new AssertionError(detail); }
    private void run(String name, Checked action) { try { action.run();results.add("PASS " + name);getLogger().info("CARD_PROBE_PASS " + name); } catch (Throwable failure) { fail(name, failure); } }
    private void fail(String name, Throwable failure) { failures++;results.add("FAIL " + name + ": " + failure);getLogger().log(Level.SEVERE,"CARD_PROBE_FAIL " + name,failure); }
    private void finish() {
        results.add("Assertions: " + assertions + "; failures: " + failures);
        results.add("Paper: " + Bukkit.getVersion());
        try { getDataFolder().mkdirs();Files.write(getDataFolder().toPath().resolve("results.txt"), results); } catch (Exception failure) { getLogger().log(Level.SEVERE,"Could not retain probe results",failure); }
        getLogger().info("CARD_PROBE_RESULT assertions=" + assertions + " failures=" + failures);
        Bukkit.shutdown();
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
