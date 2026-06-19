package storagesign.listener;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import storagesign.logging.PluginLogger;

/**
 * サーバーが Paper かどうかを検出し、適切な看板編集リスナーを登録する。
 *
 * <p>Paper は {@code io.papermc.paper.event.player.PlayerOpenSignEvent} を提供し、
 * GUI 表示前に発火する。Spigot は標準の {@code PlayerSignOpenEvent} を使用する。
 */
public final class SignEditListenerFactory {

    private static final PluginLogger LOG = PluginLogger.getLogger(SignEditListenerFactory.class);

    /** {@link PaperSignEditListener} または {@link SpigotSignEditListener} のどちらかを登録する。 */
    public static void register(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        Listener listener;
        try {
            Class.forName("io.papermc.paper.event.player.PlayerOpenSignEvent");
            listener = new PaperSignEditListener();
            LOG.info("register", "Paper 用看板編集リスナーを登録しました");
        } catch (ClassNotFoundException e) {
            listener = new SpigotSignEditListener();
            LOG.info("register", "Spigot 用看板編集リスナーを登録しました");
        }
        pm.registerEvents(listener, plugin);
    }

    private SignEditListenerFactory() {}
}
