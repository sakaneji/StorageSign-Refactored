package storagesign;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import storagesign.index.StorageSignPosition;

public final class StorageSignFacingSupport {
    private StorageSignFacingSupport() {
    }

    public static BlockFace resolveFrontFacing(BlockData data) {
        if (data instanceof Directional directional) {
            return directional.getFacing();
        }
        if (data instanceof Rotatable rotatable) {
            return rotatable.getRotation();
        }
        return null;
    }

    public static BlockFace resolveFrontFacing(Sign sign) {
        if (sign == null) return null;
        return resolveFrontFacing(sign.getBlockData());
    }

    public static StorageSignPosition frontPosition(StorageSignPosition position, BlockFace facing) {
        if (position == null || facing == null) return null;
        int dx = facing.getModX();
        int dy = facing.getModY();
        int dz = facing.getModZ();
        if (dx == 0 && dy == 0 && dz == 0) return null;
        return new StorageSignPosition(position.worldId(), position.x() + dx,
            position.y() + dy, position.z() + dz);
    }

    public static StorageSignPosition resolveFrontPosition(StorageSignPosition position,
                                                           BlockFace indexedFacing,
                                                           World world) {
        if (position == null) return null;
        BlockFace facing = indexedFacing;
        if (facing == null && world != null && world.getUID().equals(position.worldId())
            && world.isChunkLoaded(position.x() >> 4, position.z() >> 4)
            && world.getBlockAt(position.x(), position.y(), position.z()).getState() instanceof Sign sign) {
            facing = resolveFrontFacing(sign);
        }
        return frontPosition(position, facing);
    }

    public static Location centeredLocation(StorageSignPosition position, World world, float yaw, float pitch) {
        if (position == null || world == null || !world.getUID().equals(position.worldId())) return null;
        return new Location(world, position.x() + 0.5, position.y(), position.z() + 0.5, yaw, pitch);
    }

    public static String formatWorld(World world, UUID worldId) {
        return world == null ? worldId.toString() : world.getName();
    }
}
