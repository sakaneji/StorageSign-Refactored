package storagesign.item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class PotionHelperTables {
    private PotionHelperTables() {
    }

    static void register(Map<String, Map<String, org.bukkit.potion.PotionType>> signLookup,
                         Map<org.bukkit.potion.PotionType, String> reverseMap,
                         String shortName,
                         Object... codePairs) {
        Map<String, org.bukkit.potion.PotionType> codeMap = new HashMap<>();
        for (int i = 0; i < codePairs.length; i += 2) {
            String code = (String) codePairs[i];
            org.bukkit.potion.PotionType type = (org.bukkit.potion.PotionType) codePairs[i + 1];
            codeMap.put(code, type);
            reverseMap.put(type, shortName);
        }
        signLookup.put(shortName, codeMap);
    }

    static <T> PotionHelper.LookupTables<T> buildCompleteLookup(Map<String, Map<String, T>> signLookup,
                                                                Iterable<T> types,
                                                                Function<T, String> shortNameFn,
                                                                Function<T, String> codeFn) {
        Map<String, Map<String, T>> completeLookup = new HashMap<>();
        signLookup.forEach((name, values) -> completeLookup.put(name, new HashMap<>(values)));
        Set<String> ambiguous = new HashSet<>();
        for (T type : types) {
            String shortName = shortNameFn.apply(type);
            String code = codeFn.apply(type);
            T previous = completeLookup.computeIfAbsent(shortName, k -> new HashMap<>())
                .putIfAbsent(code, type);
            if (previous != null && previous != type) ambiguous.add(shortName + ":" + code);
        }
        return new PotionHelper.LookupTables<>(completeLookup, ambiguous);
    }
}
