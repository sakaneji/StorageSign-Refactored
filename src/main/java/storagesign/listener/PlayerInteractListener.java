package storagesign.listener;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.logging.PluginLogger;
import storagesign.registry.DyeRegistry;
import storagesign.registry.MaterialRegistry;

/**
 * 元の StorageSign の振る舞いに準拠したプレイヤー操作ハンドラー。
 *
 * <p>基本挙動:
 * <ul>
 *   <li>SS への操作は右クリック駆動（手動インポート/エクスポート/登録/マージ/分割）。</li>
 *   <li>オフハンドの操作はスニーク中の誤設置防止以外は無視する。</li>
 *   <li>染料/インクの操作はバニラの看板の動作に委譲する。</li>
 * </ul>
 */
public final class PlayerInteractListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(PlayerInteractListener.class);

    private static final Material INK_SAC = Material.INK_SAC;
    private static final Material GLOW_INK_SAC = Material.GLOW_INK_SAC;

    public PlayerInteractListener(StorageSignPlugin plugin) {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // スペクテーターは StorageSign を操作できない
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        if (ConfigLoader.getBannerDebug()
            && LOG.isTraceEnabled()
            && event.getHand() == EquipmentSlot.HAND
            && (event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            ItemStack debugItem = event.getItem();
            if (debugItem != null && debugItem.getType() != Material.AIR && debugItem.hasItemMeta()) {
                LOG.trace("bannerDebug", () -> "item=" + debugItem.getType()
                          + ", meta=" + debugItem.getItemMeta().getAsString());
            }
        }

        Block block = event.getClickedBlock();
        if (block == null && event.getAction() == Action.RIGHT_CLICK_AIR
            && event.useInteractedBlock() == Result.DENY) {
            block = player.getTargetBlockExact(3);
        }
        if (block == null || !MaterialRegistry.isAnySign(block.getType())) return;

        StorageSign ss = StorageSign.fromBlock(block);
        if (ss == null) return;

        // オフハンド: スニーク中の誤設置を防いでから無視する。
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack offHand = event.getItem();
            if (player.isSneaking() && StorageSign.isStorageSign(offHand)) {
                event.setUseItemInHand(Result.DENY);
                event.setUseInteractedBlock(Result.DENY);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        event.setUseItemInHand(Result.DENY);
        event.setUseInteractedBlock(Result.DENY);

        if (!player.hasPermission("storagesign.use")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            event.setCancelled(true);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) hand = new ItemStack(Material.AIR);
        Material handMat = hand.getType();

        // 空の SS: 持っているアイテムを登録する。
        if (ss.isUnregistered()) {
            registerItem(player, block, hand);
            return;
        }

        // 手持ちが StorageSign アイテム（マージ/分割/看板アイテム保管フロー）。
        StorageSign handSS = StorageSign.fromItemStack(hand);
        if (handSS != null) {
            processStorageSignItemInteraction(player, block, ss, hand, handSS);
            return;
        }

        // 手動インポート（手に合致アイテムを持っている場合）。
        if (ss.isSimilar(hand)) {
            importItems(player, block, ss, hand);
            return;
        }

        // 手動エクスポートのフォールバック。
        if (!ConfigLoader.getManualExport()) return;

        // 染料/インクはバニラの動作に委譲する。
        if (DyeRegistry.isDye(handMat) || isSac(handMat)) {
            event.setUseItemInHand(Result.ALLOW);
            event.setUseInteractedBlock(Result.ALLOW);
            return;
        }

        exportItems(player, block, ss);
    }

    private void registerItem(Player player, Block block, ItemStack hand) {
        StorageSignInteractionFlow.registerItem(LOG, player, block, hand);
    }

    private void processStorageSignItemInteraction(Player player, Block block, StorageSign blockSS,
                                                   ItemStack handItem, StorageSign handSS) {
        StorageSignInteractionFlow.processStorageSignItemInteraction(LOG, player, block, blockSS, handItem, handSS);
    }

    private void importItems(Player player, Block block, StorageSign ss, ItemStack hand) {
        StorageSignInteractionFlow.importItems(LOG, player, block, ss, hand);
    }

    private void exportItems(Player player, Block block, StorageSign ss) {
        StorageSignInteractionFlow.exportItems(LOG, player, block, ss);
    }

    private static boolean isSac(Material mat) {
        return StorageSignInteractionFlow.isSac(mat);
    }

    private static boolean isGlowSac(Material mat) {
        return mat == GLOW_INK_SAC;
    }
}
