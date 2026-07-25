package storagesign.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mockito.ArgumentCaptor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;
import storagesign.ConfigLoader;
import storagesign.logging.PluginLogger;
import org.junit.jupiter.api.AfterEach;

@Tag("integration")
class ExportSignTaskIntegrationTest {

    @BeforeEach
    void setUp() throws Exception {
        setBrewingIngredients(Set.of());
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("ExportSignTaskIntegrationTest.trace");
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.FINEST);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(jul);
        PluginLogger.initialize(plugin, "TRACE");
    }

    @AfterEach
    void tearDown() {
        PluginLogger.shutdown();
    }

    @Test
    void regularInventoryReportsOnlyAmountActuallyAccepted() throws Exception {
        Inventory chest = mock(Inventory.class);
        ItemStack item = mockItem(Material.DIAMOND, 10, 64);
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        ItemStack leftover = mock(ItemStack.class);
        when(leftover.getAmount()).thenReturn(9);
        leftovers.put(0, leftover);
        when(chest.addItem(any(ItemStack.class))).thenReturn(leftovers);

        int added = addToSource(chest, item, 10);

        assertEquals(1, added);
    }

    @Test
    void brewingInventoryRoutesFuelPotionIngredientAndRejectsUnusableItem() throws Exception {
        Inventory brewing = mock(Inventory.class);
        when(brewing.getType()).thenReturn(InventoryType.BREWING);

        assertEquals(4, addToSource(brewing, mockItem(Material.BLAZE_POWDER, 4, 64), 4));
        assertEquals(3, addToSource(brewing, mockItem(Material.POTION, 3, 1), 3));
        assertEquals(2, addToSource(brewing, mockItem(Material.NETHER_WART, 2, 64), 2));
        assertEquals(0, addToSource(brewing, mockItem(Material.COBBLESTONE, 1, 64), 1));
    }

    @Test
    void configuredFutureBrewingIngredientUsesIngredientSlotWithoutCodeChange() throws Exception {
        setBrewingIngredients(Set.of("COBBLESTONE"));
        Inventory brewing = mock(Inventory.class);
        when(brewing.getType()).thenReturn(InventoryType.BREWING);

        assertEquals(2, addToSource(brewing, mockItem(Material.COBBLESTONE, 2, 64), 2));
    }

    @Test
    void furnaceRoutesFuelAndInputToDedicatedSlots() throws Exception {
        Inventory furnace = mock(Inventory.class);
        when(furnace.getType()).thenReturn(InventoryType.FURNACE);

        assertEquals(8, addToSource(furnace, mockItem(Material.COAL, 8, 64), 8));
        assertEquals(5, addToSource(furnace, mockItem(Material.IRON_ORE, 5, 64), 5));
    }

    @Test
    void blastFurnaceAndSmokerUseTheSameFurnaceBranch() throws Exception {
        Inventory blastFurnace = mock(Inventory.class);
        when(blastFurnace.getType()).thenReturn(InventoryType.BLAST_FURNACE);
        Inventory smoker = mock(Inventory.class);
        when(smoker.getType()).thenReturn(InventoryType.SMOKER);

        assertEquals(2, addToSource(blastFurnace, mockItem(Material.IRON_ORE, 2, 64), 2));
        assertEquals(4, addToSource(smoker, mockItem(Material.COAL, 4, 64), 4));
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

        new ExportSignTask(block, inventory, mockItem(Material.DIAMOND, 1, 64), pending).run();

        assertEquals(0, pending.size());
        verify(block, never()).getState();
    }

    @Test
    void missingSignStateStillReleasesPendingReservation() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));

        new ExportSignTask(block, mock(Inventory.class),
            mockItem(Material.DIAMOND, 1, 64), pending).run();

        assertEquals(0, pending.size());
    }

    @Test
    void invalidSignStateAlsoReleasesPendingReservation() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(null);
            new ExportSignTask(block, mock(Inventory.class),
                mockItem(Material.DIAMOND, 1, 64), pending).run();
        }

        assertEquals(0, pending.size());
    }

    @Test
    void storedAmountBelowMovedAmountSkipsRefill() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(storageSign.getAmount()).thenReturn(1);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, mockItem(Material.DIAMOND, 2, 64), pending).run();
        }

        assertEquals(0, pending.size());
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void sourceAlreadyHasFullStackSkipsRefillWithoutConsumingStorageSign() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack[] contents = new ItemStack[] {
            mockItem(Material.DIAMOND, 64, 64),
            mockItem(Material.DIAMOND, 64, 64)
        };
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(inventory.getContents()).thenReturn(contents);
        when(storageSign.getAmount()).thenReturn(128);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, mockItem(Material.DIAMOND, 1, 64), new HashSet<>()).run();
        }

        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void zeroStoredAmountSkipsRefillAfterComputingZeroAddAmount() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        ItemStack moved = mockItem(Material.DIAMOND, 1, 64);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        when(inventory.getContents()).thenReturn(new ItemStack[] { moved, null });
        when(storageSign.getAmount()).thenReturn(1);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(storageSign.getContents(1)).thenReturn(moved);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, moved, pending).run();
        }

        assertEquals(0, pending.size());
        verify(storageSign).setAmount(0);
    }

    @Test
    void partiallyFilledSourceWithEmptySlotSkipsZeroAddAmount() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack full = mockItem(Material.DIAMOND, 64, 64);
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        when(inventory.getContents()).thenReturn(new ItemStack[] { full, null });
        when(storageSign.getAmount()).thenReturn(1);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(any(ItemStack.class))).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, mockItem(Material.DIAMOND, 1, 64), pending).run();
        }

        assertEquals(0, pending.size());
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void fullInventoryWithNoMatchingStackSkipsRefillWithoutConsumingStorageSign() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack[] contents = new ItemStack[] {
            mockItem(Material.GOLD_INGOT, 64, 64),
            mockItem(Material.GOLD_INGOT, 64, 64)
        };
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        when(inventory.getContents()).thenReturn(contents);
        when(storageSign.getAmount()).thenReturn(64);
        when(storageSign.isUnregistered()).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, mockItem(Material.DIAMOND, 1, 64), pending).run();
        }

        assertEquals(0, pending.size());
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).addItem(org.mockito.ArgumentMatchers.any(ItemStack.class));
    }

    @Test
    void inventoryRejectedRefillLeavesStorageSignUntouched() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign signState = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack matching = mockItem(Material.DIAMOND, 10, 64);
        ItemStack[] contents = new ItemStack[] { matching, null };
        Set<Block> pending = new HashSet<>();
        pending.add(block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(signState);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        when(inventory.getContents()).thenReturn(contents);
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        leftovers.put(0, mockItem(Material.DIAMOND, 54, 64));
        when(inventory.addItem(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenReturn(leftovers);
        when(storageSign.getAmount()).thenReturn(64);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(matching)).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, mockItem(Material.DIAMOND, 1, 64), pending).run();
        }

        assertEquals(0, pending.size());
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void brewingRunUsesPotionAndIngredientSlots() throws Exception {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory brewing = mock(Inventory.class);
        ItemStack refill = mockItem(Material.POTION, 1, 1);
        ItemStack moved = mockItem(Material.POTION, 1, 1);
        Set<Block> pending = new HashSet<>();
        pending.add(block);

        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(sign);
        when(storageSign.getAmount()).thenReturn(3);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(org.mockito.ArgumentMatchers.any(ItemStack.class))).thenReturn(false);
        when(storageSign.getContents(1)).thenReturn(refill);
        when(brewing.getType()).thenReturn(InventoryType.BREWING);
        when(brewing.getContents()).thenReturn(new ItemStack[] { null, null, null, null, null });
        when(brewing.getItem(0)).thenReturn(null);
        when(brewing.getItem(1)).thenReturn(null);
        when(brewing.getItem(2)).thenReturn(null);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(sign)).thenReturn(storageSign);
            new ExportSignTask(block, brewing, moved, pending).run();
        }

        assertEquals(0, pending.size());
    }

    @Test
    void successfulRunRefillsRegularInventory() throws Exception {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack refill = mockItem(Material.DIAMOND, 1, 64);
        ItemStack moved = mockItem(Material.DIAMOND, 1, 64);
        Set<Block> pending = new HashSet<>();
        pending.add(block);

        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(sign);
        when(storageSign.getAmount()).thenReturn(1);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(org.mockito.ArgumentMatchers.any(ItemStack.class))).thenReturn(false);
        when(storageSign.getContents(1)).thenReturn(refill);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        when(inventory.getContents()).thenReturn(new ItemStack[] { null });
        when(inventory.addItem(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenReturn(new HashMap<>());

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(sign)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, moved, pending).run();
        }

        assertEquals(0, pending.size());
    }

    @Test
    void successfulRunBuildsRefillFromStorageSignInsteadOfMovedItemMetadata() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack moved = mockItem(Material.ENCHANTED_BOOK, 1, 1);
        ItemStack canonical = mock(ItemStack.class);
        ItemStack canonicalClone = mock(ItemStack.class);
        Set<Block> pending = new HashSet<>();
        pending.add(block);

        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getState()).thenReturn(sign);
        when(storageSign.getAmount()).thenReturn(1);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.getContents(1)).thenReturn(canonical);
        when(canonical.getMaxStackSize()).thenReturn(1);
        when(canonical.clone()).thenReturn(canonicalClone);
        when(canonicalClone.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(canonicalClone.getMaxStackSize()).thenReturn(1);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);
        when(inventory.getContents()).thenReturn(new ItemStack[] { null });
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(sign)).thenReturn(storageSign);
            new ExportSignTask(block, inventory, moved, pending).run();
        }

        ArgumentCaptor<ItemStack> inserted = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).addItem(inserted.capture());
        assertSame(canonicalClone, inserted.getValue());
        verify(canonicalClone).setAmount(1);
        verify(storageSign).setAmount(0);
    }

    @Test
    void addIntoSlotCreatesAndExtendsStacksInOrder() throws Exception {
        MockBukkit.mock();
        try {
            Inventory inventory = mock(Inventory.class);
            ItemStack template = new ItemStack(Material.DIAMOND, 5);
            ItemStack existing = new ItemStack(Material.DIAMOND, 60);
            when(inventory.getItem(0)).thenReturn(null);
            when(inventory.getItem(1)).thenReturn(existing);

            ExportSignTask task = new ExportSignTask(mock(Block.class), inventory, template, new HashSet<>());
            Method method = ExportSignTask.class.getDeclaredMethod(
                "addIntoSlot", int.class, ItemStack.class, int.class);
            method.setAccessible(true);

            assertEquals(5, method.invoke(task, 0, template, 5));
            ArgumentCaptor<ItemStack> inserted = ArgumentCaptor.forClass(ItemStack.class);
            verify(inventory).setItem(eq(0), inserted.capture());
            assertEquals(5, inserted.getValue().getAmount());
            assertEquals(4, method.invoke(task, 1, template, 8));
            assertEquals(64, existing.getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void addIntoSlotRejectsMismatchedExistingStack() throws Exception {
        MockBukkit.mock();
        try {
            Inventory inventory = mock(Inventory.class);
            ItemStack template = new ItemStack(Material.DIAMOND, 5);
            ItemStack existing = new ItemStack(Material.GOLD_INGOT, 1);
            when(inventory.getItem(0)).thenReturn(existing);

            ExportSignTask task = new ExportSignTask(mock(Block.class), inventory, template, new HashSet<>());
            Method method = ExportSignTask.class.getDeclaredMethod(
                "addIntoSlot", int.class, ItemStack.class, int.class);
            method.setAccessible(true);

            assertEquals(0, method.invoke(task, 0, template, 5));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void addIntoSlotReturnsZeroWhenExistingStackIsAlreadyFull() throws Exception {
        MockBukkit.mock();
        try {
            Inventory inventory = mock(Inventory.class);
            ItemStack template = new ItemStack(Material.DIAMOND, 5);
            ItemStack existing = new ItemStack(Material.DIAMOND, 64);
            when(inventory.getItem(0)).thenReturn(existing);

            ExportSignTask task = new ExportSignTask(mock(Block.class), inventory, template, new HashSet<>());
            Method method = ExportSignTask.class.getDeclaredMethod(
                "addIntoSlot", int.class, ItemStack.class, int.class);
            method.setAccessible(true);

            assertEquals(0, method.invoke(task, 0, template, 5));
        } finally {
            MockBukkit.unmock();
        }
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

    private static ItemStack mockItem(Material type, int amount, int maxStack) {
        ItemStack item = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        when(item.getMaxStackSize()).thenReturn(maxStack);
        when(item.clone()).thenReturn(clone);
        when(clone.getType()).thenReturn(type);
        when(clone.getAmount()).thenReturn(amount);
        when(clone.getMaxStackSize()).thenReturn(maxStack);
        when(clone.clone()).thenReturn(clone);
        return item;
    }
}
