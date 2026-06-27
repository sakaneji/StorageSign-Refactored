package storagesign.index;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import storagesign.StorageSignPlugin;

final class StorageSignChunkRescanScheduler {
    private final StorageSignPlugin plugin;
    private final BooleanSupplier enabled;
    private final IntSupplier chunksPerTick;
    private final IntSupplier queueCap;
    private final Consumer<Chunk> chunkScanner;
    private final ArrayDeque<ChunkRescanRequest> queue = new ArrayDeque<>();
    private final Set<ChunkRescanRequest> queued = new HashSet<>();
    private BukkitTask task;

    StorageSignChunkRescanScheduler(StorageSignPlugin plugin, BooleanSupplier enabled,
                                    IntSupplier chunksPerTick, IntSupplier queueCap,
                                    Consumer<Chunk> chunkScanner) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.chunksPerTick = chunksPerTick;
        this.queueCap = queueCap;
        this.chunkScanner = chunkScanner;
    }

    void clear() {
        if (task != null) task.cancel();
        task = null;
        queue.clear();
        queued.clear();
    }

    void enqueue(Chunk chunk) {
        if (!enabled.getAsBoolean() || chunk == null || !chunk.isLoaded()) return;
        ChunkRescanRequest request = new ChunkRescanRequest(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (!queued.add(request)) return;
        if (queued.size() > queueCap.getAsInt()) {
            queued.remove(request);
            return;
        }
        queue.addLast(request);
        startIfNeeded();
    }

    void process() {
        int maximum = chunksPerTick.getAsInt();
        for (int i = 0; i < maximum && !queue.isEmpty(); i++) {
            ChunkRescanRequest request = queue.removeFirst();
            queued.remove(request);
            World world = Bukkit.getWorld(request.worldId());
            if (world == null || !world.isChunkLoaded(request.chunkX(), request.chunkZ())) continue;
            chunkScanner.accept(world.getChunkAt(request.chunkX(), request.chunkZ()));
        }
        if (!queue.isEmpty()) return;
        BukkitTask completed = task;
        task = null;
        if (completed != null) completed.cancel();
    }

    int size() {
        return queue.size();
    }

    private void startIfNeeded() {
        if (task != null || queue.isEmpty()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::process, 1L, 1L);
    }

    record ChunkRescanRequest(UUID worldId, int chunkX, int chunkZ) {}
}
