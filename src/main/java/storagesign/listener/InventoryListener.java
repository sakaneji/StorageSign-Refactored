package storagesign.listener;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import storagesign.ConfigLoader;
import storagesign.StorageSignPlugin;
import storagesign.adjacency.SsAdjacencyMatch;
import storagesign.adjacency.SsAdjacencyPurpose;
import storagesign.adjacency.SsAdjacencyQuery;
import storagesign.adjacency.SsAdjacencyResolver;
import storagesign.logging.PluginLogger;
import storagesign.task.ExportSignTask;

/**
 * StorageSign を含む自動インベントリ移送を処理する。
 *
 * <h3>アーキテクチャ</h3>
 * StorageSign は看板ブロックであり Bukkit {@link Inventory} を持たない。
 * ホッパー・ドロッパー・ディスペンサー・クラフター等は
 * SS ブロックに隣接するコンテナ（チェスト・樽等）とやり取りする。
 * このリスナーは各転送の送信元・受信先コンテナの隣接ブロックをスキャンする。
 *
 * <h3>自動インポート (SS がアイテムを吸収)</h3>
 * 搬送ブロックが SS に隣接するコンテナにアイテムを投入する際、コンテナが満杯ならインベントリオーバーフローを防ぐため
 * SS に吸収する。インベントリ状態が正確なようインライン実行する。
 *
 * <h3>自動エクスポート (SS がコンテナを補充)</h3>
 * 搬送ブロックが SS 隣接コンテナからアイテムを引き出すとき、1 ティック遅延の
 * {@link ExportSignTask} が SS の保管数量からコンテナを補充する。
 *
 * <h3>インベントリピックアップ</h3>
 * 搬送インベントリが落としたアイテムエンティティを拾う場合、隣接 SS のアイテムと一致し
 * 搬送先が満杯なら余剰分を SS に吸収する。
 */
public final class InventoryListener implements Listener {

    private static final PluginLogger LOG = PluginLogger.getLogger(InventoryListener.class);
    private static final SsAdjacencyResolver ADJACENCY_RESOLVER = SsAdjacencyResolver.defaultResolver();

    private final StorageSignPlugin plugin;

    /** このティックで ExportSignTask が既にスケジュール済みの SS ブロックを追跡する。 */
    private final Set<Block> pendingExports = new HashSet<>();

    public InventoryListener(StorageSignPlugin plugin) {
        this.plugin = plugin;
    }

    // ── InventoryMoveItemEvent（搬送ブロック→コンテナ移送）─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        boolean autoImport = ConfigLoader.getAutoImport();
        boolean autoExport = ConfigLoader.getAutoExport();
        if (!autoImport && !autoExport) return;

        ItemStack item = event.getItem();
        if (item == null || item.getAmount() <= 0) return;

        int maxStack = item.getMaxStackSize();

