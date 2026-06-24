package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.entity.Item;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.adjacency.SsAdjacencyMatch;
import storagesign.logging.PluginLogger;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

class InventoryListenerTest {

    @BeforeEach
    void enableTraceLogging() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("InventoryListenerTest.trace");
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

    @BeforeEach
    void resetConfigFlags() throws Exception {
        setConfigFlag("autoImport", true);
        setConfigFlag("autoExport", true);
        setConfigFlag("unregisterOnEmpty", true);
    }

    @Test
    void onItemMove_bothAutoFlagsDisabled_skipsWithoutError() throws Exception {
        setConfigFlag("autoImport", false);
        setConfigFlag("autoExport", false);

        InventoryListener listener = new InventoryListener(null);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(
            mock(Inventory.class),
            mock(ItemStack.class),
            mock(Inventory.class),
            true
        );

        assertDoesNotThrow(() -> listener.onItemMove(event));
    }

    @Test
    void cancelledMoveIsIgnored() {
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        when(event.isCancelled()).thenReturn(true);

        new InventoryListener(null).onItemMove(event);

        verify(event, never()).getItem();
        verify(event, never()).getSource();
        verify(event, never()).getDestination();
    }

    @Test
    void moveWithNullOrEmptyItemIsIgnored() {
        InventoryMoveItemEvent nullItem = mock(InventoryMoveItemEvent.class);
        when(nullItem.isCancelled()).thenReturn(false);
        when(nullItem.getItem()).thenReturn(null);
        new InventoryListener(null).onItemMove(nullItem);
        verify(nullItem).getItem();

        InventoryMoveItemEvent zeroItem = mock(InventoryMoveItemEvent.class);
        ItemStack item = mock(ItemStack.class);
        when(zeroItem.isCancelled()).thenReturn(false);
        when(zeroItem.getItem()).thenReturn(item);
        when(item.getAmount()).thenReturn(0);
        new InventoryListener(null).onItemMove(zeroItem);
        verify(zeroItem).getItem();
    }

    @Test
    void cancelledInventoryPickupIsIgnored() {
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        when(event.isCancelled()).thenReturn(true);

        new InventoryListener(null).onInventoryPickup(event);

        verify(event, never()).getInventory();
        verify(event, never()).getItem();
    }

    @Test
    void inventoryPickupWithNullInventoryOrItemIsIgnored() {
        InventoryPickupItemEvent nullInventory = mock(InventoryPickupItemEvent.class);
        when(nullInventory.isCancelled()).thenReturn(false);
        when(nullInventory.getInventory()).thenReturn(null);
        new InventoryListener(null).onInventoryPickup(nullInventory);

        InventoryPickupItemEvent nullItem = mock(InventoryPickupItemEvent.class);
        Inventory inventory = mock(Inventory.class);
        Item entity = mock(Item.class);
        when(nullItem.isCancelled()).thenReturn(false);
        when(nullItem.getInventory()).thenReturn(inventory);
        when(nullItem.getItem()).thenReturn(entity);
        when(entity.getItemStack()).thenReturn(null);
        new InventoryListener(null).onInventoryPickup(nullItem);
    }

    @Test
    void disabledAutoImportIgnoresInventoryPickup() throws Exception {
        setConfigFlag("autoImport", false);
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);

        new InventoryListener(null).onInventoryPickup(event);

