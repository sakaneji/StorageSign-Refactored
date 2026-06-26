package storagesign.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.event.StorageSignUpdatedEvent;
import storagesign.logging.PluginLogger;

/** Main-thread index of last-known StorageSign contents, including unloaded chunks. */
public final class StorageSignIndex implements Listener {
    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSignIndex.class);
    private static final long SLOW_IO_MILLIS = 250L;

    private final StorageSignPlugin plugin;
    private final boolean enabled;
    private final StorageSignIndexCodec codec = new StorageSignIndexCodec();
    private final Object ioLock = new Object();
    private final AtomicLong latestSaveRequest = new AtomicLong();
    private final Map<UUID, Map<Long, Map<StorageSignPosition, IndexedStorageSign>>> entries = new HashMap<>();
    private final Map<String, Set<StorageSignPosition>> byIdentifier = new HashMap<>();
    private final Map<UUID, Long> structureRevisions = new HashMap<>();
    private final Map<UUID, Long> contentRevisions = new HashMap<>();
    private final StorageSignChunkRescanScheduler chunkRescanScheduler;
    private BukkitTask rebuildTask;
    private int rebuildRemaining;
    private volatile boolean saving;
    private volatile long lastSavedAt;
    private volatile int lastSavedCount;
    private volatile long lastFileSize;
    private volatile String loadStatus = "not loaded";
    private boolean dirty;

    public StorageSignIndex(StorageSignPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.chunkRescanScheduler = new StorageSignChunkRescanScheduler(
            plugin, () -> this.enabled, ConfigLoader::getIndexChunksPerTick,
            ConfigLoader::getIndexChunkRescanQueueCap, this::scanChunk);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isRebuilding() { return rebuildTask != null; }
    public int getRebuildRemaining() { return rebuildRemaining; }
    public boolean isSaving() { return saving; }
    public long getLastSavedAt() { return lastSavedAt; }
    public int getLastSavedCount() { return lastSavedCount; }
    public long getLastFileSize() { return lastFileSize; }
    public String getLoadStatus() { return loadStatus; }
    public int getFormatVersion() { return StorageSignIndexCodec.VERSION; }

    public int size() {
        requirePrimaryThread();
        return entries.values().stream().flatMap(world -> world.values().stream())
            .mapToInt(Map::size).sum();
    }

    public int size(World world) {
        requirePrimaryThread();
        Map<Long, Map<StorageSignPosition, IndexedStorageSign>> chunks = entries.get(world.getUID());
        return chunks == null ? 0 : chunks.values().stream().mapToInt(Map::size).sum();
    }

    public long revision(World world) {
        requirePrimaryThread();
        return world == null ? 0L : structureRevisions.getOrDefault(world.getUID(), 0L);
    }

    public long contentRevision(World world) {
        requirePrimaryThread();
        return world == null ? 0L : contentRevisions.getOrDefault(world.getUID(), 0L);
    }

    public List<IndexedStorageSign> snapshot() {
        requirePrimaryThread();
        if (!enabled) return List.of();
        return entries.values().stream().flatMap(world -> world.values().stream())
            .flatMap(chunk -> chunk.values().stream()).toList();
    }

    public List<IndexedStorageSign> findByIdentifierExact(String identifier) {
        requirePrimaryThread();
        if (!enabled || identifier == null) return List.of();
        Set<StorageSignPosition> positions = byIdentifier.get(normalize(identifier));
        if (positions == null) return List.of();
        List<IndexedStorageSign> found = new ArrayList<>(positions.size());
        for (StorageSignPosition position : positions) {
            IndexedStorageSign entry = get(position);
            if (entry != null) found.add(entry);
        }
        return List.copyOf(found);
    }

    public List<StorageSignPosition> findNearby(Location origin, double radius) {
        requirePrimaryThread();
        if (!enabled || origin == null || origin.getWorld() == null || radius <= 0) return List.of();
        World world = origin.getWorld();
        Map<Long, Map<StorageSignPosition, IndexedStorageSign>> chunks = entries.get(world.getUID());
        if (chunks == null) return List.of();
        int minX = ((int) Math.floor(origin.getX() - radius)) >> 4;
        int maxX = ((int) Math.floor(origin.getX() + radius)) >> 4;
        int minZ = ((int) Math.floor(origin.getZ() - radius)) >> 4;
        int maxZ = ((int) Math.floor(origin.getZ() + radius)) >> 4;
        double radiusSquared = radius * radius;
        List<StorageSignPosition> found = new ArrayList<>();
        List<StorageSignPosition> stale = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            Map<StorageSignPosition, IndexedStorageSign> bucket = chunks.get(StorageSignPosition.chunkKey(cx, cz));
            if (bucket == null) continue;
            for (StorageSignPosition position : List.copyOf(bucket.keySet())) {
                if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) continue;
                double dx = position.x() + 0.5 - origin.getX();
                double dy = position.y() + 0.5 - origin.getY();
                double dz = position.z() + 0.5 - origin.getZ();
                if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                if (StorageSign.fromBlock(world.getBlockAt(position.x(), position.y(), position.z())) == null) {
                    stale.add(position);
                } else found.add(position);
            }
        }
        stale.forEach(this::unregister);
        found.sort(Comparator.comparingDouble(p -> distanceSquared(origin, p)));
        return List.copyOf(found);
    }

    public void register(Block block) {
        requirePrimaryThread();
        if (!enabled || block == null) return;
        StorageSign sign = StorageSign.fromBlock(block);
        if (sign == null || sign.isUnregistered()) return;
        upsert(new StorageSignPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()),
            sign.getIdentifier(), sign.getAmount(), System.currentTimeMillis());
    }

    public void upsert(StorageSignPosition position, String identifier, int amount, long verifiedAt) {
        requirePrimaryThread();
        if (!enabled || position == null || identifier == null || identifier.isBlank()) return;
        IndexedStorageSign replacement = new IndexedStorageSign(position, identifier, Math.max(0, amount), verifiedAt);
        Map<StorageSignPosition, IndexedStorageSign> bucket = entries
            .computeIfAbsent(position.worldId(), ignored -> new HashMap<>())
            .computeIfAbsent(position.chunkKey(), ignored -> new HashMap<>());
        IndexedStorageSign previous = bucket.put(position, replacement);
        if (previous == null || !previous.identifier().equalsIgnoreCase(identifier)) {
            if (previous != null) removeSecondary(previous);
            byIdentifier.computeIfAbsent(normalize(identifier), ignored -> new HashSet<>()).add(position);
            structureRevisions.merge(position.worldId(), 1L, Long::sum);
        }
        if (previous == null || previous.amount() != amount || !previous.identifier().equals(identifier)) {
            contentRevisions.merge(position.worldId(), 1L, Long::sum);
            dirty = true;
        }
    }

    public void unregister(Block block) {
        if (block != null) unregister(new StorageSignPosition(
            block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    public void unregister(StorageSignPosition position) {
        requirePrimaryThread();
        if (!enabled || position == null) return;
        Map<Long, Map<StorageSignPosition, IndexedStorageSign>> chunks = entries.get(position.worldId());
        if (chunks == null) return;
        Map<StorageSignPosition, IndexedStorageSign> bucket = chunks.get(position.chunkKey());
        if (bucket == null) return;
        IndexedStorageSign removed = bucket.remove(position);
        if (removed == null) return;
        removeSecondary(removed);
        if (bucket.isEmpty()) chunks.remove(position.chunkKey());
        if (chunks.isEmpty()) entries.remove(position.worldId());
        structureRevisions.merge(position.worldId(), 1L, Long::sum);
        contentRevisions.merge(position.worldId(), 1L, Long::sum);
        dirty = true;
    }

    public LoadResult load() {
        requirePrimaryThread();
        if (!enabled) return new LoadResult(false, 0, "disabled");
        Path path = indexPath();
        long started = System.nanoTime();
        try {
            chunkRescanScheduler.clear();
            List<IndexedStorageSign> loaded = codec.read(path);
            clearEntries();
            for (IndexedStorageSign entry : loaded) putLoaded(entry);
            dirty = false;
            loadStatus = Files.exists(path) ? "loaded" : "new";
            if (Files.exists(path)) {
                lastSavedCount = loaded.size();
                lastFileSize = Files.size(path);
                lastSavedAt = Files.getLastModifiedTime(path).toMillis();
            }
            long millis = elapsedMillis(started);
            logIoTime("load", loaded.size(), millis);
            return new LoadResult(true, loaded.size(), loadStatus);
        } catch (IOException e) {
            loadStatus = "corrupt: " + e.getMessage();
            quarantine(path);
            clearEntries();
            LOG.warning("load", "StorageSign index could not be loaded: " + e.getMessage());
            return new LoadResult(false, 0, loadStatus);
        }
    }

    public SaveResult saveSync() {
        requirePrimaryThread();
        if (!enabled) return new SaveResult(false, 0, 0, "disabled");
        long request = latestSaveRequest.incrementAndGet();
        return writeSnapshot(snapshot(), request);
    }

    public boolean saveAsync(Consumer<SaveResult> completion) {
        requirePrimaryThread();
        if (!enabled || saving) return false;
        List<IndexedStorageSign> snapshot = snapshot();
        long request = latestSaveRequest.incrementAndGet();
        saving = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SaveResult result = writeSnapshot(snapshot, request);
            Bukkit.getScheduler().runTask(plugin, () -> {
                saving = false;
                if (completion != null) completion.accept(result);
            });
        });
        return true;
    }

    public boolean rebuild(Collection<World> worlds, Consumer<RebuildResult> completion) {
        requirePrimaryThread();
        if (!enabled || rebuildTask != null) return false;
        chunkRescanScheduler.clear();
        ArrayDeque<Chunk> queue = new ArrayDeque<>();
        for (World world : worlds) for (Chunk chunk : world.getLoadedChunks()) queue.add(chunk);
        int total = queue.size();
        int before = size();
        rebuildRemaining = total;
        if (queue.isEmpty()) {
            if (completion != null) completion.accept(new RebuildResult(0, before, size()));
            return true;
        }
        rebuildTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < ConfigLoader.getIndexChunksPerTick() && !queue.isEmpty(); i++) {
                Chunk chunk = queue.removeFirst();
                if (chunk.isLoaded()) scanChunk(chunk);
            }
            rebuildRemaining = queue.size();
            if (!queue.isEmpty()) return;
            BukkitTask completed = rebuildTask;
            rebuildTask = null;
            rebuildRemaining = 0;
            if (completed != null) completed.cancel();
            if (completion != null) completion.accept(new RebuildResult(total, before, size()));
        }, 1L, 1L);
        return true;
    }

    public void shutdown() {
        if (rebuildTask != null) rebuildTask.cancel();
        rebuildTask = null;
        rebuildRemaining = 0;
        chunkRescanScheduler.clear();
        clearEntries();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled) return;
        chunkRescanScheduler.enqueue(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { register(event.getBlockPlaced()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { unregister(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> register(event.getBlock()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().forEach(this::unregister); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().forEach(this::unregister); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStorageSignUpdated(StorageSignUpdatedEvent event) {
        Sign sign = event.getSign();
        if (!event.isRegistered()) {
            unregister(sign.getBlock());
            return;
        }
        upsert(new StorageSignPosition(sign.getWorld().getUID(), sign.getX(), sign.getY(), sign.getZ()),
            event.getIdentifier(), event.getAmount(), System.currentTimeMillis());
    }

    private void scanChunk(Chunk chunk) {
        removeChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Sign sign) register(sign.getBlock());
        }
    }

    private void removeChunk(UUID worldId, int chunkX, int chunkZ) {
        Map<Long, Map<StorageSignPosition, IndexedStorageSign>> chunks = entries.get(worldId);
        if (chunks == null) return;
        Map<StorageSignPosition, IndexedStorageSign> removed = chunks.remove(StorageSignPosition.chunkKey(chunkX, chunkZ));
        if (removed == null) return;
        removed.values().forEach(this::removeSecondary);
        if (chunks.isEmpty()) entries.remove(worldId);
        structureRevisions.merge(worldId, 1L, Long::sum);
        contentRevisions.merge(worldId, 1L, Long::sum);
        dirty = true;
    }

    private IndexedStorageSign get(StorageSignPosition position) {
        Map<Long, Map<StorageSignPosition, IndexedStorageSign>> chunks = entries.get(position.worldId());
        if (chunks == null) return null;
        Map<StorageSignPosition, IndexedStorageSign> bucket = chunks.get(position.chunkKey());
        return bucket == null ? null : bucket.get(position);
    }

    private void putLoaded(IndexedStorageSign entry) {
        IndexedStorageSign previous = entries.computeIfAbsent(entry.position().worldId(), ignored -> new HashMap<>())
            .computeIfAbsent(entry.position().chunkKey(), ignored -> new HashMap<>())
            .put(entry.position(), entry);
        if (previous != null) removeSecondary(previous);
        byIdentifier.computeIfAbsent(normalize(entry.identifier()), ignored -> new HashSet<>())
            .add(entry.position());
    }

    private void removeSecondary(IndexedStorageSign entry) {
        Set<StorageSignPosition> positions = byIdentifier.get(normalize(entry.identifier()));
        if (positions == null) return;
        positions.remove(entry.position());
        if (positions.isEmpty()) byIdentifier.remove(normalize(entry.identifier()));
    }

    private void clearEntries() {
        entries.clear();
        byIdentifier.clear();
        structureRevisions.clear();
        contentRevisions.clear();
    }

    private SaveResult writeSnapshot(List<IndexedStorageSign> snapshot, long request) {
        long started = System.nanoTime();
        synchronized (ioLock) {
            if (request < latestSaveRequest.get()) {
                return new SaveResult(false, snapshot.size(), 0, "superseded by a newer save");
            }
            try {
                long bytes = codec.writeAtomic(indexPath(), snapshot);
                long millis = elapsedMillis(started);
                lastSavedAt = System.currentTimeMillis();
                lastSavedCount = snapshot.size();
                lastFileSize = bytes;
                dirty = false;
                logIoTime("save", snapshot.size(), millis);
                return new SaveResult(true, snapshot.size(), bytes, "saved");
            } catch (IOException e) {
                LOG.warning("save", "StorageSign index could not be saved: " + e.getMessage());
                return new SaveResult(false, snapshot.size(), 0, e.getMessage());
            }
        }
    }

    private Path indexPath() {
        return plugin.getDataFolder().toPath().resolve("storage-sign-index.bin");
    }

    private void quarantine(Path path) {
        if (!Files.exists(path)) return;
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + Instant.now().toEpochMilli()),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void logIoTime(String operation, int count, long millis) {
        String message = "StorageSign index " + operation + ": entries=" + count + ", millis=" + millis;
        if (millis > SLOW_IO_MILLIS) LOG.warning(operation, message); else LOG.info(operation, message);
    }

    private static String normalize(String identifier) { return identifier.toUpperCase(Locale.ROOT); }
    private static long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000L; }
    private static double distanceSquared(Location origin, StorageSignPosition p) {
        double dx = p.x() + 0.5 - origin.getX();
        double dy = p.y() + 0.5 - origin.getY();
        double dz = p.z() + 0.5 - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException(
            "StorageSignIndex must be accessed on the server thread");
    }

    public record RebuildResult(int chunksScanned, int countBefore, int countAfter) {}
    public record LoadResult(boolean success, int count, String status) {}
    public record SaveResult(boolean success, int count, long bytes, String message) {}
}
