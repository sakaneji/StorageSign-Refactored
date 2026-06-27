package storagesign;

import java.util.UUID;
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

    public static String formatWorld(World world, UUID worldId) {
        return world == null ? worldId.toString() : world.getName();
    }
}
