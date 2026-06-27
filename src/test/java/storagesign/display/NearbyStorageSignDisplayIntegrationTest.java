package storagesign.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.ConfigLoader;
import storagesign.index.StorageSignIndex;

@Tag("integration")
class NearbyStorageSignDisplayIntegrationTest {
    private static final String LONG_IDENTIFIER = "NETHERITE_UPGRADE_SMITHING_TEMPLATE";

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
    void shortIdentifierDoesNotCreateNearbyLabel() {
        var world = server.addSimpleWorld("short-identifier-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", "STONE", "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(20);
            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void longIdentifierPlayerGetsOneSharedLabelAndMovementKeepsItUntilOutOfRange() {
        var world = server.addSimpleWorld("nearby-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());
            TextDisplay label = world.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertEquals(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER), label.getText());

            player.teleport(new Location(world, 1.5, 64, 0.5, 0, 0));
            server.getScheduler().performTicks(6);
            assertEquals(1, display.activeLabelCount());
            assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());

            player.teleport(new Location(world, 8.5, 64, 0.5, 0, 0));
            server.getScheduler().performTicks(20);
            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void initialLabelAppearsWhileThePlayerKeepsTurning() {
        var world = server.addSimpleWorld("turning-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(5);
            player.teleport(new Location(world, 0.5, 64, 0.5, 10, 0));
            server.getScheduler().performTicks(5);
            player.teleport(new Location(world, 0.5, 64, 0.5, 20, 0));
            server.getScheduler().performTicks(6);

            assertEquals(1, display.activeLabelCount());
            assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void viewChangeStillAllowsRescanWhilePositionIsStable() {
        var world = server.addSimpleWorld("view-rescan-display");
        world.getChunkAt(0, 0).load();
        Block frontBlock = world.getBlockAt(0, 65, 2);
        frontBlock.setType(Material.OAK_SIGN);
        Sign frontSign = (Sign) frontBlock.getState();
        String frontIdentifier = LONG_IDENTIFIER;
        StorageSign.fromSignLines(new String[] {"StorageSign", frontIdentifier, "12"}).applyToSign(frontSign);

        Block sideBlock = world.getBlockAt(2, 65, 0);
        sideBlock.setType(Material.OAK_SIGN);
        Sign sideSign = (Sign) sideBlock.getState();
        String sideIdentifier = "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE";
        StorageSign.fromSignLines(new String[] {"StorageSign", sideIdentifier, "13"}).applyToSign(sideSign);

        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(frontBlock);
        index.register(sideBlock);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());
            TextDisplay label = world.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertEquals(NearbyStorageSignDisplay.wrap(frontIdentifier), label.getText());

            player.teleport(new Location(world, 0.5, 64, 0.5, 90, 0));
            server.getScheduler().performTicks(6);

            assertEquals(1, display.activeLabelCount());
            assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());
            label = world.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertEquals(NearbyStorageSignDisplay.wrap(sideIdentifier), label.getText());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void longIdentifierLabelStaysVisibleAfterViewChangeUntilNextRescan() {
        var world = server.addSimpleWorld("view-change-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());

            player.teleport(new Location(world, 0.5, 64, 0.5, 180, 0));
            server.getScheduler().performTicks(6);
            assertEquals(1, display.activeLabelCount());
            assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());

            server.getScheduler().performTicks(20);
            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void labelTextRefreshesAfterSignContentsChange() {
        var world = server.addSimpleWorld("refresh-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);
            TextDisplay label = world.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertEquals(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER), label.getText());

            StorageSign.fromSignLines(new String[] {"StorageSign", "STONE", "34"}).applyToSign(sign);
            server.getScheduler().performTicks(20);
            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void startQueuesPendingAllocationWhenGlobalLimitIsZero() throws Exception {
        var world = server.addSimpleWorld("zero-limit-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplayGlobalLimit(0);
            Block block = world.getBlockAt(0, 65, 2);
            block.setType(Material.OAK_SIGN);
            Sign sign = (Sign) block.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            index.register(block);
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

            try {
                display.start();
                server.getScheduler().performTicks(16);
                assertEquals(0, display.activeLabelCount());
                assertEquals(1, ((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).size());
            } finally {
                display.shutdown();
            }
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
        }
    }

    @Test
    void twoPlayersShareOnePrivateNonPersistentLabelUntilBothMoveOutOfRange() {
        var world = server.addSimpleWorld("shared-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        first.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        second.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());
            TextDisplay label = world.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertEquals(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER), label.getText());
            assertFalse(label.isPersistent());
            assertFalse(label.hasGravity());
            assertFalse(label.isVisibleByDefault());
            assertEquals(Display.Billboard.CENTER, label.getBillboard());

            first.teleport(first.getLocation().add(1, 0, 0));
            server.getScheduler().performTicks(6);
            assertEquals(1, display.activeLabelCount());
            second.teleport(second.getLocation().add(1, 0, 0));
            server.getScheduler().performTicks(6);
            assertEquals(1, display.activeLabelCount());
            assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());

            first.teleport(new Location(world, 8.5, 64, 0.5, 0, 0));
            second.teleport(new Location(world, 8.5, 64, 0.5, 0, 0));
            server.getScheduler().performTicks(20);
            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void maxPerPlayerCapsVisibleLabels() throws Exception {
        var world = server.addSimpleWorld("capped-display");
        world.getChunkAt(0, 0).load();
        int original = getNearbyDisplayMaxPerPlayer();
        try {
            setNearbyDisplayMaxPerPlayer(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            for (int z = 1; z <= 2; z++) {
                Block block = world.getBlockAt(0, 65, z);
                block.setType(Material.OAK_SIGN);
                Sign sign = (Sign) block.getState();
                StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, String.valueOf(z)})
                    .applyToSign(sign);
                index.register(block);
            }
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

            try {
                display.start();
                server.getScheduler().performTicks(16);

                assertEquals(1, display.activeLabelCount());
                assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());
            } finally {
                display.shutdown();
            }
        } finally {
            setNearbyDisplayMaxPerPlayer(original);
        }
    }

    @Test
    void blockedLineOfSightPreventsLabelCreation() throws Exception {
        var world = server.addSimpleWorld("blocked-display");
        world.getChunkAt(0, 0).load();
        Block target = world.getBlockAt(0, 65, 2);
        target.setType(Material.OAK_SIGN);
        Sign targetSign = (Sign) target.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(targetSign);
        world.getBlockAt(0, 65, 2).setType(Material.STONE);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(target);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);

            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void globalLabelLimitStopsAdditionalAllocations() throws Exception {
        var world = server.addSimpleWorld("limited-display");
        world.getChunkAt(0, 0).load();
        int original = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplayGlobalLimit(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            for (int z = 1; z <= 2; z++) {
                Block block = world.getBlockAt(0, 65, z);
                block.setType(Material.OAK_SIGN);
                Sign sign = (Sign) block.getState();
                StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, String.valueOf(z)})
                    .applyToSign(sign);
                index.register(block);
            }
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

            try {
                display.start();
                server.getScheduler().performTicks(16);

                assertEquals(1, display.activeLabelCount());
                assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());
                assertTrue(world.getEntitiesByClass(TextDisplay.class).iterator().next().getText()
                    .contains(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER)));
            } finally {
                display.shutdown();
            }
        } finally {
            setNearbyDisplayGlobalLimit(original);
        }
    }

