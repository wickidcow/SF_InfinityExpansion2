package net.guizhanss.infinityexpansion2.utils.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Adventure formatting at the existing string-based localization boundary. */
public final class ReactorText {
    private ReactorText() {
        throw new IllegalStateException("Utility class");
    }

    public static String grayNumber(int value) {
        return LegacyComponentSerializer.legacySection().serialize(
            Component.text(MachineLore.INSTANCE.format(value), NamedTextColor.GRAY));
    }
}
