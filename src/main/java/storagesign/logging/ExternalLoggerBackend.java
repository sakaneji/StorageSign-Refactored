package storagesign.logging;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** teruteru128/logger との連携を隔離する任意バックエンド。 */
final class ExternalLoggerBackend implements PluginLogger.LogBackend {

    private final JavaPlugin plugin;
    private final com.github.teruteru128.logger.Logger logger;
    private final PluginLogger.LevelSelection levelSelection;
    private boolean registered;

    ExternalLoggerBackend(JavaPlugin plugin, PluginLogger.LevelSelection levelSelection) {
        this.plugin = plugin;
        this.levelSelection = levelSelection;
        com.github.teruteru128.logger.Logger.register(plugin, levelSelection.externalName());
        this.registered = true;
        this.logger = com.github.teruteru128.logger.Logger.getInstance(plugin);
        if (logger == null) {
            close();
            throw new IllegalStateException("外部 Logger から登録済みインスタンスを取得できませんでした");
        }
    }

    @Override
    public boolean isLoggable(Level level) {
        return levelSelection.allows(level);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        String rendered = PluginLogger.appendThrowable(message, throwable);
        if (level.intValue() >= Level.SEVERE.intValue()) {
            logger.error(rendered);
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            logger.warn(rendered);
        } else if (level.intValue() >= Level.INFO.intValue()) {
            logger.info(rendered);
        } else if (level.intValue() >= Level.FINE.intValue()) {
            logger.debug(rendered);
        } else {
            logger.trace(rendered);
        }
    }

    @Override
    public void close() {
        if (registered) {
            com.github.teruteru128.logger.Logger.unregister(plugin);
            registered = false;
        }
    }
}