    @Test
    void pendingAllocationIsRetriedAfterTheCurrentLabelDisappears() throws Exception {
        var world = server.addSimpleWorld("retry-display");
        world.getChunkAt(0, 0).load();
        int original = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplayGlobalLimit(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            Block first = world.getBlockAt(0, 65, 2);
            first.setType(Material.OAK_SIGN);
            Sign firstSign = (Sign) first.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"}).applyToSign(firstSign);
            index.register(first);
            Block second = world.getBlockAt(0, 65, 1);
            second.setType(Material.OAK_SIGN);
            Sign secondSign = (Sign) second.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "4"}).applyToSign(secondSign);
            index.register(second);
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

            try {
                display.start();
                server.getScheduler().performTicks(16);
                assertEquals(1, display.activeLabelCount());
                assertEquals(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER),
                    world.getEntitiesByClass(TextDisplay.class).iterator().next().getText());

                first.setType(Material.AIR);
                server.getScheduler().performTicks(20);

                assertEquals(1, display.activeLabelCount());
                assertEquals(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER),
                    world.getEntitiesByClass(TextDisplay.class).iterator().next().getText());
            } finally {
                display.shutdown();
            }
        } finally {
            setNearbyDisplayGlobalLimit(original);
        }
    }

    @Test
    void disabledNearbyDisplayDoesNotScheduleLabels() throws Exception {
        var world = server.addSimpleWorld("disabled-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        boolean originalEnabled = getNearbyDisplayEnabled();
        try {
            setNearbyDisplayEnabled(false);
            display.start();
            server.getScheduler().performTicks(20);
            assertEquals(0, display.activeLabelCount());
            assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            setNearbyDisplayEnabled(originalEnabled);
            display.shutdown();
        }
    }

    @Test
    void startIsIdempotentAndShutdownIsSafeBeforeStart() throws Exception {
        var world = server.addSimpleWorld("idempotent-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        assertEquals(0, display.activeLabelCount());
        display.shutdown();
        display.start();
        display.start();
        server.getScheduler().performTicks(2);
        display.shutdown();
        assertEquals(0, display.activeLabelCount());
        assertEquals(0, world.getEntitiesByClass(TextDisplay.class).size());
    }

    @Test
    void startWhileAlreadyRunningDoesNotScheduleDuplicateTasks() throws Exception {
        var world = server.addSimpleWorld("duplicate-start-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            display.start();
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());
            assertEquals(1, world.getEntitiesByClass(TextDisplay.class).size());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void clearPlayerAndRemoveLabelReleaseViewerState() throws Exception {
        var world = server.addSimpleWorld("release-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        UUID viewer = UUID.randomUUID();
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(viewer);
        Object state = newPlayerState();
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        setField(state, "visible", new HashSet<>(java.util.List.of(position)));
        setField(state, "desired", java.util.List.of(position));
        setField(label, "viewers", new HashSet<>(java.util.List.of(viewer)));
        putMap(display, "players", viewer, state);
        putMap(display, "labels", position, label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "clearPlayer", UUID.class, Player.class, state.getClass());
        method.setAccessible(true);
        method.invoke(display, viewer, player, state);

        assertTrue(((java.util.Set<?>) getField(state, "visible")).isEmpty());
        assertTrue(((java.util.Set<?>) getField(label, "viewers")).isEmpty());
        assertEquals(java.util.List.of(), getField(state, "desired"));
        verify(textDisplay).remove();
        verify(player).hideEntity(org.mockito.ArgumentMatchers.eq(plugin), org.mockito.ArgumentMatchers.any());
        assertEquals(0, display.activeLabelCount());
    }

    @Test
    void removeLabelWithRescanRequeuesViewersAndClearsVisibility() throws Exception {
        var world = server.addSimpleWorld("rescan-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        UUID viewer = UUID.randomUUID();
        Object state = newPlayerState();
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        setField(state, "visible", new HashSet<>(java.util.List.of(position)));
        setField(state, "desired", java.util.List.of(position));
        setField(state, "searched", true);
        setField(label, "viewers", new HashSet<>(java.util.List.of(viewer)));
        putMap(display, "players", viewer, state);
        putMap(display, "labels", position, label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "removeLabel", storagesign.index.StorageSignPosition.class, label.getClass(), boolean.class);
        method.setAccessible(true);
        method.invoke(display, position, label, true);

        assertTrue(((java.util.Set<?>) getField(state, "visible")).isEmpty());
        assertFalse((boolean) getField(state, "searched"));
        assertTrue((boolean) getField(state, "needsRescan"));
        assertTrue(((java.util.Set<?>) getField(label, "viewers")).isEmpty());
        verify(textDisplay).remove();
        assertEquals(1, ((java.util.ArrayDeque<?>) getField(display, "searchQueue")).size());
    }

    @Test
    void refreshLabelsUpdatesValidEntriesAndDropsMissingOnes() throws Exception {
        var world = server.addSimpleWorld("refresh-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        Block validBlock = world.getBlockAt(0, 65, 2);
        validBlock.setType(Material.OAK_SIGN);
        Sign validSign = (Sign) validBlock.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "7"}).applyToSign(validSign);

        Object validLabel = newLabel(mock(TextDisplay.class));
        Object missingLabel = newLabel(mock(TextDisplay.class));
        var validPosition = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        var missingPosition = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1);
        setField(validLabel, "viewers", new HashSet<>());
        setField(missingLabel, "viewers", new HashSet<>());
        putMap(display, "labels", validPosition, validLabel);
        putMap(display, "labels", missingPosition, missingLabel);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("refreshLabels");
        method.setAccessible(true);
        method.invoke(display);

        assertEquals(1, display.activeLabelCount());
        verify((TextDisplay) getField(validLabel, "display"))
            .setText(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER));
    }

    @Test
    void queuedSearchSkipsMovedPlayerAndEventuallyReaddsAfterRescan() throws Exception {
        var world = server.addSimpleWorld("queue-display");
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "12"}).applyToSign(sign);
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        index.register(block);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        try {
            display.start();
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());

            player.teleport(new Location(world, 1.5, 64, 0.5, 0, 0));
            server.getScheduler().performTicks(1);
            assertEquals(0, display.activeLabelCount());

            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            server.getScheduler().performTicks(16);
            assertEquals(1, display.activeLabelCount());
        } finally {
            display.shutdown();
        }
    }

    @Test
    void searchAndAllocationQueuesHandleMissingPlayersAndRetryWithoutCrashing() throws Exception {
        var world = server.addSimpleWorld("queue-edge-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        UUID missing = UUID.randomUUID();
        Object missingState = newPlayerState();
        setField(missingState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
        setField(missingState, "searched", false);
        putMap(display, "players", missing, missingState);
        enqueue(display, missing);

        Method processSearchQueue = NearbyStorageSignDisplay.class.getDeclaredMethod("processSearchQueue");
        processSearchQueue.setAccessible(true);
        processSearchQueue.invoke(display);

        Object retryState = newPlayerState();
        UUID retryId = UUID.randomUUID();
        setField(retryState, "searched", true);
        setField(retryState, "desired", java.util.List.of());
        putMap(display, "players", retryId, retryState);
        java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
        pending.add(retryId);
        setField(display, "allocationPending", pending);

        Method processAllocationPending = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "processAllocationPending");
        processAllocationPending.setAccessible(true);
        processAllocationPending.invoke(display);

        assertEquals(0, display.activeLabelCount());
    }

    @Test
    void searchQueueSkipsMissingSearchedAndNotIdlePlayers() throws Exception {
        var world = server.addSimpleWorld("queue-skip-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        UUID missing = UUID.randomUUID();
        Object searchedState = newPlayerState();
        setField(searchedState, "searched", true);
        setField(searchedState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
        UUID searched = server.addPlayer().getUniqueId();
        putMap(display, "players", missing, newPlayerState());
        putMap(display, "players", searched, searchedState);

        PlayerMock idleSoon = server.addPlayer();
        idleSoon.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object idleState = newPlayerState();
        setField(idleState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks() - 1);
        putMap(display, "players", idleSoon.getUniqueId(), idleState);

        Method enqueue = NearbyStorageSignDisplay.class.getDeclaredMethod("enqueue", UUID.class);
        enqueue.setAccessible(true);
        enqueue.invoke(display, missing);
        enqueue.invoke(display, searched);
        enqueue.invoke(display, idleSoon.getUniqueId());

        Method processSearchQueue = NearbyStorageSignDisplay.class.getDeclaredMethod("processSearchQueue");
        processSearchQueue.setAccessible(true);
        processSearchQueue.invoke(display);

        assertEquals(0, display.activeLabelCount());
    }

    @Test
    void allocationPendingStopsWhenGlobalLimitIsReached() throws Exception {
        var world = server.addSimpleWorld("allocation-limit-display");
        world.getChunkAt(0, 0).load();
        int original = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplayGlobalLimit(0);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            UUID viewer = server.addPlayer().getUniqueId();
            Object state = newPlayerState();
            setField(state, "searched", true);
            setField(state, "desired", java.util.List.of());
            putMap(display, "players", viewer, state);
            java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
            pending.add(viewer);
            setField(display, "allocationPending", pending);

            Method processAllocationPending = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "processAllocationPending");
            processAllocationPending.setAccessible(true);
            processAllocationPending.invoke(display);

            assertEquals(0, display.activeLabelCount());
        } finally {
            setNearbyDisplayGlobalLimit(original);
        }
    }

    @Test
    void processSearchQueueSkipsInvalidStatesAndQueuesFailedAllocation() throws Exception {
        var world = server.addSimpleWorld("search-queue-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        try {
            setNearbyDisplayGlobalLimit(0);
            setNearbyDisplaySearchesPerTick(10);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            Block tracked = world.getBlockAt(0, 65, 2);
            tracked.setType(Material.OAK_SIGN);
            Sign trackedSign = (Sign) tracked.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"})
                .applyToSign(trackedSign);
            index.register(tracked);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

            UUID missing = UUID.randomUUID();
            UUID searched = UUID.randomUUID();
            UUID tooSoon = UUID.randomUUID();
            PlayerMock validPlayer = server.addPlayer();
            validPlayer.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            UUID valid = validPlayer.getUniqueId();

            putMap(display, "players", missing, newPlayerState());

            Object searchedState = newPlayerState();
            setField(searchedState, "searched", true);
            setField(searchedState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
            putMap(display, "players", searched, searchedState);

            Object tooSoonState = newPlayerState();
            setField(tooSoonState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks() - 1);
            putMap(display, "players", tooSoon, tooSoonState);

            Object validState = newPlayerState();
            setField(validState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
            putMap(display, "players", valid, validState);

            java.util.ArrayDeque<UUID> queue = new java.util.ArrayDeque<>();
            queue.add(missing);
            queue.add(searched);
            queue.add(tooSoon);
            queue.add(valid);
            setField(display, "searchQueue", queue);
            setField(display, "queued", new java.util.HashSet<>(queue));

            Method processSearchQueue = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "processSearchQueue");
            processSearchQueue.setAccessible(true);
            processSearchQueue.invoke(display);

            assertEquals(0, display.activeLabelCount());
            assertEquals(1, ((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).size());
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
            setNearbyDisplaySearchesPerTick(originalSearches);
        }
    }

    @Test
    void processAllocationPendingRetriesWhenGlobalLimitBlocksLaterLabel() throws Exception {
        var world = server.addSimpleWorld("allocation-retry-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplayGlobalLimit(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            Block first = world.getBlockAt(0, 65, 2);
            first.setType(Material.OAK_SIGN);
            Sign firstSign = (Sign) first.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"}).applyToSign(firstSign);
            index.register(first);
            Block second = world.getBlockAt(0, 65, 1);
            second.setType(Material.OAK_SIGN);
            Sign secondSign = (Sign) second.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", "DIRT", "4"}).applyToSign(secondSign);
            index.register(second);

            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            UUID viewer = server.addPlayer().getUniqueId();
            Object state = newPlayerState();
            setField(state, "searched", true);
            setField(state, "desired", java.util.List.of(
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2),
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1)));
            putMap(display, "players", viewer, state);
            setField(display, "labels", new java.util.LinkedHashMap<>());
            java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
            pending.add(viewer);
            setField(display, "allocationPending", pending);

            Method processAllocationPending = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "processAllocationPending");
            processAllocationPending.setAccessible(true);
            processAllocationPending.invoke(display);

            assertEquals(1, display.activeLabelCount());
            assertEquals(1, ((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).size());
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
        }
    }

    @Test
    void processSearchQueueQueuesPlayerWhenGlobalLimitBlocksAdditionalLabels() throws Exception {
        var world = server.addSimpleWorld("search-queue-global-limit-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        int originalPerPlayer = getNearbyDisplayMaxPerPlayer();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        try {
            setNearbyDisplayGlobalLimit(0);
            setNearbyDisplayMaxPerPlayer(1);
            setNearbyDisplaySearchesPerTick(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            Block first = world.getBlockAt(0, 65, 2);
            first.setType(Material.OAK_SIGN);
            Sign firstSign = (Sign) first.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"}).applyToSign(firstSign);
            index.register(first);

            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            UUID viewer = player.getUniqueId();
            Object state = newPlayerState();
            setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
            putMap(display, "players", viewer, state);
            java.util.ArrayDeque<UUID> queue = new java.util.ArrayDeque<>();
            queue.add(viewer);
            setField(display, "searchQueue", queue);
            setField(display, "queued", new java.util.HashSet<>(queue));

            Method processSearchQueue = NearbyStorageSignDisplay.class.getDeclaredMethod("processSearchQueue");
            processSearchQueue.setAccessible(true);
            processSearchQueue.invoke(display);

            assertEquals(0, display.activeLabelCount());
            assertEquals(1, ((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).size());
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
            setNearbyDisplayMaxPerPlayer(originalPerPlayer);
            setNearbyDisplaySearchesPerTick(originalSearches);
        }
    }

    @Test
    void processSearchQueueAppliesDesiredWhenCapacityExists() throws Exception {
        var world = server.addSimpleWorld("search-queue-capacity-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        int originalPerPlayer = getNearbyDisplayMaxPerPlayer();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        try {
            setNearbyDisplayGlobalLimit(1);
            setNearbyDisplayMaxPerPlayer(1);
            setNearbyDisplaySearchesPerTick(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            Block first = world.getBlockAt(0, 65, 2);
            first.setType(Material.OAK_SIGN);
            Sign firstSign = (Sign) first.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"}).applyToSign(firstSign);
            index.register(first);

            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
            UUID viewer = player.getUniqueId();
            Object state = newPlayerState();
            setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
            putMap(display, "players", viewer, state);
            java.util.ArrayDeque<UUID> queue = new java.util.ArrayDeque<>();
            queue.add(viewer);
            setField(display, "searchQueue", queue);
            setField(display, "queued", new java.util.HashSet<>(queue));

            try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                mockedBukkit.when(() -> Bukkit.getPlayer(viewer)).thenReturn(player);
                mockedBukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);

                Method processSearchQueue = NearbyStorageSignDisplay.class.getDeclaredMethod("processSearchQueue");
                processSearchQueue.setAccessible(true);
                processSearchQueue.invoke(display);
            }

            assertEquals(1, display.activeLabelCount());
            assertTrue(((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).isEmpty());
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
            setNearbyDisplayMaxPerPlayer(originalPerPlayer);
            setNearbyDisplaySearchesPerTick(originalSearches);
        }
    }

    @Test
    void applyDesiredHidesStaleLabelAndUsesExistingLabel() throws Exception {
        var world = server.addSimpleWorld("apply-desired-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        UUID viewer = server.addPlayer().getUniqueId();
        Object state = newPlayerState();
        storagesign.index.StorageSignPosition stale =
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        storagesign.index.StorageSignPosition desired =
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1);
        setField(state, "visible", new HashSet<>(java.util.List.of(stale, desired)));
        setField(state, "desired", java.util.List.of(desired));
        TextDisplay staleDisplay = mock(TextDisplay.class);
        Object staleLabel = newLabel(staleDisplay);
        setField(staleLabel, "viewers", new HashSet<>(java.util.List.of(viewer)));
        TextDisplay desiredDisplay = mock(TextDisplay.class);
        Object desiredLabel = newLabel(desiredDisplay);
        setField(desiredLabel, "viewers", new HashSet<>());
        putMap(display, "players", viewer, state);
        putMap(display, "labels", stale, staleLabel);
        putMap(display, "labels", desired, desiredLabel);
        org.bukkit.entity.Player player = server.getPlayer(viewer);

        Method applyDesired = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "applyDesired", org.bukkit.entity.Player.class, state.getClass());
        applyDesired.setAccessible(true);
        boolean complete = (boolean) applyDesired.invoke(display, player, state);

        assertTrue(complete);
        assertTrue(((java.util.Set<?>) getField(state, "visible")).contains(desired));
        assertFalse(((java.util.Set<?>) getField(state, "visible")).contains(stale));
        verify(staleDisplay).remove();
    }

    @Test
    void applyDesiredReturnsFalseWhenGlobalLimitBlocksNewLabel() throws Exception {
        var world = server.addSimpleWorld("apply-desired-limit-display");
        world.getChunkAt(0, 0).load();
        int original = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplayGlobalLimit(0);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            UUID viewer = server.addPlayer().getUniqueId();
            Object state = newPlayerState();
            storagesign.index.StorageSignPosition desired =
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1);
            setField(state, "visible", new HashSet<>());
            setField(state, "desired", java.util.List.of(desired));
            putMap(display, "players", viewer, state);
            org.bukkit.entity.Player player = server.getPlayer(viewer);

            Method applyDesired = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "applyDesired", org.bukkit.entity.Player.class, state.getClass());
            applyDesired.setAccessible(true);
            boolean complete = (boolean) applyDesired.invoke(display, player, state);

            assertFalse(complete);
        } finally {
            setNearbyDisplayGlobalLimit(original);
        }
    }

    @Test
    void applyDesiredSkipsMissingStorageSignWithoutCreatingLabel() throws Exception {
        var world = server.addSimpleWorld("apply-desired-missing-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        UUID viewer = server.addPlayer().getUniqueId();
        Object state = newPlayerState();
        storagesign.index.StorageSignPosition desired =
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1);
        setField(state, "visible", new HashSet<>());
        setField(state, "desired", java.util.List.of(desired));
        putMap(display, "players", viewer, state);
        org.bukkit.entity.Player player = server.getPlayer(viewer);

        Method applyDesired = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "applyDesired", org.bukkit.entity.Player.class, state.getClass());
        applyDesired.setAccessible(true);
        boolean complete = (boolean) applyDesired.invoke(display, player, state);

        assertTrue(complete);
        assertEquals(0, display.activeLabelCount());
        assertTrue(((java.util.Map<?, ?>) getField(display, "labels")).isEmpty());
    }

    @Test
    void applyDesiredUsesExistingLabelAndShowsEntity() throws Exception {
        var world = server.addSimpleWorld("apply-desired-existing-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        UUID viewer = server.addPlayer().getUniqueId();
        Object state = newPlayerState();
        var desired = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1);
        setField(state, "visible", new HashSet<>());
        setField(state, "desired", java.util.List.of(desired));
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        setField(label, "viewers", new HashSet<>());
        putMap(display, "players", viewer, state);
        putMap(display, "labels", desired, label);
        org.bukkit.entity.Player player = server.getPlayer(viewer);

        Method applyDesired = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "applyDesired", org.bukkit.entity.Player.class, state.getClass());
        applyDesired.setAccessible(true);
        boolean complete = (boolean) applyDesired.invoke(display, player, state);

        assertTrue(complete);
        assertTrue(((java.util.Set<?>) getField(state, "visible")).contains(desired));
        verify(textDisplay, never()).remove();
    }

    @Test
    void applyDesiredCreatesLabelThroughCreateLabelPath() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        org.bukkit.World world = mock(org.bukkit.World.class);
        Block block = mock(Block.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getBlockAt(0, 65, 2)).thenReturn(block);
        when(block.getX()).thenReturn(0);
        when(block.getY()).thenReturn(65);
        when(block.getZ()).thenReturn(3);
        when(block.getLocation()).thenReturn(new Location(world, 0.5, 65, 3.5));
        TextDisplay textDisplay = mock(TextDisplay.class);
        StorageSign resolved = StorageSign.fromSignLines(
            new String[] {"StorageSign", LONG_IDENTIFIER, "7"});
        Object state = newPlayerState();
        UUID viewer = server.addPlayer().getUniqueId();
        var desired = new storagesign.index.StorageSignPosition(worldId, 0, 65, 2);
        setField(state, "visible", new HashSet<>());
        setField(state, "desired", java.util.List.of(desired));
        putMap(display, "players", viewer, state);
        try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<StorageSign> mockedSigns = org.mockito.Mockito.mockStatic(StorageSign.class)) {
            mockedBukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            mockedSigns.when(() -> StorageSign.fromBlock(block)).thenReturn(resolved);
            org.mockito.Mockito.doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<org.bukkit.entity.TextDisplay> initializer =
                    invocation.getArgument(2);
                initializer.accept(textDisplay);
                return textDisplay;
            }).when(world).spawn(
                org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.entity.TextDisplay.class),
                org.mockito.ArgumentMatchers.<Consumer<org.bukkit.entity.TextDisplay>>any());

            Method applyDesired = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "applyDesired", org.bukkit.entity.Player.class, state.getClass());
            applyDesired.setAccessible(true);
            boolean complete = (boolean) applyDesired.invoke(display, server.getPlayer(viewer), state);

            assertTrue(complete);
            assertEquals(1, display.activeLabelCount());
            assertTrue(((java.util.Map<?, ?>) getField(display, "labels")).containsKey(desired));
        }
    }

    @Test
    void applyDesiredCreatesNewLabelWhenSpaceExists() throws Exception {
        var world = server.addSimpleWorld("apply-desired-create-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        UUID viewer = server.addPlayer().getUniqueId();
        Object state = newPlayerState();
        storagesign.index.StorageSignPosition desired =
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1);
        Block block = world.getBlockAt(0, 65, 1);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "4"}).applyToSign(sign);
        setField(state, "visible", new HashSet<>());
        setField(state, "desired", java.util.List.of(desired));
        putMap(display, "players", viewer, state);
        org.bukkit.entity.Player player = server.getPlayer(viewer);

        Method applyDesired = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "applyDesired", org.bukkit.entity.Player.class, state.getClass());
        applyDesired.setAccessible(true);
        boolean complete = (boolean) applyDesired.invoke(display, player, state);

        assertTrue(complete);
        assertEquals(1, display.activeLabelCount());
    }

    @Test
    void hideWithNullPlayerRemovesLabelWithoutException() throws Exception {
        var world = server.addSimpleWorld("hide-null-player-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        Object state = newPlayerState();
        UUID viewer = java.util.UUID.randomUUID();
        setField(state, "visible", new HashSet<>(java.util.List.of(
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2))));
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        setField(label, "viewers", new HashSet<>(java.util.List.of(viewer)));
        putMap(display, "players", viewer, state);
        putMap(display, "labels", new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2), label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hide", UUID.class, org.bukkit.entity.Player.class, state.getClass(),
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        method.invoke(display, viewer, null, state,
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2));

        Method wrapper = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hide", org.bukkit.entity.Player.class, state.getClass(),
            storagesign.index.StorageSignPosition.class);
        wrapper.setAccessible(true);
        wrapper.invoke(display, null, state,
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2));

        assertTrue(((java.util.Set<?>) getField(state, "visible")).isEmpty());
        verify(textDisplay).remove();
    }

    @Test
    void hideWithNonNullPlayerUsesWrapperAndRemovesLabel() throws Exception {
        var world = server.addSimpleWorld("hide-player-wrapper-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        Object state = newPlayerState();
        UUID viewer = player.getUniqueId();
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        setField(state, "visible", new HashSet<>(java.util.List.of(position)));
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        setField(label, "viewers", new HashSet<>(java.util.List.of(viewer)));
        putMap(display, "players", viewer, state);
        putMap(display, "labels", position, label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hide", org.bukkit.entity.Player.class, state.getClass(),
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        method.invoke(display, player, state, position);

        assertTrue(((java.util.Set<?>) getField(state, "visible")).isEmpty());
        verify(textDisplay).remove();
    }

    @Test
    void hidePlayerWrapperDelegatesToUuidAwareHide() throws Exception {
        var world = server.addSimpleWorld("hide-player-wrapper-delegate-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        Object state = newPlayerState();
        UUID viewer = player.getUniqueId();
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        setField(state, "visible", new HashSet<>(java.util.List.of(position)));
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        setField(label, "viewers", new HashSet<>(java.util.List.of(viewer)));
        putMap(display, "players", viewer, state);
        putMap(display, "labels", position, label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hide", org.bukkit.entity.Player.class, state.getClass(),
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        method.invoke(display, player, state, position);

        assertTrue(((java.util.Set<?>) getField(state, "visible")).isEmpty());
        verify(textDisplay).remove();
    }

    @Test
    void hideWithMissingLabelOnlyClearsVisibleState() throws Exception {
        var world = server.addSimpleWorld("hide-missing-label-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        Object state = newPlayerState();
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        setField(state, "visible", new HashSet<>(java.util.List.of(position)));
        setField(state, "desired", java.util.List.of(position));
        putMap(display, "players", java.util.UUID.randomUUID(), state);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hide", UUID.class, org.bukkit.entity.Player.class, state.getClass(),
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        method.invoke(display, null, null, state, position);

        assertTrue(((java.util.Set<?>) getField(state, "visible")).isEmpty());
    }

    @Test
    void removeLabelSkipsMissingViewerStateWhileCleaningUpLabel() throws Exception {
        var world = server.addSimpleWorld("remove-missing-viewer-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        Object state = newPlayerState();
        UUID viewer = UUID.randomUUID();
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        setField(label, "viewers", new HashSet<>(java.util.List.of(viewer)));
        putMap(display, "labels", position, label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "removeLabel", storagesign.index.StorageSignPosition.class, label.getClass(), boolean.class);
        method.setAccessible(true);
        method.invoke(display, position, label, true);

        assertTrue(((java.util.Map<?, ?>) getField(display, "labels")).isEmpty());
        verify(textDisplay).remove();
    }

    @Test
    void refreshLabelsRemovesStaleLabelsAndUnregistersTheirPositions() throws Exception {
        var world = server.addSimpleWorld("refresh-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        Object label = newLabel(mock(TextDisplay.class));
        storagesign.index.StorageSignPosition position =
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        putMap(display, "labels", position, label);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("refreshLabels");
        method.setAccessible(true);
        method.invoke(display);

        assertTrue(((java.util.Map<?, ?>) getField(display, "labels")).isEmpty());
        assertEquals(0, index.size());
    }

    @Test
    void shutdownCancelsTaskAndClearsAllState() throws Exception {
        var world = server.addSimpleWorld("shutdown-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        var task = mock(org.bukkit.scheduler.BukkitTask.class);
        setField(display, "task", task);
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        storagesign.index.StorageSignPosition position =
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        putMap(display, "labels", position, label);
        putMap(display, "players", java.util.UUID.randomUUID(), newPlayerState());

        display.shutdown();

        verify(task).cancel();
        verify(textDisplay).remove();
        assertEquals(0, display.activeLabelCount());
        assertTrue(((java.util.Map<?, ?>) getField(display, "players")).isEmpty());
    }

    @Test
    void monitorPlayersCleansUpOfflinePlayersAndResetsMovedOnes() throws Exception {
        var world = server.addSimpleWorld("monitor-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object onlineState = newPlayerState();
        setField(onlineState, "last", player.getEyeLocation().clone().add(1, 0, 0));
        setField(onlineState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks() - 1);
        setField(onlineState, "searched", true);
        setField(onlineState, "indexRevision", 1L);
        putMap(display, "players", player.getUniqueId(), onlineState);

        UUID offline = UUID.randomUUID();
        Object offlineState = newPlayerState();
        setField(offlineState, "visible", new HashSet<>());
        putMap(display, "players", offline, offlineState);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("monitorPlayers");
        method.setAccessible(true);
        method.invoke(display);

        assertEquals(1, ((java.util.Map<?, ?>) getField(display, "players")).size());
        assertEquals(0, getField(onlineState, "positionStableTicks"));
        assertEquals(false, getField(onlineState, "searched"));
        assertTrue(((java.util.Set<?>) getField(onlineState, "visible")).isEmpty());
    }

    @Test
    void monitorPlayersRequeuesIdlePlayersAndDropsOfflinePlayers() throws Exception {
        var world = server.addSimpleWorld("monitor-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock online = server.addPlayer();
        online.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object onlineState = newPlayerState();
        setField(onlineState, "last", online.getEyeLocation().clone());
        setField(onlineState, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks() - 1);
        setField(onlineState, "searched", true);
        setField(onlineState, "indexRevision", 99L);

        UUID offlineId = UUID.randomUUID();
        Object offlineState = newPlayerState();
        setField(offlineState, "visible", new HashSet<>(java.util.List.of(
            new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2))));
        setField(offlineState, "desired", java.util.List.of());
        setField(offlineState, "searched", true);

        TextDisplay offlineText = mock(TextDisplay.class);
        Object offlineLabel = newLabel(offlineText);
        setField(offlineLabel, "viewers", new HashSet<>(java.util.List.of(offlineId)));

        putMap(display, "players", online.getUniqueId(), onlineState);
        putMap(display, "players", offlineId, offlineState);
        putMap(display, "labels", new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2), offlineLabel);
        setField(display, "queued", new HashSet<UUID>(java.util.List.of(offlineId)));
        setField(display, "allocationPending", new java.util.LinkedHashSet<UUID>(java.util.List.of(offlineId)));

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("monitorPlayers");
        method.setAccessible(true);
        method.invoke(display);

        assertTrue((boolean) getField(onlineState, "searched") == false);
        assertEquals(1, ((java.util.ArrayDeque<?>) getField(display, "searchQueue")).size());
        assertTrue(((java.util.Map<?, ?>) getField(display, "players")).containsKey(online.getUniqueId()));
        assertTrue(((java.util.Map<?, ?>) getField(display, "players")).containsKey(online.getUniqueId()));
        verify(offlineText).remove();
        assertTrue(((java.util.Map<?, ?>) getField(display, "labels")).isEmpty());
    }

    @Test
    void monitorPlayersResetsSearchedStateWhenIndexRevisionChanges() throws Exception {
        var world = server.addSimpleWorld("monitor-revision-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "7"}).applyToSign(sign);
        index.register(block);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object state = newPlayerState();
        setField(state, "last", player.getEyeLocation().clone());
        setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
        setField(state, "searched", true);
        setField(state, "indexRevision", 0L);
        putMap(display, "players", player.getUniqueId(), state);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("monitorPlayers");
        method.setAccessible(true);
        method.invoke(display);

        assertFalse((boolean) getField(state, "searched"));
        assertEquals(1, ((java.util.ArrayDeque<?>) getField(display, "searchQueue")).size());
    }

    @Test
    void monitorPlayersResetsSearchedStateWhenContentRevisionChanges() throws Exception {
        var world = server.addSimpleWorld("monitor-content-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "7"}).applyToSign(sign);
        index.register(block);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object state = newPlayerState();
        setField(state, "last", player.getEyeLocation().clone());
        setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
        setField(state, "searched", true);
        setField(state, "indexRevision", index.revision(world));
        setField(state, "contentRevision", index.contentRevision(world) - 1);
        putMap(display, "players", player.getUniqueId(), state);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("monitorPlayers");
        method.setAccessible(true);
        method.invoke(display);

        assertFalse((boolean) getField(state, "searched"));
        assertEquals(1, ((java.util.ArrayDeque<?>) getField(display, "searchQueue")).size());
    }

    @Test
    void monitorPlayersKeepsSearchedStateWhenRevisionMatchesAndPlayerIsNotIdle() throws Exception {
        var world = server.addSimpleWorld("monitor-noop-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object state = newPlayerState();
        setField(state, "last", player.getEyeLocation().clone());
        setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks() - 1);
        setField(state, "searched", true);
        setField(state, "indexRevision", index.revision(world));
        setField(state, "contentRevision", index.contentRevision(world));
        putMap(display, "players", player.getUniqueId(), state);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("monitorPlayers");
        method.setAccessible(true);
        method.invoke(display);

        assertTrue((boolean) getField(state, "searched"));
        assertEquals(0, ((java.util.ArrayDeque<?>) getField(display, "searchQueue")).size());
    }

    @Test
    void tickInvokesMonitorAndRefreshPaths() throws Exception {
        var world = server.addSimpleWorld("tick-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5, 0, 0));
        Object state = newPlayerState();
        setField(state, "last", player.getEyeLocation().clone());
        setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks() - 1);
        setField(state, "searched", false);
        putMap(display, "players", player.getUniqueId(), state);
        TextDisplay textDisplay = mock(TextDisplay.class);
        Object label = newLabel(textDisplay);
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);
        setField(label, "viewers", new HashSet<>());
        putMap(display, "labels", position, label);
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "7"}).applyToSign(sign);
        setField(display, "monitorTicks", ConfigLoader.getNearbyDisplayIntervalTicks() - 1);
        setField(display, "refreshTicks", 19);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("tick");
        method.setAccessible(true);
        method.invoke(display);

        verify(textDisplay).setText(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER));
        assertEquals(0, getField(display, "monitorTicks"));
        assertEquals(0, getField(display, "refreshTicks"));
    }

    @Test
    void createLabelReturnsTextDisplayForLoadedStorageSignAndNullForMissingBlock() throws Exception {
        var world = server.addSimpleWorld("create-label-display");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        Block block = world.getBlockAt(0, 65, 2);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "7"}).applyToSign(sign);
        var position = new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "createLabel", storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        Object label = method.invoke(display, position);

        assertNotNull(label);
        assertEquals(NearbyStorageSignDisplay.wrap(LONG_IDENTIFIER),
            ((TextDisplay) getField(label, "display")).getText());

        block.setType(Material.AIR);
        assertNull(method.invoke(display, position));
    }

    @Test
    void createLabelSpawnsTextDisplayForLoadedChunkWithResolvedSign() throws Exception {
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        org.bukkit.World world = mock(org.bukkit.World.class);
        Block block = mock(Block.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(block.getX()).thenReturn(0);
        when(block.getY()).thenReturn(65);
        when(block.getZ()).thenReturn(3);
        when(block.getLocation()).thenReturn(new Location(world, 0.5, 65, 3.5));
        org.bukkit.entity.TextDisplay textDisplay = mock(org.bukkit.entity.TextDisplay.class);
        StorageSign resolved = StorageSign.fromSignLines(
            new String[] {"StorageSign", LONG_IDENTIFIER, "7"});
        storagesign.index.StorageSignPosition position =
            new storagesign.index.StorageSignPosition(worldId, 0, 65, 2);

        try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
             MockedStatic<StorageSign> mockedSigns = org.mockito.Mockito.mockStatic(StorageSign.class)) {
            mockedBukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            when(world.getBlockAt(0, 65, 2)).thenReturn(block);
            mockedSigns.when(() -> StorageSign.fromBlock(block)).thenReturn(resolved);
            org.mockito.Mockito.doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<org.bukkit.entity.TextDisplay> initializer =
                    invocation.getArgument(2);
                initializer.accept(textDisplay);
                return textDisplay;
            }).when(world).spawn(
                org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.entity.TextDisplay.class),
                org.mockito.ArgumentMatchers.<Consumer<org.bukkit.entity.TextDisplay>>any());

            Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "createLabel", storagesign.index.StorageSignPosition.class);
            method.setAccessible(true);
            Object label = method.invoke(display, position);

            assertNotNull(label);
        }
    }

    @Test
    void createLabelReturnsNullWhenWorldMissingOrChunkUnloaded() throws Exception {
        var world = server.addSimpleWorld("create-label-missing");
        world.getChunkAt(0, 0).load();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);

        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "createLabel", storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);

        assertNull(method.invoke(display,
            new storagesign.index.StorageSignPosition(UUID.randomUUID(), 0, 65, 2)));
        assertNull(method.invoke(display,
            new storagesign.index.StorageSignPosition(world.getUID(), 32, 65, 32)));
    }

    @Test
    void hasLineOfSightHandlesNullWorldAndMatchingHitBlock() throws Exception {
        Location eye = mock(Location.class);
        when(eye.getWorld()).thenReturn(null);
        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hasLineOfSight", Location.class, org.bukkit.util.Vector.class, double.class,
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        boolean nullWorld = (boolean) method.invoke(null, eye, new org.bukkit.util.Vector(1, 0, 0),
            1.0, new storagesign.index.StorageSignPosition(UUID.randomUUID(), 0, 0, 0));
        assertFalse(nullWorld);

        org.bukkit.World world = mock(org.bukkit.World.class);
        Location liveEye = mock(Location.class);
        when(liveEye.getWorld()).thenReturn(world);
        when(liveEye.getX()).thenReturn(0.5);
        when(liveEye.getY()).thenReturn(64.0);
        when(liveEye.getZ()).thenReturn(0.5);
        Block hit = mock(Block.class);
        when(hit.getX()).thenReturn(0);
        when(hit.getY()).thenReturn(64);
        when(hit.getZ()).thenReturn(3);
        org.bukkit.util.RayTraceResult trace = mock(org.bukkit.util.RayTraceResult.class);
        when(trace.getHitBlock()).thenReturn(hit);
        when(world.rayTraceBlocks(liveEye, new org.bukkit.util.Vector(0, 0, 1), 3.25,
            org.bukkit.FluidCollisionMode.NEVER, true)).thenReturn(trace);
        boolean matched = (boolean) method.invoke(null, liveEye, new org.bukkit.util.Vector(0, 0, 1),
            3.0, new storagesign.index.StorageSignPosition(UUID.randomUUID(), 0, 64, 3));
        assertTrue(matched);
    }

    @Test
    void hasLineOfSightReturnsFalseWhenTraceHitsDifferentBlock() throws Exception {
        var world = server.addSimpleWorld("los-mismatch-display");
        world.getChunkAt(0, 0).load();
        Location liveEye = new Location(world, 0.5, 64.0, 0.5);
        Block hit = world.getBlockAt(2, 64, 2);
        org.bukkit.util.RayTraceResult trace = mock(org.bukkit.util.RayTraceResult.class);
        when(trace.getHitBlock()).thenReturn(hit);
        when(world.rayTraceBlocks(liveEye, new org.bukkit.util.Vector(0, 0, 1), 3.25,
            org.bukkit.FluidCollisionMode.NEVER, true)).thenReturn(trace);
        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hasLineOfSight", Location.class, org.bukkit.util.Vector.class, double.class,
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);
        boolean matched = (boolean) method.invoke(null, liveEye, new org.bukkit.util.Vector(0, 0, 1),
            3.0, new storagesign.index.StorageSignPosition(world.getUID(), 0, 64, 3));
        assertFalse(matched);
    }

    @Test
    void processSearchAndAllocationQueuesRespectZeroLimits() throws Exception {
        var world = server.addSimpleWorld("queue-zero-limit");
        world.getChunkAt(0, 0).load();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        int originalLimit = getNearbyDisplayGlobalLimit();
        try {
            setNearbyDisplaySearchesPerTick(0);
            setNearbyDisplayGlobalLimit(0);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            UUID playerId = UUID.randomUUID();
            Object state = newPlayerState();
            setField(state, "positionStableTicks", ConfigLoader.getNearbyDisplayIdleTicks());
            setField(state, "searched", false);
            putMap(display, "players", playerId, state);
            enqueue(display, playerId);

            Method search = NearbyStorageSignDisplay.class.getDeclaredMethod("processSearchQueue");
            search.setAccessible(true);
            search.invoke(display);

            java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
            pending.add(playerId);
            setField(display, "allocationPending", pending);
            Method alloc = NearbyStorageSignDisplay.class.getDeclaredMethod("processAllocationPending");
            alloc.setAccessible(true);
            alloc.invoke(display);

            assertEquals(0, display.activeLabelCount());
            assertEquals(1, ((java.util.ArrayDeque<?>) getField(display, "searchQueue")).size());
        } finally {
            setNearbyDisplaySearchesPerTick(originalSearches);
            setNearbyDisplayGlobalLimit(originalLimit);
        }
    }

    @Test
    void processAllocationPendingSkipsNullPlayerAndMissingState() throws Exception {
        var world = server.addSimpleWorld("allocation-null-display");
        world.getChunkAt(0, 0).load();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        StorageSignIndex index = new StorageSignIndex(plugin, true);
        NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
        try {
            setNearbyDisplaySearchesPerTick(1);
            UUID missingPlayer = UUID.randomUUID();
            UUID missingState = UUID.randomUUID();
            putMap(display, "players", missingState, null);
            java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
            pending.add(missingPlayer);
            pending.add(missingState);
            setField(display, "allocationPending", pending);

            Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("processAllocationPending");
            method.setAccessible(true);
            method.invoke(display);

            assertTrue(((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).isEmpty());
        } finally {
            setNearbyDisplaySearchesPerTick(originalSearches);
        }
    }

    @Test
    void processAllocationPendingQueuesRetryWhenLaterAllocationFails() throws Exception {
        var world = server.addSimpleWorld("allocation-retry-later-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        try {
            setNearbyDisplaySearchesPerTick(1);
            setNearbyDisplayGlobalLimit(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            PlayerMock player = server.addPlayer();
            UUID viewer = player.getUniqueId();
            setField(display, "players", new HashMap<UUID, Object>());
            setField(display, "labels", new java.util.LinkedHashMap<storagesign.index.StorageSignPosition, Object>());
            Object state = newPlayerState();
            setField(state, "searched", true);
            setField(state, "desired", java.util.List.of(
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2),
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1)));
            putMap(display, "players", viewer, state);
            Block first = world.getBlockAt(0, 65, 2);
            first.setType(Material.OAK_SIGN);
            Sign firstSign = (Sign) first.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"}).applyToSign(firstSign);
            Block second = world.getBlockAt(0, 65, 1);
            second.setType(Material.OAK_SIGN);
            Sign secondSign = (Sign) second.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "4"}).applyToSign(secondSign);
            java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
            pending.add(viewer);
            setField(display, "allocationPending", pending);

            try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                mockedBukkit.when(() -> Bukkit.getPlayer(viewer)).thenReturn(player);
                mockedBukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);

                Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("processAllocationPending");
                method.setAccessible(true);
                method.invoke(display);
            }

            assertEquals(1, ((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).size());
            assertEquals(1, ((java.util.Map<?, ?>) getField(display, "labels")).size());
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
            setNearbyDisplaySearchesPerTick(originalSearches);
        }
    }

    @Test
    void processAllocationPendingRequeuesTheFirstFailedPlayer() throws Exception {
        var world = server.addSimpleWorld("allocation-requeue-display");
        world.getChunkAt(0, 0).load();
        int originalLimit = getNearbyDisplayGlobalLimit();
        int originalSearches = getNearbyDisplaySearchesPerTick();
        try {
            setNearbyDisplaySearchesPerTick(1);
            setNearbyDisplayGlobalLimit(1);
            StorageSignIndex index = new StorageSignIndex(plugin, true);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            PlayerMock player = server.addPlayer();
            UUID viewer = player.getUniqueId();
            setField(display, "players", new HashMap<UUID, Object>());
            setField(display, "labels", new java.util.LinkedHashMap<storagesign.index.StorageSignPosition, Object>());
            Object state = newPlayerState();
            setField(state, "searched", true);
            setField(state, "desired", java.util.List.of(
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 2),
                new storagesign.index.StorageSignPosition(world.getUID(), 0, 65, 1)));
            putMap(display, "players", viewer, state);
            Block first = world.getBlockAt(0, 65, 2);
            first.setType(Material.OAK_SIGN);
            Sign firstSign = (Sign) first.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "3"}).applyToSign(firstSign);
            Block second = world.getBlockAt(0, 65, 1);
            second.setType(Material.OAK_SIGN);
            Sign secondSign = (Sign) second.getState();
            StorageSign.fromSignLines(new String[] {"StorageSign", LONG_IDENTIFIER, "4"}).applyToSign(secondSign);
            java.util.LinkedHashSet<UUID> pending = new java.util.LinkedHashSet<>();
            pending.add(viewer);
            setField(display, "allocationPending", pending);

            try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                mockedBukkit.when(() -> Bukkit.getPlayer(viewer)).thenReturn(player);
                mockedBukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);

                Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("processAllocationPending");
                method.setAccessible(true);
                method.invoke(display);
            }

            assertEquals(1, ((java.util.LinkedHashSet<?>) getField(display, "allocationPending")).size());
        } finally {
            setNearbyDisplayGlobalLimit(originalLimit);
            setNearbyDisplaySearchesPerTick(originalSearches);
        }
    }

    private static int getNearbyDisplayMaxPerPlayer() throws Exception {
        return getStaticInt(ConfigLoader.class, "nearbyDisplayMaxPerPlayer");
    }

    private static boolean getNearbyDisplayEnabled() throws Exception {
        return getStaticBoolean(ConfigLoader.class, "nearbyDisplayEnabled");
    }

    private static void setNearbyDisplayMaxPerPlayer(int value) throws Exception {
        setStaticInt(ConfigLoader.class, "nearbyDisplayMaxPerPlayer", value);
    }

    private static int getNearbyDisplayGlobalLimit() throws Exception {
        return getStaticInt(ConfigLoader.class, "nearbyDisplayGlobalLimit");
    }

    private static void setNearbyDisplayGlobalLimit(int value) throws Exception {
        setStaticInt(ConfigLoader.class, "nearbyDisplayGlobalLimit", value);
    }

    private static int getNearbyDisplaySearchesPerTick() throws Exception {
        return getStaticInt(ConfigLoader.class, "nearbyDisplaySearchesPerTick");
    }

    private static void setNearbyDisplaySearchesPerTick(int value) throws Exception {
        setStaticInt(ConfigLoader.class, "nearbyDisplaySearchesPerTick", value);
    }

    private static void setNearbyDisplayEnabled(boolean value) throws Exception {
        setStaticBoolean(ConfigLoader.class, "nearbyDisplayEnabled", value);
    }

    private static int getStaticInt(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void setStaticInt(Class<?> type, String fieldName, int value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static boolean getStaticBoolean(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static void setStaticBoolean(Class<?> type, String fieldName, boolean value)
            throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private static Object newPlayerState() throws Exception {
        Class<?> type = Class.forName("storagesign.display.NearbyStorageSignDisplay$PlayerState");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Object newLabel(TextDisplay display) throws Exception {
        Class<?> type = Class.forName("storagesign.display.NearbyStorageSignDisplay$Label");
        Constructor<?> ctor = type.getDeclaredConstructor(TextDisplay.class);
        ctor.setAccessible(true);
        return ctor.newInstance(display);
    }

    private static void putMap(Object target, String fieldName, Object key, Object value)
            throws Exception {
        @SuppressWarnings("unchecked")
        java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) getField(target, fieldName);
        map.put(key, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void enqueue(NearbyStorageSignDisplay display, UUID playerId) throws Exception {
        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("enqueue", UUID.class);
        method.setAccessible(true);
        method.invoke(display, playerId);
    }

}
