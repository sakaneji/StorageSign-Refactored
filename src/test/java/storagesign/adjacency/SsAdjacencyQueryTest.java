package storagesign.adjacency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SsAdjacencyQueryTest {

    @Test
    void rejectsNullContainerBlockAndPurpose() {
        Block block = Mockito.mock(Block.class);
        ItemStack item = Mockito.mock(ItemStack.class);

        assertThrows(IllegalArgumentException.class,
            () -> new SsAdjacencyQuery(null, item, SsAdjacencyPurpose.INVENTORY_TRANSFER));
        assertThrows(IllegalArgumentException.class,
            () -> new SsAdjacencyQuery(block, item, null));
        assertDoesNotThrow(() -> new SsAdjacencyQuery(block, item,
            SsAdjacencyPurpose.INVENTORY_TRANSFER));
    }
}
