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
        String[] lines = sign.getSignLines(block.getType());
        var front = block.getSide(Side.FRONT);
        front.setLine(0, lines[0]);
        front.setLine(1, lines[1]);
        front.setLine(2, lines[2]);
        front.setLine(3, lines[3]);
        int amount = sign.getAmount();
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
