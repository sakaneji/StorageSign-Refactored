package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;
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
    void rebuildAllStartsThenCompletesWithSave() throws InterruptedException {
        var world = server.addSimpleWorld("index-command-rebuild");
        var block = world.getBlockAt(1, 64, 1);
        block.setType(org.bukkit.Material.OAK_SIGN);
        World rebuildWorld = Mockito.mock(World.class);
        org.bukkit.Chunk chunk = Mockito.mock(org.bukkit.Chunk.class);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(chunk.isLoaded()).thenReturn(true);
        Mockito.when(chunk.getTileEntities()).thenReturn(new org.bukkit.block.BlockState[] {block.getState()});
        Mockito.when(rebuildWorld.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[] {chunk});

        try (var bukkit = Mockito.mockStatic(Bukkit.class, Mockito.CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(rebuildWorld));
            assertTrue(server.dispatchCommand(player, "ssindex rebuild all"));
            assertTrue(player.nextMessage().contains("rebuild started"));
        }

        boolean sawComplete = false;
        String message;
        for (int i = 0; i < 20 && !sawComplete; i++) {
            server.getScheduler().performTicks(1);
            Thread.sleep(25);
            while ((message = player.nextMessage()) != null) {
                if (message.contains("rebuild and save complete")) {
                    sawComplete = true;
                    break;
                }
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
