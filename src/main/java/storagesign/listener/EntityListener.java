package storagesign.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;

import storagesign.ConfigLoader;
import storagesign.AmountTransfer;
import storagesign.StorageSign;
import storagesign.logging.PluginLogger;

/**
 * StorageSign に関連するエンティティイベントを処理する:
 * <ul>
 *   <li>プレイヤーが落としアイテムを拾う → 手持ち StorageSign アイテムに自動収納</li>
 *   <li>エンティティによるブロック変化（砂や砖の落下等） → SS アイテムをドロップ</li>
 * </ul>
 */
public final class EntityListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(EntityListener.class);

    // ── EntityPickupItemEvent ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && ConfigLoader.getAutocollect()) {
            Player player = (Player) event.getEntity();
            if (!player.hasPermission("storagesign.autocollect")) return;

            PlayerInventory inv = player.getInventory();
            ItemStack picked = event.getItem().getItemStack();

            CollectionResult main = autoCollectToHand(inv.getItemInMainHand(), picked, inv, event);
            if (main != null) {
                inv.setItemInMainHand(
                    updatedStorageSign(inv.getItemInMainHand(), main.storageSign(), main.accepted())
                );
                player.updateInventory();
                return;
            }
            CollectionResult off = autoCollectToHand(inv.getItemInOffHand(), picked, inv, event);
            if (off != null) {
                inv.setItemInOffHand(
                    updatedStorageSign(inv.getItemInOffHand(), off.storageSign(), off.accepted())
                );
                player.updateInventory();
                return;
            }
        }

        // プレイヤー以外のエンティティが StorageSign アイテムを拾うのを防ぐ。
        if (event.getEntityType() != EntityType.PLAYER) {
            ItemStack stack = event.getItem().getItemStack();
            if (StorageSign.isStorageSign(stack)) {
                event.getItem().setPickupDelay(20);
                event.setCancelled(true);
            }
        }
    }

    /**
     * 指定の手アイテムが {@code picked} を吸収できる登録済み StorageSign かどうかを確認する。
     *
     * @return 値を再利用して 2 回目の {@code fromItemStack()} 呼び出しを回避するため、
     *         成功時はパース済み {@link StorageSign} を返す。条件を満たさない場合は {@code null}。
     */
    private static CollectionResult autoCollectToHand(ItemStack handSSItem, ItemStack picked, PlayerInventory inv,
                                                      EntityPickupItemEvent event) {
        StorageSign ss = StorageSign.fromItemStack(handSSItem);
        if (ss == null || ss.isUnregistered()) return null;
        if (handSSItem.getAmount() != 1) return null;
        if (!ss.isSimilar(picked)) return null;
        if (!inv.containsAtLeast(picked, picked.getMaxStackSize())) return null;

        int accepted = AmountTransfer.accepted(ss.getAmount(), picked.getAmount());
        if (accepted <= 0) return null;
        event.setCancelled(true);
        if (accepted == picked.getAmount()) {
            event.getItem().remove();
        } else {
            ItemStack remaining = picked.clone();
            remaining.setAmount(picked.getAmount() - accepted);
            event.getItem().setItemStack(remaining);
        }
        return new CollectionResult(ss, accepted);
    }

    /**
     * 保管数量を加算した新しい StorageSign アイテムを返す。
     *
     * @param handSSItem 現在手持ちの SS アイテム
     * @param ss         パース済み StorageSign（{@link #autoCollectToHand} から再利用）
     * @param addAmount  拾ったアイテム数
     */
    private static ItemStack updatedStorageSign(ItemStack handSSItem, StorageSign ss, int addAmount) {
        ss.setAmount(ss.getAmount() + addAmount);
        return StorageSign.createStorageSignItem(handSSItem.getType(), ss, handSSItem.getAmount());
    }

    private record CollectionResult(StorageSign storageSign, int accepted) {}

    // ── EntityChangeBlockEvent ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!ConfigLoader.getFallingBlockItemSS()) return;
        if (!(event.getEntity() instanceof FallingBlock)) return;

        Block block = event.getBlock();
        BlockEventListener.dropAttachedStorageSignsByAdjacency(block);
        LOG.debug("onEntityChangeBlock", () -> "dropped adjacent sign=" + block.getLocation());
    }
}
