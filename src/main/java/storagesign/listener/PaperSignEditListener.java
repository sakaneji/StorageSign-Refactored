package storagesign.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import storagesign.StorageSign;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;

/**
 * 既存の StorageSign の看板編集 GUI の起動を防ぐ。
 *
 * <p><b>Paper</b> 専用（{@code io.papermc.paper.event.player.PlayerOpenSignEvent} を提供するサーバー）。
 * Spigot の {@code PlayerSignOpenEvent} より早いタイミングで発火する。
 *
 * @see SpigotSignEditListener
 * @see SignEditListenerFactory
 */
public final class PaperSignEditListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(PaperSignEditListener.class);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignOpen(io.papermc.paper.event.player.PlayerOpenSignEvent event) {
        Block block = event.getSign().getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;
        if (StorageSign.isStorageSign(block)) {
            event.setCancelled(true);
            LOG.trace("onSignOpen", () -> "cancelled sign edit=" + block.getLocation());
        }
    }
}
