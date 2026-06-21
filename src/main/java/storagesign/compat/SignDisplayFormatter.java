package storagesign.compat;

/** Fits a readable label onto a physical sign while canonical data remains in PDC. */
public final class SignDisplayFormatter {
    public static final int MAX_VANILLA_WIDTH = 90;

    private SignDisplayFormatter() {}

    public static String fit(String identifier) {
        if (identifier == null || identifier.isEmpty()) return "";
        if (width(identifier) <= MAX_VANILLA_WIDTH) return identifier;

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
            if (width(compact.toString()) <= MAX_VANILLA_WIDTH) return compact.toString();
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < identifier.length(); i++) {
            if (width(truncated + String.valueOf(identifier.charAt(i)) + "...")
                > MAX_VANILLA_WIDTH) break;
            truncated.append(identifier.charAt(i));
        }
        return truncated.append("...").toString();
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
