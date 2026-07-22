package storagesign.index;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
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
import storagesign.StorageSignFacingSupport;
import storagesign.StorageSignPlugin;
import storagesign.event.StorageSignUpdatedEvent;
import storagesign.logging.PluginLogger;

/** Main-thread index of last-known StorageSign contents, including unloaded chunks. */
public final class StorageSignIndex implements Listener {
    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSignIndex.class);
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
    private boolean savePending;
    private final List<Consumer<SaveResult>> pendingSaveCompletions = new ArrayList<>();

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
        Set<StorageSignPosition> positions = byIdentifier.get(StorageSignIndexSupport.normalize(identifier));
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
        found.sort(Comparator.comparingDouble(p -> StorageSignIndexSupport.distanceSquared(origin, p)));
        return List.copyOf(found);
    }

    public void register(Block block) {
        requirePrimaryThread();
        if (!enabled || block == null) return;
        World world = block.getWorld();
        if (world == null) return;
        StorageSignPosition position = new StorageSignPosition(
            world.getUID(), block.getX(), block.getY(), block.getZ());
        StorageSign sign = StorageSign.fromBlock(block);
        if (sign == null || sign.isUnregistered()) {
            unregister(position);
            return;
        }
        Sign state = block.getState() instanceof Sign signState ? signState : null;
        upsert(position, sign.getIdentifier(), sign.getAmount(), System.currentTimeMillis(),
            StorageSignFacingSupport.resolveFrontFacing(state));
    }

    public void upsert(StorageSignPosition position, String identifier, int amount, long verifiedAt) {
        upsert(position, identifier, amount, verifiedAt, null);
    }

    public void upsert(StorageSignPosition position, String identifier, int amount, long verifiedAt,
                       BlockFace frontFacing) {
        requirePrimaryThread();
        if (!enabled || position == null || identifier == null || identifier.isBlank()) return;
        IndexedStorageSign replacement = new IndexedStorageSign(
            position, identifier, Math.max(0, amount), verifiedAt, frontFacing);
        Map<StorageSignPosition, IndexedStorageSign> bucket = entries
            .computeIfAbsent(position.worldId(), ignored -> new HashMap<>())
            .computeIfAbsent(position.chunkKey(), ignored -> new HashMap<>());
        IndexedStorageSign previous = bucket.put(position, replacement);
        if (previous == null || !previous.identifier().equalsIgnoreCase(identifier)) {
            if (previous != null) removeSecondary(previous);
            byIdentifier.computeIfAbsent(StorageSignIndexSupport.normalize(identifier), ignored -> new HashSet<>()).add(position);
            structureRevisions.merge(position.worldId(), 1L, Long::sum);
        }
        if (previous == null || previous.amount() != replacement.amount()
            || !previous.identifier().equals(replacement.identifier())) {
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
        Path path = StorageSignIndexSupport.indexPath(plugin);
        long started = System.nanoTime();
        try {
            chunkRescanScheduler.clear();
            List<IndexedStorageSign> loaded = codec.read(path);
            clearEntries();
            for (IndexedStorageSign entry : loaded) putLoaded(entry);
            dirty = false;
            loadStatus = java.nio.file.Files.exists(path) ? "loaded" : "new";
            if (java.nio.file.Files.exists(path)) {
                lastSavedCount = loaded.size();
                lastFileSize = java.nio.file.Files.size(path);
                lastSavedAt = java.nio.file.Files.getLastModifiedTime(path).toMillis();
            }
            long millis = StorageSignIndexSupport.elapsedMillis(started);
            StorageSignIndexSupport.logIoTime(LOG, "load", loaded.size(), millis);
            return new LoadResult(true, loaded.size(), loadStatus);
        } catch (IOException e) {
            loadStatus = "corrupt: " + e.getMessage();
            StorageSignIndexSupport.quarantine(path);
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
        if (!enabled) return false;
        if (saving) {
            savePending = true;
            if (completion != null) pendingSaveCompletions.add(completion);
            return true;
        }
        return startAsyncSave(completion);
    }

    private boolean startAsyncSave(Consumer<SaveResult> completion) {
        List<IndexedStorageSign> snapshot = snapshot();
        long request = latestSaveRequest.incrementAndGet();
        saving = true;
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                SaveResult result = writeSnapshot(snapshot, request);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean runPending = savePending;
                    List<Consumer<SaveResult>> pendingCompletions = List.copyOf(pendingSaveCompletions);
                    savePending = false;
                    pendingSaveCompletions.clear();
                    saving = false;
                    if (runPending) {
                        Consumer<SaveResult> pendingCompletion = pendingResult -> pendingCompletions.forEach(
                            callback -> callback.accept(pendingResult));
                        if (!startAsyncSave(pendingCompletion)) {
                            pendingCompletion.accept(new SaveResult(false, snapshot().size(), 0,
                                "could not schedule coalesced async save"));
                        }
                    }
                    if (completion != null) completion.accept(result);
                });
            });
            return true;
        } catch (RuntimeException error) {
            saving = false;
            LOG.warning("save", "StorageSign index save could not be scheduled: " + error.getMessage());
            return false;
        }
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
        savePending = false;
        pendingSaveCompletions.clear();
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
            event.getIdentifier(), event.getAmount(), System.currentTimeMillis(),
            StorageSignFacingSupport.resolveFrontFacing(sign));
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
        byIdentifier.computeIfAbsent(StorageSignIndexSupport.normalize(entry.identifier()), ignored -> new HashSet<>())
            .add(entry.position());
    }

    private void removeSecondary(IndexedStorageSign entry) {
        Set<StorageSignPosition> positions = byIdentifier.get(StorageSignIndexSupport.normalize(entry.identifier()));
        if (positions == null) return;
        positions.remove(entry.position());
        if (positions.isEmpty()) byIdentifier.remove(StorageSignIndexSupport.normalize(entry.identifier()));
    }

    private void clearEntries() {
        entries.clear();
        byIdentifier.clear();
        structureRevisions.clear();
        contentRevisions.clear();
    }

    private void quarantine(Path path) {
        StorageSignIndexSupport.quarantine(path);
    }

    private Path indexPath() {
        return StorageSignIndexSupport.indexPath(plugin);
    }

    private void logIoTime(String operation, int count, long millis) {
        StorageSignIndexSupport.logIoTime(LOG, operation, count, millis);
    }

    private static String normalize(String identifier) {
        return StorageSignIndexSupport.normalize(identifier);
    }

    private static long elapsedMillis(long started) {
        return StorageSignIndexSupport.elapsedMillis(started);
    }

    private static double distanceSquared(Location origin, StorageSignPosition position) {
        return StorageSignIndexSupport.distanceSquared(origin, position);
    }

    private SaveResult writeSnapshot(List<IndexedStorageSign> snapshot, long request) {
        long started = System.nanoTime();
        synchronized (ioLock) {
            if (request < latestSaveRequest.get()) {
                return new SaveResult(false, snapshot.size(), 0, "superseded by a newer save");
            }
            try {
                long bytes = codec.writeAtomic(StorageSignIndexSupport.indexPath(plugin), snapshot);
                long millis = StorageSignIndexSupport.elapsedMillis(started);
                lastSavedAt = System.currentTimeMillis();
                lastSavedCount = snapshot.size();
                lastFileSize = bytes;
                dirty = false;
                StorageSignIndexSupport.logIoTime(LOG, "save", snapshot.size(), millis);
                return new SaveResult(true, snapshot.size(), bytes, "saved");
            } catch (IOException e) {
                LOG.warning("save", "StorageSign index could not be saved: " + e.getMessage());
                return new SaveResult(false, snapshot.size(), 0, e.getMessage());
            }
        }
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException(
            "StorageSignIndex must be accessed on the server thread");
    }

    public record RebuildResult(int chunksScanned, int countBefore, int countAfter) {}
    public record LoadResult(boolean success, int count, String status) {}
    public record SaveResult(boolean success, int count, long bytes, String message) {}
}
