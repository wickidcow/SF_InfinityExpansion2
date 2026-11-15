package net.guizhanss.infinityexpansion2.implementation.items.storage;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import net.guizhanss.guizhanlib.minecraft.utils.ChatUtil;
import net.guizhanss.infinityexpansion2.InfinityExpansion2;
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType;
import net.guizhanss.infinityexpansion2.utils.constant.Strings;
import net.guizhanss.infinityexpansion2.utils.items.ModernText;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Rebuild only IE2's documented four generated Chinese Storage Unit lore lines.
 *
 * <p>Every old line must match the bundled zh-CN template applied to the
 * actual typed storage ledger before an English label is emitted. Unrecognized
 * old versions, rich owner lore, extra lines, damaged ledgers and missing
 * integrations always reject. Original nested item identity, amount, owner and
 * persistent data are copied unchanged; no block or inventory is mutated.
 */
final class HistoricalStorageLorePreview {
    private static final String[] KEYS = {"item", "amount", "void-excess"};

    private HistoricalStorageLorePreview() {}

    static ItemStack candidate(ItemStack historical, ItemStack englishTemplate,
                               String historicalId, String templateId) {
        if (historical == null || englishTemplate == null || historicalId == null
                || !historicalId.equals(templateId)
                || !historicalId.startsWith("IE_STORAGE_UNIT_")) return null;
        try {
            var registration = SlimefunItem.getById(historicalId);
            if (!(registration instanceof StorageUnit)) return null;
            ItemMeta originalMeta = historical.getItemMeta();
            ItemMeta templateMeta = englishTemplate.getItemMeta();
            if (originalMeta == null || templateMeta == null
                    || templateMeta.displayName() == null
                    || templateMeta.lore() != null && !templateMeta.lore().isEmpty()) return null;

            var dataService = Slimefun.getItemDataService();
            if (!historicalId.equals(dataService.getItemData(originalMeta).orElse(null))
                    || !historicalId.equals(dataService.getItemData(templateMeta).orElse(null))) {
                return null;
            }
            StorageCache cache = PersistentDataAPI.get(originalMeta,
                StorageUnit.Companion.getSTORAGE_KEY(), PersistentStorageCacheType.getTYPE());
            if (cache == null || cache.getAmount() <= 0 || cache.getLimit() <= 0
                    || cache.getAmount() > cache.getLimit()
                    || cache.getItemStack() == null || cache.getItemStack().getType().isAir()) {
                return null;
            }

            // Derive dynamic values solely from the authoritative typed PDC
            // ledger, NEVER by parsing or silently trusting old visible text.
            var storedName = ItemUtils.getItemName(cache.getItemStack());
            String[] values = {
                storedName,
                cache.getAmount() + " / " + cache.getLimit(),
                cache.getVoidExcess() ? Strings.INSTANCE.getCHECK() : Strings.INSTANCE.getCROSS()
            };
            var oldLines = translatedLore("zh-CN", values);
            var newLines = translatedLore("en", values);
            if (oldLines == null || newLines == null
                    || !Objects.equals(oldLines, originalMeta.lore())) return null;

            var proposed = historical.clone();
            var meta = proposed.getItemMeta();
            if (meta == null) return null;
            meta.displayName(templateMeta.displayName());
            meta.lore(newLines);
            if (!proposed.setItemMeta(meta)
                    || proposed.getAmount() != historical.getAmount()
                    || !HistoricalPresentationPreview.sameNonPresentation(historical, proposed)) return null;
            return proposed;
        } catch (RuntimeException malformedOrUnavailable) {
            // Corrupt storage data, unavailable Slimefun service, invalid locale
            // or unrelated addon versions must never authorize an item rewrite.
            return null;
        }
    }

    private static List<Component> translatedLore(String lang, String[] values) {
        var plugin = InfinityExpansion2.Companion.getInstance();
        try (var input = plugin.getResource("lang/" + lang + ".yml")) {
            if (input == null) return null;
            var yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(input, StandardCharsets.UTF_8));
            String[] templates = new String[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) {
                templates[i] = yaml.getString("lores.storage." + KEYS[i]);
                if (templates[i] == null || !templates[i].contains("{0}")) return null;
            }
            return List.of(
                ModernText.loreLine(""),
                ModernText.loreLine(ChatUtil.color(MessageFormat.format(templates[0], values[0]))),
                ModernText.loreLine(ChatUtil.color(MessageFormat.format(templates[1], values[1]))),
                ModernText.loreLine(ChatUtil.color(MessageFormat.format(templates[2], values[2])))
            );
        } catch (Exception unreadableLocale) {
            return null;
        }
    }
}
