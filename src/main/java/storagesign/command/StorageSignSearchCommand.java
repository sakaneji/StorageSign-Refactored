package storagesign.command;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import storagesign.ConfigLoader;
import storagesign.StorageSignFacingSupport;
import storagesign.index.IndexedStorageSign;
import storagesign.index.StorageSignIndex;
import storagesign.index.StorageSignPosition;
import storagesign.search.StorageSignQueryService;
import storagesign.search.StorageSignSearchCriteria;
import storagesign.search.StorageSignSearchResult;

/** Extensible administrator search command; currently exposes the item criterion. */
public final class StorageSignSearchCommand implements CommandExecutor {
    private final StorageSignIndex index;
    private final StorageSignQueryService queries;

    public StorageSignSearchCommand(StorageSignIndex index, StorageSignQueryService queries) {
        this.index = index;
        this.queries = queries;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("storagesign.search.admin")) {
            sender.sendMessage("§c" + ConfigLoader.getNoPermission());
            return true;
        }
        if (!index.isEnabled()) {
            sender.sendMessage("§eStorageSign index is disabled by storage-index.enabled.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("item")) return false;
        Parsed parsed = parse(sender, args);
        if (parsed == null) return true;
        StorageSignSearchCriteria criteria = new StorageSignSearchCriteria(
            args[1], parsed.contains ? StorageSignSearchCriteria.MatchMode.CONTAINS
                : StorageSignSearchCriteria.MatchMode.EXACT,
            parsed.worldId, null, null);
        if (parsed.mode == OutputMode.DEFAULT) {
            sender.sendMessage("§7Searching StorageSign index...");
        }
        boolean accepted = queries.search(criteria,
            result -> show(sender, args[1], parsed.page, result, parsed.mode),
            error -> sender.sendMessage("§cStorageSign search failed: " + error.getMessage()));
        if (!accepted) sender.sendMessage("§eToo many StorageSign searches are already running.");
        return true;
    }

    private Parsed parse(CommandSender sender, String[] args) {
        boolean contains = false;
        UUID worldId = null;
        int page = 1;
        OutputMode mode = OutputMode.DEFAULT;
        for (int i = 2; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "--contains" -> contains = true;
                case "--coords" -> {
                    if (mode != OutputMode.DEFAULT) {
                        sender.sendMessage("§c--coords and --front cannot be combined.");
                        return null;
                    }
                    mode = OutputMode.COORDS;
                }
                case "--front" -> {
                    if (mode != OutputMode.DEFAULT) {
                        sender.sendMessage("§c--coords and --front cannot be combined.");
                        return null;
                    }
                    mode = OutputMode.FRONT;
                }
                case "--world" -> {
                    if (++i >= args.length) { sender.sendMessage("§c--world requires a world name."); return null; }
                    World world = Bukkit.getWorld(args[i]);
                    if (world == null) { sender.sendMessage("§cUnknown world: " + args[i]); return null; }
                    worldId = world.getUID();
                }
                case "--page" -> {
                    if (++i >= args.length) { sender.sendMessage("§c--page requires a number."); return null; }
                    try { page = Integer.parseInt(args[i]); }
                    catch (NumberFormatException e) { sender.sendMessage("§cInvalid page: " + args[i]); return null; }
                    if (page < 1) { sender.sendMessage("§cPage must be at least 1."); return null; }
                }
                default -> { sender.sendMessage("§cUnknown option: " + args[i]); return null; }
            }
        }
        return new Parsed(contains, worldId, page, mode);
    }

    private void show(CommandSender sender, String identifier, int page, StorageSignSearchResult result) {
        show(sender, identifier, page, result, OutputMode.DEFAULT);
    }

    private void show(CommandSender sender, String identifier, int page, StorageSignSearchResult result,
                      OutputMode mode) {
        int pageSize = ConfigLoader.getAdminSearchPageSize();
        int pages = Math.max(1, (result.entries().size() + pageSize - 1) / pageSize);
        if (page > pages) {
            sender.sendMessage("§cPage " + page + " does not exist; total pages=" + pages);
            return;
        }
        if (mode == OutputMode.DEFAULT) {
            sender.sendMessage("§aStorageSign search '" + identifier + "': matches=" + result.entries().size()
                + ", totalAmount=" + result.totalAmount() + ", page=" + page + "/" + pages);
        }
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, result.entries().size());
        for (int i = start; i < end; i++) {
            IndexedStorageSign entry = result.entries().get(i);
            if (mode == OutputMode.DEFAULT) {
                World world = Bukkit.getWorld(entry.position().worldId());
                String worldName = StorageSignFacingSupport.formatWorld(world, entry.position().worldId());
                boolean loaded = world != null && world.isChunkLoaded(
                    entry.position().x() >> 4, entry.position().z() >> 4);
                sender.sendMessage("§7" + (i + 1) + ". " + worldName + " " + entry.position().x() + " "
                    + entry.position().y() + " " + entry.position().z() + " — " + entry.amount()
                    + " [" + (loaded ? "loaded" : "cached") + "]");
            } else {
                sender.sendMessage(formatCoordinateLine(entry, mode));
            }
        }
    }

    private String formatCoordinateLine(IndexedStorageSign entry, OutputMode mode) {
        StorageSignPosition position = entry.position();
        World world = Bukkit.getWorld(position.worldId());
        String worldName = StorageSignFacingSupport.formatWorld(world, position.worldId());
        StorageSignPosition target = mode == OutputMode.FRONT
            ? resolveFrontPosition(entry, world)
            : position;
        if (target == null) {
            target = position;
        }
        return worldName + "|" + target.x() + "|" + target.y() + "|" + target.z();
    }

    private StorageSignPosition resolveFrontPosition(IndexedStorageSign entry, World world) {
        BlockFace facing = entry.frontFacing();
        if (facing == null && world != null) {
            StorageSignPosition position = entry.position();
            if (world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                if (world.getBlockAt(position.x(), position.y(), position.z()).getState() instanceof Sign sign) {
                    facing = StorageSignFacingSupport.resolveFrontFacing(sign);
                }
            }
        }
        return StorageSignFacingSupport.frontPosition(entry.position(), facing);
    }

    private enum OutputMode {
        DEFAULT,
        COORDS,
        FRONT
    }

    private record Parsed(boolean contains, UUID worldId, int page, OutputMode mode) {}
}
