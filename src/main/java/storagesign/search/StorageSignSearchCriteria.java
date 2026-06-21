package storagesign.search;

import java.util.UUID;

/** Extensible immutable criteria used by administrative index searches. */
public record StorageSignSearchCriteria(String identifier, MatchMode matchMode, UUID worldId,
                                        Integer minimumAmount, Integer maximumAmount) {
    public StorageSignSearchCriteria {
        if (identifier == null || identifier.isBlank()) throw new IllegalArgumentException("identifier is required");
        if (matchMode == null) matchMode = MatchMode.EXACT;
    }

    public enum MatchMode { EXACT, CONTAINS }
}
