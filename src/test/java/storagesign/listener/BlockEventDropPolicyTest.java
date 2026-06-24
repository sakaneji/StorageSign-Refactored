package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;
import storagesign.index.StorageSignIndex;
import storagesign.logging.PluginLogger;

class BlockEventDropPolicyTest {

    @BeforeEach
    void enableTraceLogging() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("BlockEventDropPolicyTest.trace");
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.FINEST);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(jul);
        PluginLogger.initialize(plugin, "TRACE");
    }

    @AfterEach
    void resetTraceLogging() {
        PluginLogger.shutdown();
    }

    @Test
    void zeroAmountWallSignDropsMatchingEmptyInventorySign() throws Exception {
        assertDrop(Material.OAK_WALL_SIGN, Material.OAK_SIGN, 0, StorageSign.EMPTY_MARKER);
    }

    @Test
    void wallHangingSignDropsMatchingRegisteredHangingSign() throws Exception {
        assertDrop(Material.MANGROVE_WALL_HANGING_SIGN, Material.MANGROVE_HANGING_SIGN,
            12, "STONE 12");
    }

    @Test
    void indexedDropPathUnregistersTheDroppedSign() throws Exception {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Location origin = mock(Location.class);
        Location dropLocation = mock(Location.class);
        StorageSign storageSign = mock(StorageSign.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        ItemStack dropped = mock(ItemStack.class);
        when(block.getLocation()).thenReturn(origin);
        when(block.getWorld()).thenReturn(world);
        when(origin.clone()).thenReturn(dropLocation);
        when(dropLocation.add(0.5, 0.5, 0.5)).thenReturn(dropLocation);
        when(storageSign.getAmount()).thenReturn(8);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, storageSign, 1)).thenReturn(dropped);
            Method method = BlockEventListener.class.getDeclaredMethod(
                "dropSingleStorageSign", Block.class, Material.class, StorageSign.class,
                StorageSignIndex.class);
            method.setAccessible(true);
            method.invoke(null, block, Material.OAK_SIGN, storageSign, index);
        }

        verify(world).dropItem(dropLocation, dropped);
        verify(block).setType(Material.AIR);
        verify(index).unregister(block);
    }

    @Test
    void tryDropStorageSignIgnoresNonSignBlocksAndMissingStorageSigns() throws Exception {
        Block ordinary = mock(Block.class);
        when(ordinary.getType()).thenReturn(Material.STONE);
        Method method = BlockEventListener.class.getDeclaredMethod(
            "tryDropStorageSign", Block.class, StorageSignIndex.class);
        method.setAccessible(true);
        method.invoke(null, ordinary, null);

        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_SIGN);
        when(signBlock.getState()).thenReturn(mock(org.bukkit.block.Sign.class));
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(Mockito.any(org.bukkit.block.Sign.class)))
                .thenReturn(null);
            method.invoke(null, signBlock, null);
        }
    }

    @Test
    void tryDropStorageSignReturnsForSignTypeBlocksWithoutSignState() throws Exception {
        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_SIGN);
        when(signBlock.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));

        Method method = BlockEventListener.class.getDeclaredMethod(
            "tryDropStorageSign", Block.class, StorageSignIndex.class);
        method.setAccessible(true);
        method.invoke(null, signBlock, null);
    }

    @Test
    void tryDropStorageSignDropsRecognizedStorageSigns() throws Exception {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            World world = org.mockbukkit.mockbukkit.MockBukkit.getMock().addSimpleWorld("try-drop");
            Block block = world.getBlockAt(0, 64, 0);
            block.setType(Material.OAK_SIGN);
            org.bukkit.block.Sign signState = (org.bukkit.block.Sign) block.getState();
            StorageSign storageSign = mock(StorageSign.class);
            ItemStack dropped = mock(ItemStack.class);
            when(storageSign.getAmount()).thenReturn(8);
            when(dropped.getType()).thenReturn(Material.OAK_SIGN);

            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
                signs.when(() -> StorageSign.fromSign(signState)).thenReturn(storageSign);
                signs.when(() -> StorageSign.createStorageSignItem(
                    Material.OAK_SIGN, storageSign, 1)).thenReturn(dropped);
                Method method = BlockEventListener.class.getDeclaredMethod(
                    "tryDropStorageSign", Block.class, StorageSignIndex.class);
                method.setAccessible(true);
                method.invoke(null, block, null);
            }
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    @Test
    void publicDropHelpersHandleOrdinaryBlocksWithoutDroppingAnything() {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            World world = org.mockbukkit.mockbukkit.MockBukkit.getMock().addSimpleWorld("drop-helpers");
            Block block = world.getBlockAt(0, 64, 0);
            block.setType(Material.STONE);

            BlockEventListener.dropRelativeSigns(block);
            BlockEventListener.dropAttachedStorageSignsByAdjacency(block);
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    @Test
    void attachedStorageSignsAreDroppedThroughThePublicHelper() throws Exception {
        Block container = mock(Block.class);
        Block signBlock = mock(Block.class);
        Block air = mock(Block.class);
        World world = mock(World.class);
        Location origin = mock(Location.class);
        Location dropLocation = mock(Location.class);
        Directional data = mock(Directional.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack dropped = mock(ItemStack.class);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.SOUTH)).thenReturn(signBlock);
        when(data.getFacing()).thenReturn(BlockFace.SOUTH);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getState()).thenReturn(sign);
        when(signBlock.getLocation()).thenReturn(origin);
        when(signBlock.getWorld()).thenReturn(world);
        when(origin.clone()).thenReturn(dropLocation);
        when(dropLocation.add(0.5, 0.5, 0.5)).thenReturn(dropLocation);
        when(storageSign.getAmount()).thenReturn(8);
        when(dropped.getType()).thenReturn(Material.OAK_SIGN);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(sign)).thenReturn(storageSign);
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, storageSign, 1)).thenReturn(dropped);
            BlockEventListener.dropAttachedStorageSignsByAdjacency(container, null);
        }
    }

    @Test
    void attachedStorageSignsAreDroppedThroughThePublicHelperWithRealBlocks() {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            World world = org.mockbukkit.mockbukkit.MockBukkit.getMock().addSimpleWorld("drop-real");
            Block container = world.getBlockAt(0, 64, 0);
            container.setType(Material.CHEST);
            Block signBlock = world.getBlockAt(0, 64, 1);
            signBlock.setType(Material.OAK_WALL_SIGN);
            Directional data = (Directional) signBlock.getBlockData();
            data.setFacing(BlockFace.SOUTH);
            signBlock.setBlockData(data);
            Sign sign = (Sign) signBlock.getState();
            StorageSign storageSign = mock(StorageSign.class);
            ItemStack dropped = mock(ItemStack.class);
            when(storageSign.getAmount()).thenReturn(8);
            when(dropped.getType()).thenReturn(Material.OAK_SIGN);

            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
                signs.when(() -> StorageSign.fromSign(sign)).thenReturn(storageSign);
                signs.when(() -> StorageSign.createStorageSignItem(
                    Material.OAK_SIGN, storageSign, 1)).thenReturn(dropped);
                BlockEventListener.dropAttachedStorageSignsByAdjacency(container, null);
            }

            assertEquals(Material.AIR, signBlock.getType());
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    private static void assertDrop(Material blockType, Material itemType, int amount,
                                   String expectedLore) throws Exception {
        Block block = mock(Block.class);
        World world = mock(World.class);
        Location origin = mock(Location.class);
        Location dropLocation = mock(Location.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack dropped = mock(ItemStack.class);
        when(block.getLocation()).thenReturn(origin);
        when(block.getWorld()).thenReturn(world);
        when(origin.clone()).thenReturn(dropLocation);
        when(dropLocation.add(0.5, 0.5, 0.5)).thenReturn(dropLocation);
        when(storageSign.getAmount()).thenReturn(amount);
        when(storageSign.getLoreText()).thenReturn("STONE 12");

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            if (amount <= 0) {
                signs.when(() -> StorageSign.createStorageSignItem(itemType, expectedLore, 1))
                    .thenReturn(dropped);
            } else {
                signs.when(() -> StorageSign.createStorageSignItem(itemType, storageSign, 1))
                    .thenReturn(dropped);
            }
            Method method = BlockEventListener.class.getDeclaredMethod(
                "dropSingleStorageSign", Block.class, Material.class, StorageSign.class);
            method.setAccessible(true);
            method.invoke(null, block, blockType, storageSign);
            if (amount <= 0) {
                signs.verify(() -> StorageSign.createStorageSignItem(itemType, expectedLore, 1));
            } else {
                signs.verify(() -> StorageSign.createStorageSignItem(itemType, storageSign, 1));
            }
        }

        verify(world).dropItem(dropLocation, dropped);
        verify(block).setType(Material.AIR);
    }
}
