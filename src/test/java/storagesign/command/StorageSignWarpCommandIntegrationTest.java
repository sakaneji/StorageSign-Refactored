package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.index.StorageSignPosition;

@Tag("integration")
class StorageSignWarpCommandIntegrationTest {
    private ServerMock server;
    private StorageSignPlugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
        player = server.addPlayer();
        player.setOp(false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void playerWarpsToNearestMatchingStorageSignFront() {
        var world = server.addSimpleWorld("warp-nearest");
        player.teleport(new Location(world, 0.5, 64, 0.5, 45, 10));
        createIndexedSign(world.getBlockAt(20, 64, 0), "STONE", BlockFace.EAST);
        createIndexedSign(world.getBlockAt(4, 64, 0), "STONE", BlockFace.EAST);
        world.getBlockAt(5, 63, 0).setType(Material.STONE);

        assertTrue(server.dispatchCommand(player, "sswarp STONE"));

        Location location = player.getLocation();
        assertEquals(5.5, location.getX(), 0.0001);
        assertEquals(64.0, location.getY(), 0.0001);
        assertEquals(0.5, location.getZ(), 0.0001);
        assertTrue(world.getBlockAt(location).getType().isAir());
        assertTrue(world.getBlockAt(location).getRelative(BlockFace.UP).getType().isAir());
        assertTrue(world.getBlockAt(location).getRelative(BlockFace.DOWN).getType().isSolid());
        assertTrue(player.nextMessage().contains("ワープしました"));
    }

    @Test
    void playerWarpRejectsMissingUnsafeAndUnknownFacingTargets() {
        var world = server.addSimpleWorld("warp-errors");
        player.teleport(new Location(world, 0.5, 64, 0.5));

        assertTrue(server.dispatchCommand(player, "sswarp STONE"));
        assertTrue(player.nextMessage().contains("見つかりません"));

        plugin.getStorageSignIndex().upsert(
            new StorageSignPosition(world.getUID(), 4, 64, 0), "STONE", 1, 1);
        assertTrue(server.dispatchCommand(player, "sswarp STONE"));
        assertTrue(player.nextMessage().contains("見つかりません"));

        createIndexedSign(world.getBlockAt(4, 64, 0), "STONE", BlockFace.EAST);
        world.getBlockAt(5, 63, 0).setType(Material.STONE);
        world.getBlockAt(5, 64, 0).setType(Material.STONE);
        assertTrue(server.dispatchCommand(player, "sswarp STONE"));
        assertTrue(player.nextMessage().contains("安全ではありません"));
    }

    @Test
    void playerWarpRejectsMissingSupportAndBlockedHeadroom() {
        var world = server.addSimpleWorld("warp-safety");
        Location origin = new Location(world, 0.5, 64, 0.5);
        player.teleport(origin);
        createIndexedSign(world.getBlockAt(4, 64, 0), "STONE", BlockFace.EAST);

        assertTrue(server.dispatchCommand(player, "sswarp STONE"));
        assertTrue(player.nextMessage().contains("安全ではありません"));
        assertLocation(origin, player.getLocation());

        world.getBlockAt(5, 63, 0).setType(Material.WATER);
        assertTrue(server.dispatchCommand(player, "sswarp STONE"));
        assertTrue(player.nextMessage().contains("安全ではありません"));
        assertLocation(origin, player.getLocation());

        world.getBlockAt(5, 63, 0).setType(Material.STONE);
        world.getBlockAt(5, 65, 0).setType(Material.STONE);
        assertTrue(server.dispatchCommand(player, "sswarp STONE"));
        assertTrue(player.nextMessage().contains("安全ではありません"));
        assertLocation(origin, player.getLocation());
    }

    @Test
    void playerWarpSkipsStaleNearestIndexEntry() {
        var world = server.addSimpleWorld("warp-stale");
        player.teleport(new Location(world, 0.5, 64, 0.5));
        plugin.getStorageSignIndex().upsert(
            new StorageSignPosition(world.getUID(), 2, 64, 0), "STONE", 1, 1, BlockFace.EAST);
        createIndexedSign(world.getBlockAt(4, 64, 0), "DIRT", BlockFace.EAST);
        plugin.getStorageSignIndex().upsert(
            new StorageSignPosition(world.getUID(), 4, 64, 0), "STONE", 1, 1, BlockFace.EAST);
        createIndexedSign(world.getBlockAt(8, 64, 0), "STONE", BlockFace.EAST);
        world.getBlockAt(9, 63, 0).setType(Material.STONE);

        assertTrue(server.dispatchCommand(player, "sswarp STONE"));

        Location location = player.getLocation();
        assertEquals(9.5, location.getX(), 0.0001);
        assertEquals(64.0, location.getY(), 0.0001);
        assertEquals(0.5, location.getZ(), 0.0001);
        assertTrue(plugin.getStorageSignIndex().findByIdentifierExact("STONE").stream()
            .noneMatch(entry -> entry.position().x() == 2));
        assertTrue(plugin.getStorageSignIndex().findByIdentifierExact("STONE").stream()
            .noneMatch(entry -> entry.position().x() == 4));
        assertTrue(plugin.getStorageSignIndex().findByIdentifierExact("DIRT").stream()
            .anyMatch(entry -> entry.position().x() == 4));
    }

    private void createIndexedSign(org.bukkit.block.Block block, String identifier, BlockFace facing) {
        block.setType(Material.OAK_SIGN);
        StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, identifier, "7"})
            .applyToSign((Sign) block.getState());
        plugin.getStorageSignIndex().upsert(
            new StorageSignPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()),
            identifier, 7, 1, facing);
    }

    private void assertLocation(Location expected, Location actual) {
        assertEquals(expected.getX(), actual.getX(), 0.0001);
        assertEquals(expected.getY(), actual.getY(), 0.0001);
        assertEquals(expected.getZ(), actual.getZ(), 0.0001);
    }
}
