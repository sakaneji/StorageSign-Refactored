package storagesign.index;

import org.bukkit.block.BlockFace;

/** Last known searchable state of one physical StorageSign. */
public record IndexedStorageSign(StorageSignPosition position, String identifier,
                                 int amount, long verifiedAtEpochMillis,
                                 BlockFace frontFacing) {
    public IndexedStorageSign {
        if (position == null) throw new IllegalArgumentException("position is required");
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
    }

    public IndexedStorageSign(StorageSignPosition position, String identifier,
                              int amount, long verifiedAtEpochMillis) {
        this(position, identifier, amount, verifiedAtEpochMillis, null);
    }
}
