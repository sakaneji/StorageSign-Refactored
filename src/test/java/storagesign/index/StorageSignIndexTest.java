package storagesign.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
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
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.event.StorageSignUpdatedEvent;

@Tag("integration")
class StorageSignIndexTest {
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
    void registerQueryAndUnregisterWithoutLoadingOtherChunks() {
        var world = server.addSimpleWorld("index-query");
        world.getChunkAt(0, 0).load();
        Block near = createSign(world.getBlockAt(1, 64, 1), "STONE", 7);
        Block far = createSign(world.getBlockAt(12, 64, 1), "DIRT", 2);
        StorageSignIndex index = new StorageSignIndex(plugin, true);

        index.register(far);
        index.register(near);

        List<StorageSignPosition> found = index.findNearby(new Location(world, 0.5, 64.5, 0.5), 6.0);
        assertEquals(1, found.size());
        assertEquals(near.getX(), found.getFirst().x());
        assertEquals(2, index.size(world));
        index.unregister(near);
        assertTrue(index.findNearby(new Location(world, 0.5, 64.5, 0.5), 6.0).isEmpty());
    }

    @Test
    void disabledIndexStaysEmpty() {
        var world = server.addSimpleWorld("index-disabled");
        world.getChunkAt(0, 0).load();
        Block sign = createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, false);

        index.register(sign);

        assertFalse(index.isEnabled());
        assertEquals(0, index.size());
        assertTrue(index.findNearby(sign.getLocation(), 6.0).isEmpty());
        assertFalse(index.rebuild(List.of(world), ignored -> {}));
    }

    @Test
    void staleIndexedPositionIsRemovedDuringQuery() {
        var world = server.addSimpleWorld("index-stale");
        world.getChunkAt(0, 0).load();
        Block sign = createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(sign);
        sign.setType(Material.AIR);

        assertTrue(index.findNearby(new Location(world, 1.5, 64.5, 1.5), 6.0).isEmpty());
        assertEquals(0, index.size());
    }

    @Test
    void rebuildScansLoadedChunksAcrossTicks() {
        var world = server.addSimpleWorld("index-rebuild");
        world.getChunkAt(0, 0).load();
        createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        AtomicReference<StorageSignIndex.RebuildResult> result = new AtomicReference<>();

        assertTrue(index.rebuild(List.of(world), result::set));
        server.getScheduler().performTicks(3);

        assertNotNull(result.get());
        assertEquals(1, result.get().chunksScanned());
        assertEquals(1, index.size(world));
        assertFalse(index.isRebuilding());
    }

    @Test
    void exactIdentifierIndexTracksContentWithoutChangingStructureRevision() {
        var world = server.addSimpleWorld("index-content");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);

        index.upsert(position, "STONE", 1, 10L);
        long structureRevision = index.revision(world);
        index.upsert(position, "STONE", 99, 20L);

        assertEquals(structureRevision, index.revision(world));
        assertEquals(99, index.findByIdentifierExact("stone").getFirst().amount());
        index.upsert(position, "DIRT", 99, 30L);
        assertTrue(index.findByIdentifierExact("STONE").isEmpty());
        assertEquals(1, index.findByIdentifierExact("dirt").size());
    }

    @Test
    void syncSaveLoadsIntoFreshIndexWithSecondaryLookup() throws Exception {
        var world = server.addSimpleWorld("index-persistence");
        Path file = plugin.getDataFolder().toPath().resolve("storage-sign-index.bin");
        Files.deleteIfExists(file);
        StorageSignIndex writer = new StorageSignIndex(plugin, true);
        writer.upsert(new StorageSignPosition(world.getUID(), -4, 70, 9), "STONE", 123, 456L);

        StorageSignIndex.SaveResult saved = writer.saveSync();
        StorageSignIndex reader = new StorageSignIndex(plugin, true);
        StorageSignIndex.LoadResult loaded = reader.load();

        assertTrue(saved.success());
        assertTrue(saved.bytes() > 0);
        assertTrue(loaded.success());
        assertEquals(1, loaded.count());
        assertEquals(123, reader.findByIdentifierExact("stone").getFirst().amount());
    }

    @Test
    void corruptIndexIsQuarantinedAndDoesNotExposePartialEntries() throws Exception {
        Path folder = plugin.getDataFolder().toPath();
        Path file = folder.resolve("storage-sign-index.bin");
        Files.createDirectories(folder);
        Files.write(file, new byte[] {1, 2, 3});
        StorageSignIndex index = new StorageSignIndex(plugin, true);

        StorageSignIndex.LoadResult result = index.load();

        assertFalse(result.success());
        assertEquals(0, index.size());
        assertTrue(result.status().startsWith("corrupt:"));
        assertFalse(Files.exists(file));
        try (var files = Files.list(folder)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString()
                .startsWith("storage-sign-index.bin.corrupt-")));
        }
    }

    @Test
    void storageSignUpdateEventRefreshesContentAndUnregisteredEventRemovesEntry() {
        var world = server.addSimpleWorld("index-update-event");
        Block block = createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        Sign sign = (Sign) block.getState();
        StorageSignIndex index = new StorageSignIndex(plugin, true);

        index.onStorageSignUpdated(new StorageSignUpdatedEvent(sign, "STONE", 42, true));
        assertEquals(42, index.findByIdentifierExact("STONE").getFirst().amount());

        index.onStorageSignUpdated(new StorageSignUpdatedEvent(sign, "", 0, false));
        assertTrue(index.findByIdentifierExact("STONE").isEmpty());
        assertEquals(0, index.size());
    }

    private static Block createSign(Block block, String identifier, int amount) {
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign storageSign = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, identifier, Integer.toString(amount)});
        storageSign.applyToSign(sign);
        return block;
    }
}
