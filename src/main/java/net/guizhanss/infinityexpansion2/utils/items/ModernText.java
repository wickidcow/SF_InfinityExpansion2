package net.guizhanss.infinityexpansion2.utils.items;

import java.util.ArrayList;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

/** Convert only new legacy-text input; never round-trip existing rich item components. */
public final class ModernText {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer ACTION_BAR = LegacyComponentSerializer.builder()
        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().extractUrls().build();
    private static final Pattern LEGACY_CODES = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private ModernText() { throw new IllegalStateException("Utility class"); }

    public static Component loreLine(String text) {
        return LEGACY.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void appendLore(ItemMeta meta, String... additions) {
        var existing = meta.lore();
        var lore = existing == null ? new ArrayList<Component>() : new ArrayList<>(existing);
        for (String line : additions) lore.add(loreLine(line));
        meta.lore(lore);
    }

    /** Input has already passed through the existing configured ChatUtil color translator. */
    public static Component actionBar(String coloredText) {
        return ACTION_BAR.deserialize(coloredText).colorIfAbsent(NamedTextColor.WHITE);
    }

    /** Exact legacy alternate-code/strip rules, without parsing configuration as MiniMessage. */
    public static boolean hasVisibleLegacyText(String text) {
        if (text == null) return false;
        char[] chars = text.toCharArray();
        for (int i = 0; i + 1 < chars.length; i++) {
            if (chars[i] == '&' && COLOR_CODES.indexOf(chars[i + 1]) >= 0) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return !LEGACY_CODES.matcher(new String(chars)).replaceAll("").trim().isEmpty();
    }
}
