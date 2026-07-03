package storagesign.listener;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import storagesign.AmountTransfer;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.logging.PluginLogger;
import storagesign.registry.DyeRegistry;
import storagesign.registry.MaterialRegistry;

final class StorageSignInteractionSupport {
    private StorageSignInteractionSupport() {
    }

    static void registerItem(PluginLogger log, Player player, Block block, ItemStack hand) {
        if (hand == null || hand.getType() == Material.AIR) return;

        StorageSign newSS = StorageSign.fromStoredItem(hand);
        if (newSS == null) return;

        applyToBlock(block, newSS);
        log.debug("registerItem", () -> "registered=" + newSS.getIdentifier()
                  + ", sign=" + block.getLocation());
    }

    static void processStorageSignItemInteraction(PluginLogger log, Player player, Block block, StorageSign blockSS,
                                                  ItemStack handItem, StorageSign handSS) {
        if (!handSS.isUnregistered() && ConfigLoader.getManualImport()) {
            ItemStack handContents = handSS.getContents(1);
            if (handContents != null && blockSS.isSimilar(handContents)) {
                int perItem = handSS.getAmount();
                int handCount = Math.max(1, handItem.getAmount());
                long capacity = (long) Integer.MAX_VALUE - blockSS.getAmount();
                int mergedItems = 0;
                int partialMergedAmount = 0;
                if (perItem > 0 && capacity > 0) {
                    mergedItems = (int) Math.min(handCount, capacity / perItem);
                    if (mergedItems < handCount) {
                        long remainingCapacity = capacity - ((long) mergedItems * perItem);
                        if (remainingCapacity > 0) {
                            partialMergedAmount = (int) Math.min(remainingCapacity, (long) perItem);
                        }
                    }
                }
                if (mergedItems > 0 || partialMergedAmount > 0) {
                    long add = (long) perItem * mergedItems + partialMergedAmount;
                    blockSS.setAmount((int) (blockSS.getAmount() + add));
                    applyMergedStorageSigns(player, handItem, handSS, mergedItems, partialMergedAmount);
                    applyToBlock(block, blockSS);
                    log.debug("processStorageSignItemInteraction", () -> "merged=" + add
                              + ", sign=" + block.getLocation()
                              + ", total=" + blockSS.getAmount());
                }
                return;
            }
        }

        if (handSS.isUnregistered() && ConfigLoader.getManualImport()
            && blockSS.isSignAsItem() && blockSS.getMaterial() == handItem.getType()) {
            int added = 0;
            if (player.isSneaking()) {
                added = AmountTransfer.accepted(blockSS.getAmount(), handItem.getAmount());
                if (added <= 0) return;
                consumeSlot(player, player.getInventory().getHeldItemSlot(), handItem, added);
            } else {
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item == null || item.getType() != handItem.getType()) continue;
                    StorageSign itemSS = StorageSign.fromItemStack(item);
                    if (itemSS == null || !itemSS.isUnregistered()) continue;
                    int accepted = AmountTransfer.accepted(blockSS.getAmount() + added, item.getAmount());
                    if (accepted <= 0) break;
                    added += accepted;
                    consumeSlot(player, i, item, accepted);
                }
            }
            if (added > 0) {
                blockSS.setAmount(blockSS.getAmount() + added);
                applyToBlock(block, blockSS);
                int loggedAdded = added;
                log.debug("processStorageSignItemInteraction", () -> "stored-signs=" + loggedAdded
                          + ", sign=" + block.getLocation()
                          + ", total=" + blockSS.getAmount());
            }
            return;
        }

        if (handSS.isUnregistered() && ConfigLoader.getManualExport()
            && MaterialRegistry.isAnySign(handItem.getType())
            && blockSS.getAmount() > handItem.getAmount()) {
            ItemStack template = blockSS.getContents(1);
            if (template == null) return;

            StorageSign divided = StorageSign.fromStoredItem(template);
            if (divided == null) return;

            int signsInHand = Math.max(1, handItem.getAmount());
            int limit = player.isSneaking() ? ConfigLoader.getSneakDivideLimit() : ConfigLoader.getDivideLimit();
            int perSign = AmountTransfer.dividedPerSign(blockSS.getAmount(), signsInHand, limit);
            if (perSign <= 0) return;

            divided.setAmount(perSign);
            player.getInventory().setItemInMainHand(
                StorageSign.createStorageSignItem(handItem.getType(), divided, signsInHand)
            );
            blockSS.setAmount(blockSS.getAmount() - (perSign * signsInHand));
            applyToBlock(block, blockSS);
            log.debug("processStorageSignItemInteraction", () -> "divided-per-sign=" + perSign
                      + ", signs=" + signsInHand
                      + ", sign=" + block.getLocation()
                      + ", remaining=" + blockSS.getAmount());
        }
    }

    static void importItems(PluginLogger log, Player player, Block block, StorageSign ss, ItemStack hand) {
        if (!ConfigLoader.getManualImport()) return;

        int before = ss.getAmount();
        if (player.isSneaking()) {
            int add = AmountTransfer.accepted(ss.getAmount(), hand.getAmount());
            if (add <= 0) return;
            ss.setAmount(ss.getAmount() + add);
            consumeSlot(player, player.getInventory().getHeldItemSlot(), hand, add);

            if (block.getState() instanceof Sign sign) {
                if (DyeRegistry.isDye(hand.getType())) {
                    DyeColor color = DyeRegistry.getColor(hand.getType());
                    if (color != null) sign.getSide(Side.FRONT).setColor(color);
                } else if (isSac(hand.getType())) {
                    sign.getSide(Side.FRONT).setGlowingText(isGlowSac(hand.getType()));
                }
                ss.applyToSign(sign);
            }
        } else {
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (!ss.isSimilar(item)) continue;
                int add = AmountTransfer.accepted(ss.getAmount(), item.getAmount());
                if (add <= 0) break;
                ss.setAmount(ss.getAmount() + add);
                consumeSlot(player, i, item, add);
            }
            applyToBlock(block, ss);
        }
        player.updateInventory();
        int imported = ss.getAmount() - before;
        if (imported > 0) {
            log.debug("importItems", () -> "imported=" + imported
                      + ", material=" + ss.getMaterial()
                      + ", sign=" + block.getLocation()
                      + ", total=" + ss.getAmount());
        }
    }

    static void exportItems(PluginLogger log, Player player, Block block, StorageSign ss) {
        if (ss.isUnregistered() || ss.getAmount() <= 0) return;

        ItemStack out = ss.getContents(1);
        if (out == null) return;

        int max = out.getMaxStackSize();
        if (player.isSneaking()) {
            out.setAmount(1);
            ss.setAmount(ss.getAmount() - 1);
        } else if (ss.getAmount() > max) {
            out.setAmount(max);
            ss.setAmount(ss.getAmount() - max);
        } else {
            out.setAmount(ss.getAmount());
            ss.setAmount(0);
        }

        Location dropLoc = player.getLocation().clone().add(0, 0.5, 0);
        player.getWorld().dropItem(dropLoc, out);
        applyToBlock(block, ss);
        log.debug("exportItems", () -> "exported=" + out.getAmount()
                  + ", material=" + out.getType()
                  + ", sign=" + block.getLocation()
                  + ", remaining=" + ss.getAmount());
    }

    static boolean isSac(Material mat) {
        return mat == Material.INK_SAC || mat == Material.GLOW_INK_SAC;
    }

    private static ItemStack remainingMergedStack(ItemStack original, StorageSign contents, int remainingAmount) {
        return StorageSign.createStorageSignItem(original.getType(), contents, remainingAmount);
    }

    private static void applyMergedStorageSigns(Player player, ItemStack original,
                                                StorageSign contents, int mergedItems,
                                                int partialMergedAmount) {
        int totalConsumedItems = mergedItems + (partialMergedAmount > 0 ? 1 : 0);
        int remainingItems = original.getAmount() - totalConsumedItems;
        ItemStack emptied = mergedItems > 0 ? StorageSign.createStorageSignItem(
            original.getType(), StorageSign.EMPTY_MARKER, mergedItems) : null;
        ItemStack partial = partialMergedAmount > 0 ? partialMergedStack(
            original, contents, partialMergedAmount) : null;

        if (remainingItems > 0) {
            player.getInventory().setItemInMainHand(
                remainingMergedStack(original, contents, remainingItems));
            addOrDrop(player, emptied);
            addOrDrop(player, partial);
            return;
        }

        if (partial != null) {
            player.getInventory().setItemInMainHand(partial);
            addOrDrop(player, emptied);
            return;
        }

        if (emptied != null) {
            player.getInventory().setItemInMainHand(emptied);
        }
    }

    private static ItemStack partialMergedStack(ItemStack original, StorageSign contents, int remainingAmount) {
        StorageSign partialContents = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE,
            contents.getIdentifier(),
            Integer.toString(remainingAmount)
        });
        if (partialContents == null) return null;
        return StorageSign.createStorageSignItem(original.getType(), partialContents, 1);
    }

    private static void addOrDrop(Player player, ItemStack stack) {
        if (stack == null) return;
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static void consumeSlot(Player player, int slot, ItemStack item, int consumed) {
        int remaining = item.getAmount() - consumed;
        if (remaining <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack rest = item.clone();
            rest.setAmount(remaining);
            player.getInventory().setItem(slot, rest);
        }
    }

    private static boolean isGlowSac(Material mat) {
        return mat == Material.GLOW_INK_SAC;
    }

    private static void applyToBlock(Block block, StorageSign ss) {
        if (block.getState() instanceof Sign sign) {
            ss.applyToSign(sign);
        }
    }
}