        verify(event, never()).getInventory();
        verify(event, never()).getItem();
    }

    @Test
    void inventoryPickupWithoutFullStackDoesNotResolveAdjacentSigns() {
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        Inventory inventory = mock(Inventory.class);
        Item entity = mock(Item.class);
        ItemStack item = mock(ItemStack.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getItem()).thenReturn(entity);
        when(entity.getItemStack()).thenReturn(item);
        when(item.getAmount()).thenReturn(8);
        when(item.getMaxStackSize()).thenReturn(64);
        when(inventory.containsAtLeast(item, 64)).thenReturn(false);

        new InventoryListener(null).onInventoryPickup(event);

        verify(inventory).containsAtLeast(item, 64);
        verify(inventory, never()).getHolder();
        verify(inventory, never()).removeItem(any(ItemStack.class));
    }

    @Test
    void blockDispenseWithoutContainerOrWhenDisabledIsIgnored() throws Exception {
        setConfigFlag("autoExport", false);
        BlockDispenseEvent disabled = mock(BlockDispenseEvent.class);
        new InventoryListener(null).onBlockDispense(disabled);
        verify(disabled, never()).getBlock();

        setConfigFlag("autoExport", true);
        BlockDispenseEvent event = mock(BlockDispenseEvent.class);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(event.getBlock()).thenReturn(block);
        when(block.getState()).thenReturn(state);

        new InventoryListener(null).onBlockDispense(event);

        verify(block).getState();
        verify(event, never()).getItem();
    }

    @Test
    void blockDispenseWithNullOrEmptyItemIsIgnored() throws Exception {
        setConfigFlag("autoExport", true);
        BlockDispenseEvent nullItem = mock(BlockDispenseEvent.class);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(nullItem.isCancelled()).thenReturn(false);
        when(nullItem.getBlock()).thenReturn(block);
        when(block.getState()).thenReturn(state);
        when(nullItem.getItem()).thenReturn(null);
        new InventoryListener(null).onBlockDispense(nullItem);

        BlockDispenseEvent zeroItem = mock(BlockDispenseEvent.class);
        ItemStack item = mock(ItemStack.class);
        Container container = mock(Container.class);
        when(zeroItem.isCancelled()).thenReturn(false);
        when(zeroItem.getBlock()).thenReturn(block);
        when(block.getState()).thenReturn(container);
        when(zeroItem.getItem()).thenReturn(item);
        when(item.getAmount()).thenReturn(0);
        new InventoryListener(null).onBlockDispense(zeroItem);
    }

    @Test
    void blockDispenseSchedulesExportForAdjacentStorageSign() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("dispense-export");
            world.getChunkAt(0, 0).load();
            Block chestBlock = world.getBlockAt(0, 64, 0);
            chestBlock.setType(Material.CHEST);
            Block signBlock = world.getBlockAt(1, 64, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {
                StorageSign.HEADER_LINE, "STONE", "5"}).applyToSign(sign);

            BlockDispenseEvent event = new BlockDispenseEvent(
                chestBlock, new ItemStack(Material.STONE, 1), new Vector(0, 0, 0));
            InventoryListener listener = new InventoryListener(plugin);
            listener.onBlockDispense(event);
            server.getScheduler().performTicks(1);

            assertEquals(5, StorageSign.fromBlock(signBlock).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void disablingOneDirectionDoesNotInspectThatInventory() throws Exception {
        setConfigFlag("autoImport", false);
        setConfigFlag("autoExport", true);
        Inventory source = mock(Inventory.class);
        Inventory destination = mock(Inventory.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getAmount()).thenReturn(1);
        when(item.getMaxStackSize()).thenReturn(64);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, item, destination, true);

        new InventoryListener(null).onItemMove(event);

        verify(destination, never()).containsAtLeast(item, 64);
        verify(source).getHolder();
    }

    @Test
    void sameTickExportReservationIsDeduplicatedByStorageSignBlock() {
        InventoryListener listener = new InventoryListener(null);
        Block block = mock(Block.class);
        assertTrue(listener.reserveExport(block));
        assertFalse(listener.reserveExport(block));
    }

    @Test
    void onItemMoveAutoImportAbsorbsIntoAdjacentSignAndUpdatesInventory() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("move-auto-import");
            world.getChunkAt(0, 0).load();
            Block sourceBlock = world.getBlockAt(0, 64, 0);
            sourceBlock.setType(Material.CHEST);
            Chest sourceChest = (Chest) sourceBlock.getState();
            sourceChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));
            Block destinationBlock = world.getBlockAt(1, 64, 0);
            destinationBlock.setType(Material.CHEST);
            Chest destinationChest = (Chest) destinationBlock.getState();
            destinationChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));
            Block signBlock = world.getBlockAt(2, 64, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "5"})
                .applyToSign(sign);

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(
                sourceChest.getInventory(),
                new ItemStack(Material.STONE, 64),
                destinationChest.getInventory(),
                true);

            new InventoryListener(plugin).onItemMove(event);

            assertEquals(5, StorageSign.fromBlock(signBlock).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onItemMoveCanDriveBothAutoImportAndAutoExportThroughAdjacentSigns() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("move-both-directions");
            world.getChunkAt(0, 0).load();
            Block sourceBlock = world.getBlockAt(0, 64, 0);
            sourceBlock.setType(Material.CHEST);
            Chest sourceChest = (Chest) sourceBlock.getState();
            sourceChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            Block signBlock = world.getBlockAt(1, 64, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "5"})
                .applyToSign(sign);

            Block destinationBlock = world.getBlockAt(2, 64, 0);
            destinationBlock.setType(Material.CHEST);
            Chest destinationChest = (Chest) destinationBlock.getState();
            destinationChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(
                sourceChest.getInventory(),
                new ItemStack(Material.STONE, 64),
                destinationChest.getInventory(),
                true);

            InventoryListener listener = new InventoryListener(plugin);
            listener.onItemMove(event);
            server.getScheduler().performTicks(1);

            assertEquals(5, StorageSign.fromBlock(signBlock).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onItemMoveAutoImportUsesRealAdjacencyResolution() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("move-auto-import-real");
            world.getChunkAt(0, 0).load();
            Block sourceBlock = world.getBlockAt(0, 64, 0);
            sourceBlock.setType(Material.CHEST);
            Chest sourceChest = (Chest) sourceBlock.getState();
            sourceChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            Block destinationBlock = world.getBlockAt(1, 64, 0);
            destinationBlock.setType(Material.CHEST);
            Chest destinationChest = (Chest) destinationBlock.getState();
            destinationChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            Block signBlock = world.getBlockAt(1, 65, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "5"})
                .applyToSign(sign);

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(
                sourceChest.getInventory(),
                new ItemStack(Material.STONE, 64),
                destinationChest.getInventory(),
                true);

            new InventoryListener(plugin).onItemMove(event);

            assertEquals(69, StorageSign.fromBlock(signBlock).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onItemMoveAutoExportUsesRealAdjacencyResolution() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("move-auto-export-real");
            world.getChunkAt(0, 0).load();
            Block sourceBlock = world.getBlockAt(0, 64, 0);
            sourceBlock.setType(Material.CHEST);
            Chest sourceChest = (Chest) sourceBlock.getState();
            sourceChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            Block signBlock = world.getBlockAt(0, 65, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "5"})
                .applyToSign(sign);

            Block destinationBlock = world.getBlockAt(1, 64, 0);
            destinationBlock.setType(Material.CHEST);
            Chest destinationChest = (Chest) destinationBlock.getState();

            InventoryMoveItemEvent event = new InventoryMoveItemEvent(
                sourceChest.getInventory(),
                new ItemStack(Material.STONE, 1),
                destinationChest.getInventory(),
                true);

            InventoryListener listener = new InventoryListener(plugin);
            listener.onItemMove(event);
            server.getScheduler().performTicks(1);

            assertEquals(5, StorageSign.fromBlock(signBlock).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onInventoryPickupUsesRealAdjacencyResolution() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("pickup-real");
            world.getChunkAt(0, 0).load();
            Block inventoryBlock = world.getBlockAt(0, 64, 0);
            inventoryBlock.setType(Material.CHEST);
            Chest chest = (Chest) inventoryBlock.getState();
            chest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            Block signBlock = world.getBlockAt(0, 65, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "5"})
                .applyToSign(sign);

            InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
            Item entity = mock(Item.class);
            ItemStack item = new ItemStack(Material.STONE, 64);
            when(event.isCancelled()).thenReturn(false);
            when(event.getInventory()).thenReturn(chest.getInventory());
            when(event.getItem()).thenReturn(entity);
            when(entity.getItemStack()).thenReturn(item);

            new InventoryListener(plugin).onInventoryPickup(event);

            assertEquals(69, StorageSign.fromBlock(signBlock).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onBlockDispenseUsesRealAdjacencyResolution() {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            var world = server.addSimpleWorld("dispense-real");
            world.getChunkAt(0, 0).load();
            Block sourceBlock = world.getBlockAt(0, 64, 0);
            sourceBlock.setType(Material.CHEST);
            Chest sourceChest = (Chest) sourceBlock.getState();
            sourceChest.getBlockInventory().setItem(0, new ItemStack(Material.STONE, 64));

            Block signBlock = world.getBlockAt(0, 65, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "5"})
                .applyToSign(sign);

            BlockDispenseEvent event = mock(BlockDispenseEvent.class);
            when(event.isCancelled()).thenReturn(false);
            when(event.getBlock()).thenReturn(sourceBlock);
            when(event.getItem()).thenReturn(new ItemStack(Material.STONE, 1));

            new InventoryListener(plugin).onBlockDispense(event);
            server.getScheduler().performTicks(1);

            assertEquals(Material.OAK_SIGN, signBlock.getType());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onItemMoveAutoImportUsesResolvedAdjacency() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            InventoryListener listener = new InventoryListener(plugin);
            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
            Inventory source = mock(Inventory.class);
            Inventory destination = mock(Inventory.class);
            ItemStack item = new ItemStack(Material.STONE, 64);
            Block sourceBlock = mock(Block.class);
            Block destinationBlock = mock(Block.class);
            BlockState sourceState = mock(BlockState.class, org.mockito.Mockito.withSettings().extraInterfaces(InventoryHolder.class));
            BlockState destinationState = mock(BlockState.class, org.mockito.Mockito.withSettings().extraInterfaces(InventoryHolder.class));
            StorageSign storageSign = mock(StorageSign.class);
            Sign sign = mock(Sign.class);
            SsAdjacencyMatch match = new SsAdjacencyMatch(destinationBlock, sign, storageSign);
            when(event.isCancelled()).thenReturn(false);
            when(event.getItem()).thenReturn(item);
            when(event.getSource()).thenReturn(source);
            when(event.getDestination()).thenReturn(destination);
            when(destination.containsAtLeast(item, 64)).thenReturn(true);
            when(source.getHolder()).thenReturn((InventoryHolder) sourceState);
            when(destination.getHolder()).thenReturn((InventoryHolder) destinationState);
            when(sourceState.getBlock()).thenReturn(sourceBlock);
            when(destinationState.getBlock()).thenReturn(destinationBlock);
            when(storageSign.getAmount()).thenReturn(5);
            when(storageSign.isUnregistered()).thenReturn(false);

            try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
                mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(destinationBlock, item))
                    .thenReturn(java.util.Optional.of(match));
                mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(sourceBlock, item))
                    .thenReturn(java.util.Optional.empty());
                mocked.when(() -> InventoryListener.absorbAvailable(destination, item, match))
                    .thenReturn(1);
                setConfigFlag("autoImport", true);
                setConfigFlag("autoExport", false);
                listener.onItemMove(event);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onItemMoveAutoExportUsesResolvedAdjacency() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            InventoryListener listener = new InventoryListener(plugin);
            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
            Inventory source = mock(Inventory.class);
            Inventory destination = mock(Inventory.class);
            ItemStack item = new ItemStack(Material.STONE, 1);
            Block sourceBlock = mock(Block.class);
            BlockState sourceState = mock(BlockState.class, org.mockito.Mockito.withSettings().extraInterfaces(InventoryHolder.class));
            StorageSign storageSign = mock(StorageSign.class);
            Sign sign = mock(Sign.class);
            SsAdjacencyMatch match = new SsAdjacencyMatch(sourceBlock, sign, storageSign);
            when(event.isCancelled()).thenReturn(false);
            when(event.getItem()).thenReturn(item);
            when(event.getSource()).thenReturn(source);
            when(event.getDestination()).thenReturn(destination);
            when(source.getHolder()).thenReturn((InventoryHolder) sourceState);
            when(sourceState.getBlock()).thenReturn(sourceBlock);
            when(storageSign.getAmount()).thenReturn(5);
            when(storageSign.isUnregistered()).thenReturn(false);

            try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
                mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(sourceBlock, item))
                    .thenReturn(java.util.Optional.of(match));
                setConfigFlag("autoImport", false);
                setConfigFlag("autoExport", true);
                listener.onItemMove(event);
                server.getScheduler().performTicks(1);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onItemMoveNullSourceAndDestinationSkipsCleanly() throws Exception {
        setConfigFlag("autoImport", true);
        setConfigFlag("autoExport", true);
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        ItemStack item = new ItemStack(Material.STONE, 1);
        when(event.isCancelled()).thenReturn(false);
        when(event.getItem()).thenReturn(item);
        when(event.getSource()).thenReturn(null);
        when(event.getDestination()).thenReturn(null);

        assertDoesNotThrow(() -> new InventoryListener(null).onItemMove(event));
    }

    @Test
    void onItemMoveWithoutResolvedAdjacencyDoesNotMutate() throws Exception {
        setConfigFlag("autoImport", true);
        setConfigFlag("autoExport", true);
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        Inventory source = mock(Inventory.class);
        Inventory destination = mock(Inventory.class);
        ItemStack item = new ItemStack(Material.STONE, 64);
        Block sourceBlock = mock(Block.class);
        Block destinationBlock = mock(Block.class);
        BlockState sourceState = mock(BlockState.class, org.mockito.Mockito.withSettings().extraInterfaces(InventoryHolder.class));
        BlockState destinationState = mock(BlockState.class, org.mockito.Mockito.withSettings().extraInterfaces(InventoryHolder.class));
        when(event.isCancelled()).thenReturn(false);
        when(event.getItem()).thenReturn(item);
        when(event.getSource()).thenReturn(source);
        when(event.getDestination()).thenReturn(destination);
        when(source.getHolder()).thenReturn((InventoryHolder) sourceState);
        when(destination.getHolder()).thenReturn((InventoryHolder) destinationState);
        when(sourceState.getBlock()).thenReturn(sourceBlock);
        when(destinationState.getBlock()).thenReturn(destinationBlock);
        when(destination.containsAtLeast(item, 64)).thenReturn(true);

        try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
            mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(destinationBlock, item))
                .thenReturn(java.util.Optional.empty());
            mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(sourceBlock, item))
                .thenReturn(java.util.Optional.empty());

            assertDoesNotThrow(() -> new InventoryListener(null).onItemMove(event));
        }
    }

    @Test
    void onItemMoveAutoImportSkipsWhenDestinationIsNotFullEnough() throws Exception {
        setConfigFlag("autoImport", true);
        setConfigFlag("autoExport", false);
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        Inventory source = mock(Inventory.class);
        Inventory destination = mock(Inventory.class);
        ItemStack item = new ItemStack(Material.STONE, 64);
        when(event.isCancelled()).thenReturn(false);
        when(event.getItem()).thenReturn(item);
        when(event.getSource()).thenReturn(source);
        when(event.getDestination()).thenReturn(destination);
        when(destination.containsAtLeast(item, 64)).thenReturn(false);

        assertDoesNotThrow(() -> new InventoryListener(null).onItemMove(event));
    }

    @Test
    void onItemMoveAutoExportSkipsWhenReservationAlreadyExists() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            PluginLogger.initialize(plugin, "TRACE");
            InventoryListener listener = new InventoryListener(plugin);
            Block sourceBlock = mock(Block.class);
            StorageSign storageSign = mock(StorageSign.class);
            Sign sign = mock(Sign.class);
            SsAdjacencyMatch match = new SsAdjacencyMatch(sourceBlock, sign, storageSign);
            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
            Inventory source = mock(Inventory.class);
            ItemStack item = new ItemStack(Material.STONE, 1);
            BlockState state = mock(BlockState.class, org.mockito.Mockito.withSettings()
                .extraInterfaces(InventoryHolder.class));
            when(event.isCancelled()).thenReturn(false);
            when(event.getItem()).thenReturn(item);
            when(event.getSource()).thenReturn(source);
            when(source.getHolder()).thenReturn((InventoryHolder) state);
            when(state.getBlock()).thenReturn(sourceBlock);
            when(storageSign.getAmount()).thenReturn(5);
            when(storageSign.isUnregistered()).thenReturn(false);

            listener.reserveExport(sourceBlock);

            try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
                mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(sourceBlock, item))
                    .thenReturn(java.util.Optional.of(match));
                setConfigFlag("autoImport", false);
                setConfigFlag("autoExport", true);
                listener.onItemMove(event);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onInventoryPickupWithoutResolvedAdjacencyDoesNotAbsorb() throws Exception {
        setConfigFlag("autoImport", true);
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        Inventory inventory = mock(Inventory.class);
        Item entity = mock(Item.class);
        ItemStack item = new ItemStack(Material.STONE, 16);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class, org.mockito.Mockito.withSettings().extraInterfaces(InventoryHolder.class));
        when(event.isCancelled()).thenReturn(false);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getItem()).thenReturn(entity);
        when(entity.getItemStack()).thenReturn(item);
        when(inventory.containsAtLeast(item, 64)).thenReturn(true);
        when(inventory.getHolder()).thenReturn((InventoryHolder) state);
        when(state.getBlock()).thenReturn(block);

        try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
            mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(block, item))
                .thenReturn(java.util.Optional.empty());

            assertDoesNotThrow(() -> new InventoryListener(null).onInventoryPickup(event));
        }
    }

    @Test
    void onInventoryPickupAbsorbsIntoAdjacentSignAndUpdatesStorageSign() throws Exception {
        setConfigFlag("autoImport", true);
        InventoryPickupItemEvent event = mock(InventoryPickupItemEvent.class);
        Inventory inventory = mock(Inventory.class);
        Item entity = mock(Item.class);
        ItemStack item = new ItemStack(Material.STONE, 64);
        Block block = mock(Block.class);
        StorageSign storageSign = mock(StorageSign.class);
        Sign sign = mock(Sign.class);
        SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);
        BlockState state = mock(BlockState.class, org.mockito.Mockito.withSettings()
            .extraInterfaces(InventoryHolder.class));
        when(event.isCancelled()).thenReturn(false);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getItem()).thenReturn(entity);
        when(entity.getItemStack()).thenReturn(item);
        when(inventory.containsAtLeast(item, 64)).thenReturn(true);
        when(inventory.getHolder()).thenReturn((InventoryHolder) state);
        when(state.getBlock()).thenReturn(block);
        when(storageSign.getAmount()).thenReturn(5);
        when(storageSign.isUnregistered()).thenReturn(false);

        try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
            mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(block, item))
                .thenReturn(java.util.Optional.of(match));
            mocked.when(() -> InventoryListener.absorbAvailable(inventory, item, match))
                .thenReturn(3);

            assertDoesNotThrow(() -> new InventoryListener(null).onInventoryPickup(event));
        }
    }

    @Test
    void blockDispenseWithContainerButNoResolvedAdjacencyDoesNotSchedule() throws Exception {
        setConfigFlag("autoExport", true);
        BlockDispenseEvent event = mock(BlockDispenseEvent.class);
        Block block = mock(Block.class);
        Container state = mock(Container.class);
        ItemStack item = new ItemStack(Material.STONE, 1);
        when(event.isCancelled()).thenReturn(false);
        when(event.getBlock()).thenReturn(block);
        when(block.getState()).thenReturn(state);
        when(event.getItem()).thenReturn(item);

        try (MockedStatic<InventoryListener> mocked = org.mockito.Mockito.mockStatic(InventoryListener.class)) {
            mocked.when(() -> InventoryListener.resolveAdjacentStorageSign(block, item))
                .thenReturn(java.util.Optional.empty());

            assertDoesNotThrow(() -> new InventoryListener(null).onBlockDispense(event));
        }
    }

    @Test
    void resolveAdjacentStorageSignForInventoryRejectsUnknownHolder() throws Exception {
        Inventory inventory = mock(Inventory.class);
        InventoryHolder holder = mock(InventoryHolder.class);
        when(inventory.getHolder()).thenReturn(holder);

        Method method = InventoryListener.class.getDeclaredMethod(
            "resolveAdjacentStorageSignForInventory", Inventory.class, ItemStack.class);
        method.setAccessible(true);

        Object result = method.invoke(new InventoryListener(null), inventory, new ItemStack(Material.STONE, 1));
        assertTrue(result instanceof Optional<?>);
        assertTrue(((Optional<?>) result).isEmpty());
    }

    @Test
    void removeMatchingAmountHandlesNullsZeroAndMutableRemovalFallback() throws Exception {
        Method method = InventoryListener.class.getDeclaredMethod(
            "removeMatchingAmount", Inventory.class, ItemStack.class);
        method.setAccessible(true);

        assertEquals(0, method.invoke(null, null, null));
        assertEquals(0, method.invoke(null, mock(Inventory.class), mock(ItemStack.class)));

        Inventory inventory = mock(Inventory.class);
        ItemStack requested = mock(ItemStack.class);
        ItemStack toRemove = new ItemStack(Material.STONE, 5);
        when(requested.getAmount()).thenReturn(5);
        when(requested.clone()).thenReturn(toRemove);
        doAnswer(invocation -> {
            ItemStack item = invocation.getArgument(0);
            item.setAmount(2);
            return new HashMap<Integer, ItemStack>();
        }).when(inventory).removeItem(any(ItemStack.class));

        assertEquals(3, method.invoke(null, inventory, requested));
    }

    @Test
    @Tag("integration")
    void autoImportNearMaximumAbsorbsOnlyAvailableCapacity() {
        MockBukkit.mock();
        try {
            Inventory inventory = mock(Inventory.class);
            StorageSign storageSign = mock(StorageSign.class);
            Sign sign = mock(Sign.class);
            Block block = mock(Block.class);
            ItemStack item = new ItemStack(Material.STONE, 8);
            when(storageSign.getAmount()).thenReturn(Integer.MAX_VALUE - 3);
            when(inventory.removeItem(any(ItemStack.class))).thenReturn(new HashMap<>());
            SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

            int absorbed = InventoryListener.absorbAvailable(inventory, item, match);

            assertTrue(absorbed == 3);
            verify(storageSign).setAmount(Integer.MAX_VALUE);
            verify(storageSign).applyToSign(sign);
            ArgumentCaptor<ItemStack[]> removed = ArgumentCaptor.forClass(ItemStack[].class);
            verify(inventory).removeItem(removed.capture());
            assertTrue(removed.getValue().length == 1);
            assertTrue(removed.getValue()[0].getType() == Material.STONE);
            assertTrue(removed.getValue()[0].getAmount() == 3);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    @Tag("integration")
    void autoImportAtMaximumDoesNotRemoveInventoryItems() {
        MockBukkit.mock();
        try {
            Inventory inventory = mock(Inventory.class);
            StorageSign storageSign = mock(StorageSign.class);
            Sign sign = mock(Sign.class);
            Block block = mock(Block.class);
            ItemStack item = new ItemStack(Material.STONE, 8);
            when(storageSign.getAmount()).thenReturn(Integer.MAX_VALUE);
            SsAdjacencyMatch match = new SsAdjacencyMatch(block, sign, storageSign);

            int absorbed = InventoryListener.absorbAvailable(inventory, item, match);

            assertTrue(absorbed == 0);
            verify(inventory, never()).removeItem(any(ItemStack.class));
            verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        } finally {
            MockBukkit.unmock();
        }
    }

    private static void setConfigFlag(String fieldName, boolean value) throws Exception {
        Field f = ConfigLoader.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setBoolean(null, value);
    }
}
