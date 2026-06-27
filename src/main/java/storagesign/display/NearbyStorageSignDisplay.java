package storagesign.display;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.index.StorageSignIndex;
import storagesign.index.StorageSignPosition;

/** Shows shared, viewer-scoped TextDisplays for nearby indexed StorageSigns. */
public final class NearbyStorageSignDisplay {
    private final StorageSignPlugin plugin;
    private final StorageSignIndex index;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final Map<StorageSignPosition, Label> labels = new HashMap<>();
    private final ArrayDeque<UUID> searchQueue = new ArrayDeque<>();
    private final Set<UUID> queued = new HashSet<>();
    private final LinkedHashSet<UUID> allocationPending = new LinkedHashSet<>();
    private BukkitTask task;
    private int refreshTicks;
    private int monitorTicks;

    public NearbyStorageSignDisplay(StorageSignPlugin plugin, StorageSignIndex index) {
        this.plugin = plugin;
        this.index = index;
    }

    public void start() {
        if (!ConfigLoader.getEffectiveNearbyDisplayEnabled() || task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public int activeLabelCount() {
        return labels.size();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Label label : List.copyOf(labels.values())) label.display.remove();
        labels.clear();
        players.clear();
        searchQueue.clear();
        queued.clear();
        allocationPending.clear();
    }

    private void tick() {
        monitorTicks++;
        refreshTicks++;
        if (monitorTicks >= ConfigLoader.getNearbyDisplayIntervalTicks()) {
            monitorTicks = 0;
            monitorPlayers();
        }
        processSearchQueue();
        processAllocationPending();
        if (refreshTicks >= 20) {
            refreshTicks = 0;
            refreshLabels();
        }
    }

    private void monitorPlayers() {
        Set<UUID> online = new HashSet<>();
        int interval = ConfigLoader.getNearbyDisplayIntervalTicks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            PlayerState state = players.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
            Location current = player.getEyeLocation();
            if (state.last == null) {
                state.last = current.clone();
                state.positionStableTicks = 0;
                state.viewStableTicks = 0;
                continue;
            }

            boolean positionMoved = movedPosition(state.last, current);
            boolean viewMoved = movedView(state.last, current);
            if (positionMoved) {
                state.positionStableTicks = 0;
                state.searched = false;
                state.needsRescan = true;
            } else {
                state.positionStableTicks += interval;
            }
            if (viewMoved) {
                state.viewStableTicks = 0;
                state.searched = false;
                state.needsRescan = true;
            } else {
                state.viewStableTicks += interval;
            }
            state.last = current.clone();
            if (state.searched && (
                state.indexRevision != index.revision(player.getWorld())
                    || state.contentRevision != index.contentRevision(player.getWorld()))) {
                state.searched = false;
                state.needsRescan = true;
            }

            boolean idle = state.positionStableTicks >= ConfigLoader.getNearbyDisplayIdleTicks();
            if (!state.searched && state.needsRescan && idle) {
                enqueue(player.getUniqueId());
            }
        }
        for (Iterator<Map.Entry<UUID, PlayerState>> iterator = players.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, PlayerState> entry = iterator.next();
            if (online.contains(entry.getKey())) continue;
            clearPlayer(entry.getKey(), Bukkit.getPlayer(entry.getKey()), entry.getValue());
            queued.remove(entry.getKey());
            allocationPending.remove(entry.getKey());
            iterator.remove();
        }
    }

    private void enqueue(UUID playerId) {
        if (queued.add(playerId)) searchQueue.addLast(playerId);
    }

    private void processSearchQueue() {
        int maximum = ConfigLoader.getNearbyDisplaySearchesPerTick();
        for (int processed = 0; processed < maximum && !searchQueue.isEmpty(); processed++) {
            UUID playerId = searchQueue.removeFirst();
            queued.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            PlayerState state = players.get(playerId);
            if (player == null || state == null || state.searched
                || state.positionStableTicks < ConfigLoader.getNearbyDisplayIdleTicks()) continue;
            state.desired = select(player);
            state.searched = true;
            state.needsRescan = false;
            state.indexRevision = index.revision(player.getWorld());
            state.contentRevision = index.contentRevision(player.getWorld());
            if (!applyDesired(player, state)) allocationPending.add(playerId);
        }
    }

    private void processAllocationPending() {
        if (labels.size() >= ConfigLoader.getNearbyDisplayGlobalLimit()) return;
        int maximum = ConfigLoader.getNearbyDisplaySearchesPerTick();
        Iterator<UUID> iterator = allocationPending.iterator();
        UUID retry = null;
        for (int processed = 0; processed < maximum && iterator.hasNext();) {
            UUID playerId = iterator.next();
            iterator.remove();
            Player player = Bukkit.getPlayer(playerId);
            PlayerState state = players.get(playerId);
            if (player == null || state == null || !state.searched) continue;
            processed++;
            if (!applyDesired(player, state)) {
                retry = playerId;
                break;
            }
        }
        if (retry != null) allocationPending.add(retry);
    }

