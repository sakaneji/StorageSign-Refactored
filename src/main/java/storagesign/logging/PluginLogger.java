package storagesign.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * StorageSign 全体で使用するロギングファサード。
 *
 * <p>サーバーに外部の Logger プラグインが導入されている場合はそちらを使い、
 * 利用できない場合は Bukkit/JDK の標準ロガーへフォールバックする。
 */
public final class PluginLogger {

    private static volatile LogBackend activeBackend;

    private final java.util.logging.Logger bootstrapLogger;
    private final String sourceName;

    private PluginLogger(Class<?> source) {
        this.bootstrapLogger = java.util.logging.Logger.getLogger(source.getName());
        this.sourceName = source.getSimpleName();
    }

    public static PluginLogger getLogger(Class<?> source) {
        return new PluginLogger(Objects.requireNonNull(source, "source"));
    }

    /** config 読み込み後に呼び出し、使用するバックエンドを決定する。 */
    public static synchronized void initialize(JavaPlugin plugin, String configuredLevel) {
        Objects.requireNonNull(plugin, "plugin");
        shutdown();

        LevelSelection selection = LevelSelection.parse(configuredLevel);
        LogBackend selected = null;

        try {
            Plugin loggerPlugin = plugin.getServer().getPluginManager().getPlugin("Logger");
            if (loggerPlugin != null && loggerPlugin.isEnabled()) {
                selected = new ExternalLoggerBackend(plugin, selection);
            }
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(
                Level.WARNING,
                "[PluginLogger#initialize] 外部 Logger の初期化に失敗したため標準ロガーを使用します",
                e
            );
        }

        if (selected == null) {
            selected = new JulLogBackend(plugin.getLogger(), selection);
        }
        activeBackend = selected;

        if (!selection.valid()) {
            getLogger(PluginLogger.class).warning("initialize",
                "config の log-level が不正です: " + configuredLevel + " — INFO を使用します"
            );
        }
    }

    /** 外部 Logger の登録を解除し、初期化前の状態へ戻す。 */
    public static synchronized void shutdown() {
        LogBackend backend = activeBackend;
        activeBackend = null;
        if (backend != null) {
            backend.close();
        }
    }

    public void severe(String message) {
        log(Level.SEVERE, null, message);
    }

    public void warning(String message) {
        log(Level.WARNING, null, message);
    }

    public void warning(String operation, String message) {
        log(Level.WARNING, operation, message);
    }

    public void info(String message) {
        log(Level.INFO, null, message);
    }

    public void info(String operation, String message) {
        log(Level.INFO, operation, message);
    }

    public void fine(String message) {
        log(Level.FINE, null, message);
    }

    public void fine(Supplier<String> messageSupplier) {
        log(Level.FINE, messageSupplier);
    }

    public void finest(String message) {
        log(Level.FINEST, null, message);
    }

    public boolean isDebugEnabled() {
        return isLoggable(Level.FINE);
    }

    public boolean isTraceEnabled() {
        return isLoggable(Level.FINEST);
    }

    public void debug(String operation, Supplier<String> messageSupplier) {
        log(Level.FINE, operation, messageSupplier);
    }

    public void trace(String operation, Supplier<String> messageSupplier) {
        log(Level.FINEST, operation, messageSupplier);
    }

    public void log(Level level, String message) {
        log(level, null, message);
    }

    public void log(Level level, String operation, String message) {
        logInternal(level, formatMessage(operation, message), null);
    }

    public void log(Level level, String pattern, Object parameter) {
        log(level, null, pattern, parameter);
    }

    public void log(Level level, String operation, String pattern, Object parameter) {
        if (!isLoggable(level)) return;
        logInternal(level, formatMessage(operation, MessageFormat.format(pattern, parameter)), null);
    }

    public void log(Level level, String pattern, Object[] parameters) {
        log(level, null, pattern, parameters);
    }

    public void log(Level level, String operation, String pattern, Object[] parameters) {
        if (!isLoggable(level)) return;
        logInternal(level, formatMessage(operation, MessageFormat.format(pattern, parameters)), null);
    }

    public void log(Level level, String message, Throwable throwable) {
        log(level, null, message, throwable);
    }

    public void log(Level level, String operation, String message, Throwable throwable) {
        if (!isLoggable(level)) return;
        logInternal(level, formatMessage(operation, message), throwable);
    }

    private void log(Level level, Supplier<String> messageSupplier) {
        log(level, null, messageSupplier);
    }

    private void log(Level level, String operation, Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, "messageSupplier");
        if (isLoggable(level)) {
            logInternal(level, formatMessage(operation, messageSupplier.get()), null);
        }
    }

    private String formatMessage(String operation, String message) {
        String source = operation == null || operation.isBlank()
            ? sourceName
            : sourceName + "#" + operation;
        return "[" + source + "] " + message;
    }

    private boolean isLoggable(Level level) {
        LogBackend backend = activeBackend;
        return backend == null ? bootstrapLogger.isLoggable(level) : backend.isLoggable(level);
    }

    private void logInternal(Level level, String message, Throwable throwable) {
        Objects.requireNonNull(level, "level");
        LogBackend backend = activeBackend;
        if (backend == null) {
            bootstrapLogger.log(level, message, throwable);
        } else if (backend.isLoggable(level)) {
            backend.log(level, message, throwable);
        }
    }

    static String appendThrowable(String message, Throwable throwable) {
        if (throwable == null) return message;
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        return message + System.lineSeparator() + stackTrace;
    }

    interface LogBackend {
        boolean isLoggable(Level level);
        void log(Level level, String message, Throwable throwable);
        default void close() {}
    }

    record LevelSelection(String externalName, Level julLevel, int threshold, boolean valid) {
        private static final int OFF = 0;
        private static final int ERROR = 200;
        private static final int WARN = 300;
        private static final int INFO = 400;
        private static final int DEBUG = 500;
        private static final int TRACE = 600;
        private static final int ALL = Integer.MAX_VALUE;

        static LevelSelection parse(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "OFF" -> new LevelSelection("OFF", Level.OFF, OFF, true);
                case "SEVERE", "ERROR" -> new LevelSelection("ERROR", Level.SEVERE, ERROR, true);
                case "WARNING", "WARN" -> new LevelSelection("WARN", Level.WARNING, WARN, true);
                case "INFO", "CONFIG" -> new LevelSelection("INFO", Level.INFO, INFO, true);
                case "FINE", "DEBUG" -> new LevelSelection("DEBUG", Level.FINE, DEBUG, true);
                case "FINER", "FINEST", "TRACE" ->
                    new LevelSelection("TRACE", Level.FINEST, TRACE, true);
                case "ALL" -> new LevelSelection("ALL", Level.ALL, ALL, true);
                default -> new LevelSelection("INFO", Level.INFO, INFO, false);
            };
        }

        boolean allows(Level eventLevel) {
            if (threshold == OFF) return false;
            return eventThreshold(eventLevel) <= threshold;
        }

        private static int eventThreshold(Level level) {
            if (level.intValue() >= Level.SEVERE.intValue()) return ERROR;
            if (level.intValue() >= Level.WARNING.intValue()) return WARN;
            if (level.intValue() >= Level.INFO.intValue()) return INFO;
            if (level.intValue() >= Level.FINE.intValue()) return DEBUG;
            return TRACE;
        }
    }
}
