package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class ConfigLoaderTest {

    @Test
    void loadCreatesRuntimeConfigFromThePackagedDefaultAndReadsRepresentativeValues() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Path dataFolder = tempDataFolder();
        Path defaultResource = Path.of("src/main/resources/config.default.yml");
        FileConfiguration parsedDefault = YamlConfiguration.loadConfiguration(defaultResource.toFile());
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml")).thenAnswer(invocation ->
            Files.newInputStream(defaultResource));
        when(plugin.getConfig()).thenReturn(parsedDefault);
        doNothing().when(plugin).reloadConfig();

        ConfigLoader.load(plugin);

        assertArrayEquals(Files.readAllBytes(defaultResource),
            Files.readAllBytes(dataFolder.resolve("config.yml")));
        assertEquals("INFO", ConfigLoader.getLogLevel());
        assertTrue(ConfigLoader.getManualImport());
        assertEquals(345600, ConfigLoader.getDivideLimit());
        assertTrue(ConfigLoader.getStorageIndexEnabled());
        assertEquals(3.0, ConfigLoader.getNearbyDisplayDistance());
        assertEquals(Map.of("HorseEgg", "END_PORTAL:1", "EmptySign", "OAK_SIGN:1"),
            ConfigLoader.getVirtualItemIdentifiers());
    }

    @Test
    void loadCachesAllScalarsAndSanitizedCompatibilityMaps() throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Path dataFolder = tempDataFolder();
        ConfigurationSection aliases = section(
            Set.of(" old ", "blank", "missing"),
            Map.of(" old ", " NEW ", "blank", "  ")
        );
        ConfigurationSection virtuals = section(Set.of("Legacy"), Map.of("Legacy", "STONE:2"));
        ConfigurationSection potionAliases = section(
            Set.of("minecraft:old"), Map.of("minecraft:old", "minecraft:new"));
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml")).thenReturn(
            new ByteArrayInputStream("default-config".getBytes()));
        when(plugin.getConfig()).thenReturn(config);
        doNothing().when(plugin).reloadConfig();
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
        when(config.getInt("storage-index.chunk-rescan-queue-cap", 512)).thenReturn(256);
        when(config.getBoolean("nearby-display.enabled", true)).thenReturn(true);
        when(config.getDouble("nearby-display.distance", 3.0)).thenReturn(8.0);
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

        verify(plugin).getResource("config.default.yml");
        verify(plugin).reloadConfig();
        assertArrayEquals("default-config".getBytes(), Files.readAllBytes(dataFolder.resolve("config.yml")));
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
        assertEquals(256, ConfigLoader.getIndexChunkRescanQueueCap());
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
        Path dataFolder = tempDataFolder();
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml")).thenReturn(
            new ByteArrayInputStream("default-config".getBytes()));
        when(plugin.getConfig()).thenReturn(config);
        doNothing().when(plugin).reloadConfig();

        ConfigLoader.load(plugin);

        assertTrue(ConfigLoader.getIdentifierAliases().isEmpty());
        assertTrue(ConfigLoader.getPotionKeyAliases().isEmpty());
        assertTrue(ConfigLoader.getBrewingIngredientIdentifiers().isEmpty());
        assertTrue(ConfigLoader.getVirtualItemIdentifiers().isEmpty());
    }

    @Test
    void loadFallsBackForNonPositiveSizingValues() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Path dataFolder = tempDataFolder();
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml")).thenReturn(
            new ByteArrayInputStream("default-config".getBytes()));
        when(plugin.getConfig()).thenReturn(config);
        doNothing().when(plugin).reloadConfig();
        when(config.getStringList("brewing-ingredient-identifiers")).thenReturn(List.of());
        when(config.getConfigurationSection("item-identifier-aliases")).thenReturn(null);
        when(config.getConfigurationSection("potion-key-aliases")).thenReturn(null);
        when(config.getConfigurationSection("virtual-item-identifiers")).thenReturn(null);
        when(config.getInt("storage-index.rebuild-chunks-per-tick", 8)).thenReturn(0);
        when(config.getInt("storage-index.chunk-rescan-queue-cap", 512)).thenReturn(0);
        when(config.getBoolean("storage-index.enabled", true)).thenReturn(true);
        when(config.getBoolean("nearby-display.enabled", true)).thenReturn(true);
        when(config.getDouble("nearby-display.distance", 3.0)).thenReturn(-1.0);
        when(config.getDouble("nearby-display.field-of-view-degrees", 90.0)).thenReturn(Double.NaN);
        when(config.getInt("nearby-display.idle-delay-ticks", 10)).thenReturn(-5);
        when(config.getInt("nearby-display.monitor-interval-ticks", 5)).thenReturn(0);
        when(config.getInt("nearby-display.max-per-player", 3)).thenReturn(-1);
        when(config.getInt("nearby-display.max-searches-per-tick", 25)).thenReturn(0);
        when(config.getInt("nearby-display.global-label-limit", 512)).thenReturn(-7);
        when(config.getInt("admin-search.page-size", 10)).thenReturn(0);
        when(config.getInt("admin-search.max-concurrent", 2)).thenReturn(-3);

        ConfigLoader.load(plugin);

        assertEquals(8, ConfigLoader.getIndexChunksPerTick());
        assertEquals(512, ConfigLoader.getIndexChunkRescanQueueCap());
        assertEquals(3.0, ConfigLoader.getNearbyDisplayDistance());
        assertEquals(1.0, ConfigLoader.getNearbyDisplayFov());
        assertEquals(10, ConfigLoader.getNearbyDisplayIdleTicks());
        assertEquals(5, ConfigLoader.getNearbyDisplayIntervalTicks());
        assertEquals(3, ConfigLoader.getNearbyDisplayMaxPerPlayer());
        assertEquals(25, ConfigLoader.getNearbyDisplaySearchesPerTick());
        assertEquals(512, ConfigLoader.getNearbyDisplayGlobalLimit());
        assertEquals(10, ConfigLoader.getAdminSearchPageSize());
        assertEquals(2, ConfigLoader.getAdminSearchMaxConcurrent());
    }

    @Test
    void nearbyDisplayIsEffectivelyDisabledWhenEitherGateIsOff() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Path dataFolder = tempDataFolder();
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml")).thenReturn(
            new ByteArrayInputStream("default-config".getBytes()));
        when(plugin.getConfig()).thenReturn(config);
        doNothing().when(plugin).reloadConfig();
        when(config.getStringList("brewing-ingredient-identifiers")).thenReturn(List.of());
        when(config.getConfigurationSection("item-identifier-aliases")).thenReturn(null);
        when(config.getConfigurationSection("potion-key-aliases")).thenReturn(null);
        when(config.getConfigurationSection("virtual-item-identifiers")).thenReturn(null);
        when(config.getBoolean("storage-index.enabled", true)).thenReturn(true);
        when(config.getBoolean("nearby-display.enabled", true)).thenReturn(false);

        ConfigLoader.load(plugin);

        assertFalse(ConfigLoader.getNearbyDisplayEnabled());
        assertFalse(ConfigLoader.getEffectiveNearbyDisplayEnabled());
    }

    @Test
    void loadDoesNotOverwriteExistingRuntimeConfig() throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Path dataFolder = tempDataFolder();
        Path runtimeConfig = dataFolder.resolve("config.yml");
        Files.writeString(runtimeConfig, "existing-config");
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml")).thenThrow(new AssertionError("default resource should not be read"));
        when(plugin.getConfig()).thenReturn(config);
        doNothing().when(plugin).reloadConfig();
        when(config.getStringList("brewing-ingredient-identifiers")).thenReturn(List.of());
        when(config.getConfigurationSection("item-identifier-aliases")).thenReturn(null);
        when(config.getConfigurationSection("potion-key-aliases")).thenReturn(null);
        when(config.getConfigurationSection("virtual-item-identifiers")).thenReturn(null);

        ConfigLoader.load(plugin);

        assertEquals("existing-config", Files.readString(runtimeConfig));
    }

    @Test
    void loadMigratesLegacyConfigBeforeCreatingDefaults() throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Path pluginsFolder = Files.createTempDirectory("storagesign-config-migration");
        Path dataFolder = pluginsFolder.resolve("StorageSign-Refactored");
        Path legacyFolder = pluginsFolder.resolve("StorageSign");
        Files.createDirectories(legacyFolder);
        Files.writeString(legacyFolder.resolve("config.yml"), "manual-import: false");
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("config.default.yml"))
            .thenThrow(new AssertionError("default resource should not be read"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doNothing().when(plugin).reloadConfig();
        when(config.getStringList("brewing-ingredient-identifiers")).thenReturn(List.of());

        ConfigLoader.load(plugin);

        assertEquals("manual-import: false", Files.readString(dataFolder.resolve("config.yml")));
        assertEquals("manual-import: false", Files.readString(legacyFolder.resolve("config.yml")));
        try (var files = Files.list(dataFolder)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
        verify(plugin).reloadConfig();
    }

    @Test
    void failedLegacyCopyPreservesSourceTargetAndCleansTemporaryFile() throws Exception {
        Path folder = tempDataFolder();
        Path source = folder.resolve("legacy.yml");
        Path target = folder.resolve("config.yml");
        Files.writeString(source, "legacy-source-config");

        IOException error;
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.copy(Mockito.eq(source), Mockito.any(Path.class),
                    Mockito.eq(StandardCopyOption.REPLACE_EXISTING)))
                .thenThrow(new IOException("injected copy failure"));

            error = assertThrows(IOException.class, () -> invokeCopyAtomically(source, target));
        }

        assertEquals("injected copy failure", error.getMessage());
        assertEquals("legacy-source-config", Files.readString(source));
        assertFalse(Files.exists(target));
        try (var files = Files.list(folder)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void legacyCopyFallsBackFromAtomicMoveWithoutChangingTheSource() throws Exception {
        Path folder = tempDataFolder();
        Path source = folder.resolve("legacy.yml");
        Path target = folder.resolve("config.yml");
        Files.writeString(source, "legacy-content");

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(Mockito.any(Path.class), Mockito.any(Path.class),
                    Mockito.eq(StandardCopyOption.ATOMIC_MOVE)))
                .thenThrow(new AtomicMoveNotSupportedException(source.toString(), target.toString(), "unsupported"));

            invokeCopyAtomically(source, target);

            files.verify(() -> Files.move(Mockito.any(Path.class), Mockito.eq(target),
                Mockito.eq(StandardCopyOption.ATOMIC_MOVE)));
            files.verify(() -> Files.move(Mockito.any(Path.class), Mockito.eq(target)));
        }
        assertEquals("legacy-content", Files.readString(source));
        assertEquals("legacy-content", Files.readString(target));
        try (var files = Files.list(folder)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static ConfigurationSection section(Set<String> keys, Map<String, String> values) {
        ConfigurationSection section = mock(ConfigurationSection.class);
        when(section.getKeys(false)).thenReturn(keys);
        for (String key : keys) {
            when(section.getString(key)).thenReturn(values.get(key));
        }
        return section;
    }

    private static Path tempDataFolder() {
        try {
            return Files.createTempDirectory("storagesign-config-test");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void invokeCopyAtomically(Path source, Path target) throws Exception {
        Method method = ConfigLoader.class.getDeclaredMethod("copyAtomically", Path.class, Path.class);
        method.setAccessible(true);
        try {
            method.invoke(null, source, target);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IOException ioException) throw ioException;
            throw new AssertionError(exception.getCause());
        }
    }
}
