package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

import java.lang.reflect.Field;
import java.util.HashMap;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.adjacency.SsAdjacencyMatch;

class InventoryListenerTest {

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