        // ── AUTO-IMPORT: destination container is adjacent to a matching SS ───
        // 搬送元がアイテムをコンテナへ押し込む際、余剰をSSへ吸収する。
        if (autoImport) {
            Inventory destination = event.getDestination();
            // Fast-fail for the common case: nothing to absorb when the destination is not at least one stack.
            if (destination != null && destination.containsAtLeast(item, maxStack)) {
                Optional<SsAdjacencyMatch> matchOpt = resolveAdjacentStorageSignForInventory(destination, item);
                if (matchOpt.isPresent()) {
                    SsAdjacencyMatch match = matchOpt.get();
                    int absorbed = removeMatchingAmount(destination, item);
                    if (absorbed > 0) {
                        match.storageSign().setAmount(match.storageSign().getAmount() + absorbed);
                        match.storageSign().applyToSign(match.signState());
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("onItemMove", () -> "auto-import absorbed=" + absorbed
                                      + ", material=" + item.getType()
                                      + ", sign=" + match.signBlock().getLocation());
                        }
                    }
                }
            }
        }

        // ── AUTO-EXPORT: source container is adjacent to a matching SS ─────────
        // 搬送元がコンテナからアイテムを引き出す際、SSからの補充をスケジュールする。
        if (autoExport) {
            Inventory source = event.getSource();
            Optional<SsAdjacencyMatch> matchOpt = resolveAdjacentStorageSignForInventory(source, item);
            if (matchOpt.isPresent()) {
                SsAdjacencyMatch match = matchOpt.get();
                if (pendingExports.add(match.signBlock())) {
                    new ExportSignTask(match.signBlock(), source, item.clone(), pendingExports).runTask(plugin);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("onItemMove", () -> "auto-export scheduled, material=" + item.getType()
                                  + ", sign=" + match.signBlock().getLocation());
                    }
                }
            }
        }
    }

    // ── InventoryPickupItemEvent（搬送インベントリがドロップを回収）────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (!ConfigLoader.getAutoImport()) return;

        Inventory inventory = event.getInventory();
        if (inventory == null) return;

        ItemStack item = event.getItem().getItemStack();
        if (item == null || item.getAmount() <= 0) return;

        int maxStack = item.getMaxStackSize();
        // Fast-fail for the common case: nothing to absorb when the inventory is not at least one stack.
        if (!inventory.containsAtLeast(item, maxStack)) return;

        Optional<SsAdjacencyMatch> matchOpt = resolveAdjacentStorageSignForInventory(inventory, item);
        if (matchOpt.isEmpty()) return;

        SsAdjacencyMatch match = matchOpt.get();
        int absorbed = removeMatchingAmount(inventory, item);
        if (absorbed > 0) {
            match.storageSign().setAmount(match.storageSign().getAmount() + absorbed);
            match.storageSign().applyToSign(match.signState());
            if (LOG.isTraceEnabled()) {
                LOG.trace("onInventoryPickup", () -> "pickup absorbed=" + absorbed
                          + ", material=" + item.getType()
                          + ", sign=" + match.signBlock().getLocation());
            }
        }
    }

    // ── ヘルパー ──────────────────────────────────────────────────────────────────

    /**
     * 指定ブロックに隣接し、{@code item} と一致する StorageSign を 1 件解決する。
     */
    static Optional<SsAdjacencyMatch> resolveAdjacentStorageSign(Block container, ItemStack item) {
        return ADJACENCY_RESOLVER.findFirst(
            new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER)
        );
    }

    private static Optional<SsAdjacencyMatch> resolveAdjacentStorageSignForInventory(Inventory inventory, ItemStack item) {
        if (inventory == null) return Optional.empty();
        InventoryHolder holder = inventory.getHolder();
        // null = 非物理インベントリ（クラフトテーブル等）。
        // 他の非 BlockState ホルダーは instanceof チェーンを通らず empty を返す。
        if (holder == null) return Optional.empty();

        if (holder instanceof HopperMinecart hopperMinecart) {
            return resolveAdjacentStorageSign(hopperMinecart.getLocation().getBlock(), item);
        }

        if (holder instanceof StorageMinecart storageMinecart) {
            return resolveAdjacentStorageSign(storageMinecart.getLocation().getBlock(), item);
        }

        if (holder instanceof ChestBoat chestBoat) {
            return resolveAdjacentStorageSign(chestBoat.getLocation().getBlock(), item);
        }

        if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof BlockState left) {
                Optional<SsAdjacencyMatch> match = resolveAdjacentStorageSign(left.getBlock(), item);
                if (match.isPresent()) return match;
            }
            if (dc.getRightSide() instanceof BlockState right) {
                return resolveAdjacentStorageSign(right.getBlock(), item);
            }
            return Optional.empty();
        }

        if (holder instanceof BlockState bs) {
            return resolveAdjacentStorageSign(bs.getBlock(), item);
        }
        return Optional.empty();
    }

    /**
     * {@code requested.getAmount()} 以下の一致アイテムを削除し、実際に削除した数を返す。
     *
     * <p>サーバー実装によっては {@link Inventory#removeItem(ItemStack...)} 実行中に
     * 渡した {@link ItemStack} の数量が変更される場合がある。
     * このメソッドは左ったアイテムから削除数を計算することでその可変状態に依存しない。</p>
     */
    private static int removeMatchingAmount(Inventory inventory, ItemStack requested) {
        if (inventory == null || requested == null) return 0;

        int requestAmount = requested.getAmount();
        if (requestAmount <= 0) return 0;

        ItemStack toRemove = requested.clone();
        // clone() は amount を保持する; setAmount(requestAmount) は意図的に省略。
        // この時点で requested.getAmount() == requestAmount なので追加設定不要。

        Map<Integer, ItemStack> leftovers = inventory.removeItem(toRemove);
        int notRemoved = 0;
        for (ItemStack leftover : leftovers.values()) {
            notRemoved += leftover.getAmount();
        }

        // 渡したスタックの amount を変更するが leftovers を空に返す実装へのフォールバック。
        if (notRemoved == 0 && toRemove.getAmount() != requestAmount) {
            notRemoved = toRemove.getAmount();
        }

        return Math.max(0, requestAmount - notRemoved);
    }
}
