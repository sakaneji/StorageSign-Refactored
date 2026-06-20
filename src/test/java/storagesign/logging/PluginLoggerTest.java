package storagesign.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PluginLoggerTest {

    @AfterEach
    void resetBackend() {
        PluginLogger.shutdown();
    }

    @Test
    void mapsLegacyJulLevelsToExternalLevels() {
        assertSelection("SEVERE", "ERROR", Level.SEVERE);
        assertSelection("WARNING", "WARN", Level.WARNING);
        assertSelection("CONFIG", "INFO", Level.INFO);
        assertSelection("FINE", "DEBUG", Level.FINE);
        assertSelection("FINER", "TRACE", Level.FINEST);
        assertSelection("FINEST", "TRACE", Level.FINEST);
    }

    @Test
    void acceptsExternalLoggerLevelNames() {
        assertSelection("ERROR", "ERROR", Level.SEVERE);
        assertSelection("WARN", "WARN", Level.WARNING);
        assertSelection("INFO", "INFO", Level.INFO);
        assertSelection("DEBUG", "DEBUG", Level.FINE);
        assertSelection("TRACE", "TRACE", Level.FINEST);
        assertSelection("OFF", "OFF", Level.OFF);
        assertSelection("ALL", "ALL", Level.ALL);
    }

    @Test
    void fatalIsRejectedBecauseStorageSignHasNoFatalEvents() {
        assertFalse(PluginLogger.LevelSelection.parse("FATAL").valid());
    }

    @Test
    void invalidLevelFallsBackToInfo() {
        PluginLogger.LevelSelection selection = PluginLogger.LevelSelection.parse("invalid");

        assertFalse(selection.valid());
        assertEquals("INFO", selection.externalName());
        assertEquals(Level.INFO, selection.julLevel());
    }

    @Test
    void thresholdFiltersLowerPriorityMessages() {
        PluginLogger.LevelSelection info = PluginLogger.LevelSelection.parse("INFO");
        assertTrue(info.allows(Level.SEVERE));
        assertTrue(info.allows(Level.WARNING));
        assertTrue(info.allows(Level.INFO));
        assertFalse(info.allows(Level.FINE));
        assertFalse(info.allows(Level.FINEST));

        PluginLogger.LevelSelection off = PluginLogger.LevelSelection.parse("OFF");
        assertFalse(off.allows(Level.SEVERE));

        PluginLogger.LevelSelection all = PluginLogger.LevelSelection.parse("ALL");
        assertTrue(all.allows(Level.FINEST));
    }

    @Test
    void appendsThrowableStackTrace() {
        String rendered = PluginLogger.appendThrowable("failed", new IllegalStateException("boom"));

        assertTrue(rendered.startsWith("failed" + System.lineSeparator()));
        assertTrue(rendered.contains("IllegalStateException: boom"));
    }

    @Test
    void initializesJulFallbackWhenExternalPluginIsMissing() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.fallback"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).warning("fallback", "fallback-message");

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest#fallback] fallback-message",
                     handler.records.getFirst().getMessage());
    }

    @Test
    void initializesJulFallbackWhenExternalLoggerInitializationFails() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.failed-external"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger"))
            .thenThrow(new IllegalStateException("broken Logger plugin"));
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).info("fallback", "still-running");

        assertTrue(handler.records.stream().anyMatch(record ->
            record.getMessage().contains("外部 Logger の初期化に失敗")));
        assertTrue(handler.records.stream().anyMatch(record ->
            record.getMessage().equals("[PluginLoggerTest#fallback] still-running")));
    }

    @Test
    void disabledDebugDoesNotEvaluateMessageSupplier() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.lazy"
        );
        AtomicBoolean evaluated = new AtomicBoolean(false);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).debug("lazy", () -> {
            evaluated.set(true);
            return "should-not-be-built";
        });

        assertFalse(evaluated.get());
    }

    @Test
    void externalBackendUsesSameSourceFormat() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Plugin loggerPlugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.external-format"
        );
        CapturingHandler handler = new CapturingHandler();
        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(loggerPlugin);
        when(loggerPlugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(julLogger);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).info("external", "external-message");

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest#external] external-message",
                     handler.records.getFirst().getMessage());
    }

    @Test
    void externalBackendRegistersAndUnregistersPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.external"
        );
        when(plugin.getLogger()).thenReturn(julLogger);

        ExternalLoggerBackend backend = new ExternalLoggerBackend(
            plugin,
            PluginLogger.LevelSelection.parse("DEBUG")
        );

        assertNotNull(com.github.teruteru128.logger.Logger.getInstance(plugin));
        backend.close();
        assertNull(com.github.teruteru128.logger.Logger.getInstance(plugin));
    }

    private static void assertSelection(String input, String externalName, Level julLevel) {
        PluginLogger.LevelSelection selection = PluginLogger.LevelSelection.parse(input);
        assertTrue(selection.valid());
        assertEquals(externalName, selection.externalName());
        assertEquals(julLevel, selection.julLevel());
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
