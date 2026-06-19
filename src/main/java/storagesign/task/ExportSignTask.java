package storagesign.task;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import storagesign.StorageSign;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;

/**
 * コンテナを隣接する StorageSign から補充する一回限りの {@link BukkitRunnable}。
 *
 * <p>ホッパーがコンテナからアイテムを引き出した <em>次のティック</em> に実行される。
 * これによりインベントリ状態が安定し、ホッパーが実際に取った状態を正確に反映できる。
 *
 * <h3>補充ロジック</h3>
 * <ol>
 *   <li>看板ブロックから SS を再読み込む（常に最新状態を取得）。</li>
 *   <li>ソースコンテナにアイテムの満杯スタックがあれば補充不要。</li>
 *   <li>既存スタックを満杯に捨てるために追加する個数を計算する。</li>
 *   <li>スロット選択はサーバーに委ねるため {@link Inventory#addItem} を使用する。</li>
 *   <li>SS の保管数量から実際に追加した分だけ差し引く。</li>
 * </ol>
 *
 * <h3>1 ティック遅延の理由</h3>
 * Spigot/Paper どちらでも {@code InventoryMoveItemEvent} はアイテム移動<em>前</em>に発火する。
 * すぐに補充するとホッパーが取り出す前に補充されてしまう。
 * 遅延によりアイテムが届いてから補充するかどうかを正確に判断できる。
 */
public final class ExportSignTask extends BukkitRunnable {

    private static final PluginLogger LOG = PluginLogger.getLogger(ExportSignTask.class);

    /** 補充元の StorageSign ブロック。 */
    private final Block ssBlock;

    /** SS の隣接コンテナ（ホッパーがアイテムを引き出すコンテナ）。 */
    private final Inventory sourceInventory;

    /** 移動したアイテムのクローン—一致スロットを検索・カウントするために使用。 */
    private final ItemStack movedItem;

    /**
     * {@link storagesign.listener.InventoryListener} と共有し、エクスポートタスクの重複登録を防ぐセット。
     * {@link #run()} の先頭で自分のブロックを削除し、次のティックでの再スケジュールを可能にする。
     */
    private final Set<Block> pendingExports;

    /**
     * @param ssBlock         ソースコンテナの隣接ブロックにある StorageSign
     * @param sourceInventory ホッパーが引き出すコンテナのインベントリ
     * @param movedItem       {@code event.getItem()} のクローン（アイテム種別照合用）
     * @param pendingExports  タスク重複登録を防ぐための共有セット
     */
    public ExportSignTask(Block ssBlock, Inventory sourceInventory, ItemStack movedItem,
                          Set<Block> pendingExports) {
        this.ssBlock         = ssBlock;
        this.sourceInventory = sourceInventory;
        this.movedItem       = movedItem;
        this.pendingExports  = pendingExports;
    }

    @Override
    public void run() {
        // 将来のホッパーティックがこのタスクを再スケジュールできるようにする。
        pendingExports.remove(ssBlock);

        // タスク実行時に看板の状態を再読み込む（常に最新状態を得るため重要）。
        // Sign ブロック状態を一度取得し、パースと applyToSign の両方で再利用する。
        // fromBlock() + 条件付き getState() という2 回の呼び出しコストを回避。
        if (!(ssBlock.getState() instanceof Sign sign)) {
            traceSkip("sign-state-missing");
            return;
        }
        StorageSign ss = StorageSign.fromSign(sign);
        if (ss == null || ss.isUnregistered() || ss.getAmount() <= 0) {
            traceSkip("storage-sign-empty-or-invalid");
            return;
        }

        int maxStack = movedItem.getMaxStackSize();

        // 早期リターン: SS の兇存アイテムがホッパー 1 回分未満 — 追加する意味がない。
        if (ss.getAmount() < movedItem.getAmount()) {
            traceSkip("stored-amount-below-moved-amount");
            return;
        }

        // インベントリ内容を一度のパスで処理:
        // - 一致スタック数と合計数量をカウントし（補充最大数を計算する）
        // - 空スロットの存在を併せて確認（firstEmpty() の追加スキャンを不要にする）
        // containsAtLeast + getContents() + firstEmpty() の 3 回スキャンパターンを置換。
        int stacks = 0;
        int total  = 0;
        boolean hasEmpty = false;
        for (ItemStack slot : sourceInventory.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                hasEmpty = true;
            } else if (ss.isSimilar(slot)) {
                stacks++;
                total += slot.getAmount();
                // total >= maxStack になった時点で containsAtLeast(満杯) と同等。
                if (total >= maxStack) {
                    traceSkip("source-already-has-full-stack");
                    return;
                }
            }
        }

