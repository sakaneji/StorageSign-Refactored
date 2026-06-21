package storagesign.index;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

/** Immutable, non-chunk-loading reference to a StorageSign block. */
public record StorageSignPosition(UUID worldId, int x, int y, int z) {
    public Location toLocation(World world) {
        if (world == null || !world.getUID().equals(worldId)) {
            throw new IllegalArgumentException("World does not match indexed position");
        }
        return new Location(world, x, y, z);
    }

    public long chunkKey() {
        return chunkKey(x >> 4, z >> 4);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
