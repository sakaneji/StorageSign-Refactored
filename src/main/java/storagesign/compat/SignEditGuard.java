package storagesign.compat;

import java.lang.reflect.Method;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import storagesign.StorageSign;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;

/** Registers the first compatible sign-open event without linking server-specific APIs. */
public final class SignEditGuard {

    private static final PluginLogger LOG = PluginLogger.getLogger(SignEditGuard.class);
    private final String[] eventCandidates;

    public SignEditGuard() {
        this("io.papermc.paper.event.player.PlayerOpenSignEvent",
            "org.bukkit.event.player.PlayerSignOpenEvent");
    }

    SignEditGuard(String... eventCandidates) {
        this.eventCandidates = eventCandidates.clone();
    }

    public boolean register(JavaPlugin plugin) {
        for (String className : eventCandidates) {
            if (register(plugin, className)) {
                LOG.info("register", "看板編集ガードを登録しました: " + className);
                return true;
            }
        }
        LOG.warning("register", "互換性のある看板編集イベントがないため、編集ガードを無効化しました");
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean register(JavaPlugin plugin, String className) {
        try {
            Class<?> candidate = Class.forName(className, false, plugin.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(candidate) || !Cancellable.class.isAssignableFrom(candidate)) {
                return false;
            }
            Method getSign = candidate.getMethod("getSign");
            if (!Sign.class.isAssignableFrom(getSign.getReturnType())) return false;

            Class<? extends Event> eventClass = (Class<? extends Event>) candidate;
            PluginManager manager = plugin.getServer().getPluginManager();
            Listener owner = new Listener() {};
            manager.registerEvent(eventClass, owner, EventPriority.HIGH, (listener, event) -> {
                if (((Cancellable) event).isCancelled()) return;
                Object result;
                try {
                    result = getSign.invoke(event);
                } catch (ReflectiveOperationException e) {
                    throw new EventException(e);
                }
                if (!(result instanceof Sign sign)) return;
                Block block = sign.getBlock();
                if (MaterialRegistry.isAnySign(block.getType()) && StorageSign.isStorageSign(block)) {
                    ((Cancellable) event).setCancelled(true);
                    LOG.trace("onSignOpen", () -> "cancelled sign edit=" + block.getLocation());
                }
            }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
