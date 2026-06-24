package storagesign.display;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.index.StorageSignIndex;
import storagesign.index.StorageSignPosition;

class NearbyStorageSignDisplayTest {
    @Test
    void forwardConeUsesConfiguredFullAngle() {
        Vector forward = new Vector(0, 0, 1);

        assertTrue(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(0.9, 0, 1), 90.0));
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(1.1, 0, 1), 90.0));
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(0, 0, -1), 90.0));
    }

    @Test
    void forwardConeHonorsHalfAngleBoundary() {
        Vector forward = new Vector(0, 0, 1);

        assertTrue(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(1, 0, 1), 180.0));
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(1, 0, 1), 89.0));
    }

    @Test
    void forwardConeRejectsZeroVectors() {
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            new Vector(0, 0, 0), new Vector(1, 0, 0), 90.0));
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            new Vector(1, 0, 0), new Vector(0, 0, 0), 90.0));
    }

    @Test
    void movementIncludesPositionAndViewChanges() {
        Location original = new Location(null, 1, 2, 3, 20, 10);

        assertFalse(NearbyStorageSignDisplay.moved(original, original.clone()));
        assertTrue(NearbyStorageSignDisplay.moved(
            original, new Location(null, 1.1, 2, 3, 20, 10)));
        assertTrue(NearbyStorageSignDisplay.moved(
            original, new Location(null, 1, 2, 3, 21, 10)));
    }

    @Test
    void movementTreatsWorldChangesAsMovement() {
        World first = org.mockito.Mockito.mock(World.class);
        World second = org.mockito.Mockito.mock(World.class);
        Location original = new Location(first, 1, 2, 3, 20, 10);
        Location changed = new Location(second, 1, 2, 3, 20, 10);

        assertTrue(NearbyStorageSignDisplay.moved(original, changed));
    }

    @Test
    void movementUsesSmallThresholdForNearlyIdenticalLocations() {
        Location original = new Location(null, 1, 2, 3, 20, 10);
        assertFalse(NearbyStorageSignDisplay.moved(
            original, new Location(null, 1.00001, 2, 3, 20, 10)));
        assertTrue(NearbyStorageSignDisplay.moved(
            original, new Location(null, 1.01, 2, 3, 20, 10)));
    }

    @Test
    void movementHandlesAngleWraparound() {
        Location original = new Location(null, 1, 2, 3, 359, 10);
        Location changed = new Location(null, 1, 2, 3, 1, 10);

        assertTrue(NearbyStorageSignDisplay.moved(original, changed));
    }

    @Test
    void hasLineOfSightRejectsNullWorldAndAcceptsDirectTargetHit() throws Exception {
        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hasLineOfSight", Location.class, Vector.class, double.class,
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(null,
            new Location(null, 0, 0, 0), new Vector(1, 0, 0), 1.0,
            new storagesign.index.StorageSignPosition(java.util.UUID.randomUUID(), 0, 0, 0)));

        World world = mock(World.class);
        Location eye = new Location(world, 0, 0, 0);
        Block hit = mock(Block.class);
        when(hit.getX()).thenReturn(1);
        when(hit.getY()).thenReturn(0);
        when(hit.getZ()).thenReturn(0);
        RayTraceResult trace = mock(RayTraceResult.class);
        when(trace.getHitBlock()).thenReturn(hit);
        when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
            org.mockito.ArgumentMatchers.any(Vector.class),
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(trace);
        assertTrue((boolean) method.invoke(null, eye, new Vector(1, 0, 0), 1.0,
            new storagesign.index.StorageSignPosition(java.util.UUID.randomUUID(), 1, 0, 0)));
    }

    @Test
    void hasLineOfSightAcceptsNoHitAndRejectsDifferentHit() throws Exception {
        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hasLineOfSight", Location.class, Vector.class, double.class,
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);

        World world = mock(World.class);
        Location eye = new Location(world, 0, 0, 0);
        when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
            org.mockito.ArgumentMatchers.any(Vector.class),
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(null);
        assertTrue((boolean) method.invoke(null, eye, new Vector(1, 0, 0), 1.0,
            new storagesign.index.StorageSignPosition(java.util.UUID.randomUUID(), 1, 0, 0)));

        Block hit = mock(Block.class);
        when(hit.getX()).thenReturn(2);
        when(hit.getY()).thenReturn(0);
        when(hit.getZ()).thenReturn(0);
        RayTraceResult trace = mock(RayTraceResult.class);
        when(trace.getHitBlock()).thenReturn(hit);
        when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
            org.mockito.ArgumentMatchers.any(Vector.class),
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(trace);
        assertFalse((boolean) method.invoke(null, eye, new Vector(1, 0, 0), 1.0,
            new storagesign.index.StorageSignPosition(java.util.UUID.randomUUID(), 1, 0, 0)));
    }

    @Test
    void hasLineOfSightAcceptsTraceWithoutHitBlock() throws Exception {
        Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
            "hasLineOfSight", Location.class, Vector.class, double.class,
            storagesign.index.StorageSignPosition.class);
        method.setAccessible(true);

        World world = mock(World.class);
        Location eye = new Location(world, 0, 0, 0);
        RayTraceResult trace = mock(RayTraceResult.class);
        when(trace.getHitBlock()).thenReturn(null);
        when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
            org.mockito.ArgumentMatchers.any(Vector.class),
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(trace);
        assertTrue((boolean) method.invoke(null, eye, new Vector(1, 0, 0), 1.0,
            new StorageSignPosition(java.util.UUID.randomUUID(), 1, 0, 0)));
    }

    @Test
    void selectSkipsZeroDistanceBehindAndRespectsLimit() throws Exception {
        MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            StorageSignIndex index = mock(StorageSignIndex.class);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            World world = mock(World.class);
            Location eye = mock(Location.class);
            when(eye.getWorld()).thenReturn(world);
            when(eye.getX()).thenReturn(0.5);
            when(eye.getY()).thenReturn(64.5);
            when(eye.getZ()).thenReturn(0.5);
            when(eye.getDirection()).thenReturn(new Vector(0, 0, 1));
            Player player = mock(Player.class);
            when(player.getEyeLocation()).thenReturn(eye);

            StorageSignPosition zero = new StorageSignPosition(java.util.UUID.randomUUID(), 0, 64, 0);
            StorageSignPosition behind = new StorageSignPosition(java.util.UUID.randomUUID(), 0, 64, -3);
            StorageSignPosition front = new StorageSignPosition(java.util.UUID.randomUUID(), 0, 64, 3);
            when(index.findNearby(eye, ConfigLoader.getNearbyDisplayDistance()))
                .thenReturn(java.util.List.of(zero, behind, front));
            when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.any(Vector.class),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(null);

            Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("select", Player.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<StorageSignPosition> result =
                (java.util.List<StorageSignPosition>) method.invoke(display, player);

            assertEquals(1, result.size());
            assertEquals(front, result.getFirst());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void selectSkipsTargetsBlockedByLineOfSight() throws Exception {
        MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            StorageSignIndex index = mock(StorageSignIndex.class);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            World world = mock(World.class);
            Location eye = mock(Location.class);
            when(eye.getWorld()).thenReturn(world);
            when(eye.getX()).thenReturn(0.5);
            when(eye.getY()).thenReturn(64.5);
            when(eye.getZ()).thenReturn(0.5);
            when(eye.getDirection()).thenReturn(new Vector(0, 0, 1));
            Player player = mock(Player.class);
            when(player.getEyeLocation()).thenReturn(eye);

            StorageSignPosition front = new StorageSignPosition(java.util.UUID.randomUUID(), 0, 64, 3);
            when(index.findNearby(eye, ConfigLoader.getNearbyDisplayDistance()))
                .thenReturn(java.util.List.of(front));
            Block hit = mock(Block.class);
            when(hit.getX()).thenReturn(0);
            when(hit.getY()).thenReturn(64);
            when(hit.getZ()).thenReturn(2);
            RayTraceResult trace = mock(RayTraceResult.class);
            when(trace.getHitBlock()).thenReturn(hit);
            when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.any(Vector.class),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(trace);

            Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("select", Player.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<StorageSignPosition> result =
                (java.util.List<StorageSignPosition>) method.invoke(display, player);

            assertEquals(0, result.size());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void selectStopsAfterReachingPerPlayerLimit() throws Exception {
        MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            StorageSignIndex index = mock(StorageSignIndex.class);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, index);
            World world = mock(World.class);
            Location eye = mock(Location.class);
            when(eye.getWorld()).thenReturn(world);
            when(eye.getX()).thenReturn(0.5);
            when(eye.getY()).thenReturn(64.5);
            when(eye.getZ()).thenReturn(0.5);
            when(eye.getDirection()).thenReturn(new Vector(0, 0, 1));
            Player player = mock(Player.class);
            when(player.getEyeLocation()).thenReturn(eye);

            Field field = ConfigLoader.class.getDeclaredField("nearbyDisplayMaxPerPlayer");
            field.setAccessible(true);
            int original = field.getInt(null);
            field.setInt(null, 1);
            try {
                StorageSignPosition first = new StorageSignPosition(java.util.UUID.randomUUID(), 0, 64, 3);
                StorageSignPosition second = new StorageSignPosition(java.util.UUID.randomUUID(), 0, 64, 4);
                when(index.findNearby(eye, ConfigLoader.getNearbyDisplayDistance()))
                    .thenReturn(java.util.List.of(first, second));
                when(world.rayTraceBlocks(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.any(Vector.class),
                    org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(null);

                Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("select", Player.class);
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<StorageSignPosition> result =
                    (java.util.List<StorageSignPosition>) method.invoke(display, player);

                assertEquals(1, result.size());
                assertEquals(first, result.getFirst());
            } finally {
                field.setInt(null, original);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void createLabelReturnsNullWhenWorldOrChunkIsMissing() throws Exception {
        MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, new StorageSignIndex(plugin, true));
            Method method = NearbyStorageSignDisplay.class.getDeclaredMethod(
                "createLabel", StorageSignPosition.class);
            method.setAccessible(true);

            assertEquals(null, method.invoke(display,
                new StorageSignPosition(java.util.UUID.randomUUID(), 1, 64, 1)));

            var world = MockBukkit.getMock().addSimpleWorld("label-chunk");
            assertEquals(null, method.invoke(display,
                new StorageSignPosition(world.getUID(), 16, 64, 16)));

            var loaded = MockBukkit.getMock().addSimpleWorld("label-not-sign");
            loaded.getChunkAt(0, 0).load();
            assertEquals(null, method.invoke(display,
                new StorageSignPosition(loaded.getUID(), 1, 64, 1)));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void wrappingNeverRemovesIdentifierText() {
        String identifier = "NETHERITE_UPGRADE_SMITHING_TEMPLATE_WITH_EXTRA_SUFFIX";
        String wrapped = NearbyStorageSignDisplay.wrap(identifier);

        assertTrue(wrapped.contains("\n"));
        assertTrue(wrapped.replace("\n", "").equals(identifier));
    }

    @Test
    void wrappingLeavesShortIdentifiersUnchangedAndWrapsAtColumnBoundary() {
        assertTrue(NearbyStorageSignDisplay.wrap("STONE").equals("STONE"));
        String boundary = "1234567890123456789012345678";
        assertTrue(NearbyStorageSignDisplay.wrap(boundary).equals(boundary));
    }

    @Test
    void wrappingNullCollapsesToEmpty() {
        assertEquals("", NearbyStorageSignDisplay.wrap(""));
    }

    @Test
    void labelTextAndEnqueueAreDeterministicForPrivateHelpers() throws Exception {
        MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, new StorageSignIndex(plugin, true));

            Method enqueue = NearbyStorageSignDisplay.class.getDeclaredMethod("enqueue", java.util.UUID.class);
            enqueue.setAccessible(true);
            java.util.UUID id = java.util.UUID.randomUUID();
            enqueue.invoke(display, id);
            enqueue.invoke(display, id);
            java.util.ArrayDeque<?> queue = (java.util.ArrayDeque<?>) getField(display, "searchQueue");
            assertEquals(1, queue.size());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void labelTextUsesWrappedIdentifierAndAmountSuffix() throws Exception {
        MockBukkit.mock();
        try {
            StorageSign sign = mock(StorageSign.class);
            when(sign.getIdentifier()).thenReturn(
                "NETHERITE_UPGRADE_SMITHING_TEMPLATE_WITH_EXTRA_SUFFIX");
            when(sign.getAmount()).thenReturn(3);
            Method method = NearbyStorageSignDisplay.class.getDeclaredMethod("labelText", StorageSign.class);
            method.setAccessible(true);
            String text = (String) method.invoke(null, sign);

            assertTrue(text.contains("\n× 3"));
            assertTrue(text.replace("\n", "").contains("NETHERITE_UPGRADE_SMITHING_TEMPLATE_WITH_EXTRA_SUFFIX"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void shutdownClearsQueuedAndAllocatedStateWithoutRunningTask() throws Exception {
        MockBukkit.mock();
        try {
            StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
            NearbyStorageSignDisplay display = new NearbyStorageSignDisplay(plugin, new StorageSignIndex(plugin, true));
            Method enqueue = NearbyStorageSignDisplay.class.getDeclaredMethod("enqueue", java.util.UUID.class);
            enqueue.setAccessible(true);
            enqueue.invoke(display, java.util.UUID.randomUUID());
            display.shutdown();
            assertEquals(0, display.activeLabelCount());
            assertTrue(((java.util.ArrayDeque<?>) getField(display, "searchQueue")).isEmpty());
        } finally {
            MockBukkit.unmock();
        }
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
