package storagesign;

import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.persistence.PersistentDataType;
import storagesign.event.StorageSignUpdatedEvent;

final class StorageSignBlockWriter {
    private StorageSignBlockWriter() {
    }

    static void applyToSign(StorageSign sign, Sign block) {
        String identifier = sign.getDisplayIdentifier();
        int amount = sign.getAmount();
        int lc = amount / 3456;
        int rem = amount % 3456;
        int stacks = rem / 64;
        int singles = rem % 64;
        var front = block.getSide(Side.FRONT);
        front.setLine(0, StorageSign.HEADER_LINE);
        front.setLine(1, identifier);
        front.setLine(2, String.valueOf(amount));
        front.setLine(3, lc + "LC " + stacks + "s " + singles);
        if (sign.isUnregistered()) {
            block.getPersistentDataContainer().remove(StorageSign.canonicalPotionIdentifierKey());
        } else {
            block.getPersistentDataContainer().set(
                StorageSign.canonicalPotionIdentifierKey(), PersistentDataType.STRING, sign.getIdentifier());
        }
        String canonical = sign.getCanonicalPotionIdentifier();
        if (canonical == null) {
            block.getPersistentDataContainer().remove(StorageSign.potionIdentifierKey());
        } else {
            block.getPersistentDataContainer().set(
                StorageSign.potionIdentifierKey(), PersistentDataType.STRING, canonical);
        }
        block.update();
        if (Bukkit.getServer() != null) {
            Bukkit.getPluginManager().callEvent(
                new StorageSignUpdatedEvent(block, sign.isUnregistered() ? "" : sign.getIdentifier(),
                    amount, !sign.isUnregistered()));
        }
    }
}