    private List<StorageSignPosition> select(Player player) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        int maximum = ConfigLoader.getNearbyDisplayMaxPerPlayer();
        List<StorageSignPosition> result = new ArrayList<>(maximum);
        for (StorageSignPosition position : index.findNearby(eye, ConfigLoader.getNearbyDisplayDistance())) {
            Vector direction = new Vector(
                position.x() + 0.5 - eye.getX(),
                position.y() + 0.5 - eye.getY(),
                position.z() + 0.5 - eye.getZ());
            double distance = direction.length();
            if (distance == 0.0
                || !isInForwardCone(forward, direction, ConfigLoader.getNearbyDisplayFov())) continue;
            if (!hasLineOfSight(eye, direction, distance, position)) continue;
            result.add(position);
            if (result.size() >= maximum) break;
        }
        return List.copyOf(result);
    }

    private static boolean hasLineOfSight(Location eye, Vector direction, double distance,
                                          StorageSignPosition target) {
        return NearbyStorageSignDisplaySupport.hasLineOfSight(eye, direction, distance, target);
    }

    private boolean applyDesired(Player player, PlayerState state) {
        for (StorageSignPosition visible : List.copyOf(state.visible)) {
            if (!state.desired.contains(visible)) hide(player, state, visible);
        }
        boolean complete = true;
        for (StorageSignPosition position : state.desired) {
            if (state.visible.contains(position)) continue;
            Label label = labels.get(position);
            if (label == null) {
                if (labels.size() >= ConfigLoader.getNearbyDisplayGlobalLimit()) {
                    complete = false;
                    continue;
                }
                label = createLabel(position);
                if (label == null) continue;
                labels.put(position, label);
            }
            label.viewers.add(player.getUniqueId());
            state.visible.add(position);
            player.showEntity(plugin, label.display);
        }
        return complete;
    }

    private Label createLabel(StorageSignPosition position) {
        World world = Bukkit.getWorld(position.worldId());
        if (world == null || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) return null;
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        StorageSign storageSign = StorageSign.fromBlock(block);
        if (storageSign == null) {
            index.unregister(position);
            return null;
        }
        if (!shouldDisplay(storageSign, block.getType())) return null;
        Location location = new Location(world, position.x() + 0.5, position.y() + 1.25, position.z() + 0.5);
        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setVisibleByDefault(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setLineWidth(180);
            entity.setText(labelText(storageSign));
        });
        return new Label(display);
    }

    private void refreshLabels() {
        for (Map.Entry<StorageSignPosition, Label> entry : List.copyOf(labels.entrySet())) {
            StorageSignPosition position = entry.getKey();
            Label label = entry.getValue();
            World world = Bukkit.getWorld(position.worldId());
            StorageSign storageSign = null;
            boolean displayable = false;
            if (world != null && world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                Block block = world.getBlockAt(position.x(), position.y(), position.z());
                storageSign = StorageSign.fromBlock(block);
                if (storageSign != null) {
                    displayable = shouldDisplay(storageSign, block.getType());
                }
            }
            if (storageSign == null) {
                removeLabel(position, label, true);
                index.unregister(position);
            } else if (!displayable) {
                removeLabel(position, label, true);
            } else {
                label.display.setText(labelText(storageSign));
            }
        }
    }

    private void clearPlayer(UUID playerId, Player player, PlayerState state) {
        for (StorageSignPosition position : List.copyOf(state.visible)) {
            hide(playerId, player, state, position);
        }
        state.desired = List.of();
        allocationPending.remove(playerId);
    }

    private void hide(Player player, PlayerState state, StorageSignPosition position) {
        hide(player == null ? null : player.getUniqueId(), player, state, position);
    }

    private void hide(UUID playerId, Player player, PlayerState state, StorageSignPosition position) {
        Label label = labels.get(position);
        state.visible.remove(position);
        if (label == null) return;
        if (playerId != null) label.viewers.remove(playerId);
        if (player != null) player.hideEntity(plugin, label.display);
        if (label.viewers.isEmpty()) removeLabel(position, label, false);
    }

    private void removeLabel(StorageSignPosition position, Label label, boolean rescanViewers) {
        labels.remove(position);
        label.display.remove();
        for (UUID viewer : List.copyOf(label.viewers)) {
            PlayerState state = players.get(viewer);
            if (state == null) continue;
            state.visible.remove(position);
            if (rescanViewers) {
                state.searched = false;
                state.needsRescan = true;
                enqueue(viewer);
            }
        }
        label.viewers.clear();
    }

    static boolean moved(Location previous, Location current) {
        return NearbyStorageSignDisplaySupport.moved(previous, current);
    }

    static boolean movedPosition(Location previous, Location current) {
        return NearbyStorageSignDisplaySupport.movedPosition(previous, current);
    }

    static boolean movedView(Location previous, Location current) {
        return NearbyStorageSignDisplaySupport.movedView(previous, current);
    }

    static boolean isInForwardCone(Vector forward, Vector direction, double fieldOfViewDegrees) {
        return NearbyStorageSignDisplaySupport.isInForwardCone(forward, direction, fieldOfViewDegrees);
    }

    private static String labelText(StorageSign storageSign) {
        return NearbyStorageSignDisplaySupport.labelText(storageSign);
    }

    private static boolean shouldDisplay(StorageSign storageSign, Material signMaterial) {
        return NearbyStorageSignDisplaySupport.shouldDisplay(storageSign, signMaterial);
    }

    static String wrap(String value) {
        return NearbyStorageSignDisplaySupport.wrap(value);
    }

    private static final class PlayerState {
        private Location last;
        private int positionStableTicks;
        private int viewStableTicks;
        private boolean searched;
        private boolean needsRescan = true;
        private long indexRevision;
        private long contentRevision;
        private List<StorageSignPosition> desired = List.of();
        private final Set<StorageSignPosition> visible = new HashSet<>();
    }

    private static final class Label {
        private final TextDisplay display;
        private final Set<UUID> viewers = new HashSet<>();

        private Label(TextDisplay display) {
            this.display = display;
        }
    }
}
