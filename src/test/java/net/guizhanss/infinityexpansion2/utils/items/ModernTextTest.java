package net.guizhanss.infinityexpansion2.utils.items;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Random;
import net.guizhanss.infinityexpansion2.core.services.LocalizationService;
import net.guizhanss.infinityexpansion2.utils.constant.Strings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

@SuppressWarnings("deprecation") // Historical string behavior is an oracle, not production code.
class ModernTextTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @ParameterizedTest @NullSource
    @ValueSource(strings={"", " ", "&a", "&X&1&2&A&B&3&4", "§x§1§2§a§b§3§4", "&a Name", "§7Name", "&z", "§z", "<red>", "&&a", "&L &R", "§l§r", "&a&l  "})
    void configuredNameVisibilityRetainsTheExactOldColorRules(String text) {
        boolean old = text != null && !ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)).trim().isEmpty();
        assertEquals(old, ModernText.hasVisibleLegacyText(text));
    }

    @Test void visibilityMatchesLegacyForDeterministicMixedCodes() {
        var random = new Random(0x1E217);
        String alphabet="&§0123456789aAfFkKlLmMnNoOrRxXzZ <> \t";
        for (int sample=0; sample<512; sample++) {
            var text=new StringBuilder();
            for (int i=0; i<random.nextInt(40); i++) text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String value=text.toString();
            assertEquals(!ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value)).trim().isEmpty(),
                ModernText.hasVisibleLegacyText(value), value);
        }
    }

    @Test void appendingLorePreservesEveryExistingRichComponentAndTypedIdentity() {
        var item=new ItemStack(Material.PAPER, 37);
        var meta=item.getItemMeta();
        var rgb=TextColor.color(0x12ab34);
        var original=Component.text("Owner",rgb).hoverEvent(HoverEvent.showText(Component.translatable("entity.minecraft.vex")))
            .append(Component.text(" note").insertion("retain me").clickEvent(ClickEvent.runCommand("/help")));
        var translated=Component.translatable("item.minecraft.diamond");
        meta.lore(List.of(original,translated));
        var key=new NamespacedKey("otheraddon","identity");
        meta.getPersistentDataContainer().set(key,PersistentDataType.LONG,9_000_000_001L);
        var before=meta.clone();
        ModernText.appendLore(meta,"", "§7Amount: 1-3", "§x§1§2§a§b§3§4RGB");
        assertEquals(List.of(original,translated),meta.lore().subList(0,2));
        assertEquals(before.getPersistentDataContainer(),meta.getPersistentDataContainer());
        assertEquals(5,meta.lore().size());
        assertEquals(rgb,meta.lore().get(4).color());
        assertEquals(List.of(original,translated),before.lore());
        item.setItemMeta(meta);
        assertEquals(37,item.getAmount());
    }

    @Test void newLoreKeepsExplicitItalicAndDisablesImplicitItalic() {
        assertEquals(TextDecoration.State.TRUE,ModernText.loreLine("§oItalic").decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.FALSE,ModernText.loreLine("§7Normal").decoration(TextDecoration.ITALIC));
        var meta=new ItemStack(Material.PAPER).getItemMeta();
        ModernText.appendLore(meta,"§7New");
        assertEquals(1,meta.lore().size());
        assertEquals("New",PlainTextComponentSerializer.plainText().serialize(meta.lore().getFirst()));
    }

    @Test void actionBarRetainsRgbAndUrlInteractionWithoutMiniMessageInterpretation() {
        var output=ModernText.actionBar("§x§1§2§a§b§3§4RGB <red> https://example.com");
        assertEquals("RGB <red> https://example.com",PlainTextComponentSerializer.plainText().serialize(output));
        assertTrue(containsColor(output,TextColor.color(0x12ab34)));
        assertTrue(containsUrl(output,"https://example.com"));
        assertEquals(NamedTextColor.WHITE,ModernText.actionBar("plain").color());
    }

    @Test void localizationSendsOneAdventureActionbarWithExistingArgumentFormatting() {
        var service=mock(LocalizationService.class,CALLS_REAL_METHODS);
        doReturn("&aBalance: {0}").when(service).getString("messages.balance");
        var player=mock(Player.class);
        service.sendActionbarMessage(player,"balance","42");
        verify(player).sendActionBar(ModernText.actionBar("§aBalance: 42"));
        verify(player,never()).spigot();
        verifyNoMoreInteractions(player);
    }

    @Test void publicStringConstantsKeepTheirExactHistoricalValues() {
        assertEquals(ChatColor.GREEN.toString()+"✔",Strings.INSTANCE.getCHECK());
        assertEquals(ChatColor.RED.toString()+"✘",Strings.INSTANCE.getCROSS());
    }

    private static boolean containsColor(Component text,TextColor color) {
        return color.equals(text.color()) || text.children().stream().anyMatch(child->containsColor(child,color));
    }
    private static boolean containsUrl(Component text,String url) {
        return ClickEvent.openUrl(url).equals(text.clickEvent()) || text.children().stream().anyMatch(child->containsUrl(child,url));
    }
}
