package storagesign.command;

import java.util.Locale;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.registry.MaterialRegistry;

/**
 * /storagesigngive（エイリアス: /ssgive）コマンドの処理クラス。
 */
public final class SsGiveCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤー専用です。");
            return true;
        }

        if (!player.hasPermission("storagesign.give")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            return true;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.sendMessage("§cこのコマンドはクリエイティブモードでのみ使用できます。");
            return true;
        }

        if (args.length < 2 || args.length > 3) {
            player.sendMessage("§e使い方: /" + label + " <itemIdentifier> <amount> [signType]");
            player.sendMessage("§7例: /" + label + " STONE 128 OAK_SIGN");
            return true;
        }

        String identifier = args[0].trim();
        int amount;
        try {
            amount = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§camount は整数で指定してください。");
            return true;
        }
        if (amount < 0) {
            player.sendMessage("§camount は 0 以上で指定してください。");
            return true;
        }

        Material signMaterial = resolveSignMaterial(args.length >= 3 ? args[2] : "OAK_SIGN");
        if (signMaterial == null || !MaterialRegistry.SIGN_MATERIALS.contains(signMaterial)) {
            player.sendMessage("§c看板種類が不正です。例: OAK_SIGN, SPRUCE_SIGN");
            return true;
        }

        StorageSign parsed = StorageSign.fromSignLines(
            new String[]{StorageSign.HEADER_LINE, identifier, Integer.toString(amount)}
        );
        if (parsed == null || parsed.isUnregistered()) {
            player.sendMessage("§citemIdentifier が不正です。");
            return true;
        }

        ItemStack ssItem = StorageSign.createStorageSignItem(signMaterial, parsed, 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(ssItem);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage("§aStorageSign を付与しました: "
            + signMaterial.name() + " / " + parsed.getLoreText());
        return true;
    }

    private static Material resolveSignMaterial(String raw) {
        if (raw == null || raw.isBlank()) return Material.OAK_SIGN;

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        if (!normalized.endsWith("_SIGN")) {
            normalized = normalized + "_SIGN";
        }
        return Material.matchMaterial(normalized);
    }
}
