package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import storagesign.ConfigLoader;
import storagesign.StorageSign;

class PlayerInteractImportPolicyTest {

    @AfterEach
    void restoreManualImport() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);
    }

    @Test
    void nonSneakingImportConsumesMatchingInventorySlotAndUpdatesSign() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack matched = mock(ItemStack.class);
        ItemStack[] contents = new ItemStack[] { matched, null };

        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(contents);
        when(matched.getAmount()).thenReturn(4);
        when(storageSign.getAmount()).thenReturn(5);
        when(storageSign.isSimilar(matched)).thenReturn(true);
        when(block.getState()).thenReturn(sign);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign, matched);

        verify(storageSign).setAmount(9);
        verify(storageSign).applyToSign(sign);
        verify(player).updateInventory();
        verify(inventory).setItem(0, null);
    }

    @Test
    void sneakingImportConsumesHeldItemAndUpdatesSign() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack held = mock(ItemStack.class);

        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(0);
        when(held.getAmount()).thenReturn(4);
        when(storageSign.getAmount()).thenReturn(5);
        when(storageSign.isSimilar(held)).thenReturn(true);
        when(block.getState()).thenReturn(sign);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign, held);

        verify(storageSign).setAmount(9);
        verify(storageSign).applyToSign(sign);
        verify(player).updateInventory();
        verify(inventory).setItem(0, null);
    }

    @Test
    void sneakingImportSkipsWhenAcceptedAmountIsZero() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack held = mock(ItemStack.class);

        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(0);
        when(held.getAmount()).thenReturn(0);
        when(storageSign.getAmount()).thenReturn(5);
        when(storageSign.isSimilar(held)).thenReturn(true);
        when(block.getState()).thenReturn(sign);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign, held);

        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(storageSign, never()).applyToSign(sign);
        verify(player, never()).updateInventory();
    }

    @Test
    void nonSneakingImportSkipsWhenMatchingItemAmountIsZero() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack matched = mock(ItemStack.class);
        ItemStack[] contents = new ItemStack[] { matched };

        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(contents);
        when(matched.getAmount()).thenReturn(0);
        when(storageSign.getAmount()).thenReturn(5);
        when(storageSign.isSimilar(matched)).thenReturn(true);
        when(block.getState()).thenReturn(sign);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign, matched);

        verify(storageSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(player).updateInventory();
    }

}
