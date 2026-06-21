package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;
import storagesign.ConfigLoader;

class PlayerInteractModePolicyTest {

    @Test
    void spectatorCannotOperateStorageSign() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);

        new PlayerInteractListener(null).onPlayerInteract(event);

        verify(event, never()).getClickedBlock();
        verify(player, never()).hasPermission("storagesign.use");
    }

    @Test
    void nonRightClickDoesNotMutateStorageSign() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(storageSign);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(player, never()).hasPermission("storagesign.use");
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void sneakingOffHandStorageSignDeniesPlacementWithoutOperatingBlock() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        ItemStack offHand = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isSneaking()).thenReturn(true);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getItem()).thenReturn(offHand);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.isStorageSign(offHand)).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(event).setUseItemInHand(Result.DENY);
        verify(event).setUseInteractedBlock(Result.DENY);
        verify(player, never()).hasPermission("storagesign.use");
        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void ordinaryOffHandInteractionIsIgnoredWithoutCancellingVanillaUse() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(mock(StorageSign.class));
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(event, never()).setUseItemInHand(Result.DENY);
        verify(event, never()).setUseInteractedBlock(Result.DENY);
        assertEquals(null, event.getItem());
    }

    @Test
    void deniedAirInteractionUsesTargetedStorageSign() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block target = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.useInteractedBlock()).thenReturn(Result.DENY);
        when(player.getTargetBlockExact(3)).thenReturn(target);
        when(target.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(target)).thenReturn(mock(StorageSign.class));
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(player).getTargetBlockExact(3);
        verify(event).setCancelled(true);
    }

    @Test
    void dyeForDifferentStoredItemIsDelegatedToVanillaSignUse() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack dye = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(dye);
        when(dye.getType()).thenReturn(Material.RED_DYE);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.isSimilar(dye)).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(storageSign);
            signs.when(() -> StorageSign.fromItemStack(dye)).thenReturn(null);
            config.when(ConfigLoader::getManualExport).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(event).setUseItemInHand(Result.ALLOW);
        verify(event).setUseInteractedBlock(Result.ALLOW);
        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }
}
