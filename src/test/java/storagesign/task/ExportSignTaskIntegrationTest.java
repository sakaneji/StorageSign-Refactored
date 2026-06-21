package storagesign.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;
import storagesign.ConfigLoader;

@Tag("integration")
class ExportSignTaskIntegrationTest {

    private ServerMock server;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        setBrewingIngredients(Set.of());
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
    void configuredFutureBrewingIngredientUsesIngredientSlotWithoutCodeChange() throws Exception {
        setBrewingIngredients(Set.of("COBBLESTONE"));
        Inventory brewing = server.createInventory(null, InventoryType.BREWING);

        assertEquals(2, addToSource(brewing, new ItemStack(Material.COBBLESTONE, 2), 2));
        assertEquals(Material.COBBLESTONE, brewing.getItem(3).getType());
    }

    @Test
    void furnaceRoutesFuelAndInputToDedicatedSlots() throws Exception {
        Inventory furnace = server.createInventory(null, InventoryType.FURNACE);

        assertEquals(8, addToSource(furnace, new ItemStack(Material.COAL, 8), 8));
        assertEquals(Material.COAL, furnace.getItem(1).getType());
        assertEquals(5, addToSource(furnace, new ItemStack(Material.IRON_ORE, 5), 5));
        assertEquals(Material.IRON_ORE, furnace.getItem(0).getType());
    }

    @Test
    void unloadedChunkAbortsWithoutForcingBlockStateRead() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Inventory inventory = mock(Inventory.class);
        HashSet<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(32);
        when(block.getZ()).thenReturn(48);
        when(world.isChunkLoaded(2, 3)).thenReturn(false);

        new ExportSignTask(block, inventory, new ItemStack(Material.STONE), pending).run();

        assertEquals(0, pending.size());
        verify(block, never()).getState();
    }

    @Test
    void existingFullStackDoesNotConsumeStorageSign() {
        Inventory inventory = server.createInventory(null, 9);
        inventory.setItem(0, new ItemStack(Material.STONE, 64));
        runWithStorageSign(inventory, 128);
    }

    @Test
    void completelyFullInventoryDoesNotConsumeStorageSign() {
        Inventory inventory = server.createInventory(null, 9);
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        runWithStorageSign(inventory, 128);
    }

    private static void runWithStorageSign(Inventory inventory, int amount) {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(block.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(storageSign.getAmount()).thenReturn(amount);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenAnswer(invocation -> invocation.<ItemStack>getArgument(0).getType() == Material.STONE);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, new ItemStack(Material.STONE), new HashSet<>()).run();
        }
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    private static void setBrewingIngredients(Set<String> values) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("brewingIngredientIdentifiers");
        field.setAccessible(true);
        field.set(null, values);
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
