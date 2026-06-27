package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.junit.jupiter.api.Test;
import storagesign.index.StorageSignPosition;

class StorageSignFacingSupportTest {
    @Test
    void resolveFrontFacingReadsDirectionalAndRotatableData() {
        Directional directional = mock(Directional.class);
        when(directional.getFacing()).thenReturn(BlockFace.WEST);
        Rotatable rotatable = mock(Rotatable.class);
        when(rotatable.getRotation()).thenReturn(BlockFace.NORTH_EAST);

        assertEquals(BlockFace.WEST, StorageSignFacingSupport.resolveFrontFacing((BlockData) directional));
        assertEquals(BlockFace.NORTH_EAST, StorageSignFacingSupport.resolveFrontFacing((BlockData) rotatable));
    }

    @Test
    void resolveFrontFacingAcceptsSignsAndNulls() {
        Sign sign = mock(Sign.class);
        Directional directional = mock(Directional.class);
        when(directional.getFacing()).thenReturn(BlockFace.SOUTH);
        when(sign.getBlockData()).thenReturn(directional);

        assertEquals(BlockFace.SOUTH, StorageSignFacingSupport.resolveFrontFacing(sign));
        assertNull(StorageSignFacingSupport.resolveFrontFacing((Sign) null));
        assertNull(StorageSignFacingSupport.resolveFrontFacing((BlockData) null));
    }

    @Test
    void frontPositionAndWorldFormattingFollowExpectedFallbacks() {
        StorageSignPosition position = new StorageSignPosition(UUID.randomUUID(), 10, 64, 20);
        assertEquals(new StorageSignPosition(position.worldId(), 10, 64, 19),
            StorageSignFacingSupport.frontPosition(position, BlockFace.NORTH));
        assertEquals(new StorageSignPosition(position.worldId(), 11, 64, 19),
            StorageSignFacingSupport.frontPosition(position, BlockFace.NORTH_EAST));
        assertNull(StorageSignFacingSupport.frontPosition(position, null));

        World world = mock(World.class);
        when(world.getName()).thenReturn("test-world");
        assertEquals("test-world", StorageSignFacingSupport.formatWorld(world, position.worldId()));
        assertEquals(position.worldId().toString(), StorageSignFacingSupport.formatWorld(null, position.worldId()));
    }
}
