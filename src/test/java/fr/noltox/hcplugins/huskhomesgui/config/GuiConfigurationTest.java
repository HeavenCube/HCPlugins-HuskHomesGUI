package fr.noltox.hcplugins.huskhomesgui.config;

import fr.noltox.hcconfig.HCConfigurations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuiConfigurationTest {

    @Test
    void keepsFilterOrderAndTakesImmutableSnapshots() {
        var source = new LinkedHashMap<String, GuiConfiguration.FilterConfig>();
        var filter = new GuiConfiguration.FilterConfig("selected", "unselected", List.of());
        source.put("distance", filter);
        source.put("alphabetical", filter);
        var config = new GuiConfiguration.MenuConfig("title", 0, List.of(), "", source, Map.of());
        source.clear();
        assertEquals(List.of("distance", "alphabetical"), List.copyOf(config.filters().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> config.filters().clear());

        var runtime = new LinkedHashMap<String, GuiConfiguration.FilterDefinition>();
        runtime.put("distance", new GuiConfiguration.FilterDefinition("distance", "", "", List.of()));
        runtime.put("alphabetical", new GuiConfiguration.FilterDefinition("alphabetical", "", "", List.of()));
        var menu = new GuiConfiguration.MenuDefinition("homes", "", List.of(), 0, null, Map.of(), Map.of(), runtime);
        runtime.clear();
        assertEquals(List.of("distance", "alphabetical"), List.copyOf(menu.filters().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> menu.filters().clear());
    }

    @Test
    void loadsBundledConfigurationsViaConfigLib(@TempDir Path tempDir) throws Exception {
        Path itemsPath = copyResource("items.yml", tempDir);
        Path menusPath = copyResource("menus.yml", tempDir);
        Path localePath = copyResource("locale.yml", tempDir);

        GuiConfiguration.SharedItemsConfig sharedItems = HCConfigurations.load(
                itemsPath,
                GuiConfiguration.SharedItemsConfig.class
        );
        assertNotNull(sharedItems);
        assertNotNull(sharedItems.filler());
        assertNotNull(sharedItems.previousPage());
        assertFalse(sharedItems.toMap().isEmpty());

        GuiConfiguration.MenusConfigFile menusConfig = HCConfigurations.load(
                menusPath,
                GuiConfiguration.MenusConfigFile.class
        );
        assertNotNull(menusConfig);
        assertNotNull(menusConfig.homes());
        assertFalse(menusConfig.homes().shape().isEmpty());
        assertNotNull(menusConfig.homes().items());
        assertNotNull(menusConfig.editHome());
        assertNotNull(menusConfig.selectIcon());
        assertNotNull(menusConfig.arrangeHomes());

        GuiConfiguration.LocaleConfig localeConfig = HCConfigurations.load(
                localePath,
                GuiConfiguration.LocaleConfig.class
        );
        assertNotNull(localeConfig);
        assertNotNull(localeConfig.toMessages());
    }

    private static Path copyResource(String resourceName, Path directory) throws Exception {
        Path target = directory.resolve(resourceName);
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(in, "Resource " + resourceName + " should exist in test/main classpath");
            Files.copy(in, target);
        }
        return target;
    }
}
