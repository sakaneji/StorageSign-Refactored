package storagesign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

class OminousBannerMetaTest {

    @Test
    void acceptsExactVanillaOminousBannerPatterns() {
        assertTrue(StorageSignPlugin.isOminousBannerPatterns(
            standardColors(), standardTypes()
        ));
    }

    @Test
    void rejectsArbitraryEightPatternWhiteBanner() {
        List<DyeColor> colors = new java.util.ArrayList<>(standardColors());
        colors.set(0, DyeColor.RED);
        assertFalse(StorageSignPlugin.isOminousBannerPatterns(colors, standardTypes()));
    }

    @Test
    void rejectsBannerWithoutEightPatterns() {
        assertFalse(StorageSignPlugin.isOminousBannerPatterns(List.of(), List.of()));
    }

    private static List<DyeColor> standardColors() {
        return List.of(
            DyeColor.CYAN, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.LIGHT_GRAY,
            DyeColor.BLACK, DyeColor.LIGHT_GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK
        );
    }

    private static List<String> standardTypes() {
        return List.of(
            "RHOMBUS", "STRIPE_BOTTOM", "STRIPE_CENTER", "BORDER",
            "STRIPE_MIDDLE", "HALF_HORIZONTAL", "CIRCLE", "BORDER"
        );
    }
}
