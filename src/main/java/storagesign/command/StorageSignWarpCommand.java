package storagesign.command;

import java.util.Comparator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import storagesign.StorageSign;
import storagesign.StorageSignFacingSupport;
import storagesign.index.IndexedStorageSign;
import storagesign.index.StorageSignIndex;
import storagesign.index.StorageSignPosition;

/** Player-facing warp to the front of the nearest matching StorageSign. */
public final class StorageSignWarpCommand implements CommandExecutor {
    private static final String HAND_OPTION = "--hand";
    private static final String OPTION_ESCAPE_PREFIX = "\\";
    private final StorageSignIndex index;

    public StorageSignWarpCommand(StorageSignIndex index) {
        this.index = index;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤー専用です。");
            return true;
        }
        if (args.length != 1 || args[0].isBlank()) {
            sendUsage(player, label);
            return true;
        }

        String identifier = resolveIdentifier(player, args[0].trim());
        if (identifier == null) {
            return true;
        }
        if (identifier.isBlank()) {
            sendUsage(player, label);
            return true;
        }
        if (index == null || !index.isEnabled()) {
            player.sendMessage("§eStorageSign index is disabled by storage-index.enabled.");
            return true;
        }

        World world = player.getWorld();
        WarpTarget target = nearestValidTarget(player.getLocation(), identifier);
        if (target == null) {
            player.sendMessage("§eこのワールドには対象アイテムの StorageSign が見つかりません: " + identifier);
            return true;
        }

        StorageSignPosition front = StorageSignFacingSupport.resolveFrontPosition(
            target.entry().position(), target.entry().frontFacing(), world);
        Location destination = StorageSignFacingSupport.centeredLocation(
            front, world, player.getLocation().getYaw(), player.getLocation().getPitch());
        if (destination == null) {
            player.sendMessage("§cStorageSign の前面方向を特定できません: " + identifier);
            return true;
        }
        if (!isSafeDestination(destination)) {
            player.sendMessage("§cワープ先の前面ブロックが安全ではありません: "
                + front.x() + " " + front.y() + " " + front.z());
            return true;
        }

        if (player.teleport(destination)) {
            player.sendMessage("§aStorageSign の前へワープしました: " + target.entry().identifier());
        } else {
            player.sendMessage("§cワープに失敗しました。");
        }
        return true;
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage("§e使い方: /" + label + " <itemIdentifier|--hand>");
        player.sendMessage("§7例: /" + label + " STONE");
        player.sendMessage("§7例: /" + label + " --hand");
        player.sendMessage("§7例: /" + label + " \\--hand");
    }

    private String resolveIdentifier(Player player, String input) {
        if (input.startsWith(OPTION_ESCAPE_PREFIX) && input.length() > OPTION_ESCAPE_PREFIX.length()) {
            return input.substring(OPTION_ESCAPE_PREFIX.length());
        }
        if (!HAND_OPTION.equalsIgnoreCase(input)) return input;

        ItemStack hand = player.getInventory().getItemInMainHand();
        StorageSign storageSignItem = StorageSign.fromItemStack(hand);
        if (storageSignItem != null) {
            if (storageSignItem.isUnregistered()) {
                player.sendMessage("§e登録済みのStorageSignアイテム、または保管対象のアイテムを手に持ってください。");
                return null;
            }
            return storageSignItem.getIdentifier();
        }

        StorageSign storedItem = StorageSign.fromStoredItem(hand);
        if (storedItem == null || storedItem.isUnregistered()) {
            player.sendMessage("§e保管対象にできるアイテムを手に持ってください。");
            return null;
        }
        return storedItem.getIdentifier();
    }

    private WarpTarget nearestValidTarget(Location origin, String identifier) {
        if (origin == null || origin.getWorld() == null) return null;
        return index.findByIdentifierExact(identifier).stream()
            .filter(entry -> entry.position().worldId().equals(origin.getWorld().getUID()))
            .sorted(Comparator.comparingDouble(entry -> distanceSquared(origin, entry.position())))
            .map(entry -> validateTarget(origin.getWorld(), identifier, entry))
            .filter(target -> target != null)
            .findFirst()
            .orElse(null);
    }

    private WarpTarget validateTarget(World world, String identifier, IndexedStorageSign entry) {
        StorageSignPosition position = entry.position();
        int chunkX = position.x() >> 4;
        int chunkZ = position.z() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ);
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)) return null;
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        StorageSign storageSign = StorageSign.fromBlock(block);
        if (storageSign == null || storageSign.isUnregistered()) {
            index.unregister(position);
            return null;
        }
        if (!storageSign.getIdentifier().equalsIgnoreCase(identifier)) {
            index.register(block);
            return null;
        }
        return new WarpTarget(entry);
    }

    private double distanceSquared(Location origin, StorageSignPosition position) {
        double dx = position.x() + 0.5 - origin.getX();
        double dy = position.y() + 0.5 - origin.getY();
        double dz = position.z() + 0.5 - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean isSafeDestination(Location destination) {
        World world = destination.getWorld();
        if (world == null) return false;
        Block feet = world.getBlockAt(destination);
        Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
        Block support = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        return feet.getType().isAir() && head.getType().isAir() && support.getType().isSolid();
    }

    private record WarpTarget(IndexedStorageSign entry) {}
}
