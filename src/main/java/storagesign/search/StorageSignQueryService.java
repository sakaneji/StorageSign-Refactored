package storagesign.search;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import storagesign.ConfigLoader;
import storagesign.StorageSignPlugin;
import storagesign.index.IndexedStorageSign;
import storagesign.index.StorageSignIndex;

/** Asynchronous query facade over immutable StorageSign index snapshots. */
public final class StorageSignQueryService {
    private final StorageSignPlugin plugin;
    private final StorageSignIndex index;
    private final AtomicInteger active = new AtomicInteger();

    public StorageSignQueryService(StorageSignPlugin plugin, StorageSignIndex index) {
        this.plugin = plugin;
        this.index = index;
    }

    public boolean search(StorageSignSearchCriteria criteria,
                          Consumer<StorageSignSearchResult> success,
                          Consumer<Throwable> failure) {
        if (!index.isEnabled()) return false;
        List<IndexedStorageSign> snapshot = criteria.matchMode() == StorageSignSearchCriteria.MatchMode.EXACT
            ? index.findByIdentifierExact(criteria.identifier()) : index.snapshot();
        if (!tryAcquire(active, ConfigLoader.getAdminSearchMaxConcurrent())) return false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StorageSignSearchResult result = filter(snapshot, criteria);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    active.decrementAndGet();
                    success.accept(result);
                });
            } catch (Throwable error) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    active.decrementAndGet();
                    failure.accept(error);
                });
            }
        });
        return true;
    }

    static boolean tryAcquire(AtomicInteger counter, int maximum) {
        while (true) {
            int current = counter.get();
            if (current >= maximum) return false;
            if (counter.compareAndSet(current, current + 1)) return true;
        }
    }

    static StorageSignSearchResult filter(List<IndexedStorageSign> snapshot,
                                          StorageSignSearchCriteria criteria) {
        String needle = criteria.identifier().toUpperCase(Locale.ROOT);
        List<IndexedStorageSign> matches = snapshot.stream()
            .filter(entry -> criteria.worldId() == null
                || entry.position().worldId().equals(criteria.worldId()))
            .filter(entry -> criteria.matchMode() == StorageSignSearchCriteria.MatchMode.EXACT
                ? entry.identifier().equalsIgnoreCase(criteria.identifier())
                : entry.identifier().toUpperCase(Locale.ROOT).contains(needle))
            .filter(entry -> criteria.minimumAmount() == null || entry.amount() >= criteria.minimumAmount())
            .filter(entry -> criteria.maximumAmount() == null || entry.amount() <= criteria.maximumAmount())
            .sorted(Comparator.comparing((IndexedStorageSign entry) -> entry.position().worldId())
                .thenComparingInt(entry -> entry.position().x())
                .thenComparingInt(entry -> entry.position().y())
                .thenComparingInt(entry -> entry.position().z()))
            .toList();
        return new StorageSignSearchResult(matches,
            matches.stream().mapToLong(IndexedStorageSign::amount).sum());
    }
}
