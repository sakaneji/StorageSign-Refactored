package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Sign;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;

class PlayerInteractRegistrationPolicyTest {

    @Test
    void airHandDoesNotAttemptRegistration() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        ItemStack hand = mock(ItemStack.class);
        when(hand.getType()).thenReturn(Material.AIR);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            invoke(player, block, hand);
            signs.verifyNoInteractions();
        }
    }

    @Test
    void unrecognizedItemDoesNotMutateTheBlock() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        ItemStack hand = mock(ItemStack.class);
        when(hand.getType()).thenReturn(Material.DIAMOND);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromStoredItem(hand)).thenReturn(null);
            invoke(player, block, hand);
            signs.verify(() -> StorageSign.fromStoredItem(hand));
        }

        verify(block, never()).getState();
    }

    @Test
    void recognizedItemWritesSignToBlock() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(hand.getType()).thenReturn(Material.DIAMOND);
        when(block.getState()).thenReturn(sign);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromStoredItem(hand)).thenReturn(storageSign);
            invoke(player, block, hand);
            signs.verify(() -> StorageSign.fromStoredItem(hand));
        }

        verify(storageSign).applyToSign(sign);
    }

    private static void invoke(Player player, Block block, ItemStack hand) throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "registerItem", Player.class, Block.class, ItemStack.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, hand);
    }
}
