package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import storagesign.StorageSign;

class PlayerInteractExportPolicyTest {

    @Test
    void sneakingExportDropsSingleItemAndReducesStorageCountByOne() throws Exception {
        runExport(true, 9, 1, 8, Material.DIAMOND);
    }

    @Test
    void normalExportDropsMaxStackWhenEnoughStockExists() throws Exception {
        runExport(false, 70, 64, 6, Material.DIAMOND);
    }

    @Test
    void unregisteredOrEmptyStorageSignIsIgnoredByExport() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isUnregistered()).thenReturn(true);
        when(storageSign.getAmount()).thenReturn(0);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "exportItems", Player.class, Block.class, StorageSign.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign);

        verify(storageSign).isUnregistered();
    }

    private static void runExport(boolean sneaking, int initialAmount, int expectedDropAmount,
                                  int expectedRemaining, Material material) throws Exception {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack out = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);

        when(player.isSneaking()).thenReturn(sneaking);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(location.clone()).thenReturn(location);
        when(location.add(0, 0.5, 0)).thenReturn(location);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(location);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.getAmount()).thenReturn(initialAmount);
        when(storageSign.getContents(1)).thenReturn(out);
        when(out.getMaxStackSize()).thenReturn(64);
        when(out.clone()).thenReturn(clone);
        when(clone.getMaxStackSize()).thenReturn(64);
        when(clone.getType()).thenReturn(material);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "exportItems", Player.class, Block.class, StorageSign.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign);

        verify(out).setAmount(expectedDropAmount);
        verify(storageSign).setAmount(expectedRemaining);
        verify(world).dropItem(location, out);
        verify(storageSign).applyToSign(sign);
    }
}
