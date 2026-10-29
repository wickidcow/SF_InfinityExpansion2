package net.guizhanss.infinityexpansion2.utils.items;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Random;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** String parity at the existing localization boundary, not a claim about arbitrary rich text. */
@SuppressWarnings("deprecation") // Old Bukkit output is the test oracle only.
@ResourceLock("java.util.Locale.default")
class ReactorTextTest {
    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de-DE", "fr-FR", "ar-EG", "hi-IN", "ja-JP"})
    void grayNumbersKeepExactLegacyBytesAndLocaleFormatting(String languageTag) {
        Locale original = Locale.getDefault();
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            int[] boundaries = {Integer.MIN_VALUE, -1, 0, 1, 999, 1000, 28_800, 172_800, 5_184_000, Integer.MAX_VALUE};
            for (int value : boundaries) assertParity(value);
            var random = new Random(0x1E2L);
            for (int i = 0; i < 128; i++) assertParity(random.nextInt());
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
        }
    }

    private void assertParity(int value) {
        assertEquals(ChatColor.GRAY.toString() + MachineLore.INSTANCE.format(value), ReactorText.grayNumber(value));
    }
}
