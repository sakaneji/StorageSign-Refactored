package storagesign.compat;

import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Encodes the vanilla ominous banner using stable registry keys. */
public final class OminousBannerCodec {

    private static final List<Entry> DEFINITION = List.of(
        entry(DyeColor.CYAN, "rhombus"),
        entry(DyeColor.LIGHT_GRAY, "stripe_bottom"),
        entry(DyeColor.GRAY, "stripe_center"),
        entry(DyeColor.LIGHT_GRAY, "border"),
        entry(DyeColor.BLACK, "stripe_middle"),
        entry(DyeColor.LIGHT_GRAY, "half_horizontal"),
        entry(DyeColor.LIGHT_GRAY, "circle"),
        entry(DyeColor.BLACK, "border")
    );

    public BannerMeta create() {
        ItemMeta itemMeta = new ItemStack(Material.WHITE_BANNER).getItemMeta();
        if (!(itemMeta instanceof BannerMeta bannerMeta)) return null;

        List<Pattern> patterns = DEFINITION.stream().map(definition -> {
            PatternType type = Registry.BANNER_PATTERN.get(definition.key());
            if (type == null) {
                throw new IllegalStateException("Missing banner pattern: " + definition.key());
            }
            return new Pattern(definition.color(), type);
        }).toList();
        bannerMeta.setPatterns(patterns);
        return bannerMeta;
    }

    public boolean matches(BannerMeta meta) {
        if (meta == null || meta.numberOfPatterns() != DEFINITION.size()) return false;
        List<Pattern> patterns = meta.getPatterns();
        for (int index = 0; index < DEFINITION.size(); index++) {
            Entry expected = DEFINITION.get(index);
            Pattern actual = patterns.get(index);
            if (actual.getColor() != expected.color()) return false;
            if (!matchesKey(actual.getPattern(), expected.key())) return false;
        }
        return true;
    }

    /**
     * 現行サーバーの空BannerMetaへ、検証済みの不吉な旗の模様だけを移す。
     *
     * <p>起動時のレジストリからの再構築が失敗しても、DataFixerを通過した実物から
     * 独自Lore等を持ち込まずに正規メタを復旧できる。
     */
    public BannerMeta canonicalize(BannerMeta source) {
        if (!matches(source)) return null;
        ItemMeta itemMeta = new ItemStack(Material.WHITE_BANNER).getItemMeta();
        if (!(itemMeta instanceof BannerMeta canonical)) return null;
        canonical.setPatterns(source.getPatterns());
        return canonical;
    }

    public static boolean matches(List<DyeColor> colors, List<NamespacedKey> keys) {
        if (colors.size() != DEFINITION.size() || keys.size() != DEFINITION.size()) return false;
        for (int index = 0; index < DEFINITION.size(); index++) {
            Entry expected = DEFINITION.get(index);
            if (colors.get(index) != expected.color() || !keys.get(index).equals(expected.key())) {
                return false;
            }
        }
        return true;
    }

    private static Entry entry(DyeColor color, String key) {
        return new Entry(color, NamespacedKey.minecraft(key));
    }

    private boolean matchesKey(PatternType type, NamespacedKey expected) {
        try {
            NamespacedKey registryKey = Registry.BANNER_PATTERN.getKey(type);
            if (expected.equals(registryKey)) return true;
        } catch (LinkageError | RuntimeException ignored) {
            // Some registry implementations only support forward lookup.
        }
        try {
            PatternType registered = Registry.BANNER_PATTERN.get(expected);
            if (registered != null && registered.equals(type)) return true;
        } catch (LinkageError | RuntimeException ignored) {
            // Fall through to older PatternType#getKey implementations.
        }
        try {
            Object key = PatternType.class.getMethod("getKey").invoke(type);
            return expected.equals(key);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private record Entry(DyeColor color, NamespacedKey key) {}
}
