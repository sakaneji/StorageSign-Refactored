package storagesign.listener;

import java.util.Map;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import storagesign.AmountTransfer;
import storagesign.adjacency.SsAdjacencyMatch;
import storagesign.adjacency.SsAdjacencyPurpose;
import storagesign.adjacency.SsAdjacencyQuery;
import storagesign.adjacency.SsAdjacencyResolver;

final class InventoryTransferSupport {
    private static final SsAdjacencyResolver ADJACENCY_RESOLVER = SsAdjacencyResolver.defaultResolver();

    private InventoryTransferSupport() {
    }

    static int absorbAvailable(Inventory inventory, ItemStack item, SsAdjacencyMatch match) {
        int requested = Math.min(item.getAmount(),
            AmountTransfer.accepted(match.storageSign().getAmount(), item.getAmount()));
        int absorbed = removeMatchingAmount(inventory, withAmount(item, requested));
        if (absorbed > 0) {
            match.storageSign().setAmount(match.storageSign().getAmount() + absorbed);
            match.storageSign().applyToSign(match.signState());
        }
        return absorbed;
    }

    static Optional<SsAdjacencyMatch> resolveAdjacentStorageSign(Block container, ItemStack item) {
        return ADJACENCY_RESOLVER.findFirst(
            new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER)
        );
    }

    static int removeMatchingAmount(Inventory inventory, ItemStack requested) {
        if (inventory == null || requested == null) return 0;

        int requestAmount = requested.getAmount();
        if (requestAmount <= 0) return 0;

        ItemStack toRemove = requested.clone();
        Map<Integer, ItemStack> leftovers = inventory.removeItem(toRemove);
        int notRemoved = 0;
        for (ItemStack leftover : leftovers.values()) {
            notRemoved += leftover.getAmount();
        }

        if (notRemoved == 0 && toRemove.getAmount() != requestAmount) {
            notRemoved = toRemove.getAmount();
        }

        return Math.max(0, requestAmount - notRemoved);
    }

    static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) return null;
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}
