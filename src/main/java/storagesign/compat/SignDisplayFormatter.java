package storagesign.compat;

import org.bukkit.Material;

/** Fits a readable label onto a physical sign while canonical data remains in PDC. */
public final class SignDisplayFormatter {
    public static final int MAX_VANILLA_WIDTH = 90;
    public static final int MAX_HANGING_SIGN_WIDTH = 80;

    private SignDisplayFormatter() {}

    public static String fit(String identifier) {
        return fit(identifier, null);
    }

    public static String fit(String identifier, Material signMaterial) {
        if (identifier == null || identifier.isEmpty()) return "";
        int maxWidth = maxWidth(signMaterial);
        if (width(identifier) <= maxWidth) return identifier;

        int suffixAt = identifier.indexOf(':');
        String base = suffixAt < 0 ? identifier : identifier.substring(0, suffixAt);
        String suffix = suffixAt < 0 ? "" : identifier.substring(suffixAt);
        String[] words = base.split("_");
        if (words.length > 1) {
            StringBuilder compact = new StringBuilder();
            for (int i = 0; i < words.length - 1; i++) {
                if (!words[i].isEmpty()) compact.append(words[i].charAt(0));
            }
            compact.append(':').append(words[words.length - 1]).append(suffix);
            if (width(compact.toString()) <= maxWidth) return compact.toString();
        }
        return truncate(identifier, maxWidth);
    }

    public static String truncate(String identifier) {
        return truncate(identifier, MAX_VANILLA_WIDTH);
    }

    private static String truncate(String identifier, int maxWidth) {
        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < identifier.length(); i++) {
            if (width(truncated + String.valueOf(identifier.charAt(i)) + "...")
                > maxWidth) break;
            truncated.append(identifier.charAt(i));
        }
        return truncated.append("...").toString();
    }

    private static int maxWidth(Material signMaterial) {
        if (signMaterial != null && signMaterial.name().endsWith("_HANGING_SIGN")) {
            return MAX_HANGING_SIGN_WIDTH;
        }
        return MAX_VANILLA_WIDTH;
    }

    public static int width(String value) {
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            width += switch (value.charAt(i)) {
                case ':', '!', '.', ',' -> 2;
                case 'I', '1', 'i', 'l' -> 4;
                default -> 6;
            };
        }
        return width;
    }
}
