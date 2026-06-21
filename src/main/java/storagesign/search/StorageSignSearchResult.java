package storagesign.search;

import java.util.List;
import storagesign.index.IndexedStorageSign;

public record StorageSignSearchResult(List<IndexedStorageSign> entries, long totalAmount) {}
