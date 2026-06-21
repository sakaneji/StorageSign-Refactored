package storagesign.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
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

    private static IndexedStorageSign entry(UUID world, int x, String identifier, int amount) {
        return new IndexedStorageSign(new StorageSignPosition(world, x, 64, 0),
            identifier, amount, 1L);
    }
}
