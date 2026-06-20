package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

    @Test
    void loadCachesAllScalarsAndSanitizedCompatibilityMaps() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        ConfigurationSection aliases = section(
            Set.of(" old ", "blank", "missing"),
            Map.of(" old ", " NEW ", "blank", "  ")
        );
        ConfigurationSection virtuals = section(Set.of("Legacy"), Map.of("Legacy", "STONE:2"));
        when(plugin.getConfig()).thenReturn(config);
        when(config.getString("no-permisson", "You don't have permission")).thenReturn("denied");
        when(config.getString("log-level", "INFO")).thenReturn("TRACE");
        when(config.getBoolean("manual-import", true)).thenReturn(false);
        when(config.getBoolean("manual-export", true)).thenReturn(false);
        when(config.getBoolean("auto-import", true)).thenReturn(false);
        when(config.getBoolean("auto-export", true)).thenReturn(false);
        when(config.getBoolean("autocollect", true)).thenReturn(false);
        when(config.getBoolean("hardrecipe", false)).thenReturn(true);
        when(config.getInt("divide-limit", 345600)).thenReturn(10);
        when(config.getInt("sneak-divide-limit", 34560)).thenReturn(20);
        when(config.getInt("max-stack-size", 16)).thenReturn(3);
        when(config.getBoolean("unregister-on-empty", false)).thenReturn(true);
        when(config.getBoolean("no-bud", false)).thenReturn(true);
        when(config.getBoolean("falling-block-itemSS", false)).thenReturn(true);
        when(config.getBoolean("banner-debug", false)).thenReturn(true);
        when(config.getConfigurationSection("item-identifier-aliases")).thenReturn(aliases);
        when(config.getConfigurationSection("virtual-item-identifiers")).thenReturn(virtuals);

        ConfigLoader.load(plugin);

        verify(plugin).saveDefaultConfig();
        verify(plugin).reloadConfig();
        assertEquals("denied", ConfigLoader.getNoPermission());
        assertEquals("TRACE", ConfigLoader.getLogLevel());
        assertFalse(ConfigLoader.getManualImport());
        assertFalse(ConfigLoader.getManualExport());
        assertFalse(ConfigLoader.getAutoImport());
        assertFalse(ConfigLoader.getAutoExport());
        assertFalse(ConfigLoader.getAutocollect());
        assertTrue(ConfigLoader.getHardrecipe());
        assertEquals(10, ConfigLoader.getDivideLimit());
        assertEquals(20, ConfigLoader.getSneakDivideLimit());
        assertEquals(3, ConfigLoader.getMaxStackSize());
        assertTrue(ConfigLoader.getUnregisterOnEmpty());
        assertTrue(ConfigLoader.getNoBud());
        assertTrue(ConfigLoader.getFallingBlockItemSS());
        assertTrue(ConfigLoader.getBannerDebug());
        assertEquals(Map.of("old", "NEW"), ConfigLoader.getIdentifierAliases());
        assertEquals(Map.of("Legacy", "STONE:2"), ConfigLoader.getVirtualItemIdentifiers());
        assertThrows(UnsupportedOperationException.class,
            () -> ConfigLoader.getIdentifierAliases().put("x", "y"));
    }

    @Test
    void loadUsesEmptyMapsWhenSectionsAreAbsent() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);

        ConfigLoader.load(plugin);

        assertTrue(ConfigLoader.getIdentifierAliases().isEmpty());
        assertTrue(ConfigLoader.getVirtualItemIdentifiers().isEmpty());
    }

    private static ConfigurationSection section(Set<String> keys, Map<String, String> values) {
        ConfigurationSection section = mock(ConfigurationSection.class);
        when(section.getKeys(false)).thenReturn(keys);
        for (String key : keys) {
            when(section.getString(key)).thenReturn(values.get(key));
        }
        return section;
    }
}
