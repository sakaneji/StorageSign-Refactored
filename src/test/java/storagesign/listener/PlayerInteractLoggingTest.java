package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.logging.PluginLogger;

class PlayerInteractLoggingTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void resetLoggingAndConfig() throws Exception {
        PluginLogger.shutdown();
        setBannerDebug(false);
        MockBukkit.unmock();
    }

    @Test
    void bannerDebugAtTraceLogsMainHandRightClickMetadataWithSource() throws Exception {
        CapturingHandler handler = initializeLogger("TRACE", "trace");
        setBannerDebug(true);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(item);
        when(item.getType()).thenReturn(Material.BLACK_BANNER);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getAsString()).thenReturn("{test:1b}");

        new PlayerInteractListener(null).onPlayerInteract(event);

        assertEquals(1, handler.records.size());
        String message = handler.records.getFirst().getMessage();
        assertTrue(message.startsWith("[PlayerInteractListener#bannerDebug] "));
        assertTrue(message.contains("item=BLACK_BANNER"));
        assertTrue(message.contains("meta={test:1b}"));
    }

    @Test
    void bannerDebugBelowTraceDoesNotReadOrSerializeItemMetadata() throws Exception {
        initializeLogger("INFO", "disabled");
        setBannerDebug(true);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        new PlayerInteractListener(null).onPlayerInteract(event);

        verify(event, never()).getItem();
    }

    @Test
    void importItemsLogsWhenNonSneakingImportSucceeds() throws Exception {
        CapturingHandler handler = initializeLogger("TRACE", "import");
        setManualImport(true);

        Player player = mock(Player.class);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack matched = new ItemStack(Material.STONE, 4);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[] { matched });
        when(block.getState()).thenReturn(sign);
        when(storageSign.getAmount()).thenReturn(5, 9);
        when(storageSign.isSimilar(matched)).thenReturn(true);
        when(storageSign.getMaterial()).thenReturn(Material.STONE);

        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class);
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, storageSign, matched);

        assertEquals(1, handler.records.size());
        assertTrue(handler.records.getFirst().getMessage().contains("imported=4"));
        assertEquals(9, storageSign.getAmount());
    }

    private static CapturingHandler initializeLogger(String level, String suffix) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        java.util.logging.Logger fallback = java.util.logging.Logger.getLogger(
            "PlayerInteractLoggingTest." + suffix
        );
        CapturingHandler handler = new CapturingHandler();
        fallback.setUseParentHandlers(false);
        fallback.addHandler(handler);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(fallback);
        PluginLogger.initialize(plugin, level);
        return handler;
    }

    private static void setBannerDebug(boolean value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("bannerDebug");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private static void setManualImport(boolean value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
