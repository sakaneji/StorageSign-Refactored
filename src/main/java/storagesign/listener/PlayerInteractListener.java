package storagesign.listener;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
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
import storagesign.AmountTransfer;
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
        if (hand == null || hand.getType() == Material.AIR) return;

        StorageSign newSS = StorageSign.fromStoredItem(hand);
        if (newSS == null) return;

        // 元の動作: 登録はアイテム種別の設定のみ行い、手持ちアイテムは消費しない。
        applyToBlock(block, newSS);
        LOG.debug("registerItem", () -> "registered=" + newSS.getIdentifier()
                  + ", sign=" + block.getLocation());
    }

    private void processStorageSignItemInteraction(Player player, Block block, StorageSign blockSS,
                                                   ItemStack handItem, StorageSign handSS) {
        // SS アイテム整スタックを看板にマージする。
        if (!handSS.isUnregistered() && ConfigLoader.getManualImport()) {
            ItemStack handContents = handSS.getContents(1);
            if (handContents != null && blockSS.isSimilar(handContents)) {
                int perItem = handSS.getAmount();
                int mergeableItems = perItem <= 0 ? 0 : Math.min(
                    Math.max(1, handItem.getAmount()),
                    (Integer.MAX_VALUE - blockSS.getAmount()) / perItem
                );
                if (mergeableItems > 0) {
                    long add = (long) perItem * mergeableItems;
                    blockSS.setAmount((int) (blockSS.getAmount() + add));
                    applyMergedStorageSigns(player, handItem, handSS, mergeableItems);
                    applyToBlock(block, blockSS);
                    LOG.debug("processStorageSignItemInteraction", () -> "merged=" + add
                              + ", sign=" + block.getLocation()
                              + ", total=" + blockSS.getAmount());
                }
                return;
            }
        }

        // 空の看板を "sign-in-sign" StorageSign に保管する。
        if (handSS.isUnregistered() && ConfigLoader.getManualImport()
            && blockSS.isSignAsItem() && blockSS.getMaterial() == handItem.getType()) {
            int added = 0;
            if (player.isSneaking()) {
                added = AmountTransfer.accepted(blockSS.getAmount(), handItem.getAmount());
                if (added <= 0) return;
                consumeSlot(player, player.getInventory().getHeldItemSlot(), handItem, added);
            } else {
                // getContents() はバッキング配列を一度の呼び出しで返す。N 回の getItem() API 呼び出しを回避できる。
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item == null || item.getType() != handItem.getType()) continue;
                    StorageSign itemSS = StorageSign.fromItemStack(item);
                    if (itemSS == null || !itemSS.isUnregistered()) continue;
                    int accepted = AmountTransfer.accepted(blockSS.getAmount() + added, item.getAmount());
                    if (accepted <= 0) break;
                    added += accepted;
                    consumeSlot(player, i, item, accepted);
                }
            }
            if (added > 0) {
                blockSS.setAmount(blockSS.getAmount() + added);
                applyToBlock(block, blockSS);
                int loggedAdded = added;
                LOG.debug("processStorageSignItemInteraction", () -> "stored-signs=" + loggedAdded
                          + ", sign=" + block.getLocation()
                          + ", total=" + blockSS.getAmount());
            }
            return;
        }

        // 手持ちの空 SS スタックにブロック SS を分割する。
        if (handSS.isUnregistered() && ConfigLoader.getManualExport()
            && handItem.getType() == MaterialRegistry.toItemSignMaterial(block.getType())
            && blockSS.getAmount() > handItem.getAmount()) {
            ItemStack template = blockSS.getContents(1);
            if (template == null) return;

            StorageSign divided = StorageSign.fromStoredItem(template);
            if (divided == null) return;

            int signsInHand = Math.max(1, handItem.getAmount());
            int limit = player.isSneaking() ? ConfigLoader.getSneakDivideLimit() : ConfigLoader.getDivideLimit();
            int perSign = AmountTransfer.dividedPerSign(blockSS.getAmount(), signsInHand, limit);
            if (perSign <= 0) return;

            divided.setAmount(perSign);
            player.getInventory().setItemInMainHand(
                StorageSign.createStorageSignItem(handItem.getType(), divided, signsInHand)
            );
            blockSS.setAmount(blockSS.getAmount() - (perSign * signsInHand));
            applyToBlock(block, blockSS);
            LOG.debug("processStorageSignItemInteraction", () -> "divided-per-sign=" + perSign
                      + ", signs=" + signsInHand
                      + ", sign=" + block.getLocation()
                      + ", remaining=" + blockSS.getAmount());
        }
    }

    private void importItems(Player player, Block block, StorageSign ss, ItemStack hand) {
        if (!ConfigLoader.getManualImport()) return;

        int before = ss.getAmount();
        if (player.isSneaking()) {
            int add = AmountTransfer.accepted(ss.getAmount(), hand.getAmount());
            if (add <= 0) return;
            ss.setAmount(ss.getAmount() + add);
            consumeSlot(player, player.getInventory().getHeldItemSlot(), hand, add);

            // スニークインポート: 手持ちアイテムのみが対象。染料/インクも併せて看板固有の制限を適用する。
            if (block.getState() instanceof Sign sign) {
                if (DyeRegistry.isDye(hand.getType())) {
                    DyeColor color = DyeRegistry.getColor(hand.getType());
                    if (color != null) sign.getSide(Side.FRONT).setColor(color);
                } else if (isSac(hand.getType())) {
                    sign.getSide(Side.FRONT).setGlowingText(isGlowSac(hand.getType()));
                }
                ss.applyToSign(sign);  // 看板の行テキスト + sign.update() を 1 回で実行
            }
        } else {
            // getContents() はバッキング配列を一度の呼び出しで返す。
            // その後のスロットアクセスは純粋な Java 配列インデックス操作—
            // Bukkit API の getItem(i) を N 回呼ぶコストを回避できる。
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (!ss.isSimilar(item)) continue;
                int add = AmountTransfer.accepted(ss.getAmount(), item.getAmount());
                if (add <= 0) break;
                ss.setAmount(ss.getAmount() + add);
                consumeSlot(player, i, item, add);
            }
            applyToBlock(block, ss);
        }
        player.updateInventory();
        int imported = ss.getAmount() - before;
        if (imported > 0) {
            LOG.debug("importItems", () -> "imported=" + imported
                      + ", material=" + ss.getMaterial()
                      + ", sign=" + block.getLocation()
                      + ", total=" + ss.getAmount());
        }
    }

    private void exportItems(Player player, Block block, StorageSign ss) {
        if (ss.isUnregistered() || ss.getAmount() <= 0) return;

        ItemStack out = ss.getContents(1);
        if (out == null) return;

        int max = out.getMaxStackSize();
        if (player.isSneaking()) {
            out.setAmount(1);
            ss.setAmount(ss.getAmount() - 1);
        } else if (ss.getAmount() > max) {
            out.setAmount(max);
            ss.setAmount(ss.getAmount() - max);
        } else {
            out.setAmount(ss.getAmount());
            ss.setAmount(0);
        }

        Location dropLoc = player.getLocation().clone().add(0, 0.5, 0);
        player.getWorld().dropItem(dropLoc, out);
        applyToBlock(block, ss);
        LOG.debug("exportItems", () -> "exported=" + out.getAmount()
                  + ", material=" + out.getType()
                  + ", sign=" + block.getLocation()
                  + ", remaining=" + ss.getAmount());
    }

    private static boolean isSac(Material mat) {
        return mat == INK_SAC || mat == GLOW_INK_SAC;  // インクサックか光るインクサックであれば次
    }

    private static ItemStack remainingMergedStack(ItemStack original, StorageSign contents, int mergedItems) {
        ItemStack remaining = original.clone();
        int remainingAmount = original.getAmount() - mergedItems;
        remaining.setAmount(remainingAmount);
        return StorageSign.createStorageSignItem(remaining.getType(), contents, remainingAmount);
    }

    private static void applyMergedStorageSigns(Player player, ItemStack original,
                                                StorageSign contents, int mergedItems) {
        if (mergedItems == original.getAmount()) {
            player.getInventory().setItemInMainHand(StorageSign.createStorageSignItem(
                original.getType(), StorageSign.EMPTY_MARKER, mergedItems));
            return;
        }

        player.getInventory().setItemInMainHand(
            remainingMergedStack(original, contents, mergedItems));
        ItemStack emptied = StorageSign.createStorageSignItem(
            original.getType(), StorageSign.EMPTY_MARKER, mergedItems);
        for (ItemStack leftover : player.getInventory().addItem(emptied).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static void consumeSlot(Player player, int slot, ItemStack item, int consumed) {
        int remaining = item.getAmount() - consumed;
        if (remaining <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack rest = item.clone();
            rest.setAmount(remaining);
            player.getInventory().setItem(slot, rest);
        }
    }

    private static boolean isGlowSac(Material mat) {
        return mat == GLOW_INK_SAC;  // 光るインクサックのみ true
    }

    private static void applyToBlock(Block block, StorageSign ss) {
        if (block.getState() instanceof Sign sign) {
            ss.applyToSign(sign);
        }
    }
}
