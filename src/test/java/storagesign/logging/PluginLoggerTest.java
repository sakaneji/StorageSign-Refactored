package storagesign.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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
    void nullLevelFallsBackToInfo() {
        PluginLogger.LevelSelection selection = PluginLogger.LevelSelection.parse(null);

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
    void convenienceOverloadsAndThresholdChecksDelegateCorrectly() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.convenience"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "TRACE");
        PluginLogger logger = PluginLogger.getLogger(PluginLoggerTest.class);

        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isTraceEnabled());
        logger.severe("severe-message");
        logger.warning("warning-message");
        logger.warning("warn-op", "warning-with-operation");
        logger.info("info-message");
        logger.info("info-op", "info-with-operation");
        logger.fine("fine-message");
        logger.fine(() -> "fine-supplier");
        logger.finest("finest-message");
        logger.debug("debug-op", () -> "debug-supplier");
        logger.trace("trace-op", () -> "trace-supplier");
        logger.log(Level.INFO, "plain-log");
        Object single = "one";
        logger.log(Level.INFO, "pattern {0}", single);
        logger.log(Level.INFO, "pattern {0} / {1}", new Object[] {"one", "two"});
        logger.log(Level.SEVERE, "thrown-log", new IllegalStateException("boom"));

        assertTrue(handler.records.stream().anyMatch(record ->
            record.getMessage().contains("[PluginLoggerTest#warn-op] warning-with-operation")));
        assertTrue(handler.records.stream().anyMatch(record ->
            record.getMessage().contains("[PluginLoggerTest#trace-op] trace-supplier")));
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
    void initializesJulFallbackWhenExternalLoggerPluginIsDisabled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Plugin loggerPlugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.disabled-external"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(loggerPlugin);
        when(loggerPlugin.isEnabled()).thenReturn(false);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).info("plain-message");

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest] plain-message",
            handler.records.getFirst().getMessage());
    }

    @Test
    void initializesJulFallbackAndWarnsWhenConfiguredLevelIsInvalid() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.invalid-level"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "fatal");
        PluginLogger.getLogger(PluginLoggerTest.class).info("plain-message");

        assertTrue(handler.records.stream().anyMatch(record ->
            record.getMessage().contains("log-level が不正です")));
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
    void logWithoutOperationUsesOnlySourceName() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.no-operation"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).info("plain-message");

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest] plain-message",
            handler.records.getFirst().getMessage());
    }

    @Test
    void logWithBlankOperationUsesOnlySourceName() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.blank-operation"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class).info("", "blank-message");

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest] blank-message",
            handler.records.getFirst().getMessage());
    }

    @Test
    void logWithThrowableRecordsThrowableAndOperation() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.throwable"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        IllegalStateException boom = new IllegalStateException("boom");
        PluginLogger.getLogger(PluginLoggerTest.class).log(
            Level.SEVERE, "repair", "broken", boom);

        assertEquals(1, handler.records.size());
        LogRecord record = handler.records.getFirst();
        assertEquals("[PluginLoggerTest#repair] broken", record.getMessage());
        assertEquals(boom, record.getThrown());
    }

    @Test
    void logPatternObjectIsFilteredWhenBelowThreshold() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.pattern-object-filtered"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class)
            .log(Level.FINE, "repair", "broken {0}", "value");

        assertTrue(handler.records.isEmpty());
    }

    @Test
    void logPatternObjectIsRecordedWhenLoggable() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.pattern-object-recorded"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class)
            .log(Level.INFO, "repair", "broken {0}", "value");

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest#repair] broken value",
            handler.records.getFirst().getMessage());
    }

    @Test
    void logPatternArrayIsRecordedWhenLoggable() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.pattern-array-recorded"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class)
            .log(Level.INFO, "repair", "broken {0} / {1}", new Object[] {"one", "two"});

        assertEquals(1, handler.records.size());
        assertEquals("[PluginLoggerTest#repair] broken one / two",
            handler.records.getFirst().getMessage());
    }

    @Test
    void logPatternArrayIsFilteredWhenBelowThreshold() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.pattern-array-filtered"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class)
            .log(Level.FINE, "repair", "broken {0} / {1}", new Object[] {"one", "two"});

        assertTrue(handler.records.isEmpty());
    }

    @Test
    void logWithThrowableIsFilteredWhenBelowThreshold() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PluginLoggerTest.throwable-filtered"
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);

        PluginLogger.initialize(plugin, "INFO");
        PluginLogger.getLogger(PluginLoggerTest.class)
            .log(Level.FINE, "repair", "broken", new IllegalStateException("boom"));

        assertTrue(handler.records.isEmpty());
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

    @Test
    void externalBackendRoutesAllLevelsAndClosesIdempotently() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        com.github.teruteru128.logger.Logger external = mock(
            com.github.teruteru128.logger.Logger.class);

        try (MockedStatic<com.github.teruteru128.logger.Logger> mocked =
                org.mockito.Mockito.mockStatic(com.github.teruteru128.logger.Logger.class)) {
            mocked.when(() -> com.github.teruteru128.logger.Logger.getInstance(plugin))
                .thenReturn(external);

            ExternalLoggerBackend backend = new ExternalLoggerBackend(
                plugin,
                PluginLogger.LevelSelection.parse("TRACE")
            );

            backend.log(Level.SEVERE, "severe", new IllegalStateException("boom"));
            backend.log(Level.WARNING, "warn", null);
            backend.log(Level.INFO, "info", null);
            backend.log(Level.FINE, "debug", null);
            backend.log(Level.FINEST, "trace", null);
            backend.close();
            backend.close();

            verify(external).error(org.mockito.ArgumentMatchers.contains("IllegalStateException"));
            verify(external).warn("warn");
            verify(external).info("info");
            verify(external).debug("debug");
            verify(external).trace("trace");
            mocked.verify(() -> com.github.teruteru128.logger.Logger.register(
                plugin, "TRACE"), times(1));
            mocked.verify(() -> com.github.teruteru128.logger.Logger.unregister(plugin),
                times(1));
        }
    }

    @Test
    void externalBackendFailsClosedWhenNoExternalInstanceIsAvailable() {
        JavaPlugin plugin = mock(JavaPlugin.class);

        try (MockedStatic<com.github.teruteru128.logger.Logger> mocked =
                org.mockito.Mockito.mockStatic(com.github.teruteru128.logger.Logger.class)) {
            mocked.when(() -> com.github.teruteru128.logger.Logger.getInstance(plugin))
                .thenReturn(null);

            assertThrows(IllegalStateException.class, () -> new ExternalLoggerBackend(
                plugin,
                PluginLogger.LevelSelection.parse("INFO")
            ));
            mocked.verify(() -> com.github.teruteru128.logger.Logger.register(
                plugin, "INFO"), times(1));
            mocked.verify(() -> com.github.teruteru128.logger.Logger.unregister(plugin),
                times(1));
        }
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
