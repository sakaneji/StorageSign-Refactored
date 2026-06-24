package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.StorageSign;
import storagesign.adjacency.SsAdjacencyMatch;

class InventoryListenerResolutionTest {

    @Test
    void nullInventoryAndNullHolderResolveToEmpty() throws Exception {
        invoke(null, mock(ItemStack.class));

        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(null);
        invoke(inventory, mock(ItemStack.class));
    }

    @Test
    void unknownHolderAndDoubleChestWithoutBlockStateResolveToEmpty() throws Exception {
        ItemStack item = mock(ItemStack.class);
        InventoryHolder holder = mock(InventoryHolder.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        invoke(inventory, item);

        DoubleChest doubleChest = mock(DoubleChest.class);
        when(doubleChest.getLeftSide()).thenReturn(mock(InventoryHolder.class));
        when(doubleChest.getRightSide()).thenReturn(mock(InventoryHolder.class));
        Inventory doubleChestInventory = mock(Inventory.class);
        when(doubleChestInventory.getHolder()).thenReturn(doubleChest);
        invoke(doubleChestInventory, item);
    }

    @Test
    void inventoryHolderVariantsDispatchToTheirBlocks() throws Exception {
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        when(block.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

        try (MockedStatic<InventoryListener> listener =
                 Mockito.mockStatic(InventoryListener.class, Mockito.CALLS_REAL_METHODS)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(
                block, item))
                .thenReturn(Optional.of(match));

            HopperMinecart hopper = mock(HopperMinecart.class);
            when(hopper.getLocation()).thenReturn(location);
            Inventory hopperInventory = mock(Inventory.class);
            when(hopperInventory.getHolder()).thenReturn(hopper);
            invoke(hopperInventory, item);

            StorageMinecart storageMinecart = mock(StorageMinecart.class);
            when(storageMinecart.getLocation()).thenReturn(location);
            Inventory storageInventory = mock(Inventory.class);
            when(storageInventory.getHolder()).thenReturn(storageMinecart);
            invoke(storageInventory, item);

            ChestBoat chestBoat = mock(ChestBoat.class);
            when(chestBoat.getLocation()).thenReturn(location);
            Inventory chestBoatInventory = mock(Inventory.class);
            when(chestBoatInventory.getHolder()).thenReturn(chestBoat);
            invoke(chestBoatInventory, item);

            Chest left = mock(Chest.class);
            when(left.getBlock()).thenReturn(block);
            DoubleChest doubleChest = mock(DoubleChest.class);
            when(doubleChest.getLeftSide()).thenReturn(left);
            Inventory doubleChestInventory = mock(Inventory.class);
            when(doubleChestInventory.getHolder()).thenReturn(doubleChest);
            invoke(doubleChestInventory, item);

            Chest state = mock(Chest.class);
            when(state.getBlock()).thenReturn(block);
            Inventory stateInventory = mock(Inventory.class);
            when(stateInventory.getHolder()).thenReturn(state);
            invoke(stateInventory, item);
        }
    }

    @Test
    void proxyInventoryHolderVariantsDispatchToTheirBlocks() throws Exception {
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        when(block.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

        try (MockedStatic<InventoryListener> listener =
                 Mockito.mockStatic(InventoryListener.class, Mockito.CALLS_REAL_METHODS)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(
                block, item))
                .thenReturn(Optional.of(match));

            Inventory hopperInventory = proxyInventory(HopperMinecart.class, location);
            invoke(hopperInventory, item);

            Inventory storageInventory = proxyInventory(StorageMinecart.class, location);
            invoke(storageInventory, item);

            Inventory chestBoatInventory = proxyInventory(ChestBoat.class, location);
            invoke(chestBoatInventory, item);
        }
    }

    @Test
    void blockStateHolderDispatchesDirectlyToItsBlock() throws Exception {
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        when(block.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        Chest state = mock(Chest.class);
        when(state.getBlock()).thenReturn(block);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(state);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

        try (MockedStatic<InventoryListener> listener =
                 Mockito.mockStatic(InventoryListener.class, Mockito.CALLS_REAL_METHODS)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(
                block, item))
                .thenReturn(Optional.of(match));

            invoke(inventory, item);
        }
    }

    @Test
    void doubleChestFallsBackToRightSideWhenLeftSideDoesNotMatch() throws Exception {
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        when(block.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

        Chest right = mock(Chest.class);
        when(right.getBlock()).thenReturn(block);
        DoubleChest doubleChest = mock(DoubleChest.class);
        when(doubleChest.getLeftSide()).thenReturn(mock(InventoryHolder.class));
        when(doubleChest.getRightSide()).thenReturn(right);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(doubleChest);

        try (MockedStatic<InventoryListener> listener =
                 Mockito.mockStatic(InventoryListener.class, Mockito.CALLS_REAL_METHODS)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(
                block, item))
                .thenReturn(Optional.of(match));
            invoke(inventory, item);
        }
    }

    @Test
    void doubleChestUsesRightSideWhenLeftSideMissing() throws Exception {
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        when(block.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

        Chest right = mock(Chest.class);
        when(right.getBlock()).thenReturn(block);
        DoubleChest doubleChest = mock(DoubleChest.class);
        when(doubleChest.getLeftSide()).thenReturn(null);
        when(doubleChest.getRightSide()).thenReturn(right);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(doubleChest);

        try (MockedStatic<InventoryListener> listener =
                 Mockito.mockStatic(InventoryListener.class, Mockito.CALLS_REAL_METHODS)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(
                block, item))
                .thenReturn(Optional.of(match));
            invoke(inventory, item);
        }
    }

    @Test
    void doubleChestLeftSideCanShortCircuitBeforeTheRightSideIsRead() throws Exception {
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        when(block.getLocation()).thenReturn(location);
        when(location.getBlock()).thenReturn(block);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

        Chest left = mock(Chest.class);
        when(left.getBlock()).thenReturn(block);
        DoubleChest doubleChest = mock(DoubleChest.class);
        when(doubleChest.getLeftSide()).thenReturn(left);
        when(doubleChest.getRightSide()).thenThrow(new AssertionError("right side should not be read"));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(doubleChest);

        try (MockedStatic<InventoryListener> listener = Mockito.mockStatic(InventoryListener.class)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(block, item))
                .thenReturn(Optional.of(match));
            invoke(inventory, item);
        }
    }

