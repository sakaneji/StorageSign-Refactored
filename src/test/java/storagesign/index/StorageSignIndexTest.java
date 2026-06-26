package storagesign.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Mockito;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.event.StorageSignUpdatedEvent;
import org.bukkit.Bukkit;
import storagesign.logging.PluginLogger;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("integration")
class StorageSignIndexTest {
    private ServerMock server;
    private StorageSignPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
        enableTraceLogging();
    }

    @AfterEach
    void tearDown() {
        PluginLogger.shutdown();
        MockBukkit.unmock();
    }

    @BeforeEach
    void enableTraceLogging() {
        JavaPlugin loggerPlugin = mock(JavaPlugin.class);
        Server serverMock = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("StorageSignIndexTest.trace");
        jul.setUseParentHandlers(false);
        when(loggerPlugin.getServer()).thenReturn(serverMock);
        when(serverMock.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Logger")).thenReturn(null);
        when(loggerPlugin.getLogger()).thenReturn(jul);
        PluginLogger.initialize(loggerPlugin, "TRACE");
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
    void disabledIndexLoadAndSaveRemainNoOps() {
        StorageSignIndex index = new StorageSignIndex(plugin, false);
        assertFalse(index.load().success());
        assertFalse(index.saveAsync(null));
        assertTrue(index.findByIdentifierExact("STONE").isEmpty());
        assertTrue(index.findNearby(new Location(server.addSimpleWorld("index-disabled-lookup"), 0, 0, 0), 6.0).isEmpty());
    }

    @Test
    void disabledIndexIgnoresMutationAndChunkEvents() {
        StorageSignIndex index = new StorageSignIndex(plugin, false);
        var world = server.addSimpleWorld("index-disabled-mutation");
        world.getChunkAt(0, 0).load();
        Block sign = world.getBlockAt(1, 64, 1);
        sign.setType(Material.OAK_SIGN);

        index.register(sign);
        index.unregister(sign);
        index.upsert(new StorageSignPosition(world.getUID(), 1, 64, 1), "STONE", 1, 1L);

        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(world.getChunkAt(0, 0));
        index.onChunkLoad(event);

        assertEquals(0, index.size());
    }

    @Test
    void onChunkLoadScansLoadedChunkAfterSchedulerTick() throws Exception {
        var world = server.addSimpleWorld("index-on-chunk-load");
        world.getChunkAt(0, 0).load();
        Block sign = createSign(world.getBlockAt(1, 64, 1), "STONE", 4);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(world.getChunkAt(0, 0));

        index.onChunkLoad(event);
        assertEquals(0, index.size());
        assertEquals(1, pendingChunkRescanCount(index));
        server.getScheduler().performTicks(3);

        assertEquals(1, index.findNearby(sign.getLocation(), 6.0).size());
        assertEquals(0, pendingChunkRescanCount(index));
    }

    @Test
    void chunkRescanQueueDropsEntriesBeyondConfiguredCap() throws Exception {
        var world = server.addSimpleWorld("index-on-chunk-load-cap");
        world.getChunkAt(0, 0).load();
        world.getChunkAt(1, 0).load();
        createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        createSign(world.getBlockAt(17, 64, 1), "DIRT", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        ChunkLoadEvent first = mock(ChunkLoadEvent.class);
        ChunkLoadEvent second = mock(ChunkLoadEvent.class);
        when(first.getChunk()).thenReturn(world.getChunkAt(0, 0));
        when(second.getChunk()).thenReturn(world.getChunkAt(1, 0));

        setStaticInt("indexChunkRescanQueueCap", 1);
        try {
            index.onChunkLoad(first);
            index.onChunkLoad(second);
            assertEquals(1, pendingChunkRescanCount(index));
            server.getScheduler().performTicks(3);
        } finally {
            setStaticInt("indexChunkRescanQueueCap", 512);
        }

        assertEquals(1, index.findByIdentifierExact("STONE").size());
        assertTrue(index.findByIdentifierExact("DIRT").isEmpty());
        assertEquals(0, pendingChunkRescanCount(index));
    }

    @Test
    void onChunkLoadSkipsUnloadedChunks() {
        var world = server.addSimpleWorld("index-on-chunk-load-skipped");
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(world.getChunkAt(0, 0));

        index.onChunkLoad(event);
        server.getScheduler().performTicks(1);

        assertEquals(0, index.size());
    }

    @Test
    void onChunkLoadIgnoresExplicitlyUnloadedChunk() {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        when(chunk.isLoaded()).thenReturn(false);
        when(event.getChunk()).thenReturn(chunk);

        index.onChunkLoad(event);
        server.getScheduler().performTicks(1);

        assertEquals(0, index.size());
    }

    @Test
    void findByIdentifierExactAndNearbyReturnEmptyForMissingEntries() {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        var world = server.addSimpleWorld("index-missing-lookups");

        assertTrue(index.findByIdentifierExact("STONE").isEmpty());
        assertTrue(index.findNearby(new Location(world, 0, 0, 0), 6.0).isEmpty());
    }

    @Test
    void findByIdentifierExactAndNearbyHandleMissingIdentifierAndUnloadedChunk() {
        var world = server.addSimpleWorld("index-missing-lookups-2");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Block loadedBlock = createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignPosition loaded = new StorageSignPosition(world.getUID(), 1, 64, 1);
        StorageSignPosition unloaded = new StorageSignPosition(world.getUID(), 32, 64, 1);
        index.upsert(loaded, "STONE", 1, 1L);
        index.upsert(unloaded, "DIRT", 1, 1L);

        assertTrue(index.findByIdentifierExact("MISSING").isEmpty());
        assertEquals(1, index.findNearby(loadedBlock.getLocation(), 40.0).size());
    }

    @Test
    void registerRejectsNullAndUnregisteredSigns() {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Block block = mock(Block.class);
        try (var mocked = org.mockito.Mockito.mockStatic(StorageSign.class)) {
            mocked.when(() -> StorageSign.fromBlock(block)).thenReturn(null);
            index.register(block);
            mocked.when(() -> StorageSign.fromBlock(block)).thenReturn(mock(StorageSign.class));
            StorageSign unregistered = mock(StorageSign.class);
            when(unregistered.isUnregistered()).thenReturn(true);
            mocked.when(() -> StorageSign.fromBlock(block)).thenReturn(unregistered);
            index.register(block);
        }
    }

    @Test
    void upsertRejectsNullAndBlankInputsAndExistingEntriesCanBeReplaced() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        var world = server.addSimpleWorld("index-upsert");
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);

        index.upsert(null, "STONE", 1, 1L);
        index.upsert(position, null, 1, 1L);
        index.upsert(position, "", 1, 1L);
        index.upsert(position, "STONE", 1, 1L);
        index.upsert(position, "DIRT", 2, 2L);

        assertEquals(1, index.findByIdentifierExact("DIRT").size());
    }

    @Test
    void unregisterMissingAndLoadedEntriesFollowTheirBranches() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        var world = server.addSimpleWorld("index-unregister");
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);

        index.unregister(position);
        putLoaded(index, new IndexedStorageSign(position, "STONE", 1, 1L));
        index.unregister(position);
        index.unregister(position);
        assertTrue(index.findByIdentifierExact("STONE").isEmpty());
    }

    @Test
    void removeSecondaryHandlesMissingAndLastReferenceCleanup() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        var world = server.addSimpleWorld("index-secondary");
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);
        IndexedStorageSign entry = new IndexedStorageSign(position, "STONE", 1, 1L);

        Method removeSecondary = StorageSignIndex.class.getDeclaredMethod(
            "removeSecondary", IndexedStorageSign.class);
        removeSecondary.setAccessible(true);

        removeSecondary.invoke(index, entry);
        putLoaded(index, entry);
        removeSecondary.invoke(index, entry);
        removeSecondary.invoke(index, entry);
        Method get = StorageSignIndex.class.getDeclaredMethod("get", StorageSignPosition.class);
        get.setAccessible(true);
        assertNull(get.invoke(index, new StorageSignPosition(world.getUID(), 2, 64, 1)));
    }

    @Test
    void snapshotAndRebuildingStateReflectCurrentIndexStatus() throws Exception {
        var world = server.addSimpleWorld("index-snapshot");
        world.getChunkAt(0, 0).load();
        StorageSignIndex enabled = new StorageSignIndex(plugin, true);
        enabled.upsert(new StorageSignPosition(world.getUID(), 1, 64, 1), "STONE", 1, 1L);
        assertEquals(1, enabled.snapshot().size());

        StorageSignIndex disabled = new StorageSignIndex(plugin, false);
        assertTrue(disabled.snapshot().isEmpty());

        Field rebuildTask = StorageSignIndex.class.getDeclaredField("rebuildTask");
        rebuildTask.setAccessible(true);
        rebuildTask.set(enabled, org.mockito.Mockito.mock(org.bukkit.scheduler.BukkitTask.class));
        assertTrue(enabled.isRebuilding());
        rebuildTask.set(enabled, null);
        assertFalse(enabled.isRebuilding());
    }

    @Test
    void nullLookupInputsReturnEmptyAndDisabledSaveReportsDisabled() {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        StorageSignIndex disabled = new StorageSignIndex(plugin, false);

        assertTrue(index.findByIdentifierExact(null).isEmpty());
        assertTrue(index.findNearby(null, 6.0).isEmpty());
        assertTrue(index.findNearby(new Location(null, 0, 0, 0), 6.0).isEmpty());
        assertTrue(index.findNearby(new Location(server.addSimpleWorld("index-radius-zero"), 0, 0, 0), 0.0).isEmpty());
        assertEquals("disabled", disabled.saveSync().message());
        assertTrue(index.saveAsync(null));
        assertTrue(index.rebuild(List.of(), null));
    }

    @Test
    void nullRegisterAndUnregisterInputsDoNothing() {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register((Block) null);
        index.unregister((Block) null);
        index.unregister((StorageSignPosition) null);
        assertEquals(0, index.size());
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
    void rebuildIgnoresUnloadedChunksAndStillCompletes() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Chunk loaded = mock(org.bukkit.Chunk.class);
        org.bukkit.Chunk unloaded = mock(org.bukkit.Chunk.class);
        when(loaded.getWorld()).thenReturn(world);
        when(loaded.getX()).thenReturn(0);
        when(loaded.getZ()).thenReturn(0);
        when(loaded.isLoaded()).thenReturn(true);
        when(loaded.getTileEntities()).thenReturn(new org.bukkit.block.BlockState[0]);
        when(unloaded.isLoaded()).thenReturn(false);
        when(world.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[] { loaded, unloaded });
        AtomicReference<StorageSignIndex.RebuildResult> result = new AtomicReference<>();

        assertTrue(index.rebuild(List.of(world), result::set));
        server.getScheduler().performTicks(3);

        assertNotNull(result.get());
        assertEquals(2, result.get().chunksScanned());
    }

    @Test
    void rebuildProcessesMultipleTicksAndSkipsUnloadedChunks() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        AtomicReference<StorageSignIndex.RebuildResult> result = new AtomicReference<>();
        org.bukkit.World mockedWorld = mock(org.bukkit.World.class);
        org.bukkit.Chunk loadedChunk = mock(org.bukkit.Chunk.class);
        org.bukkit.Chunk skippedChunk = mock(org.bukkit.Chunk.class);
        when(loadedChunk.getWorld()).thenReturn(mockedWorld);
        when(loadedChunk.getX()).thenReturn(0);
        when(loadedChunk.getZ()).thenReturn(0);
        when(loadedChunk.isLoaded()).thenReturn(true);
        when(loadedChunk.getTileEntities()).thenReturn(new org.bukkit.block.BlockState[0]);
        when(skippedChunk.getWorld()).thenReturn(mockedWorld);
        when(skippedChunk.getX()).thenReturn(1);
        when(skippedChunk.getZ()).thenReturn(0);
        when(skippedChunk.isLoaded()).thenReturn(false);
        when(skippedChunk.getTileEntities()).thenReturn(new org.bukkit.block.BlockState[0]);
        when(mockedWorld.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[] { loadedChunk, skippedChunk });

        setStaticInt("indexChunksPerTick", 1);
        try {
            assertTrue(index.rebuild(List.of(mockedWorld), result::set));
            server.getScheduler().performTicks(1);
            assertTrue(index.isRebuilding());
            server.getScheduler().performTicks(2);
        } finally {
            setStaticInt("indexChunksPerTick", 4);
        }

        assertNotNull(result.get());
        assertEquals(2, result.get().chunksScanned());
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
    void contentRevisionTracksQuantityChangesAndIgnoresNullWorlds() {
        var world = server.addSimpleWorld("index-revision");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);

        assertEquals(0L, index.revision(null));
        assertEquals(0L, index.contentRevision(null));

        index.upsert(position, "STONE", 1, 10L);
        long structureRevision = index.revision(world);
        long contentRevision = index.contentRevision(world);

        index.upsert(position, "STONE", 2, 20L);
        assertEquals(structureRevision, index.revision(world));
        assertTrue(index.contentRevision(world) > contentRevision);
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
    void missingIndexLoadsAsNew() throws Exception {
        Path folder = plugin.getDataFolder().toPath();
        Path file = folder.resolve("storage-sign-index.bin");
        Files.createDirectories(folder);
        Files.deleteIfExists(file);
        StorageSignIndex index = new StorageSignIndex(plugin, true);

        StorageSignIndex.LoadResult result = index.load();

        assertTrue(result.success());
        assertEquals(0, result.count());
        assertEquals("new", result.status());
        assertEquals("new", index.getLoadStatus());
    }

    @Test
    void saveAsyncRejectsWhenSaveIsAlreadyRunning() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Field saving = StorageSignIndex.class.getDeclaredField("saving");
        saving.setAccessible(true);
        saving.setBoolean(index, true);

        assertFalse(index.saveAsync(result -> {}));
        assertTrue(index.isSaving());
    }

    @Test
    void rebuildRejectsWhenAlreadyRunning() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Field rebuildTask = StorageSignIndex.class.getDeclaredField("rebuildTask");
        rebuildTask.setAccessible(true);
        rebuildTask.set(index, org.mockito.Mockito.mock(org.bukkit.scheduler.BukkitTask.class));

        assertFalse(index.rebuild(List.of(), null));
    }

    @Test
    void writeSnapshotRejectsSupersededRequests() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        var method = StorageSignIndex.class.getDeclaredMethod(
            "writeSnapshot", List.class, long.class);
        method.setAccessible(true);
        Field latestSaveRequest = StorageSignIndex.class.getDeclaredField("latestSaveRequest");
        latestSaveRequest.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) latestSaveRequest.get(index)).set(10L);

        StorageSignIndex.SaveResult result = (StorageSignIndex.SaveResult) method.invoke(index, List.of(), 1L);
        assertFalse(result.success());
        assertTrue(result.message().contains("superseded"));
    }

    @Test
    void writeSnapshotReportsCodecFailures() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Field codecField = StorageSignIndex.class.getDeclaredField("codec");
        codecField.setAccessible(true);
        StorageSignIndexCodec codec = Mockito.mock(StorageSignIndexCodec.class);
        codecField.set(index, codec);
        when(codec.writeAtomic(Mockito.any(), Mockito.anyList())).thenThrow(new IOException("disk full"));

        StorageSignIndex.SaveResult result = index.saveSync();

        assertFalse(result.success());
        assertTrue(result.message().contains("disk full"));
    }

    @Test
    void saveAsyncCompletesAndClearsSavingFlag() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.upsert(new StorageSignPosition(server.addSimpleWorld("index-save-async").getUID(), 1, 64, 1),
            "STONE", 9, 1L);
        AtomicReference<StorageSignIndex.SaveResult> result = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean(index.saveAsync(result::set));

        assertTrue(started.get());
        assertTrue(index.isSaving());
        for (int i = 0; i < 20 && result.get() == null; i++) {
            server.getScheduler().performTicks(1);
            Thread.sleep(25);
        }
        assertNotNull(result.get());
        assertTrue(result.get().success());
        assertFalse(index.isSaving());
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

    @Test
    void blockAndChunkEventsKeepTheIndexInSync() {
        var world = server.addSimpleWorld("index-events");
        world.getChunkAt(0, 0).load();
        Block block = createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);

        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getBlockPlaced()).thenReturn(block);
        index.onPlace(place);
        assertEquals(1, index.size());

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, server.addPlayer());
        index.onBreak(breakEvent);
        assertEquals(0, index.size());

        index.register(block);
        SignChangeEvent signChange = mock(SignChangeEvent.class);
        when(signChange.getBlock()).thenReturn(block);
        index.onSignChange(signChange);
        server.getScheduler().performTicks(1);
        assertEquals(1, index.size());

        BlockExplodeEvent blockExplode = mock(BlockExplodeEvent.class);
        when(blockExplode.blockList()).thenReturn(List.of(block));
        index.onBlockExplode(blockExplode);
        assertEquals(0, index.size());

        index.register(block);
        EntityExplodeEvent entityExplode = mock(EntityExplodeEvent.class);
        when(entityExplode.blockList()).thenReturn(List.of(block));
        index.onEntityExplode(entityExplode);
        assertEquals(0, index.size());

        ChunkLoadEvent chunkLoad = mock(ChunkLoadEvent.class);
        when(chunkLoad.getChunk()).thenReturn(world.getChunkAt(0, 0));
        index.onChunkLoad(chunkLoad);
        server.getScheduler().performTicks(1);
        assertEquals(1, index.size());
    }

    @Test
    void privateRemoveChunkPutLoadedAndThreadGuardBranchesAreCovered() throws Exception {
        var world = server.addSimpleWorld("index-private");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);
        IndexedStorageSign first = new IndexedStorageSign(position, "STONE", 1, 1L);
        IndexedStorageSign second = new IndexedStorageSign(position, "DIRT", 2, 2L);

        Method putLoaded = StorageSignIndex.class.getDeclaredMethod("putLoaded", IndexedStorageSign.class);
        putLoaded.setAccessible(true);
        Method removeChunk = StorageSignIndex.class.getDeclaredMethod("removeChunk", java.util.UUID.class, int.class, int.class);
        removeChunk.setAccessible(true);

        putLoaded.invoke(index, first);
        assertEquals(1, index.findByIdentifierExact("STONE").size());
        putLoaded.invoke(index, second);
        assertTrue(index.findByIdentifierExact("STONE").isEmpty());
        assertEquals(1, index.findByIdentifierExact("DIRT").size());

        removeChunk.invoke(index, world.getUID(), 9, 9);
        assertEquals(1, index.findByIdentifierExact("DIRT").size());
        removeChunk.invoke(index, world.getUID(), 0, 0);
        assertTrue(index.findByIdentifierExact("DIRT").isEmpty());

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                index.size();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.start();
        thread.join();
        assertInstanceOf(IllegalStateException.class, error.get());
    }

    @Test
    void helperMethodsCoverUnknownLookupRemovalAndLoggingBranches() throws Exception {
        var world = server.addSimpleWorld("index-helper");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);
        IndexedStorageSign entry = new IndexedStorageSign(position, "STONE", 1, 1L);

        Method get = StorageSignIndex.class.getDeclaredMethod("get", StorageSignPosition.class);
        get.setAccessible(true);
        Method removeSecondary = StorageSignIndex.class.getDeclaredMethod(
            "removeSecondary", IndexedStorageSign.class);
        removeSecondary.setAccessible(true);
        Method quarantine = StorageSignIndex.class.getDeclaredMethod("quarantine", Path.class);
        quarantine.setAccessible(true);
        Method logIoTime = StorageSignIndex.class.getDeclaredMethod(
            "logIoTime", String.class, int.class, long.class);
        logIoTime.setAccessible(true);

        assertNull(get.invoke(index, position));
        removeSecondary.invoke(index, entry);
        putLoaded(index, entry);
        removeSecondary.invoke(index, entry);
        removeSecondary.invoke(index, entry);
        logIoTime.invoke(index, "load", 1, 1L);
        logIoTime.invoke(index, "save", 1, 999L);

        Path folder = plugin.getDataFolder().toPath();
        Path missing = folder.resolve("missing-index.bin");
        Files.deleteIfExists(missing);
        quarantine.invoke(index, missing);

        Files.createDirectories(folder);
        Files.write(missing, new byte[] {1, 2, 3});
        quarantine.invoke(index, missing);
        try (var files = Files.list(folder)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString()
                .startsWith("missing-index.bin.corrupt-")));
        }
    }

    @Test
    void unregisterAndGetCoverEmptyChunkBuckets() throws Exception {
        var world = server.addSimpleWorld("index-empty-bucket");
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        StorageSignPosition position = new StorageSignPosition(world.getUID(), 1, 64, 1);
        Field entries = StorageSignIndex.class.getDeclaredField("entries");
        entries.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<java.util.UUID, java.util.Map<Long, java.util.Map<StorageSignPosition, IndexedStorageSign>>> map =
            (java.util.Map<java.util.UUID, java.util.Map<Long, java.util.Map<StorageSignPosition, IndexedStorageSign>>>) entries.get(index);
        map.put(world.getUID(), new java.util.HashMap<>());

        index.unregister(position);

        Method get = StorageSignIndex.class.getDeclaredMethod("get", StorageSignPosition.class);
        get.setAccessible(true);
        assertNull(get.invoke(index, position));

        java.util.Map<Long, java.util.Map<StorageSignPosition, IndexedStorageSign>> chunks =
            map.get(world.getUID());
        chunks.put(position.chunkKey(), new java.util.HashMap<>());
        index.unregister(position);
        assertNull(get.invoke(index, position));
    }

    @Test
    void quarantineSwallowsMoveFailures() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Path folder = plugin.getDataFolder().toPath();
        Files.createDirectories(folder);
        Path file = folder.resolve("index-quarantine-failure.bin");
        Files.write(file, new byte[] {1});
        Method quarantine = StorageSignIndex.class.getDeclaredMethod("quarantine", Path.class);
        quarantine.setAccessible(true);

        try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.exists(file)).thenReturn(true);
            files.when(() -> Files.move(Mockito.eq(file), Mockito.any(Path.class),
                Mockito.eq(StandardCopyOption.REPLACE_EXISTING)))
                .thenThrow(new IOException("move failed"));

            quarantine.invoke(index, file);
        }
    }

    @Test
    void scanChunkSkipsNonSignTileEntitiesAndRegistersSigns() throws Exception {
        var world = server.addSimpleWorld("index-scan");
        world.getChunkAt(0, 0).load();
        Block chestBlock = world.getBlockAt(1, 64, 1);
        chestBlock.setType(Material.CHEST);
        Chest chest = (Chest) chestBlock.getState();
        chest.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.STONE));
        Block signBlock = createSign(world.getBlockAt(2, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);

        Method scanChunk = StorageSignIndex.class.getDeclaredMethod("scanChunk", org.bukkit.Chunk.class);
        scanChunk.setAccessible(true);
        scanChunk.invoke(index, world.getChunkAt(0, 0));

        assertTrue(index.findByIdentifierExact("STONE").size() >= 1);
        assertEquals(1, index.findNearby(signBlock.getLocation(), 6.0).size());
    }

    @Test
    void scanChunkDirectlyVisitsSignEntries() throws Exception {
        var world = server.addSimpleWorld("index-scan-direct");
        world.getChunkAt(0, 0).load();
        Block signBlock = createSign(world.getBlockAt(2, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(chunk.getTileEntities()).thenReturn(new org.bukkit.block.BlockState[] {
            signBlock.getState()
        });

        Method scanChunk = StorageSignIndex.class.getDeclaredMethod("scanChunk", org.bukkit.Chunk.class);
        scanChunk.setAccessible(true);
        scanChunk.invoke(index, chunk);

        assertEquals(1, index.findByIdentifierExact("STONE").size());
    }

    @Test
    void scanChunkOnEmptyChunkLeavesIndexUnchanged() throws Exception {
        var world = server.addSimpleWorld("index-scan-empty");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Method scanChunk = StorageSignIndex.class.getDeclaredMethod("scanChunk", org.bukkit.Chunk.class);
        scanChunk.setAccessible(true);

        scanChunk.invoke(index, world.getChunkAt(0, 0));

        assertTrue(index.snapshot().isEmpty());
    }

    @Test
    void onChunkLoadSkipsWhenDisabledAndWhenChunkIsNotLoaded() {
        var world = server.addSimpleWorld("index-chunk-load");
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        when(event.getChunk()).thenReturn(chunk);

        StorageSignIndex disabled = new StorageSignIndex(plugin, false);
        disabled.onChunkLoad(event);
        assertTrue(disabled.snapshot().isEmpty());

        StorageSignIndex index = new StorageSignIndex(plugin, true);
        when(chunk.isLoaded()).thenReturn(false);
        index.onChunkLoad(event);
        assertTrue(index.snapshot().isEmpty());
    }

    @Test
    void onChunkLoadSchedulesALoadedChunkScan() throws Exception {
        var world = server.addSimpleWorld("index-chunk-load-success");
        world.getChunkAt(0, 0).load();
        createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(world.getChunkAt(0, 0));

        index.onChunkLoad(event);
        assertEquals(1, pendingChunkRescanCount(index));
        assertEquals(0, index.size());
        server.getScheduler().performTicks(3);

        assertEquals(1, index.size());
    }

    @Test
    void onChunkLoadLambdaCanBeInvokedDirectlyForLoadedChunks() throws Exception {
        var world = server.addSimpleWorld("index-chunk-load-lambda");
        world.getChunkAt(0, 0).load();
        createSign(world.getBlockAt(1, 64, 1), "STONE", 1);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Method enqueue = StorageSignIndex.class.getDeclaredMethod("enqueueChunkRescan", org.bukkit.Chunk.class);
        enqueue.setAccessible(true);
        Method process = StorageSignIndex.class.getDeclaredMethod("processChunkRescanQueue");
        process.setAccessible(true);

        enqueue.invoke(index, world.getChunkAt(0, 0));
        assertEquals(1, pendingChunkRescanCount(index));
        process.invoke(index);

        assertEquals(1, index.size());
    }

    private static void putLoaded(StorageSignIndex index, IndexedStorageSign entry) throws Exception {
        Method putLoaded = StorageSignIndex.class.getDeclaredMethod("putLoaded", IndexedStorageSign.class);
        putLoaded.setAccessible(true);
        putLoaded.invoke(index, entry);
    }

    private static Block createSign(Block block, String identifier, int amount) {
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign storageSign = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, identifier, Integer.toString(amount)});
        storageSign.applyToSign(sign);
        return block;
    }

    private static void setStaticInt(String fieldName, int value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static int pendingChunkRescanCount(StorageSignIndex index) throws Exception {
        Field field = StorageSignIndex.class.getDeclaredField("chunkRescanQueue");
        field.setAccessible(true);
        return ((java.util.ArrayDeque<?>) field.get(index)).size();
    }
}
