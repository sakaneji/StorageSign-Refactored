package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import storagesign.ConfigLoader;

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

    private static void setConfigFlag(String fieldName, boolean value) throws Exception {
        Field f = ConfigLoader.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setBoolean(null, value);
    }
}
