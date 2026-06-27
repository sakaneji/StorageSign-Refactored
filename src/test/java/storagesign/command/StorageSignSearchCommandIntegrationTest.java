package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSignPlugin;
import storagesign.StorageSign;
import storagesign.index.StorageSignPosition;

@Tag("integration")
class StorageSignSearchCommandIntegrationTest {
    private ServerMock server;
    private StorageSignPlugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
        player = server.addPlayer();
        player.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void exactContainsWorldAndPaginationProduceExpectedResults() throws Exception {
        var first = server.addSimpleWorld("search-first");
        var second = server.addSimpleWorld("search-second");
        for (int x = 0; x < 11; x++) {
            plugin.getStorageSignIndex().upsert(
                new StorageSignPosition(first.getUID(), x, 64, 0), "STONE", x + 1, 1);
        }
        plugin.getStorageSignIndex().upsert(
            new StorageSignPosition(second.getUID(), 0, 64, 0), "SUSPICIOUS_STONE", 100, 1);

        assertTrue(server.dispatchCommand(player, "sssearch item STONE --world search-first --page 2"));
        assertTrue(player.nextMessage().contains("Searching"));
        assertTrue(awaitMessage("matches=11").contains("matches=11"));
        assertTrue(awaitMessage("11.").contains("11."));

        assertTrue(server.dispatchCommand(player, "sssearch item stone --contains --world search-second"));
        assertTrue(player.nextMessage().contains("Searching"));
        assertTrue(awaitMessage("matches=1").contains("matches=1"));
    }

    @Test
    void invalidOptionsAndPagesAreRejected() {
        for (String[] commandAndMessage : new String[][] {
            {"sssearch item STONE --world", "requires"},
            {"sssearch item STONE --world missing", "Unknown world"},
            {"sssearch item STONE --page nope", "Invalid page"},
            {"sssearch item STONE --page 0", "at least 1"},
            {"sssearch item STONE --bad", "Unknown option"},
        }) {
            assertTrue(server.dispatchCommand(player, commandAndMessage[0]));
            assertTrue(player.nextMessage().contains(commandAndMessage[1]));
        }
    }

    @Test
    void permissionAndOutOfRangePageAreReported() throws Exception {
        player.addAttachment(plugin, "storagesign.search.admin", false);
        assertTrue(server.dispatchCommand(player, "sssearch item STONE"));
        assertTrue(player.nextMessage().toLowerCase().contains("permission"));

        player.addAttachment(plugin, "storagesign.search.admin", true);
        assertTrue(server.dispatchCommand(player, "sssearch item STONE --page 2"));
        assertTrue(player.nextMessage().contains("Searching"));
        assertTrue(awaitMessage("does not exist").contains("does not exist"));
    }

    @Test
    void coordsAndFrontModesReturnPipeDelimitedLines() throws Exception {
        var world = server.addSimpleWorld("search-coords");
        var block = world.getBlockAt(10, 64, 20);
        block.setType(Material.OAK_WALL_SIGN);
        Directional data = (Directional) block.getBlockData();
        data.setFacing(BlockFace.SOUTH);
        block.setBlockData(data);
        StorageSign.fromSignLines(new String[] {StorageSign.HEADER_LINE, "STONE", "7"}).applyToSign((Sign) block.getState());
        plugin.getStorageSignIndex().register(block);

        assertTrue(server.dispatchCommand(player, "sssearch item STONE --coords"));
        assertTrue(player.nextMessage() == null);
        assertTrue(awaitMessage("search-coords|10|64|20").equals("search-coords|10|64|20"));

        assertTrue(server.dispatchCommand(player, "sssearch item STONE --front"));
        assertTrue(player.nextMessage() == null);
        assertTrue(awaitMessage("search-coords|10|64|19").equals("search-coords|10|64|19"));
    }

    @Test
    void frontModeFallsBackToUuidWhenWorldIsMissing() throws Exception {
        var worldId = java.util.UUID.randomUUID();
        plugin.getStorageSignIndex().upsert(
            new StorageSignPosition(worldId, 10, 64, 20), "STONE", 7, 1, BlockFace.SOUTH);

        assertTrue(server.dispatchCommand(player, "sssearch item STONE --front"));
        assertTrue(player.nextMessage() == null);
        assertTrue(awaitMessage(worldId.toString() + "|10|64|21").equals(worldId.toString() + "|10|64|21"));
    }

    private String awaitMessage(String expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            server.getScheduler().performOneTick();
            String message;
            while ((message = player.nextMessage()) != null) {
                if (message.contains(expected)) return message;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for message containing: " + expected);
    }
}
