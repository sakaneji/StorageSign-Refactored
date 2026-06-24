package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.index.StorageSignIndex;
import storagesign.logging.PluginLogger;

class EntityListenerTest {

    @BeforeEach
    void enableTraceLogging() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("EntityListenerTest.trace");
        jul.setUseParentHandlers(false);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(jul);
        PluginLogger.initialize(plugin, "TRACE");
    }

    @AfterEach
    void restoreFlags() throws Exception {
        PluginLogger.shutdown();
        setFlag("autocollect", true);
        setFlag("fallingBlockItemSS", false);
    }

    @Test
    void usesOffHandWhenMainHandCannotCollect() throws Exception {
        setFlag("autocollect", true);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item entityItem = mock(Item.class);
        ItemStack main = mock(ItemStack.class);
        ItemStack off = mock(ItemStack.class);
        ItemStack picked = mock(ItemStack.class);
        ItemStack updated = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getEntityType()).thenReturn(EntityType.PLAYER);
        when(event.getEntity()).thenReturn(player);
        when(player.hasPermission("storagesign.autocollect")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(main);
        when(inventory.getItemInOffHand()).thenReturn(off);
        when(event.getItem()).thenReturn(entityItem);
        when(entityItem.getItemStack()).thenReturn(picked);
        when(off.getAmount()).thenReturn(1);
        when(picked.getAmount()).thenReturn(8);
        when(picked.getMaxStackSize()).thenReturn(64);
        when(inventory.containsAtLeast(picked, 64)).thenReturn(true);
        when(ss.isUnregistered()).thenReturn(false);
        when(ss.isSimilar(picked)).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(main)).thenReturn(null);
            signs.when(() -> StorageSign.fromItemStack(off)).thenReturn(ss);
            signs.when(() -> StorageSign.createStorageSignItem(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(StorageSign.class),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(updated);
            new EntityListener().onPlayerPickupItem(event);
        }

        verify(event).setCancelled(true);
        verify(entityItem).remove();
        verify(inventory).setItemInOffHand(updated);
    }

    @Test
    void autocollectNearMaximumLeavesExcessInDroppedEntity() throws Exception {
        setFlag("autocollect", true);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item entityItem = mock(Item.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack picked = mock(ItemStack.class);
        ItemStack remaining = mock(ItemStack.class);
        ItemStack updated = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getEntityType()).thenReturn(EntityType.PLAYER);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(entityItem);
        when(entityItem.getItemStack()).thenReturn(picked);
        when(player.hasPermission("storagesign.autocollect")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getAmount()).thenReturn(1);
        when(picked.getAmount()).thenReturn(8);
        when(picked.getMaxStackSize()).thenReturn(64);
        when(picked.clone()).thenReturn(remaining);
        when(inventory.containsAtLeast(picked, 64)).thenReturn(true);
        when(ss.isUnregistered()).thenReturn(false);
        when(ss.isSimilar(picked)).thenReturn(true);
        when(ss.getAmount()).thenReturn(Integer.MAX_VALUE - 3);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(ss);
            signs.when(() -> StorageSign.createStorageSignItem(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(StorageSign.class),
                org.mockito.ArgumentMatchers.eq(1))).thenReturn(updated);
            new EntityListener().onPlayerPickupItem(event);
        }

        verify(event).setCancelled(true);
        verify(remaining).setAmount(5);
        verify(entityItem).setItemStack(remaining);
        verify(entityItem, never()).remove();
        verify(ss).setAmount(Integer.MAX_VALUE);
        verify(inventory).setItemInMainHand(updated);
    }

    @Test
    void autocollectAtMaximumLeavesVanillaPickupUntouched() throws Exception {
        setFlag("autocollect", true);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item entityItem = mock(Item.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack picked = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getEntityType()).thenReturn(EntityType.PLAYER);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(entityItem);
        when(entityItem.getItemStack()).thenReturn(picked);
        when(player.hasPermission("storagesign.autocollect")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(inventory.getItemInOffHand()).thenReturn(hand);
        when(hand.getAmount()).thenReturn(1);
        when(picked.getAmount()).thenReturn(8);
        when(picked.getMaxStackSize()).thenReturn(64);
        when(inventory.containsAtLeast(picked, 64)).thenReturn(true);
        when(ss.isUnregistered()).thenReturn(false);
        when(ss.isSimilar(picked)).thenReturn(true);
        when(ss.getAmount()).thenReturn(Integer.MAX_VALUE);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(ss);
            new EntityListener().onPlayerPickupItem(event);
        }

        verify(event, never()).setCancelled(true);
        verify(entityItem, never()).remove();
        verify(ss, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void permissionDenialLeavesVanillaPickupUntouched() throws Exception {
        setFlag("autocollect", true);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Player player = mock(Player.class);
        when(event.getEntityType()).thenReturn(EntityType.PLAYER);
        when(event.getEntity()).thenReturn(player);
        when(player.hasPermission("storagesign.autocollect")).thenReturn(false);

        new EntityListener().onPlayerPickupItem(event);

        verify(event, never()).setCancelled(true);
        verify(player, never()).getInventory();
    }

    @Test
    void disabledAutocollectLeavesPlayerPickupUntouched() throws Exception {
        setFlag("autocollect", false);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntityType()).thenReturn(EntityType.PLAYER);
        new EntityListener().onPlayerPickupItem(event);
        verify(event, never()).getEntity();
        verify(event, never()).setCancelled(true);
    }

    @Test
    void stackedStorageSignAndMissingBufferStackDoNotCollect() throws Exception {
        setFlag("autocollect", true);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item entityItem = mock(Item.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack picked = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getEntityType()).thenReturn(EntityType.PLAYER);
        when(event.getEntity()).thenReturn(player);
        when(player.hasPermission("storagesign.autocollect")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(inventory.getItemInOffHand()).thenReturn(hand);
        when(event.getItem()).thenReturn(entityItem);
        when(entityItem.getItemStack()).thenReturn(picked);
        when(hand.getAmount()).thenReturn(2);
        when(ss.isUnregistered()).thenReturn(false);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(ss);
            new EntityListener().onPlayerPickupItem(event);
        }
        verify(event, never()).setCancelled(true);

        when(hand.getAmount()).thenReturn(1);
        when(picked.getMaxStackSize()).thenReturn(64);
        when(ss.isSimilar(picked)).thenReturn(true);
        when(inventory.containsAtLeast(picked, 64)).thenReturn(false);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(ss);
            new EntityListener().onPlayerPickupItem(event);
        }
        verify(event, never()).setCancelled(true);
    }

    @Test
    void autoCollectToHandReturnsNullWhenStoredItemDoesNotMatchPickedStack() throws Exception {
        setFlag("autocollect", true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack picked = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(hand.getAmount()).thenReturn(1);
        when(picked.getAmount()).thenReturn(8);
        when(picked.getMaxStackSize()).thenReturn(64);
        when(inventory.containsAtLeast(picked, 64)).thenReturn(true);
        when(ss.isUnregistered()).thenReturn(false);
        when(ss.isSimilar(picked)).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(ss);
            Method method = EntityListener.class.getDeclaredMethod(
                "autoCollectToHand", ItemStack.class, ItemStack.class, PlayerInventory.class,
                EntityPickupItemEvent.class);
            method.setAccessible(true);
            Assertions.assertNull(method.invoke(null, hand, picked, inventory, event));
        }
    }

    @Test
    void nonPlayerCannotPickUpStorageSignItem() {
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        Item entityItem = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);
        when(event.getEntityType()).thenReturn(EntityType.ZOMBIE);
        when(event.getItem()).thenReturn(entityItem);
        when(entityItem.getItemStack()).thenReturn(stack);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.isStorageSign(stack)).thenReturn(true);
            new EntityListener().onPlayerPickupItem(event);
        }
        verify(entityItem).setPickupDelay(20);
        verify(event).setCancelled(true);
    }

    @Test
    void ignoresNonFallingEntityAndDisabledFallingHandling() throws Exception {
        Block block = mock(Block.class);
        EntityChangeBlockEvent ordinary = mock(EntityChangeBlockEvent.class);
        when(ordinary.getEntity()).thenReturn(mock(Player.class));
        setFlag("fallingBlockItemSS", true);
        new EntityListener().onEntityChangeBlock(ordinary);
        verify(ordinary, never()).getBlock();

        EntityChangeBlockEvent falling = mock(EntityChangeBlockEvent.class);
        when(falling.getEntity()).thenReturn(mock(FallingBlock.class));
        setFlag("fallingBlockItemSS", false);
        new EntityListener().onEntityChangeBlock(falling);
        verify(falling, never()).getBlock();
    }

    @Test
    void enabledFallingHandlingDropsAttachedStorageSigns() throws Exception {
        setFlag("fallingBlockItemSS", true);
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        FallingBlock falling = mock(FallingBlock.class);
        Block block = mock(Block.class);
        when(event.getEntity()).thenReturn(falling);
        when(event.getBlock()).thenReturn(block);
        try (MockedStatic<BlockEventListener> blocks = Mockito.mockStatic(BlockEventListener.class)) {
            new EntityListener().onEntityChangeBlock(event);
            blocks.verify(() -> BlockEventListener.dropAttachedStorageSignsByAdjacency(block));
        }
    }

    @Test
    void enabledFallingHandlingUsesIndexedDropPathWhenIndexExists() throws Exception {
        setFlag("fallingBlockItemSS", true);
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        FallingBlock falling = mock(FallingBlock.class);
        Block block = mock(Block.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        when(event.getEntity()).thenReturn(falling);
        when(event.getBlock()).thenReturn(block);

        try (MockedStatic<BlockEventListener> blocks = Mockito.mockStatic(BlockEventListener.class)) {
            new EntityListener(index).onEntityChangeBlock(event);
            blocks.verify(() -> BlockEventListener.dropAttachedStorageSignsByAdjacency(block, index));
        }
    }

    private static void setFlag(String name, boolean value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
