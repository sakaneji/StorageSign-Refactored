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
}
