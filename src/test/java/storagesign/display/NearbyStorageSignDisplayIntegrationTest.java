package storagesign.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.index.StorageSignIndex;

@Tag("integration")
class NearbyStorageSignDisplayIntegrationTest {
    private ServerMock server;
    private StorageSignPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void stationaryPlayerGetsOneSharedLabelAndMovementRemovesIt() {
        var world = server.addSimpleWorld("nearby-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 3);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", "STONE", "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        display.start();
        server.getScheduler().performTicks(16);
        assertEquals(1, display.activeLabelCount());

        player.teleport(new Location(world, 1.5, 64, 0.5, 0, 0));
        server.getScheduler().performTicks(6);
        assertEquals(0, display.activeLabelCount());
        display.shutdown();
    }
}
