package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;

class BlockEventDropPolicyTest {

    @Test
    void zeroAmountWallSignDropsMatchingEmptyInventorySign() throws Exception {
        assertDrop(Material.OAK_WALL_SIGN, Material.OAK_SIGN, 0, StorageSign.EMPTY_MARKER);
    }

    @Test
    void wallHangingSignDropsMatchingRegisteredHangingSign() throws Exception {
        assertDrop(Material.MANGROVE_WALL_HANGING_SIGN, Material.MANGROVE_HANGING_SIGN,
            12, "STONE 12");
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
