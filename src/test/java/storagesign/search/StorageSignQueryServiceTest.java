package storagesign.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import storagesign.index.IndexedStorageSign;
import storagesign.index.StorageSignPosition;
import storagesign.StorageSignPlugin;
import storagesign.index.StorageSignIndex;

class StorageSignQueryServiceTest {
    @Test
    void exactAndContainsMatchingAreCaseInsensitive() {
        UUID world = UUID.randomUUID();
        List<IndexedStorageSign> entries = List.of(
            entry(world, 1, "STONE", 10),
            entry(world, 2, "SUSPICIOUS_STONE", 20),
            entry(world, 3, "DIRT", 30));

        var exact = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "stone", StorageSignSearchCriteria.MatchMode.EXACT, null, null, null));
        var contains = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "stone", StorageSignSearchCriteria.MatchMode.CONTAINS, null, null, null));

        assertEquals(1, exact.entries().size());
        assertEquals(10, exact.totalAmount());
        assertEquals(2, contains.entries().size());
        assertEquals(30, contains.totalAmount());
    }

    @Test
    void worldAndAmountFiltersCombineAndTotalUsesLong() {
        UUID selected = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<IndexedStorageSign> entries = List.of(
            entry(selected, 1, "STONE", Integer.MAX_VALUE),
            entry(selected, 2, "STONE", Integer.MAX_VALUE),
            entry(other, 3, "STONE", 50));
        var result = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.EXACT, selected, 100, null));

        assertEquals(2, result.entries().size());
        assertEquals(2L * Integer.MAX_VALUE, result.totalAmount());
    }

    @Test
    void amountBoundariesAreInclusiveAndResultsHaveStableCoordinateOrder() {
        UUID laterWorld = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID earlierWorld = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<IndexedStorageSign> entries = List.of(
            new IndexedStorageSign(new StorageSignPosition(laterWorld, 0, 64, 0), "STONE", 10, 1),
            new IndexedStorageSign(new StorageSignPosition(earlierWorld, 2, 64, 0), "STONE", 20, 1),
            new IndexedStorageSign(new StorageSignPosition(earlierWorld, 1, 65, 0), "STONE", 15, 1),
            new IndexedStorageSign(new StorageSignPosition(earlierWorld, 1, 64, 0), "STONE", 14, 1));

        var result = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.EXACT, null, 10, 20));

        assertEquals(List.of(14, 15, 20, 10),
            result.entries().stream().map(IndexedStorageSign::amount).toList());
        assertEquals(59, result.totalAmount());
    }

    @Test
    void stableOrderFallsThroughToZCoordinateComparison() {
        UUID world = UUID.randomUUID();
        List<IndexedStorageSign> entries = List.of(
            new IndexedStorageSign(new StorageSignPosition(world, 1, 64, 4), "STONE", 10, 1),
            new IndexedStorageSign(new StorageSignPosition(world, 1, 64, 2), "STONE", 20, 1),
            new IndexedStorageSign(new StorageSignPosition(world, 1, 64, 3), "STONE", 30, 1));

        var result = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.EXACT, null, null, null));

        assertEquals(List.of(20, 30, 10),
            result.entries().stream().map(IndexedStorageSign::amount).toList());
    }

    @Test
    void emptyResultHasZeroTotal() {
        var result = StorageSignQueryService.filter(List.of(), new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.EXACT, null, null, null));
        assertEquals(List.of(), result.entries());
        assertEquals(0, result.totalAmount());
    }

    @Test
    void minimumAndMaximumAmountFiltersExcludeOutOfRangeEntries() {
        UUID world = UUID.randomUUID();
        List<IndexedStorageSign> entries = List.of(
            entry(world, 1, "STONE", 5),
            entry(world, 2, "STONE", 10),
            entry(world, 3, "STONE", 15));

        var filtered = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.CONTAINS, null, 6, 12));

        assertEquals(List.of(10), filtered.entries().stream().map(IndexedStorageSign::amount).toList());
        assertEquals(10, filtered.totalAmount());
    }

    @Test
    void minimumAndMaximumAmountFiltersAcceptInclusiveBoundaries() {
        UUID world = UUID.randomUUID();
        List<IndexedStorageSign> entries = List.of(
            entry(world, 1, "STONE", 6),
            entry(world, 2, "STONE", 12),
            entry(world, 3, "STONE", 13));

        var filtered = StorageSignQueryService.filter(entries, new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.CONTAINS, null, 6, 12));

        assertEquals(List.of(6, 12), filtered.entries().stream().map(IndexedStorageSign::amount).toList());
        assertEquals(18, filtered.totalAmount());
    }

    @Test
    void concurrencySlotAcquisitionNeverExceedsLimit() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        List<Thread> threads = java.util.stream.IntStream.range(0, 50)
            .mapToObj(ignored -> new Thread(() -> {
                if (StorageSignQueryService.tryAcquire(active, 3)) accepted.incrementAndGet();
            })).toList();
        threads.forEach(Thread::start);
        for (Thread thread : threads) thread.join();

        assertEquals(3, accepted.get());
        assertEquals(3, active.get());
    }

    @Test
    void tryAcquireReturnsFalseWhenLimitReached() {
        AtomicInteger active = new AtomicInteger(3);
        assertFalse(StorageSignQueryService.tryAcquire(active, 3));
    }

    @Test
    void saturatedSearchDoesNotCopyTheIndexSnapshot() throws Exception {
        StorageSignIndex index = mock(StorageSignIndex.class);
        when(index.isEnabled()).thenReturn(true);
        StorageSignQueryService service = new StorageSignQueryService(
            mock(StorageSignPlugin.class), index);
        Field activeField = StorageSignQueryService.class.getDeclaredField("active");
        activeField.setAccessible(true);
        ((AtomicInteger) activeField.get(service)).set(1);
        setMaxConcurrent(1);

        assertFalse(service.search(
            new StorageSignSearchCriteria("stone", StorageSignSearchCriteria.MatchMode.CONTAINS,
                null, null, null),
            result -> {}, error -> {}));

        verify(index, never()).snapshot();
        verify(index, never()).findByIdentifierExact(any());
        setMaxConcurrent(2);
    }

    @Test
    void snapshotFailureReleasesConcurrencySlot() throws Exception {
        StorageSignIndex index = mock(StorageSignIndex.class);
        when(index.isEnabled()).thenReturn(true);
        IllegalStateException expected = new IllegalStateException("snapshot failed");
        when(index.snapshot()).thenThrow(expected);
        StorageSignQueryService service = new StorageSignQueryService(
            mock(StorageSignPlugin.class), index);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        setMaxConcurrent(1);

        assertTrue(service.search(
            new StorageSignSearchCriteria("stone", StorageSignSearchCriteria.MatchMode.CONTAINS,
                null, null, null),
            result -> {}, failure::set));

        assertEquals(expected, failure.get());
        Field activeField = StorageSignQueryService.class.getDeclaredField("active");
        activeField.setAccessible(true);
        assertEquals(0, ((AtomicInteger) activeField.get(service)).get());
        setMaxConcurrent(2);
    }

    @Test
    void schedulingFailureReleasesConcurrencySlotAndReportsFailure() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        IllegalStateException expected = new IllegalStateException("scheduler stopped");
        when(index.isEnabled()).thenReturn(true);
        when(index.snapshot()).thenReturn(List.of());
        when(scheduler.runTaskAsynchronously(any(), org.mockito.ArgumentMatchers.<Runnable>any()))
            .thenThrow(expected);
        StorageSignQueryService service = new StorageSignQueryService(plugin, index);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        setMaxConcurrent(1);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertTrue(service.search(
                new StorageSignSearchCriteria("stone", StorageSignSearchCriteria.MatchMode.CONTAINS,
                    null, null, null),
                result -> {}, failure::set));
        }

        assertEquals(expected, failure.get());
        Field activeField = StorageSignQueryService.class.getDeclaredField("active");
        activeField.setAccessible(true);
        assertEquals(0, ((AtomicInteger) activeField.get(service)).get());
        setMaxConcurrent(2);
    }

    @Test
    void completionSchedulingFailureStillReleasesConcurrencySlot() throws Exception {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(index.isEnabled()).thenReturn(true);
        when(index.snapshot()).thenReturn(List.of());
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTaskAsynchronously(any(), org.mockito.ArgumentMatchers.<Runnable>any());
        when(scheduler.runTask(any(), org.mockito.ArgumentMatchers.<Runnable>any()))
            .thenThrow(new IllegalStateException("scheduler stopped"));
        StorageSignQueryService service = new StorageSignQueryService(plugin, index);
        setMaxConcurrent(1);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertTrue(service.search(
                new StorageSignSearchCriteria("stone", StorageSignSearchCriteria.MatchMode.CONTAINS,
                    null, null, null),
                result -> {}, error -> {}));
        }

        Field activeField = StorageSignQueryService.class.getDeclaredField("active");
        activeField.setAccessible(true);
        assertEquals(0, ((AtomicInteger) activeField.get(service)).get());
        setMaxConcurrent(2);
    }

    @Test
    void tryAcquireCanReturnFalseUnderConcurrentContention() throws Exception {
        boolean sawFalse = false;
        for (int round = 0; round < 32 && !sawFalse; round++) {
            AtomicInteger active = new AtomicInteger();
            CyclicBarrier barrier = new CyclicBarrier(8);
            try (var executor = Executors.newFixedThreadPool(8)) {
                List<Future<Boolean>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(() -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return StorageSignQueryService.tryAcquire(active, 1);
                    }))
                    .toList();
                boolean sawTrue = false;
                for (Future<Boolean> future : futures) {
                    boolean result = future.get(5, TimeUnit.SECONDS);
                    sawTrue |= result;
                    sawFalse |= !result;
                }
                assertTrue(sawTrue);
            }
        }
        assertTrue(sawFalse);
    }

    @Test
    void searchReturnsFalseWhenIndexIsDisabled() {
        StorageSignPlugin plugin = Mockito.mock(StorageSignPlugin.class);
        StorageSignIndex index = Mockito.mock(StorageSignIndex.class);
        Mockito.when(index.isEnabled()).thenReturn(false);
        StorageSignQueryService service = new StorageSignQueryService(plugin, index);

        assertFalse(service.search(new StorageSignSearchCriteria("STONE",
            StorageSignSearchCriteria.MatchMode.EXACT, null, null, null),
            result -> { throw new AssertionError("should not be called"); },
            error -> { throw new AssertionError("should not be called"); }));
    }

    @Test
    void searchSuccessUsesExactLookupAndInvokesSuccessCallback() {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        StorageSignQueryService service = new StorageSignQueryService(plugin, index);
        IndexedStorageSign exactEntry = mock(IndexedStorageSign.class);
        StorageSignPosition position = mock(StorageSignPosition.class);

        when(index.isEnabled()).thenReturn(true);
        setMaxConcurrent(1);
        when(index.findByIdentifierExact("STONE")).thenReturn(List.of(exactEntry));
        when(exactEntry.position()).thenReturn(position);
        when(position.worldId()).thenReturn(UUID.randomUUID());
        when(position.x()).thenReturn(1);
        when(position.y()).thenReturn(2);
        when(position.z()).thenReturn(3);
        when(exactEntry.identifier()).thenReturn("STONE");
        when(exactEntry.amount()).thenReturn(7);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTaskAsynchronously(any(), org.mockito.ArgumentMatchers.<Runnable>any());
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(any(), org.mockito.ArgumentMatchers.<Runnable>any());

        AtomicInteger successes = new AtomicInteger();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertEquals(true, service.search(
                new StorageSignSearchCriteria("STONE", StorageSignSearchCriteria.MatchMode.EXACT,
                    null, null, null),
                result -> {
                    assertEquals(1, result.entries().size());
                    assertEquals(7, result.totalAmount());
                    successes.incrementAndGet();
                },
                error -> { throw new AssertionError("should not fail", error); }));
        }

        assertEquals(1, successes.get());
        setMaxConcurrent(2);
    }

    @Test
    void searchSuccessUsesSnapshotForContainsLookup() {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        StorageSignQueryService service = new StorageSignQueryService(plugin, index);
        IndexedStorageSign exactEntry = mock(IndexedStorageSign.class);
        IndexedStorageSign otherEntry = mock(IndexedStorageSign.class);
        StorageSignPosition exactPosition = mock(StorageSignPosition.class);
        StorageSignPosition otherPosition = mock(StorageSignPosition.class);

        when(index.isEnabled()).thenReturn(true);
        setMaxConcurrent(1);
        when(index.snapshot()).thenReturn(List.of(exactEntry, otherEntry));
        when(exactEntry.position()).thenReturn(exactPosition);
        when(otherEntry.position()).thenReturn(otherPosition);
        when(exactPosition.worldId()).thenReturn(UUID.randomUUID());
        when(otherPosition.worldId()).thenReturn(UUID.randomUUID());
        when(exactPosition.x()).thenReturn(4);
        when(exactPosition.y()).thenReturn(5);
        when(exactPosition.z()).thenReturn(6);
        when(otherPosition.x()).thenReturn(7);
        when(otherPosition.y()).thenReturn(8);
        when(otherPosition.z()).thenReturn(9);
        when(exactEntry.identifier()).thenReturn("STONE");
        when(otherEntry.identifier()).thenReturn("SUSPICIOUS_STONE");
        when(exactEntry.amount()).thenReturn(11);
        when(otherEntry.amount()).thenReturn(13);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTaskAsynchronously(any(), org.mockito.ArgumentMatchers.<Runnable>any());
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(any(), org.mockito.ArgumentMatchers.<Runnable>any());

        AtomicInteger successes = new AtomicInteger();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertEquals(true, service.search(
                new StorageSignSearchCriteria("stone", StorageSignSearchCriteria.MatchMode.CONTAINS,
                    null, null, null),
                result -> {
                    assertEquals(2, result.entries().size());
                    assertEquals(24, result.totalAmount());
                    successes.incrementAndGet();
                },
                error -> { throw new AssertionError("should not fail", error); }));
        }

        assertEquals(1, successes.get());
        setMaxConcurrent(2);
    }

    @Test
    void searchSurfacesFailuresFromAsyncFiltering() {
        StorageSignPlugin plugin = mock(StorageSignPlugin.class);
        StorageSignIndex index = mock(StorageSignIndex.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        StorageSignQueryService service = new StorageSignQueryService(plugin, index);
        IndexedStorageSign broken = mock(IndexedStorageSign.class);
        StorageSignPosition position = mock(StorageSignPosition.class);

        when(index.isEnabled()).thenReturn(true);
        setMaxConcurrent(1);
        when(index.findByIdentifierExact("STONE")).thenReturn(List.of(broken));
        when(broken.position()).thenReturn(position);
        when(position.worldId()).thenThrow(new RuntimeException("boom"));
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return task;
        }).when(scheduler).runTaskAsynchronously(any(), org.mockito.ArgumentMatchers.<Runnable>any());
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            callback.run();
            return task;
        }).when(scheduler).runTask(any(), org.mockito.ArgumentMatchers.<Runnable>any());

        AtomicInteger failures = new AtomicInteger();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertEquals(true, service.search(
                new StorageSignSearchCriteria("STONE", StorageSignSearchCriteria.MatchMode.EXACT,
                    null, null, null),
                result -> { throw new AssertionError("should not succeed"); },
                error -> failures.incrementAndGet()));
        }

        assertEquals(1, failures.get());
        setMaxConcurrent(2);
    }

    @Test
    void searchCriteriaDefaultsMatchModeAndRejectsBlankIdentifier() {
        UUID world = UUID.randomUUID();
        StorageSignSearchCriteria criteria = new StorageSignSearchCriteria(
            "STONE", null, world, null, null);
        assertEquals(StorageSignSearchCriteria.MatchMode.EXACT, criteria.matchMode());
        assertThrows(IllegalArgumentException.class, () ->
            new StorageSignSearchCriteria(" ", StorageSignSearchCriteria.MatchMode.EXACT, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
            new StorageSignSearchCriteria(null, StorageSignSearchCriteria.MatchMode.EXACT, null, null, null));
    }

    @Test
    void indexedStorageSignRejectsInvalidConstructorInputs() {
        UUID world = UUID.randomUUID();
        StorageSignPosition position = new StorageSignPosition(world, 1, 64, 0);
        assertThrows(IllegalArgumentException.class, () ->
            new IndexedStorageSign(null, "STONE", 1, 1L));
        assertThrows(IllegalArgumentException.class, () ->
            new IndexedStorageSign(position, " ", 1, 1L));
        assertThrows(IllegalArgumentException.class, () ->
            new IndexedStorageSign(position, "", 1, 1L));
        assertThrows(IllegalArgumentException.class, () ->
            new IndexedStorageSign(position, null, 1, 1L));
        assertThrows(IllegalArgumentException.class, () ->
            new IndexedStorageSign(position, "STONE", -1, 1L));
        assertEquals(0, new IndexedStorageSign(position, "STONE", 0, 1L).amount());
    }

    private static IndexedStorageSign entry(UUID world, int x, String identifier, int amount) {
        return new IndexedStorageSign(new StorageSignPosition(world, x, 64, 0),
            identifier, amount, 1L);
    }

    private static void setMaxConcurrent(int value) {
        try {
            var field = storagesign.ConfigLoader.class.getDeclaredField("adminSearchMaxConcurrent");
            field.setAccessible(true);
            field.setInt(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
