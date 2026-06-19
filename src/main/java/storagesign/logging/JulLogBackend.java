package storagesign.logging;

import java.util.logging.Level;

/** Bukkit/JDK 標準ロガーを使用するバックエンド。 */
final class JulLogBackend implements PluginLogger.LogBackend {

    private final java.util.logging.Logger logger;
    private final PluginLogger.LevelSelection levelSelection;

    JulLogBackend(java.util.logging.Logger logger, PluginLogger.LevelSelection levelSelection) {
        this.logger = logger;
        this.levelSelection = levelSelection;
        this.logger.setLevel(levelSelection.julLevel());
    }

    @Override
    public boolean isLoggable(Level level) {
        return levelSelection.allows(level) && logger.isLoggable(level);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        logger.log(level, message, throwable);
    }
}
