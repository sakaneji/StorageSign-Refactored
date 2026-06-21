package storagesign.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import storagesign.index.IndexedStorageSign;
import storagesign.index.StorageSignPosition;

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
    void emptyResultHasZeroTotal() {
        var result = StorageSignQueryService.filter(List.of(), new StorageSignSearchCriteria(
            "STONE", StorageSignSearchCriteria.MatchMode.EXACT, null, null, null));
        assertEquals(List.of(), result.entries());
        assertEquals(0, result.totalAmount());
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

    private static IndexedStorageSign entry(UUID world, int x, String identifier, int amount) {
        return new IndexedStorageSign(new StorageSignPosition(world, x, 64, 0),
            identifier, amount, 1L);
    }
}
