package storagesign.item;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionType;
import storagesign.ConfigLoader;
import storagesign.compat.LegacyStorageSignCompatibility;
import storagesign.logging.PluginLogger;

import static org.bukkit.potion.PotionType.*;

/**
 * ポーションアイテム（{@link Material#POTION}、{@link Material#SPLASH_POTION}、
 * {@link Material#LINGERING_POTION}）のシリアライズユーティリティ。
 *
 * <h3>データフォーマット（元プラグインと互換）</h3>
 * <ul>
 *   <li><b>看板テキスト（行1）:</b> {@code "[P|S|L]POTION:SHORT:CODE"}
 *       例: {@code "POTION:HEAL:0"}、{@code "SPOTION:REGEN:1"}</li>
 *   <li><b>アイテム Lore:</b> {@code "POTION:HEALING:0 100"}
 *       （フルの PotionType 名、新規データ）</li>
 * </ul>
 *
 * <h3>環境強化コード</h3>
 * <ul>
 *   <li>{@code "0"} — 通常</li>
 *   <li>{@code "1"} — 延長（{@code LONG_…}）</li>
 *   <li>{@code "2"} — 強化（{@code STRONG_…}）</li>
 * </ul>
 *
 * <p>特殊ケース以外のポーションは汎用パスで処理される:
 * ベースタイプ名の先頭 5 文字（{@code LONG_} / {@code STRONG_} 除く）。
 * Minecraft が新ポーションタイプを追加しても自動対応する。
 */
public final class PotionHelper {

    private static final PluginLogger LOG = PluginLogger.getLogger(PotionHelper.class);

    // ── Prefix characters for splash / lingering in sign text ────────────────
    static final String PREFIX_SPLASH   = "S";
    static final String PREFIX_LINGERING = "L";

    // ── Enhance codes ─────────────────────────────────────────────────────────
    private static final String CODE_NORMAL    = "0";
    private static final String CODE_EXTENDED  = "1";
    private static final String CODE_STRONG    = "2";

    private static final String LONG_PREFIX   = "LONG_";
    private static final String STRONG_PREFIX = "STRONG_";

    /**
     * Special-case short names. Potions in this map deviate from the generic
     * "first-5-chars-of-base-name" rule for historical reasons.
     *
     * Key   = short name used in sign text / item lore
     * Value = Map of (enhanceCode → PotionType)
     */
    private static final Map<String, Map<String, PotionType>> SIGN_LOOKUP;

    /** Reverse: PotionType → short name. */
    private static final Map<PotionType, String> POTION_TO_SHORT;

    /**
     * Complete lookup table covering ALL {@link PotionType} values.
     * Built at class-load time so {@link #fromSignText} never iterates PotionType.values()
     * at runtime. Special-case entries from SIGN_LOOKUP take priority; generic entries
     * (derived from the 5-char rule) fill the rest via putIfAbsent.
     */
    private static final Map<String, Map<String, PotionType>> COMPLETE_SIGN_LOOKUP;
    private static final Set<String> AMBIGUOUS_LEGACY_KEYS;

    // ── Legacy NBT name → short name (migration for very old data) ────────────
    private static final Map<String, String> LEGACY_NAME_MAP = Map.of(
        "INSTANT_HEAL",    "HEAL",
        "INSTANT_DAMAGE",  "DAMAG",
        "JUMP",            "JUMP",
        "SPEED",           "SPEED",
        "REGEN",           "REGEN",
        "WATER_BREATHING", "BREAT"
    );