        // 一致スロットがすべて満杯で空スロットもなければ追加不要。
        if (total == stacks * maxStack && !hasEmpty) {
            traceSkip("source-has-no-capacity");
            return;
        }

        // 追加数を計算: 既存の部分スタックを満杯にする。
        int addAmount = (stacks == 0) ? maxStack : (maxStack * stacks - total);
        addAmount = Math.min(addAmount, ss.getAmount());
        if (addAmount <= 0) {
            traceSkip("calculated-add-amount-zero");
            return;
        }

        // movedItem はスケジュール時に isSimilar で検証済み。
        // クローンすることでメタデータを保持し、再度の isSimilar チェックを不要にする。
        ItemStack refill = movedItem.clone();
        refill.setAmount(addAmount);

        int actualAdded = addToSource(refill, addAmount);

        if (actualAdded <= 0) {
            traceSkip("inventory-rejected-refill");
            return;
        }

        ss.setAmount(ss.getAmount() - actualAdded);
        ss.applyToSign(sign);
        if (LOG.isTraceEnabled()) {
            LOG.trace("run", () -> "refilled=" + actualAdded
                      + ", material=" + movedItem.getType()
                      + ", sign=" + ssBlock.getLocation()
                      + ", remaining=" + ss.getAmount());
        }
    }

    private void traceSkip(String reason) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("run", () -> "skip=" + reason
                      + ", material=" + movedItem.getType()
                      + ", sign=" + ssBlock.getLocation());
        }
    }

    private int addToSource(ItemStack refill, int addAmount) {
        InventoryType type = sourceInventory.getType();
        if (type == InventoryType.BREWING) {
            return addToBrewing(refill, addAmount);
        }
        if (type == InventoryType.FURNACE
            || type == InventoryType.BLAST_FURNACE
            || type == InventoryType.SMOKER) {
            return addToFurnace(refill, addAmount);
        }
        var leftover = sourceInventory.addItem(refill);
        int leftAmount = 0;
        for (ItemStack rest : leftover.values()) {
            leftAmount += rest.getAmount();
        }
        return addAmount - leftAmount;
    }

    private int addToBrewing(ItemStack refill, int maxAdd) {
        Material type = refill.getType();
        if (type == Material.BLAZE_POWDER) {
            return addIntoSlot(4, refill, maxAdd);
        }
        if (MaterialRegistry.POTION_MATERIALS.contains(type)) {
            int remaining = maxAdd;
            int added = 0;
            for (int slot = 0; slot < 3 && remaining > 0; slot++) {
                int oneAdded = addIntoSlot(slot, refill, remaining);
                remaining -= oneAdded;
                added += oneAdded;
            }
            return added;
        }
        if (!isBrewingIngredient(type)) return 0;
        return addIntoSlot(3, refill, maxAdd);
    }

    private int addToFurnace(ItemStack refill, int maxAdd) {
        int slot = refill.getType().isFuel() ? 1 : 0;
        return addIntoSlot(slot, refill, maxAdd);
    }

    private int addIntoSlot(int slot, ItemStack template, int maxAdd) {
        ItemStack existing = sourceInventory.getItem(slot);
        int stackMax = template.getMaxStackSize();
        if (existing == null || existing.getType() == Material.AIR) {
            int add = Math.min(maxAdd, stackMax);
            ItemStack inserted = template.clone();
            inserted.setAmount(add);
            sourceInventory.setItem(slot, inserted);
            return add;
        }
        if (!existing.isSimilar(template)) return 0;
        int space = stackMax - existing.getAmount();
        if (space <= 0) return 0;
        int add = Math.min(maxAdd, space);
        existing.setAmount(existing.getAmount() + add);
        sourceInventory.setItem(slot, existing);
        return add;
    }

    private static boolean isBrewingIngredient(Material material) {
        return switch (material) {
            case NETHER_WART, SUGAR, REDSTONE, GLOWSTONE_DUST, GUNPOWDER,
                 RABBIT_FOOT, GLISTERING_MELON_SLICE, GOLDEN_CARROT, MAGMA_CREAM,
                 GHAST_TEAR, SPIDER_EYE, FERMENTED_SPIDER_EYE, DRAGON_BREATH,
                 PUFFERFISH, TURTLE_SCUTE, PHANTOM_MEMBRANE, BREEZE_ROD -> true;
            default -> false;
        };
    }
}
