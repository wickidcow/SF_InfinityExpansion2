package net.guizhanss.infinityexpansion2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Build modernizations must retain the generated descriptor's established access defaults. */
class PluginPermissionMetadataTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PluginDescriptionFile descriptor() throws Exception {
        // CI separately verifies that the shaded JAR contains these exact descriptor bytes.
        try (InputStream input = Files.newInputStream(Path.of("build/resources/main/plugin.yml"))) {
            return new PluginDescriptionFile(input);
        }
    }

    @Test
    void generatedPluginDescriptorRetainsAllFiveExistingCommandPermissions() throws Exception {
        PluginDescriptionFile descriptor = descriptor();
        assertEquals("InfinityExpansion2", descriptor.getName());
        Map<String, PermissionDefault> actual = descriptor.getPermissions().stream()
                .collect(Collectors.toMap(Permission::getName, Permission::getDefault));
        assertEquals(Map.of(
                "infinityexpansion2.command.doctor", PermissionDefault.OP,
                "infinityexpansion2.command.giverecipe", PermissionDefault.OP,
                "infinityexpansion2.command.guide", PermissionDefault.TRUE,
                "infinityexpansion2.command.printitem", PermissionDefault.OP,
                "infinityexpansion2.command.id", PermissionDefault.OP), actual);
    }

    @Test
    void commandAliasesAndRequiredCoreRemainUnchanged() throws Exception {
        PluginDescriptionFile descriptor = descriptor();
        assertEquals(List.of("Slimefun"), descriptor.getDepend());
        assertEquals(List.of("ie", "ie2"), descriptor.getCommands().get("infinityexpansion2").get("aliases"));
    }
}
