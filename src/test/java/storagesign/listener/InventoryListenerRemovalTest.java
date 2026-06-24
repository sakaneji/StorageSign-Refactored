package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class InventoryListenerRemovalTest {

    @Test
    void zeroAndPartialRemovalAreHandledDeterministically() throws Exception {
        Inventory inventory = mock(Inventory.class);
        ItemStack requested = mock(ItemStack.class);
        when(requested.getAmount()).thenReturn(10);
        when(requested.clone()).thenReturn(requested);
        HashMap<Integer, ItemStack> leftovers = leftovers(3);
        when(inventory.removeItem(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenReturn(leftovers);

        assertEquals(7, invoke(inventory, requested));

        ItemStack zero = mock(ItemStack.class);
        when(zero.getAmount()).thenReturn(0);
        when(zero.clone()).thenReturn(zero);
        assertEquals(0, invoke(inventory, zero));
    }

    @Test
    void mutatedRequestedStackFallsBackToReportedRemovedAmount() throws Exception {
        Inventory inventory = mock(Inventory.class);
        ItemStack requested = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(requested.getAmount()).thenReturn(10);
        when(requested.clone()).thenReturn(clone);
        when(clone.getAmount()).thenReturn(3);
        when(inventory.removeItem(org.mockito.ArgumentMatchers.any(ItemStack.class)))
            .thenReturn(new HashMap<>());

        assertEquals(7, invoke(inventory, requested));
    }

    private static int invoke(Inventory inventory, ItemStack requested) throws Exception {
        Method method = InventoryListener.class.getDeclaredMethod(
            "removeMatchingAmount", Inventory.class, ItemStack.class
        );
        method.setAccessible(true);
        return (int) method.invoke(null, inventory, requested);
    }

    private static HashMap<Integer, ItemStack> leftovers(int amount) {
        HashMap<Integer, ItemStack> map = new HashMap<>();
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(amount);
        map.put(0, stack);
        return map;
    }
}
