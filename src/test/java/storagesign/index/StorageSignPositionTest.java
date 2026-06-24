package storagesign.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StorageSignPositionTest {

    @Test
    void toLocationRequiresMatchingWorldId() {
        UUID worldId = UUID.randomUUID();
        StorageSignPosition position = new StorageSignPosition(worldId, 1, 64, 2);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> position.toLocation(world));
        assertThrows(IllegalArgumentException.class, () -> position.toLocation(null));
    }

    @Test
    void toLocationRejectsWorldWithNullUid() {
        UUID worldId = UUID.randomUUID();
        StorageSignPosition position = new StorageSignPosition(worldId, 1, 64, 2);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getUID()).thenReturn(null);

        assertThrows(NullPointerException.class, () -> position.toLocation(world));
    }

    @Test
    void toLocationBuildsLocationForMatchingWorld() {
        UUID worldId = UUID.randomUUID();
        StorageSignPosition position = new StorageSignPosition(worldId, 1, 64, 2);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getUID()).thenReturn(worldId);

        Location location = position.toLocation(world);

        assertEquals(1.0, location.getX());
        assertEquals(64.0, location.getY());
        assertEquals(2.0, location.getZ());
    }
}
