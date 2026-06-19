package storagesign.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import storagesign.StorageSign;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;

/**
 * StorageSign ブロックの {@link BlockPhysicsEvent} をキャンセルする。
 *
 * <p>看板ブロックを自然に落とす可能性がある BUD（Block Update Detector）動作を防ぐ。
 *
 * <p>{@code config.yml} で {@code no-bud: true} を設定した場合のみ登録される。
 */
public final class SignPhysicsListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(SignPhysicsListener.class);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;
        if (StorageSign.isStorageSign(block)) {
            event.setCancelled(true);
        }
    }
}