    static {
        Map<String, Map<String, PotionType>> signLookup = new HashMap<>();
        Map<PotionType, String> potionToShort = new EnumMap<>(PotionType.class);

        // Potions whose short name does NOT follow the generic 5-char rule
        PotionHelperTables.register(signLookup, potionToShort, "HEAL",
            CODE_NORMAL, HEALING,
            CODE_STRONG, STRONG_HEALING);

        PotionHelperTables.register(signLookup, potionToShort, "BREAT",
            CODE_NORMAL,   WATER_BREATHING,
            CODE_EXTENDED, LONG_WATER_BREATHING);

        PotionHelperTables.register(signLookup, potionToShort, "DAMAG",
            CODE_NORMAL, HARMING,
            CODE_STRONG, STRONG_HARMING);

        PotionHelperTables.register(signLookup, potionToShort, "JUMP",
            CODE_NORMAL,   LEAPING,
            CODE_EXTENDED, LONG_LEAPING,
            CODE_STRONG,   STRONG_LEAPING);

        PotionHelperTables.register(signLookup, potionToShort, "SPEED",
            CODE_NORMAL,   SWIFTNESS,
            CODE_EXTENDED, LONG_SWIFTNESS,
            CODE_STRONG,   STRONG_SWIFTNESS);

        PotionHelperTables.register(signLookup, potionToShort, "REGEN",
            CODE_NORMAL,   REGENERATION,
            CODE_EXTENDED, LONG_REGENERATION,
            CODE_STRONG,   STRONG_REGENERATION);

        SIGN_LOOKUP    = Collections.unmodifiableMap(signLookup);
        POTION_TO_SHORT = Collections.unmodifiableMap(potionToShort);

        LookupTables<PotionType> lookup = PotionHelperTables.buildCompleteLookup(
            signLookup, java.util.List.of(PotionType.values()),
            PotionHelper::getShortName, PotionHelper::getEnhanceCode);
        COMPLETE_SIGN_LOOKUP = Collections.unmodifiableMap(lookup.lookup());
        AMBIGUOUS_LEGACY_KEYS = Collections.unmodifiableSet(lookup.ambiguousKeys());
    }

    static <T> LookupTables<T> buildCompleteLookup(Map<String, Map<String, T>> signLookup,
                                                   Iterable<T> types,
                                                   java.util.function.Function<T, String> shortNameFn,
                                                   java.util.function.Function<T, String> codeFn) {
        return PotionHelperTables.buildCompleteLookup(signLookup, types, shortNameFn, codeFn);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the short name used in sign text for the given {@link PotionType}.
     *
     * <p>Special cases are looked up first; all others use the generic rule
     * (first 5 chars of the base name after stripping {@code LONG_}/{@code STRONG_}).
     */
    public static String getShortName(PotionType pot) {
        String shortName = POTION_TO_SHORT.get(pot);
        if (shortName != null) return shortName;

        // Generic: strip enhance prefix, take first 5 chars
        String name = pot.name();
        if (name.startsWith(LONG_PREFIX))   name = name.substring(LONG_PREFIX.length());
        else if (name.startsWith(STRONG_PREFIX)) name = name.substring(STRONG_PREFIX.length());
        return name.length() <= 5 ? name : name.substring(0, 5);
    }

    /** Returns {@code "0"}, {@code "1"}, or {@code "2"} for normal / extended / strong. */
    public static String getEnhanceCode(PotionType pot) {
        String name = pot.name();
        if (name.startsWith(LONG_PREFIX))   return CODE_EXTENDED;
        if (name.startsWith(STRONG_PREFIX)) return CODE_STRONG;
        return CODE_NORMAL;
    }

    /**
     * Sign text prefix for splash/lingering. Empty string for normal potions.
     *
     * <p>Sign text format: {@code "[S|L]POTION:SHORT:CODE"}.
     */
    public static String getMaterialPrefix(Material mat) {
        if (mat == Material.SPLASH_POTION)   return PREFIX_SPLASH;
        if (mat == Material.LINGERING_POTION) return PREFIX_LINGERING;
        return "";
    }

    /**
     * Formats the sign-text line-1 representation.
     *
     * <p>Example: {@code "SPOTION:REGEN:1"} for long-regen splash potion.
     */
    public static String toSignText(Material mat, PotionType pot, short damage) {
        return getMaterialPrefix(mat) + "POTION:" + getShortName(pot) + ":" + damage;
    }

    /**
     * Formats the item lore representation.
     *
     * <p>Example: {@code "POTION:HEALING:0 100"}.
     */
    public static String toLoreText(Material mat, PotionType pot, short damage, int amount) {
        return mat.toString() + ":" + getShortName(pot) + ":" + damage + " " + amount;
    }

    /** Registry keyを使った、表示幅に依存しない正規保存識別子。 */
    public static String toCanonicalIdentifier(Material mat, PotionType pot) {
        return getMaterialPrefix(mat) + "POTION:" + pot.getKey();
    }

    /** 看板表示用。従来形式と同じ短い文字列を維持する。 */
    public static String toDisplayIdentifier(Material mat, PotionType pot) {
        return getMaterialPrefix(mat) + "POTION:" + getShortName(pot) + ":" + getEnhanceCode(pot);
    }

    /** 正規形式または従来形式のPotion識別子を解決する。 */
    public static PotionData fromIdentifier(String identifier) {
        if (identifier == null) return null;
        int potionIndex = identifier.indexOf("POTION:");
        if (potionIndex < 0) return null;
        String prefix = identifier.substring(0, potionIndex);
        if (!LegacyStorageSignCompatibility.isPotionPrefix(prefix)) {
            return null;
        }
        Material material = LegacyStorageSignCompatibility.potionMaterial(prefix);
        String value = identifier.substring(potionIndex + "POTION:".length());

        PotionType canonical = resolveRegistryKey(value);
        if (canonical != null) return new PotionData(material, canonical);

        String[] legacy = value.split(":", -1);
        if (legacy.length != 2 || !legacy[1].matches("[012]")) return null;
        String shortName = normalizeName(legacy[0]);
        PotionType type = LegacyStorageSignCompatibility.resolveFullPotionName(
            shortName, legacy[1]);
        if (type == null) type = fromSignText(shortName, legacy[1]);
        return type == null ? null : new PotionData(material, type);
    }

    private static PotionType resolveRegistryKey(String rawKey) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        if (key == null) return null;
        PotionType direct = Registry.POTION.get(key);
        if (direct != null) return direct;

        String alias = ConfigLoader.getPotionKeyAliases().get(rawKey);
        if (alias == null || alias.isBlank() || alias.equals(rawKey)) {
            if (alias != null) {
                LOG.log(Level.WARNING, "resolvePotionKey", "Invalid potion key alias: {0} -> {1}",
                    new Object[]{rawKey, alias});
            }
            return null;
        }
        NamespacedKey target = NamespacedKey.fromString(alias);
        PotionType resolved = target == null ? null : Registry.POTION.get(target);
        if (resolved == null && ConfigLoader.getPotionKeyAliases().containsKey(alias)) {
            LOG.log(Level.WARNING, "resolvePotionKey", "Chained potion key alias is not allowed: {0} -> {1}",
                new Object[]{rawKey, alias});
            return null;
        }
        if (resolved == null) {
            LOG.log(Level.WARNING, "resolvePotionKey", "Potion key alias target is unavailable: {0} -> {1}",
                new Object[]{rawKey, alias});
        }
        return resolved;
    }

