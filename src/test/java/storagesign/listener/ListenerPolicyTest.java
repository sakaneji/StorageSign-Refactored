package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.SignSide;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.GameMode;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;
import storagesign.logging.PluginLogger;

class ListenerPolicyTest {

    @BeforeEach
    void enableTraceLogging() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("ListenerPolicyTest.trace");
        jul.setUseParentHandlers(false);
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
    void storageSignBreakWithoutPermissionIsCancelled() {
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        when(player.hasPermission("storagesign.break")).thenReturn(false);

        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);

            new BlockEventListener(null).onBlockBreak(event);
        }

        assertTrue(event.isCancelled());
        verify(player).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void ordinaryBlockBreakDoesNotApplyStorageSignPermissionPolicy() {
        Block block = mock(Block.class);
        Block neighbor = mock(Block.class);
        Player player = mock(Player.class);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        when(block.getType()).thenReturn(Material.STONE);
        when(block.getRelative(org.mockito.ArgumentMatchers.any(org.bukkit.block.BlockFace.class)))
            .thenReturn(neighbor);
        when(neighbor.getType()).thenReturn(Material.AIR);

        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(false);

            new BlockEventListener(null).onBlockBreak(event);
        }

        assertFalse(event.isCancelled());
        verify(player, never()).hasPermission("storagesign.break");
    }

    @Test
    void supportBreakWithoutPermissionIsCancelledBeforeAttachedStorageSignDrops() {
        MockBukkit.mock();
        try {
            var world = MockBukkit.getMock().addSimpleWorld("support-break-denied");
            Block support = world.getBlockAt(0, 64, 0);
            support.setType(Material.STONE);
            Block signBlock = world.getBlockAt(0, 65, 0);
            signBlock.setType(Material.OAK_SIGN);
            Sign sign = (Sign) signBlock.getState();
            sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
            sign.getSide(Side.FRONT).setLine(1, "STONE");
            sign.getSide(Side.FRONT).setLine(2, "12");
            sign.update();

            Player player = mock(Player.class);
            when(player.hasPermission("storagesign.break")).thenReturn(false);
            BlockBreakEvent event = new BlockBreakEvent(support, player);

            new BlockEventListener(null).onBlockBreak(event);

            assertTrue(event.isCancelled());
            assertEquals(Material.OAK_SIGN, signBlock.getType());
            verify(player).sendMessage(org.mockito.ArgumentMatchers.anyString());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void storageSignBreakWithPermissionDisablesDrops() {
        MockBukkit.mock();
        try {
            var world = MockBukkit.getMock().addSimpleWorld("break-permission");
            Block block = world.getBlockAt(0, 64, 0);
            block.setType(Material.OAK_SIGN);
            Player player = mock(Player.class);
            BlockBreakEvent event = mock(BlockBreakEvent.class);
            when(player.hasPermission("storagesign.break")).thenReturn(true);
            when(event.getBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);

            try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
                storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);

                new BlockEventListener(null).onBlockBreak(event);
            }

            assertFalse(event.isCancelled());
            verify(event).setDropItems(false);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void storageSignCraftWithoutPermissionIsCancelled() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        HumanEntity player = mock(HumanEntity.class);
        ItemStack current = mock(ItemStack.class);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getWhoClicked()).thenReturn(player);
        when(player.hasPermission("storagesign.craft")).thenReturn(false);

        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.fromItemStack(current)).thenReturn(mock(StorageSign.class));

            new CraftListener().onPlayerCraft(event);
        }

        verify(event).setCancelled(true);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void storageSignCraftWithPermissionIsNotCancelled() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        HumanEntity player = mock(HumanEntity.class);
        ItemStack current = mock(ItemStack.class);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getWhoClicked()).thenReturn(player);
        when(player.hasPermission("storagesign.craft")).thenReturn(true);

        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.fromItemStack(current)).thenReturn(mock(StorageSign.class));
            new CraftListener().onPlayerCraft(event);
        }

        verify(event, never()).setCancelled(true);
    }

    @Test
    void ordinaryCraftDoesNotApplyStorageSignPermissionPolicy() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        HumanEntity player = mock(HumanEntity.class);
        ItemStack current = mock(ItemStack.class);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getWhoClicked()).thenReturn(player);

        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.fromItemStack(current)).thenReturn(null);
            new CraftListener().onPlayerCraft(event);
        }

        verify(player, never()).hasPermission("storagesign.craft");
        verify(event, never()).setCancelled(true);
    }

    @Test
    void physicsIsCancelledOnlyForStorageSignBlocks() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        BlockPhysicsEvent event = mock(BlockPhysicsEvent.class);
        when(event.getBlock()).thenReturn(block);

        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);

            new SignPhysicsListener().onBlockPhysics(event);
        }

        verify(event).setCancelled(true);
    }

    @Test
    void physicsDoesNotCancelOrdinarySign() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        BlockPhysicsEvent event = mock(BlockPhysicsEvent.class);
        when(event.getBlock()).thenReturn(block);
        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(false);
            new SignPhysicsListener().onBlockPhysics(event);
        }
        verify(event, never()).setCancelled(true);
    }

    @Test
    void physicsIgnoresNonSignBlocks() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        BlockPhysicsEvent event = mock(BlockPhysicsEvent.class);
        when(event.getBlock()).thenReturn(block);

        new SignPhysicsListener().onBlockPhysics(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void storageSignPlacementWithoutPermissionIsCancelled() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemInHand()).thenReturn(item);
        when(player.hasPermission("storagesign.place")).thenReturn(false);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(mock(StorageSign.class));
            new BlockEventListener(null).onBlockPlace(event);
        }
        verify(event).setCancelled(true);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void storageSignPlacementRestoresSignAndClosesInventory() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemInHand()).thenReturn(item);
        when(player.hasPermission("storagesign.place")).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);
        when(block.getState()).thenReturn(sign);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(storageSign);
            new BlockEventListener(null).onBlockPlace(event);
        }

        verify(storageSign).applyToSign(sign);
        verify(player).closeInventory();
        verify(event, never()).setCancelled(true);
    }

    @Test
    void nonSignPlacementReturnsBeforeRestoration() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemInHand()).thenReturn(item);
        when(player.hasPermission("storagesign.place")).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);
        when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(mock(StorageSign.class));
            new BlockEventListener(null).onBlockPlace(event);
        }

        verify(player, never()).closeInventory();
        verify(event, never()).setCancelled(true);
    }

    @Test
    void nullStorageSignPlacementReturnsBeforeStateChecks() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemInHand()).thenReturn(item);
        when(player.hasPermission("storagesign.place")).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(null);
            new BlockEventListener(null).onBlockPlace(event);
        }

        verify(player, never()).closeInventory();
        verify(event, never()).setCancelled(true);
    }

    @Test
    void storageSignPlacementWithoutSignStateIsIgnored() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemInHand()).thenReturn(item);
        when(player.hasPermission("storagesign.place")).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);
        when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(mock(StorageSign.class));
            new BlockEventListener(null).onBlockPlace(event);
        }

        verify(event, never()).setCancelled(true);
        verify(player, never()).closeInventory();
    }

    @Test
    void darkOakSignPlacementForcesWhiteFrontColor() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        SignSide front = mock(SignSide.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemInHand()).thenReturn(item);
        when(player.hasPermission("storagesign.place")).thenReturn(true);
        when(event.getBlockPlaced()).thenReturn(block);
        when(block.getState()).thenReturn(sign);
        when(sign.getSide(Side.FRONT)).thenReturn(front);
        when(item.getType()).thenReturn(Material.DARK_OAK_SIGN);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(storageSign);
            new BlockEventListener(null).onBlockPlace(event);
        }

        verify(front).setColor(org.bukkit.DyeColor.WHITE);
        verify(sign).update();
    }

    @Test
    void survivalCannotCreateStorageSignByEditingVanillaSign() {
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getLine(0)).thenReturn("StorageSign");
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.isStorageSign(block)).thenReturn(false);
            new BlockEventListener(null).onSignChange(event);
        }
        verify(event).setCancelled(true);
    }

    @Test
    void signChangeWithoutFirstLineReturnsBeforePermissionCheck() {
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getLine(0)).thenReturn(null);
        when(event.getPlayer()).thenReturn(player);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.isStorageSign(block)).thenReturn(false);
            new BlockEventListener(null).onSignChange(event);
        }

        verify(player, never()).hasPermission("storagesign.place");
        verify(event, never()).setCancelled(true);
    }

    @Test
    void signChangeOnNonSignBlockReturnsImmediately() {
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.STONE);
        when(event.getPlayer()).thenReturn(player);

        new BlockEventListener(null).onSignChange(event);

        verify(player, never()).hasPermission("storagesign.place");
        verify(event, never()).setCancelled(true);
    }

    @Test
    void creativeCanCreateStorageSignByEditingVanillaSign() {
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getLine(0)).thenReturn("storagesign");
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.isStorageSign(block)).thenReturn(false);
            new BlockEventListener(null).onSignChange(event);
        }
        verify(event).setLine(0, StorageSign.HEADER_LINE);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void existingStorageSignWithoutSignStateIsCancelled() {
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getPlayer()).thenReturn(player);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);
            when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));
            new BlockEventListener(null).onSignChange(event);
        }

        verify(event).setCancelled(true);
    }

    @Test
    void existingStorageSignEditRestoresItsOriginalLines() {
        SignChangeEvent event = mock(SignChangeEvent.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        SignSide side = mock(SignSide.class);
        Player player = mock(Player.class);
        String[] lines = {"A", "B", "C", "D"};
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getPlayer()).thenReturn(player);
        when(block.getState()).thenReturn(sign);
        when(sign.getSide(Side.FRONT)).thenReturn(side);
        when(side.getLines()).thenReturn(lines);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);
            new BlockEventListener(null).onSignChange(event);
        }

        verify(event).setLine(0, "A");
        verify(event).setLine(1, "B");
        verify(event).setLine(2, "C");
        verify(event).setLine(3, "D");
        verify(event, never()).setCancelled(true);
    }
}
