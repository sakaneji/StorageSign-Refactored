package storagesign.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

@Tag("integration")
class ExportSignTaskIntegrationTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void regularInventoryReportsOnlyAmountActuallyAccepted() throws Exception {
        Inventory chest = server.createInventory(null, 9);
        for (int slot = 0; slot < 8; slot++) chest.setItem(slot, new ItemStack(Material.DIRT, 64));
        chest.setItem(8, new ItemStack(Material.STONE, 63));

        int added = addToSource(chest, new ItemStack(Material.STONE, 10), 10);

        assertEquals(1, added);
        assertEquals(64, chest.getItem(8).getAmount());
    }

    @Test
    void brewingInventoryRoutesFuelPotionIngredientAndRejectsUnusableItem() throws Exception {
        Inventory brewing = server.createInventory(null, InventoryType.BREWING);

        assertEquals(4, addToSource(brewing, new ItemStack(Material.BLAZE_POWDER, 4), 4));
        assertEquals(Material.BLAZE_POWDER, brewing.getItem(4).getType());
        assertEquals(3, addToSource(brewing, new ItemStack(Material.POTION, 3), 3));
        assertEquals(1, brewing.getItem(0).getAmount());
        assertEquals(2, addToSource(brewing, new ItemStack(Material.NETHER_WART, 2), 2));
        assertEquals(Material.NETHER_WART, brewing.getItem(3).getType());
        assertEquals(0, addToSource(brewing, new ItemStack(Material.COBBLESTONE, 1), 1));
    }

    @Test
    void furnaceRoutesFuelAndInputToDedicatedSlots() throws Exception {
        Inventory furnace = server.createInventory(null, InventoryType.FURNACE);

        assertEquals(8, addToSource(furnace, new ItemStack(Material.COAL, 8), 8));
        assertEquals(Material.COAL, furnace.getItem(1).getType());
        assertEquals(5, addToSource(furnace, new ItemStack(Material.IRON_ORE, 5), 5));
        assertEquals(Material.IRON_ORE, furnace.getItem(0).getType());
    }

    private static int addToSource(Inventory inventory, ItemStack item, int amount) throws Exception {
        ExportSignTask task = new ExportSignTask(
            mock(Block.class), inventory, item, new HashSet<>()
        );
        Method method = ExportSignTask.class.getDeclaredMethod("addToSource", ItemStack.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(task, item, amount);
    }
}
