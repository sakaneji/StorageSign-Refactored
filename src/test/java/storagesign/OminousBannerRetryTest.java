package storagesign;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class OminousBannerRetryTest {

    @AfterEach
    void clearCachedMeta() throws Exception {
        setCachedMeta(null);
    }

    @Test
    void schedulesOnlyOneRetryAndStopsWhenAnotherPathRecoversMeta() throws Exception {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, CALLS_REAL_METHODS);

        when(scheduler.runTaskTimer(any(Plugin.class), runnable.capture(), eq(1L), eq(100L)))
            .thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            invoke(plugin, "scheduleOminousBannerRetry");
            invoke(plugin, "scheduleOminousBannerRetry");

            verify(scheduler, times(1))
                .runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(100L));

            setCachedMeta(mock(BannerMeta.class));
            runnable.getValue().run();

            verify(task).cancel();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryCachesRecoveredMetaAndStopsTask() throws Exception {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        StorageSignPlugin plugin = mock(StorageSignPlugin.class, CALLS_REAL_METHODS);
        Function<Boolean, BannerMeta> factory = mock(Function.class);
        BannerMeta recovered = mock(BannerMeta.class);

        when(scheduler.runTaskTimer(any(Plugin.class), runnable.capture(), eq(1L), eq(100L)))
            .thenReturn(task);
        when(factory.apply(false)).thenReturn(recovered);
        when(recovered.clone()).thenReturn(recovered);
        when(recovered.hasItemName()).thenReturn(true);
        setFactory(plugin, factory);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            invoke(plugin, "scheduleOminousBannerRetry");
            runnable.getValue().run();

            assertSame(recovered, getCachedMeta());
            verify(factory).apply(false);
            verify(task).cancel();
        }
    }

    private static void invoke(StorageSignPlugin plugin, String methodName) throws Exception {
        Method method = StorageSignPlugin.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private static void setCachedMeta(BannerMeta meta) throws Exception {
        Field field = StorageSignPlugin.class.getDeclaredField("ominousBannerMeta");
        field.setAccessible(true);
        field.set(null, meta);
    }

    private static BannerMeta getCachedMeta() throws Exception {
        Field field = StorageSignPlugin.class.getDeclaredField("ominousBannerMeta");
        field.setAccessible(true);
        return (BannerMeta) field.get(null);
    }

    private static void setFactory(StorageSignPlugin plugin,
                                   Function<Boolean, BannerMeta> factory) throws Exception {
        Field field = StorageSignPlugin.class.getDeclaredField("ominousBannerMetaFactory");
        field.setAccessible(true);
        field.set(plugin, factory);
    }
}
