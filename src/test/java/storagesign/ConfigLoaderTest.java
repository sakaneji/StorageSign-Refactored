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
import java.util.List;
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
        ConfigurationSection potionAliases = section(
            Set.of("minecraft:old"), Map.of("minecraft:old", "minecraft:new"));
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
        when(config.getBoolean("storage-index.enabled", true)).thenReturn(false);
        when(config.getInt("storage-index.rebuild-chunks-per-tick", 8)).thenReturn(4);
        when(config.getBoolean("nearby-display.enabled", true)).thenReturn(true);
        when(config.getDouble("nearby-display.distance", 6.0)).thenReturn(8.0);
        when(config.getDouble("nearby-display.field-of-view-degrees", 90.0)).thenReturn(120.0);
        when(config.getInt("nearby-display.idle-delay-ticks", 10)).thenReturn(20);
        when(config.getInt("nearby-display.monitor-interval-ticks", 5)).thenReturn(2);
        when(config.getInt("nearby-display.max-per-player", 3)).thenReturn(5);
        when(config.getInt("nearby-display.max-searches-per-tick", 25)).thenReturn(12);
        when(config.getInt("nearby-display.global-label-limit", 512)).thenReturn(100);
        when(config.getInt("admin-search.page-size", 10)).thenReturn(7);
        when(config.getInt("admin-search.max-concurrent", 2)).thenReturn(3);
        when(config.getConfigurationSection("item-identifier-aliases")).thenReturn(aliases);
        when(config.getConfigurationSection("potion-key-aliases")).thenReturn(potionAliases);
        when(config.getConfigurationSection("virtual-item-identifiers")).thenReturn(virtuals);
        when(config.getStringList("brewing-ingredient-identifiers"))
            .thenReturn(List.of(" modded_ingredient ", " "));

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
        assertFalse(ConfigLoader.getStorageIndexEnabled());
        assertEquals(4, ConfigLoader.getIndexChunksPerTick());
        assertTrue(ConfigLoader.getNearbyDisplayEnabled());
        assertFalse(ConfigLoader.getEffectiveNearbyDisplayEnabled());
        assertEquals(8.0, ConfigLoader.getNearbyDisplayDistance());
        assertEquals(120.0, ConfigLoader.getNearbyDisplayFov());
        assertEquals(20, ConfigLoader.getNearbyDisplayIdleTicks());
        assertEquals(2, ConfigLoader.getNearbyDisplayIntervalTicks());
        assertEquals(5, ConfigLoader.getNearbyDisplayMaxPerPlayer());
        assertEquals(12, ConfigLoader.getNearbyDisplaySearchesPerTick());
        assertEquals(100, ConfigLoader.getNearbyDisplayGlobalLimit());
        assertEquals(7, ConfigLoader.getAdminSearchPageSize());
        assertEquals(3, ConfigLoader.getAdminSearchMaxConcurrent());
        assertEquals(Map.of("old", "NEW"), ConfigLoader.getIdentifierAliases());
        assertEquals(Map.of("Legacy", "STONE:2"), ConfigLoader.getVirtualItemIdentifiers());
        assertEquals(Map.of("minecraft:old", "minecraft:new"), ConfigLoader.getPotionKeyAliases());
        assertEquals(Set.of("MODDED_INGREDIENT"), ConfigLoader.getBrewingIngredientIdentifiers());
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
        assertTrue(ConfigLoader.getPotionKeyAliases().isEmpty());
        assertTrue(ConfigLoader.getBrewingIngredientIdentifiers().isEmpty());
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
