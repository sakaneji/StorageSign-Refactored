package storagesign.compat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionType;

/**
 * 元版 StorageSign が保存した値だけを解釈する互換レイヤー。
 *
 * <p>通常の識別子処理へ旧版固有の例外を散らさず、起動後のホットパスでは
 * 定数比較と enum lookup だけで解決する。
 */
public final class LegacyStorageSignCompatibility {

    private static final String NORMAL_POTION_PREFIX = "";
    private static final String SHORT_SPLASH_PREFIX = "S";
    private static final String SHORT_LINGERING_PREFIX = "L";
    private static final String ITEM_SPLASH_PREFIX = "SPLASH_";
    private static final String ITEM_LINGERING_PREFIX = "LINGERING_";
    private static final Set<Material> LEGACY_RECIPE_SIGNS = Collections.unmodifiableSet(
        EnumSet.of(
            Material.OAK_SIGN,
            Material.SPRUCE_SIGN,
            Material.BIRCH_SIGN,
            Material.JUNGLE_SIGN,
            Material.ACACIA_SIGN,
            Material.DARK_OAK_SIGN,
            Material.CRIMSON_SIGN,
            Material.WARPED_SIGN,
            Material.MANGROVE_SIGN,
            Material.CHERRY_SIGN,
            Material.BAMBOO_SIGN,
            Material.PALE_OAK_SIGN
        )
    );

    private LegacyStorageSignCompatibility() {}

    /**
     * 元版で通常Materialと異なる意味を持っていた識別子を解決する。
     *
     * <p>{@code SIGN} はdamage省略時に空StorageSignアイテム、
     * {@code STONE_SLAB} はdamage有無で石ハーフと旧Smooth Stone Slabを区別していた。
     */
    public static MaterialData resolveSpecialMaterial(String token, boolean hasExplicitDamage,
                                                      short explicitDamage) {
        if (token == null) return null;
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if ("SIGN".equals(normalized)) {
            return new MaterialData(
                Material.OAK_SIGN,
                hasExplicitDamage ? explicitDamage : (short) 1
            );
        }
        if ("STONE_SLAB".equals(normalized)) {
            return hasExplicitDamage
                ? new MaterialData(Material.STONE_SLAB, explicitDamage)
                : new MaterialData(Material.SMOOTH_STONE_SLAB, (short) 0);
        }
        return null;
    }

    /** 元版の看板表示・StorageSignアイテムLoreで使われたPotion prefixを判定する。 */
    public static boolean isPotionPrefix(String prefix) {
        return NORMAL_POTION_PREFIX.equals(prefix)
            || SHORT_SPLASH_PREFIX.equals(prefix)
            || SHORT_LINGERING_PREFIX.equals(prefix)
            || ITEM_SPLASH_PREFIX.equals(prefix)
            || ITEM_LINGERING_PREFIX.equals(prefix);
    }

    /** 元版の短縮形式とMaterial名形式を現在のPotion Materialへ変換する。 */
    public static Material potionMaterial(String prefix) {
        return switch (prefix) {
            case SHORT_SPLASH_PREFIX, ITEM_SPLASH_PREFIX -> Material.SPLASH_POTION;
            case SHORT_LINGERING_PREFIX, ITEM_LINGERING_PREFIX -> Material.LINGERING_POTION;
            default -> Material.POTION;
        };
    }

    /**
     * 元版のStorageSignアイテムLoreに保存された完全なPotionType名を解決する。
     *
     * <p>例: {@code NIGHT_VISION:0}、{@code POISON:1}。
     */
    public static PotionType resolveFullPotionName(String name, String enhanceCode) {
        if (name == null || enhanceCode == null) return null;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        String candidate = switch (enhanceCode) {
            case "0" -> normalized;
            case "1" -> "LONG_" + normalized;
            case "2" -> "STRONG_" + normalized;
            default -> null;
        };
        if (candidate == null) return null;
        try {
            return PotionType.valueOf(candidate);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 元版が登録したレシピキー。吊り看板は元版非対応なので対象外。 */
    public static NamespacedKey legacyRecipeKey(Material signMaterial) {
        if (!LEGACY_RECIPE_SIGNS.contains(signMaterial)) return null;
        return new NamespacedKey(
            "storagesign",
            "ssr" + signMaterial.name().toLowerCase(Locale.ROOT)
        );
    }

    public record MaterialData(Material material, short damage) {}
}
