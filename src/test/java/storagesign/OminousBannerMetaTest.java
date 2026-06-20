package storagesign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import storagesign.compat.OminousBannerCodec;

class OminousBannerMetaTest {

    @Test
    void acceptsExactVanillaOminousBannerPatterns() {
        assertTrue(OminousBannerCodec.matches(standardColors(), standardKeys()));
    }

    @Test
    void rejectsArbitraryEightPatternWhiteBanner() {
        List<DyeColor> colors = new java.util.ArrayList<>(standardColors());
        colors.set(0, DyeColor.RED);
        assertFalse(OminousBannerCodec.matches(colors, standardKeys()));
    }

    @Test
    void rejectsBannerWithoutEightPatterns() {
        assertFalse(OminousBannerCodec.matches(List.of(), List.of()));
    }

    private static List<DyeColor> standardColors() {
        return List.of(
            DyeColor.CYAN, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.LIGHT_GRAY,
            DyeColor.BLACK, DyeColor.LIGHT_GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK
        );
    }

    private static List<NamespacedKey> standardKeys() {
        return List.of(
            key("rhombus"), key("stripe_bottom"), key("stripe_center"), key("border"),
            key("stripe_middle"), key("half_horizontal"), key("circle"), key("border")
        );
    }

    private static NamespacedKey key(String value) {
        return NamespacedKey.minecraft(value);
    }
}
