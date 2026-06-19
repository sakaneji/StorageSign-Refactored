package storagesign.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import storagesign.logging.PluginLogger;

/**
 * {@link Material#ENCHANTED_BOOK} のシリアライズユーティリティ。
 *
 * <p>看板テキストフォーマット: {@code "ENCHBOOK:fire_p:3"}（短縮キー + レベル）。
 * Lore フォーマット: {@code "ENCHANTED_BOOK:fire_protection:3 100"}（フルキー + レベル + 数量）。
 */
public final class EnchantHelper {

    private static final PluginLogger LOG = PluginLogger.getLogger(EnchantHelper.class);

    /**
     * {@link #fromPrefix} で O(1) 検索を実現するための事前計算マップ。
     * クラスロード時に登録済み全エンチャントから構築する。
     * SHORT_KEY_MAP: 短縮看板テキストキー（例: "sharp")→ Enchantment
     * FULL_KEY_MAP:  フルネームスペースキー（例: "sharpness")→ Enchantment
     *
     * 看板データは常に {@link #toShortKey} の結果を保存するため、
     * 短縮キー検索がメインパス。フルキー検索は手動編集看板や管理者作成看板向け。
     */
    private static final Map<String, Enchantment> SHORT_KEY_MAP;
    private static final Map<String, Enchantment> FULL_KEY_MAP;
    static {
        Map<String, Enchantment> shortMap = new HashMap<>();
        Map<String, Enchantment> fullMap  = new HashMap<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            shortMap.put(toShortKey(e), e);
            fullMap.put(e.getKey().getKey(), e);
        }
        SHORT_KEY_MAP = Collections.unmodifiableMap(shortMap);
        FULL_KEY_MAP  = Collections.unmodifiableMap(fullMap);
    }

    private EnchantHelper() {}

    /**
     * 看板テキスト向けの短縮エンチャントキーを返す。
     *
     * <p>変換ルール（元プラグインの EnchantInfo と互換）:
     * <ul>
     *   <li>{@code fire_protection} → {@code "fire_p"}</li>
     *   <li>{@code fire_aspect}     → {@code "fire_a"}</li>
     *   <li>5文字以下のキー → そのまま返却</li>
     *   <li>その他         → 先頭 5 文字</li>
     * </ul>
     */
    public static String toShortKey(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        if ("fire_protection".equals(key)) return "fire_p";
        if ("fire_aspect".equals(key))     return "fire_a";
        return key.length() <= 5 ? key : key.substring(0, 5);
    }

    /**
     * {@code prefix} で始まるキーを持つエンチャントを返す。
     *
     * <p>看板テキストの短縮キーに対応する—例: {@code "fire_p"} → fire_protection。
     *
     * <p>ファストパス: フルキー・短縮キーの O(1) マップ検索（プラグイン自身が書いたデータはここを通る）。
     * スローパス: レジストリを線形スキャン（手動編集や不完全な看板データだけ）。
     *
     * @return 一致するエンチャント、見つからなければ {@code null}。
     */
    public static Enchantment fromPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            LOG.warning("fromPrefix", "Empty enchantment prefix");
            return null;
        }

        // O(1) fast path: full key exact match takes priority (e.g. "sharpness")
        Enchantment byFullKey = FULL_KEY_MAP.get(prefix);
        if (byFullKey != null) return byFullKey;

        // O(1) fast path: short key exact match (e.g. "sharp" — produced by toShortKey())
        Enchantment byShortKey = SHORT_KEY_MAP.get(prefix);
        if (byShortKey != null) return byShortKey;

        // Slow path: prefix scan for partial inputs not exactly equal to any key
        Enchantment prefixMatch = null;
        boolean ambiguous = false;
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (e.getKey().getKey().startsWith(prefix)) {
                if (prefixMatch == null) {
                    prefixMatch = e;
                } else {
                    ambiguous = true;
                    break; // first match already found; just flag ambiguity and stop
                }
            }
        }

        if (prefixMatch != null) {
            if (ambiguous) {
                LOG.log(Level.WARNING, "fromPrefix",
                        "Ambiguous enchantment prefix: {0}; using first registry match", prefix);
            }
            return prefixMatch;
        }
        LOG.log(Level.WARNING, "fromPrefix", "No enchantment found for prefix: {0}", prefix);
        return null;
    }

    /** Sign-text representation: {@code "ENCHBOOK:fire_p:3"}. */
    public static String toSignText(Enchantment ench, short level) {
        return "ENCHBOOK:" + toShortKey(ench) + ":" + level;
    }

    /** Lore representation: {@code "ENCHANTED_BOOK:fire_protection:3 100"}. */
    public static String toLoreText(Enchantment ench, short level, int amount) {
        return Material.ENCHANTED_BOOK + ":" + ench.getKey().getKey() + ":" + level + " " + amount;
    }
}
