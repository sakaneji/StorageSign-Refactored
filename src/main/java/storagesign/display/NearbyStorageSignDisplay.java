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
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import storagesign.compat.SignDisplayFormatter;
import storagesign.index.StorageSignIndex;
import storagesign.index.StorageSignPosition;

/** Shows shared, viewer-scoped TextDisplays for nearby indexed StorageSigns. */
public final class NearbyStorageSignDisplay {
    private static final int TEXT_WRAP_COLUMNS = 28;

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
            if (state.last == null || moved(state.last, current)) {
                clearPlayer(player.getUniqueId(), player, state);
                state.last = current.clone();
                state.stableTicks = 0;
                state.searched = false;
                continue;
            }
            state.stableTicks += interval;
            if (state.searched && (
                state.indexRevision != index.revision(player.getWorld())
                    || state.contentRevision != index.contentRevision(player.getWorld()))) {
                state.searched = false;
            }
            if (!state.searched && state.stableTicks >= ConfigLoader.getNearbyDisplayIdleTicks()) {
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
                || state.stableTicks < ConfigLoader.getNearbyDisplayIdleTicks()) continue;
            state.desired = select(player);
            state.searched = true;
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
        World world = eye.getWorld();
        if (world == null) return false;
        RayTraceResult trace = world.rayTraceBlocks(
            eye, direction.normalize(), distance + 0.25, FluidCollisionMode.NEVER, true);
        if (trace == null || trace.getHitBlock() == null) return true;
        Block hit = trace.getHitBlock();
        return hit.getX() == target.x() && hit.getY() == target.y() && hit.getZ() == target.z();
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
        if (!shouldDisplay(storageSign)) return null;
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
            if (world != null && world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                storageSign = StorageSign.fromBlock(world.getBlockAt(position.x(), position.y(), position.z()));
            }
            if (storageSign == null) {
                removeLabel(position, label, true);
                index.unregister(position);
            } else if (!shouldDisplay(storageSign)) {
                removeLabel(position, label, false);
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
                enqueue(viewer);
            }
        }
        label.viewers.clear();
    }

    static boolean moved(Location previous, Location current) {
        if (previous.getWorld() != current.getWorld()) return true;
        double dx = previous.getX() - current.getX();
        double dy = previous.getY() - current.getY();
        double dz = previous.getZ() - current.getZ();
        if (dx * dx + dy * dy + dz * dz > 0.0001) return true;
        return angleDifference(previous.getYaw(), current.getYaw()) > 0.5f
            || Math.abs(previous.getPitch() - current.getPitch()) > 0.5f;
    }

    private static float angleDifference(float first, float second) {
        float difference = Math.abs(first - second) % 360.0f;
        return difference > 180.0f ? 360.0f - difference : difference;
    }

    static boolean isInForwardCone(Vector forward, Vector direction, double fieldOfViewDegrees) {
        if (forward.lengthSquared() == 0.0 || direction.lengthSquared() == 0.0) return false;
        double cosine = Math.cos(Math.toRadians(fieldOfViewDegrees / 2.0));
        return forward.clone().normalize().dot(direction.clone().normalize()) >= cosine;
    }

    private static String labelText(StorageSign storageSign) {
        return wrap(storageSign.getIdentifier());
    }

    private static boolean shouldDisplay(StorageSign storageSign) {
        if (storageSign == null || storageSign.isUnregistered()) return false;
        return !storageSign.getIdentifier().equals(SignDisplayFormatter.fit(storageSign.getIdentifier()));
    }

    static String wrap(String value) {
        StringBuilder wrapped = new StringBuilder(value.length() + 8);
        int column = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            wrapped.append(character);
            column++;
            if (column >= TEXT_WRAP_COLUMNS && i + 1 < value.length()) {
                wrapped.append('\n');
                column = 0;
            }
        }
        return wrapped.toString();
    }

    private static final class PlayerState {
        private Location last;
        private int stableTicks;
        private boolean searched;
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
