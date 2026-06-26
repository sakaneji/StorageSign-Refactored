package storagesign.listener;

import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import storagesign.StorageSign;
import storagesign.logging.PluginLogger;

final class StorageSignInteractionFlow {
    private StorageSignInteractionFlow() {}

    static void registerItem(PluginLogger log, Player player, Block block, ItemStack hand) {
        StorageSignInteractionSupport.registerItem(log, player, block, hand);
    }

    static void processStorageSignItemInteraction(PluginLogger log, Player player, Block block, StorageSign blockSS,
                                                   ItemStack handItem, StorageSign handSS) {
        StorageSignInteractionSupport.processStorageSignItemInteraction(
            log, player, block, blockSS, handItem, handSS);
    }

    static void importItems(PluginLogger log, Player player, Block block, StorageSign ss, ItemStack hand) {
        StorageSignInteractionSupport.importItems(log, player, block, ss, hand);
    }

    static void exportItems(PluginLogger log, Player player, Block block, StorageSign ss) {
        StorageSignInteractionSupport.exportItems(log, player, block, ss);
    }

    static boolean isSac(Material mat) {
        return StorageSignInteractionSupport.isSac(mat);
    }
}
