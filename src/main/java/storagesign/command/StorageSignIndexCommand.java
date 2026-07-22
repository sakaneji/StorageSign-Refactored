package storagesign.command;

import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import storagesign.ConfigLoader;
import storagesign.index.StorageSignIndex;

/** Administrative status and rebuild command for the StorageSign position index. */
public final class StorageSignIndexCommand implements CommandExecutor {
    private final StorageSignIndex index;

    public StorageSignIndexCommand(StorageSignIndex index) {
        this.index = index;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("storagesign.index.admin")) {
            sender.sendMessage("§c" + ConfigLoader.getNoPermission());
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }
        if (!args[0].equalsIgnoreCase("rebuild") || args.length > 2) return false;
        if (!index.isEnabled()) {
            sender.sendMessage("§eStorageSign index is disabled by storage-index.enabled.");
            return true;
        }
        Collection<World> worlds = resolveWorlds(sender, args);
        if (worlds == null) return true;
        int chunks = worlds.stream().mapToInt(world -> world.getLoadedChunks().length).sum();
        boolean started = index.rebuild(worlds, result -> {
            boolean saving = index.saveAsync(save -> sender.sendMessage(save.success()
                ? "§aStorageSign index rebuild and save complete: chunks=" + result.chunksScanned()
                    + ", signs=" + result.countAfter() + ", bytes=" + save.bytes()
                : "§cStorageSign index rebuild completed but save failed: " + save.message()));
            if (!saving) sender.sendMessage("§cRebuild completed; the index save could not be scheduled.");
        });
        if (!started) {
            sender.sendMessage("§eA StorageSign index rebuild is already running.");
            return true;
        }
        sender.sendMessage("§aStorageSign index rebuild started: loaded chunks=" + chunks);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§aStorageSign index: " + (index.isEnabled() ? "enabled" : "disabled"));
        sender.sendMessage("§aNearby display: "
            + (ConfigLoader.getEffectiveNearbyDisplayEnabled() ? "enabled" : "disabled"));
        if (!index.isEnabled()) return;
        sender.sendMessage("§aIndexed signs: " + index.size()
            + (index.isRebuilding() ? " (rebuilding, remaining chunks=" + index.getRebuildRemaining() + ")" : ""));
        sender.sendMessage("§aPersistence: format=" + index.getFormatVersion()
            + ", load=" + index.getLoadStatus() + ", saving=" + index.isSaving()
            + ", lastCount=" + index.getLastSavedCount() + ", bytes=" + index.getLastFileSize()
            + ", lastSavedAt=" + index.getLastSavedAt());
        for (World world : Bukkit.getWorlds()) {
            sender.sendMessage("§7- " + world.getName() + ": " + index.size(world));
        }
    }

    private Collection<World> resolveWorlds(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) return List.of(player.getWorld());
            sender.sendMessage("§cConsole must specify 'all' or a world name.");
            return null;
        }
        if (args[1].equalsIgnoreCase("all")) return List.copyOf(Bukkit.getWorlds());
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage("§cUnknown world: " + args[1]);
            return null;
        }
        return List.of(world);
    }
}