    /**
     * Converts a legacy NBT name to the current short name, or returns the input unchanged
     * if no mapping exists. Used when reading old item lore data.
     */
    public static String normalizeName(String name) {
        return LEGACY_NAME_MAP.getOrDefault(name, name);
    }

    /**
     * Reconstructs a {@link PotionType} from the sign/lore short name and enhance code.
     *
     * <p>Uses {@link #COMPLETE_SIGN_LOOKUP} for O(1) lookup covering all potion types.
     * The generic {@code PotionType.values()} scan is no longer needed at runtime.
     *
     * @param shortName short name (e.g. {@code "HEAL"}, {@code "SPEED"}, {@code "NIGHT"})
     * @param code      enhance code ({@code "0"}/{@code "1"}/{@code "2"})
     * @return matching PotionType, or {@code null} if not found.
     */
    public static PotionType fromSignText(String shortName, String code) {
        if (AMBIGUOUS_LEGACY_KEYS.contains(shortName + ":" + code)) {
            LOG.log(Level.WARNING, "resolvePotionType", "Ambiguous legacy potion identifier: {0}:{1}",
                new Object[]{shortName, code});
            return null;
        }
        Map<String, PotionType> codeMap = COMPLETE_SIGN_LOOKUP.get(shortName);
        if (codeMap != null) {
            PotionType type = codeMap.get(code);
            if (type != null) return type;
            LOG.log(Level.WARNING, "resolvePotionType", "No potion type for {0}:{1}",
                    new Object[]{shortName, code});
            return null;
        }
        LOG.log(Level.WARNING, "resolvePotionType",
                "Could not resolve potion type: shortName={0}, code={1}",
                new Object[]{shortName, code});
        return null;
    }

    /**
     * Resolves the splash/lingering Material from the type prefix stored in sign text.
     *
     * @param typePrefix first character(s) before "POTION" in sign text
     * @return SPLASH_POTION, LINGERING_POTION, or POTION (default)
     */
    public static Material materialFromPrefix(String typePrefix) {
        if (typePrefix.startsWith(PREFIX_SPLASH))    return Material.SPLASH_POTION;
        if (typePrefix.startsWith(PREFIX_LINGERING)) return Material.LINGERING_POTION;
        return Material.POTION;
    }

    private PotionHelper() {}

    public record PotionData(Material material, PotionType type) {}
    record LookupTables<T>(Map<String, Map<String, T>> lookup, Set<String> ambiguousKeys) {}
}
