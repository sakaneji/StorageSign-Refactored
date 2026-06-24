package storagesign;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.compat.OminousBannerCodec;
import storagesign.index.StorageSignIndex;
import storagesign.logging.PluginLogger;
import sun.misc.Unsafe;

class StorageSignPluginBranchCoverageTest {

    @AfterEach
    void resetBannerMeta() {
        StorageSignPlugin.setOminousBannerMeta(null);
    }

    @Test
    void registerListenersRegistersAllExpectedListenersAndNoBudHelper() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, Mockito.CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(index.isEnabled()).thenReturn(true);
        setField(plugin, "storageSignIndex", index);

        try (MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            config.when(ConfigLoader::getNoBud).thenReturn(true);
            Method method = StorageSignPlugin.class.getDeclaredMethod("registerListeners");
            method.setAccessible(true);
            method.invoke(plugin);
        }

        verify(manager).registerEvents(Mockito.any(storagesign.listener.PlayerInteractListener.class),
            Mockito.eq(plugin));
        verify(manager).registerEvents(Mockito.any(storagesign.listener.BlockEventListener.class),
            Mockito.eq(plugin));
        verify(manager).registerEvents(Mockito.any(storagesign.listener.InventoryListener.class),
            Mockito.eq(plugin));
        verify(manager).registerEvents(Mockito.any(storagesign.listener.EntityListener.class),
            Mockito.eq(plugin));
        verify(manager).registerEvents(Mockito.any(storagesign.listener.CraftListener.class),
            Mockito.eq(plugin));
        verify(manager).registerEvents(Mockito.any(storagesign.listener.SignPhysicsListener.class),
            Mockito.eq(plugin));
        verify(manager).registerEvents(Mockito.eq(index), Mockito.eq(plugin));
    }

    @Test
    void registerListenersSkipsIndexAndNoBudWhenDisabled() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, Mockito.CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(index.isEnabled()).thenReturn(false);
        setField(plugin, "storageSignIndex", index);

        try (MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            config.when(ConfigLoader::getNoBud).thenReturn(false);
            Method method = StorageSignPlugin.class.getDeclaredMethod("registerListeners");
            method.setAccessible(true);
            method.invoke(plugin);
        }

        verify(manager).registerEvents(Mockito.any(storagesign.listener.PlayerInteractListener.class),
            Mockito.eq(plugin));
        verify(manager, Mockito.never()).registerEvents(Mockito.eq(index), Mockito.eq(plugin));
        verify(manager, Mockito.never())
            .registerEvents(Mockito.any(storagesign.listener.SignPhysicsListener.class),
                Mockito.eq(plugin));
    }

    @Test
    void createOminousBannerMetaByApiCoversNullAndExceptionCodecPaths() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, Mockito.CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(
            "StorageSignPluginBranchCoverageTest.create"
        );
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(julLogger);
        PluginLogger.initialize(plugin, "DEBUG");

        OminousBannerCodec codec = mock(OminousBannerCodec.class);
        Field field = StorageSignPlugin.class.getDeclaredField("OMINOUS_BANNER_CODEC");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);

        try {
            unsafe.putObject(base, offset, codec);
            when(codec.create()).thenReturn(null);
            assertNull(invokeCreate(plugin, true));

            when(codec.create()).thenThrow(new RuntimeException("boom"));
            assertNull(invokeCreate(plugin, false));
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void isOminousBannerMetaReturnsFalseWhenCodecThrows() throws Exception {
        OminousBannerCodec codec = mock(OminousBannerCodec.class);
        Field field = StorageSignPlugin.class.getDeclaredField("OMINOUS_BANNER_CODEC");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);

        try {
            unsafe.putObject(base, offset, codec);
            when(codec.matches((BannerMeta) null)).thenThrow(new RuntimeException("boom"));
            assertFalse(StorageSignPlugin.isOminousBannerMeta(null));
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void loadOminousBannerUsesFactoryResult() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, Mockito.CALLS_REAL_METHODS);
        BannerMeta meta = mock(BannerMeta.class);
        when(meta.clone()).thenReturn(meta);
        when(meta.numberOfPatterns()).thenReturn(8);

        StorageSignPlugin.setOminousBannerMeta(null);
        setField(plugin, "ominousBannerMetaFactory",
            (java.util.function.Function<Boolean, BannerMeta>) ignored -> meta);

        Method method = StorageSignPlugin.class.getDeclaredMethod("loadOminousBanner");
        method.setAccessible(true);
        method.invoke(plugin);

        assertNotNull(StorageSignPlugin.getOminousBannerMeta());
    }

    @Test
    void loadOminousBannerSchedulesRetryWhenFactoryReturnsNull() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, Mockito.CALLS_REAL_METHODS);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskLater(Mockito.eq(plugin), Mockito.any(Runnable.class), Mockito.eq(1L)))
            .thenReturn(task);
        setField(plugin, "ominousBannerMetaFactory",
            (Function<Boolean, BannerMeta>) ignored -> null);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            Method method = StorageSignPlugin.class.getDeclaredMethod("loadOminousBanner");
            method.setAccessible(true);
            method.invoke(plugin);
        }

        verify(scheduler).runTaskLater(Mockito.eq(plugin), Mockito.any(Runnable.class), Mockito.eq(1L));
    }

    @Test
    void logDegradedBannerDecorationsIsReachableWithBothFlagsFalse() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, Mockito.CALLS_REAL_METHODS);
        setField(plugin, "ominousBannerNameAvailable", false);
        setField(plugin, "ominousBannerTooltipAvailable", false);

        Method method = StorageSignPlugin.class.getDeclaredMethod("logDegradedBannerDecorations");
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = StorageSignPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static BannerMeta invokeCreate(StorageSignPlugin plugin, boolean logFailureAsWarning)
            throws Exception {
        Method method = StorageSignPlugin.class.getDeclaredMethod(
            "createOminousBannerMetaByApi", boolean.class);
        method.setAccessible(true);
        return (BannerMeta) method.invoke(plugin, logFailureAsWarning);
    }
}
