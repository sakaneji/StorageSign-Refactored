package storagesign.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import org.bukkit.Location;
import storagesign.StorageSignPlugin;
import storagesign.logging.PluginLogger;

final class StorageSignIndexSupport {
    private static final long SLOW_IO_MILLIS = 250L;

    private StorageSignIndexSupport() {
    }

    static Path indexPath(StorageSignPlugin plugin) {
        return plugin.getDataFolder().toPath().resolve("storage-sign-index.bin");
    }

    static void quarantine(Path path) {
        if (!Files.exists(path)) return;
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + Instant.now().toEpochMilli()),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    static void logIoTime(PluginLogger log, String operation, int count, long millis) {
        String message = "StorageSign index " + operation + ": entries=" + count + ", millis=" + millis;
        if (millis > SLOW_IO_MILLIS) log.warning(operation, message); else log.info(operation, message);
    }

    static String normalize(String identifier) {
        return identifier.toUpperCase(java.util.Locale.ROOT);
    }

    static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    static double distanceSquared(Location origin, StorageSignPosition position) {
        double dx = position.x() + 0.5 - origin.getX();
        double dy = position.y() + 0.5 - origin.getY();
        double dz = position.z() + 0.5 - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
