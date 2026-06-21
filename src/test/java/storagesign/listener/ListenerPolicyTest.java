package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.GameMode;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;

class ListenerPolicyTest {

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
}
