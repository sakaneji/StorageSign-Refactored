package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSignPlugin;

@Tag("integration")
class StorageSignIndexCommandIntegrationTest {
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
    void rebuildAllStartsThenCompletesWithSave() {
        var world = server.addSimpleWorld("index-command-rebuild");
        world.getChunkAt(0, 0).load();

        assertTrue(server.dispatchCommand(player, "ssindex rebuild all"));
        assertTrue(player.nextMessage().contains("rebuild started"));

        server.getScheduler().performTicks(8);

        boolean sawComplete = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("rebuild and save complete")) {
                sawComplete = true;
                break;
            }
        }
        assertTrue(sawComplete);
    }

    @Test
    void statusReportsRebuildProgressWhenTaskIsRunning() {
        var world = server.addSimpleWorld("index-command-status");
        world.getChunkAt(0, 0).load();

        assertTrue(server.dispatchCommand(player, "ssindex rebuild all"));
        assertTrue(player.nextMessage().contains("rebuild started"));

        assertTrue(server.dispatchCommand(player, "ssindex status"));
        String status = player.nextMessage();
        assertTrue(status.contains("StorageSign index: enabled"));
        assertTrue(player.nextMessage().contains("Nearby display:"));
        assertTrue(player.nextMessage().contains("rebuilding"));
    }

    @Test
    void rebuildRejectsUnknownWorldAndRepeatedStartWhileRunning() {
        var world = server.addSimpleWorld("index-command-repeat");
        world.getChunkAt(0, 0).load();

        assertTrue(server.dispatchCommand(player, "ssindex rebuild missing-world"));
        assertTrue(player.nextMessage().contains("Unknown world"));

        assertTrue(server.dispatchCommand(player, "ssindex rebuild all"));
        assertTrue(player.nextMessage().contains("rebuild started"));
        assertTrue(server.dispatchCommand(player, "ssindex rebuild all"));
        assertTrue(player.nextMessage().contains("already running"));
    }
}
