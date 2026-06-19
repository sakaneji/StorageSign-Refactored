package storagesign.listener;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.sign.Side;

import storagesign.StorageSign;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;

/**
 * 既存の StorageSign の看板編集 GUI の起動を防ぐ。
 *
 * <p><b>Spigot</b> 専用。Paper では代わりに {@link PaperSignEditListener} が登録される。
 *
 * @see PaperSignEditListener
 * @see SignEditListenerFactory
 */
public final class SpigotSignEditListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(SpigotSignEditListener.class);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignOpen(org.bukkit.event.player.PlayerSignOpenEvent event) {
        Block block = event.getSign().getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;
        if (StorageSign.isStorageSign(block)) {
            event.setCancelled(true);
            LOG.trace("onSignOpen", () -> "cancelled sign edit=" + block.getLocation());
        }
    }
}
