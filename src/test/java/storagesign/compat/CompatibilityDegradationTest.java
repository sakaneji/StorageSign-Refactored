package storagesign.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;

class CompatibilityDegradationTest {

    @Test
    void existingNameAndCurrentTooltipFlagUseAvailableApis() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[0], new String[] {"HIDE_ADDITIONAL_TOOLTIP"}
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertTrue(result.nameAvailable());
        assertTrue(result.tooltipAvailable());
        verify(meta).addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    @Test
    void missingNameAndTooltipApisDoNotFailBannerCore() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[0], new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertFalse(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
    }

    @Test
    void missingSignEventsDisableOnlyEditGuard() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard("invalid.missing.SignOpenEvent").register(plugin);

        assertFalse(registered);
        verifyNoInteractions(manager);
    }

    @Test
    void availableSignEventRegistersAndCancelsStorageSignEditing() throws EventException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Sign sign = mock(Sign.class);
        Block block = mock(Block.class);
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(sign.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(org.bukkit.Material.OAK_SIGN);

        boolean registered = new SignEditGuard(FakeSignEvent.class.getName()).register(plugin);

        assertTrue(registered);
        verify(manager).registerEvent(
            org.mockito.ArgumentMatchers.eq(FakeSignEvent.class),
            org.mockito.ArgumentMatchers.any(Listener.class),
            org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
            executor.capture(), org.mockito.ArgumentMatchers.eq(plugin),
            org.mockito.ArgumentMatchers.eq(true)
        );
        FakeSignEvent event = new FakeSignEvent(sign);
        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);
            executor.getValue().execute(mock(Listener.class), event);
        }
        assertTrue(event.isCancelled());
    }

    public static final class FakeSignEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Sign sign;
        private boolean cancelled;

        public FakeSignEvent(Sign sign) {
            this.sign = sign;
        }

        public Sign getSign() {
            return sign;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }
}