    @Test
    void doubleChestFallsBackToRightSideForRealAdjacentInventories() throws Exception {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("inventory-double-chest");
            world.getChunkAt(0, 0).load();
            Block leftBlock = world.getBlockAt(0, 64, 0);
            Block rightBlock = world.getBlockAt(1, 64, 0);
            leftBlock.setType(org.bukkit.Material.CHEST);
            rightBlock.setType(org.bukkit.Material.CHEST);
            Inventory inventory = ((org.bukkit.block.Chest) leftBlock.getState()).getInventory();
            ItemStack item = mock(ItemStack.class);
            Sign sign = mock(Sign.class);
            StorageSign storageSign = mock(StorageSign.class);
            SsAdjacencyMatch match = new SsAdjacencyMatch(rightBlock, sign, storageSign);

        try (MockedStatic<InventoryListener> listener =
                 Mockito.mockStatic(InventoryListener.class, Mockito.CALLS_REAL_METHODS)) {
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(leftBlock, item))
                .thenReturn(Optional.empty());
            listener.when(() -> InventoryListener.resolveAdjacentStorageSign(rightBlock, item))
                .thenReturn(Optional.of(match));

                invoke(inventory, item);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    private static Optional<?> invoke(Inventory inventory, ItemStack item) throws Exception {
        Method method = InventoryListener.class.getDeclaredMethod(
            "resolveAdjacentStorageSignForInventory", Inventory.class, ItemStack.class
        );
        method.setAccessible(true);
        return (Optional<?>) method.invoke(new InventoryListener(null), inventory, item);
    }

    private static Inventory proxyInventory(Class<?> holderType, Location location) {
        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getLocation" -> location;
                case "toString" -> holderType.getSimpleName() + "Proxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        };
        Object holder = Proxy.newProxyInstance(
            InventoryListenerResolutionTest.class.getClassLoader(),
            new Class<?>[] { InventoryHolder.class, holderType },
            handler
        );
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn((InventoryHolder) holder);
        return inventory;
    }
}
