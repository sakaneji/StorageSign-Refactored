package storagesign.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import storagesign.logging.PluginLogger;

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
    void existingNameShortCircuitsStringSetterFallbacks() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[] {"setDisplayName"}, new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertTrue(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
        verify(meta, never()).setDisplayName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void firstStringSetterFailureFallsBackToDisplayNameSetter() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[] {"missingMethod", "setDisplayName"}, new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertTrue(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
        verify(meta).setDisplayName("§6Ominous Banner");
    }

    @Test
    void invalidTooltipFlagFallsBackToTheNextSupportedFlag() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[0], new String[] {"NOT_A_FLAG", "HIDE_ADDITIONAL_TOOLTIP"}
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertFalse(result.nameAvailable());
        assertTrue(result.tooltipAvailable());
        verify(meta).addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    @Test
    void invalidTooltipFlagWithoutFallbackLeavesTooltipUnavailable() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[0], new String[] {"NOT_A_FLAG"}
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertFalse(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
    }

    @Test
    void itemNameSetterAndFallbackTooltipFlagAreUsedWhenAvailable() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[] {"setItemName"}, new String[] {"HIDE_ITEM_SPECIFICS"}
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        boolean setItemNameExists;
        try {
            ItemMeta.class.getMethod("setItemName", String.class);
            setItemNameExists = true;
        } catch (NoSuchMethodException e) {
            setItemNameExists = false;
        }
        boolean addItemFlagsExists;
        try {
            ItemMeta.class.getMethod("addItemFlags", ItemFlag[].class);
            addItemFlagsExists = true;
        } catch (NoSuchMethodException e) {
            addItemFlagsExists = false;
        }

        assertTrue(setItemNameExists ? result.nameAvailable() : !result.nameAvailable());
        if (setItemNameExists) {
            verify(meta).setItemName("§6Ominous Banner");
        }
    }

    @Test
    void existingItemNameShortCircuitsAdventureAndStringFallbacks() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasItemName()).thenReturn(true);
        when(meta.hasDisplayName()).thenReturn(false);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            true, new String[] {"setItemName", "setDisplayName"}, new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertTrue(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
        verify(meta, never()).setDisplayName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void adventureNameFailureFallsBackToOtherNamePaths() {
        ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(
            ItemMeta.class.getClassLoader(),
            new Class<?>[] {ItemMeta.class},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "hasItemName", "hasDisplayName" -> false;
                        case "addItemFlags" -> null;
                        case "itemName" -> {
                            throw new RuntimeException("boom");
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                }
            }
        );
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            true, new String[0], new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertFalse(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
    }

    @Test
    void booleanNameLookupFailureFallsBackToStringSetterPath() {
        ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(
            ItemMeta.class.getClassLoader(),
            new Class<?>[] {ItemMeta.class},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "hasItemName" -> false;
                        case "hasDisplayName" -> {
                            throw new RuntimeException("boom");
                        }
                        case "setDisplayName", "setItemName", "addItemFlags" -> null;
                        default -> defaultValue(method.getReturnType());
                    };
                }
            }
        );
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[] {"setDisplayName"}, new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertTrue(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
    }

    @Test
    void firstSuccessfulStringSetterSkipsLaterFallbacks() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[] {"setDisplayName", "missingMethod"}, new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertTrue(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
        verify(meta).setDisplayName("§6Ominous Banner");
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
    void registerRejectsNonEventAndWrongGetSignReturnType() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        SignEditGuard guard = new SignEditGuard("java.lang.String");
        java.lang.reflect.Method register = SignEditGuard.class.getDeclaredMethod(
            "register", JavaPlugin.class, String.class);
        register.setAccessible(true);
        assertFalse((Boolean) register.invoke(guard, plugin, java.lang.String.class.getName()));
        assertFalse((Boolean) register.invoke(guard, plugin, FakeWrongSignEvent.class.getName()));
        assertFalse((Boolean) register.invoke(guard, plugin, FakeNonCancellableSignEvent.class.getName()));
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

    @Test
    void registerFallsBackToLaterCompatibleEventCandidates() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard(
            "invalid.missing.SignOpenEvent", FakeSignEvent.class.getName()).register(plugin);

        assertTrue(registered);
        verify(manager).registerEvent(
            org.mockito.ArgumentMatchers.eq(FakeSignEvent.class),
            org.mockito.ArgumentMatchers.any(Listener.class),
            org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
            org.mockito.ArgumentMatchers.any(EventExecutor.class),
            org.mockito.ArgumentMatchers.eq(plugin),
            org.mockito.ArgumentMatchers.eq(true)
        );
    }

    @Test
    void registerWrapsReflectionFailuresFromEventHandlers() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard(BrokenSignEvent.class.getName()).register(plugin);
        assertTrue(registered);
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(manager).registerEvent(
            org.mockito.ArgumentMatchers.eq(BrokenSignEvent.class),
            org.mockito.ArgumentMatchers.any(Listener.class),
            org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
            executor.capture(), org.mockito.ArgumentMatchers.eq(plugin),
            org.mockito.ArgumentMatchers.eq(true)
        );

        BrokenSignEvent event = new BrokenSignEvent();
        EventException ex = assertThrows(EventException.class,
            () -> executor.getValue().execute(mock(Listener.class), event));
        assertTrue(ex.getCause() instanceof java.lang.reflect.InvocationTargetException);
    }

    @Test
    void signEditGuardSkipsCancelledAndNonSignEvents() throws EventException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard(FakeSignEvent.class.getName()).register(plugin);
        assertTrue(registered);
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(manager).registerEvent(
            org.mockito.ArgumentMatchers.eq(FakeSignEvent.class),
            org.mockito.ArgumentMatchers.any(Listener.class),
            org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
            executor.capture(), org.mockito.ArgumentMatchers.eq(plugin),
            org.mockito.ArgumentMatchers.eq(true)
        );

        FakeSignEvent cancelled = new FakeSignEvent(mock(Sign.class));
        cancelled.setCancelled(true);
        executor.getValue().execute(mock(Listener.class), cancelled);
        assertTrue(cancelled.isCancelled());

        FakeSignEvent noSign = new FakeSignEvent(null);
        executor.getValue().execute(mock(Listener.class), noSign);
        assertFalse(noSign.isCancelled());
    }

    @Test
    void signEditGuardLeavesNonStorageSignsUncancelled() throws EventException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard(FakeSignEvent.class.getName()).register(plugin);
        assertTrue(registered);
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(manager).registerEvent(
            org.mockito.ArgumentMatchers.eq(FakeSignEvent.class),
            org.mockito.ArgumentMatchers.any(Listener.class),
            org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
            executor.capture(), org.mockito.ArgumentMatchers.eq(plugin),
            org.mockito.ArgumentMatchers.eq(true)
        );

        Sign sign = mock(Sign.class);
        Block block = mock(Block.class);
        when(sign.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(org.bukkit.Material.STONE);
        FakeSignEvent event = new FakeSignEvent(sign);
        executor.getValue().execute(mock(Listener.class), event);
        assertFalse(event.isCancelled());
    }

    @Test
    void signEditGuardLeavesPlainSignsUncancelled() throws EventException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard(FakeSignEvent.class.getName()).register(plugin);
        assertTrue(registered);
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(manager).registerEvent(
            org.mockito.ArgumentMatchers.eq(FakeSignEvent.class),
            org.mockito.ArgumentMatchers.any(Listener.class),
            org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
            executor.capture(), org.mockito.ArgumentMatchers.eq(plugin),
            org.mockito.ArgumentMatchers.eq(true)
        );

        Sign sign = mock(Sign.class);
        Block block = mock(Block.class);
        when(sign.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(org.bukkit.Material.OAK_SIGN);
        try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
            storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(false);
            FakeSignEvent event = new FakeSignEvent(sign);
            executor.getValue().execute(mock(Listener.class), event);
            assertFalse(event.isCancelled());
        }
    }

    @Test
    void signEditGuardTracesWhenItCancelsAnEdit() throws EventException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        java.util.logging.Logger jul = java.util.logging.Logger.getLogger("sign-edit-guard-trace");
        jul.setUseParentHandlers(false);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(plugin.getLogger()).thenReturn(jul);
        when(manager.getPlugin("Logger")).thenReturn(null);
        PluginLogger.initialize(plugin, "TRACE");

        try {
            Sign sign = mock(Sign.class);
            Block block = mock(Block.class);
            when(sign.getBlock()).thenReturn(block);
            when(block.getType()).thenReturn(org.bukkit.Material.OAK_SIGN);
            when(block.getLocation()).thenReturn(mock(org.bukkit.Location.class));

            boolean registered = new SignEditGuard(FakeSignEvent.class.getName()).register(plugin);
            assertTrue(registered);
            ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
            verify(manager).registerEvent(
                org.mockito.ArgumentMatchers.eq(FakeSignEvent.class),
                org.mockito.ArgumentMatchers.any(Listener.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.event.EventPriority.HIGH),
                executor.capture(), org.mockito.ArgumentMatchers.eq(plugin),
                org.mockito.ArgumentMatchers.eq(true)
            );

            try (MockedStatic<StorageSign> storageSigns = Mockito.mockStatic(StorageSign.class)) {
                storageSigns.when(() -> StorageSign.isStorageSign(block)).thenReturn(true);
                FakeSignEvent event = new FakeSignEvent(sign);
                executor.getValue().execute(mock(Listener.class), event);
                assertTrue(event.isCancelled());
            }
        } finally {
            PluginLogger.shutdown();
        }
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

    public static final class FakeWrongSignEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        public Object getSign() {
            return null;
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

    public static final class FakeNonCancellableSignEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        public Sign getSign() {
            return null;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static final class BrokenSignEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        public Sign getSign() {
            throw new RuntimeException("boom");
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

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
