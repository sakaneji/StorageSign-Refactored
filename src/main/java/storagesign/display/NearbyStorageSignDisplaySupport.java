package storagesign.display;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import storagesign.StorageSign;
import storagesign.compat.SignDisplayFormatter;
import storagesign.index.StorageSignPosition;

final class NearbyStorageSignDisplaySupport {
    private static final int TEXT_WRAP_COLUMNS = 28;

    private NearbyStorageSignDisplaySupport() {
    }

    static boolean hasLineOfSight(Location eye, Vector direction, double distance,
                                  StorageSignPosition target) {
        World world = eye.getWorld();
        if (world == null) return false;
        RayTraceResult trace = world.rayTraceBlocks(
            eye, direction.normalize(), distance + 0.25, FluidCollisionMode.NEVER, true);
        if (trace == null || trace.getHitBlock() == null) return true;
        Block hit = trace.getHitBlock();
        return hit.getX() == target.x() && hit.getY() == target.y() && hit.getZ() == target.z();
    }

    static boolean moved(Location previous, Location current) {
        return movedPosition(previous, current) || movedView(previous, current);
    }

    static boolean movedPosition(Location previous, Location current) {
        if (previous.getWorld() != current.getWorld()) return true;
        double dx = previous.getX() - current.getX();
        double dy = previous.getY() - current.getY();
        double dz = previous.getZ() - current.getZ();
        return dx * dx + dy * dy + dz * dz > 0.0001;
    }

    static boolean movedView(Location previous, Location current) {
        if (previous.getWorld() != current.getWorld()) return true;
        return angleDifference(previous.getYaw(), current.getYaw()) > 0.5f
            || Math.abs(previous.getPitch() - current.getPitch()) > 0.5f;
    }

    static boolean isInForwardCone(Vector forward, Vector direction, double fieldOfViewDegrees) {
        if (forward.lengthSquared() == 0.0 || direction.lengthSquared() == 0.0) return false;
        double cosine = Math.cos(Math.toRadians(fieldOfViewDegrees / 2.0));
        return forward.clone().normalize().dot(direction.clone().normalize()) >= cosine;
    }

    static String labelText(StorageSign storageSign) {
        return wrap(storageSign.getIdentifier());
    }

    static boolean shouldDisplay(StorageSign storageSign) {
        if (storageSign == null || storageSign.isUnregistered()) return false;
        return !storageSign.getIdentifier().equals(SignDisplayFormatter.fit(storageSign.getIdentifier()));
    }

    static String wrap(String value) {
        StringBuilder wrapped = new StringBuilder(value.length() + 8);
        int column = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            wrapped.append(character);
            column++;
            if (column >= TEXT_WRAP_COLUMNS && i + 1 < value.length()) {
                wrapped.append('\n');
                column = 0;
            }
        }
        return wrapped.toString();
    }

    private static float angleDifference(float first, float second) {
        float difference = Math.abs(first - second) % 360.0f;
        return difference > 180.0f ? 360.0f - difference : difference;
    }
}
