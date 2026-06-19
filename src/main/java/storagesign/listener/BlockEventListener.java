package storagesign.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.sign.Side;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.adjacency.SsAdjacencyMatch;
import storagesign.adjacency.SsAdjacencyPurpose;
import storagesign.adjacency.SsAdjacencyQuery;
import storagesign.adjacency.SsAdjacencyResolver;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;

/**
 * StorageSign のブロックレベルイベントを処理する:
 * <ul>
 *   <li>StorageSign を坂ったときに Lore 付きアイテムをドロップ</li>
 *   <li>StorageSign アイテムを設置したときに看板ブロックのテキストを復元</li>
 *   <li>データを破壊する手動看板テキスト編集の防止</li>
 * </ul>
 */
public final class BlockEventListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(BlockEventListener.class);
    private static final SsAdjacencyResolver ADJACENCY_RESOLVER = SsAdjacencyResolver.defaultResolver();

    public BlockEventListener(StorageSignPlugin plugin) {
    }

    // ── BlockBreakEvent ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // 権限チェックは StorageSign ブロックのみ対象（元プラグインの修正）。
        // 元プラグインは全ブロック破壊に適用していたため、一般ブロックも坂れなくなる問題があった。
        if (StorageSign.isStorageSign(block)) {
            if (!player.hasPermission("storagesign.break")) {
                player.sendMessage("§c" + ConfigLoader.getNoPermission());
                event.setCancelled(true);
                return;
            }
            event.setDropItems(false);
        }

        dropRelativeSigns(block);
    }

    // ── BlockPlaceEvent ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        StorageSign ss = StorageSign.fromItemStack(item);
        if (ss == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("storagesign.place")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            event.setCancelled(true);
            return;
        }

        Block placed = event.getBlockPlaced();
        if (!(placed.getState() instanceof Sign sign)) return;

        // アイテム Lore から看板テキストを復元する
        ss.applyToSign(sign);
        if (item.getType() == Material.DARK_OAK_SIGN) {
            sign.getSide(Side.FRONT).setColor(DyeColor.WHITE);
            sign.update();
        }
        // インベントリから設置するときの UI タイミング不具合を防ぐ（元プラグインと同じ動作）
        player.closeInventory();
        LOG.debug("onBlockPlace", () -> "restored sign=" + placed.getLocation());
    }

    // ── SignChangeEvent ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;

        // この看板が既に StorageSign であれば編集をキャンセルする
        if (StorageSign.isStorageSign(block)) {
            if (block.getState() instanceof Sign sign) {
                String[] lines = sign.getSide(Side.FRONT).getLines();
                for (int i = 0; i < 4 && i < lines.length; i++) {
                    event.setLine(i, lines[i]);
                }
                sign.update();
            } else {
                event.setCancelled(true);
            }
            return;
        }

        String firstLine = event.getLine(0);
        if (firstLine == null || !firstLine.equalsIgnoreCase("storagesign")) return;

        // クリエイティブ時のみ、バニラ看板の手入力から SS 化を許可する。
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            event.setLine(0, StorageSign.HEADER_LINE);
            return;
        }

        // バニラ看板から直接 StorageSign を作成することは禁止する。
        // 先頭行が "StorageSign" 指定なら内容に関わらずキャンセルする。
        event.getPlayer().sendMessage("§c" + ConfigLoader.getNoPermission());
        event.setCancelled(true);
    }

    // ── 共通ヘルパー ──────────────────────────────────────────────────────────────

    /**
     * 対象ブロックおよび隣接する取り付け StorageSign のデータをドロップする。
     */
    public static void dropRelativeSigns(Block block) {
        // ベースブロック自身が StorageSign の場合のみ処理する。
        tryDropStorageSign(block);

        dropAttachedStorageSignsByAdjacency(block);
    }

    /**
     * 対象ブロックを支持先にしている StorageSign のみをドロップする。
     * FallingBlock によるブロック変化では、ベースブロック自身は看板になり得ないためこちらを使う。
     */
    public static void dropAttachedStorageSignsByAdjacency(Block block) {
        for (SsAdjacencyMatch match : ADJACENCY_RESOLVER.findAll(
            new SsAdjacencyQuery(block, null, SsAdjacencyPurpose.ATTACHED_SIGN_DROP)
        )) {
            dropSingleStorageSign(match.signBlock(), match.signBlock().getType(), match.storageSign());
        }
    }

    private static void tryDropStorageSign(Block block) {
        Material type = block.getType();
        if (!MaterialRegistry.SIGN_MATERIALS.contains(type)
            && !MaterialRegistry.WALL_SIGN_MATERIALS.contains(type)) {
            return;
        }

        if (!(block.getState() instanceof Sign sign)) return;

        StorageSign ss = StorageSign.fromSign(sign);
        if (ss == null) return;
        dropSingleStorageSign(block, type, ss);
    }

    private static void dropSingleStorageSign(Block signBlock, Material signType, StorageSign ss) {
        Material itemMat = MaterialRegistry.WALL_TO_SIGN.getOrDefault(signType, signType);
        // アイテム化する際は数量0なら設定に関わらず必ず登録解除する
        String loreText = (ss.getAmount() <= 0) ? StorageSign.EMPTY_MARKER : ss.getLoreText();
        ItemStack drop = StorageSign.createStorageSignItem(itemMat, loreText, 1);

        Location dropLocation = signBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        signBlock.getWorld().dropItem(dropLocation, drop);
        signBlock.setType(Material.AIR);
        LOG.debug("dropStorageSign", () -> "dropped sign=" + signBlock.getLocation());
    }

}
