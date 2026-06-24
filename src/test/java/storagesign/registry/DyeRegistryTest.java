package storagesign.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DyeRegistry}.
 *
 * <p>All operations use {@link Material} and {@link DyeColor} enum constants,
 * which are available without a running Bukkit server.
 */
class DyeRegistryTest {

    // ── isDye ─────────────────────────────────────────────────────────────────

    @Test
    void isDye_trueForAllDyeMaterials() {
        assertTrue(DyeRegistry.isDye(Material.WHITE_DYE));
        assertTrue(DyeRegistry.isDye(Material.ORANGE_DYE));
        assertTrue(DyeRegistry.isDye(Material.MAGENTA_DYE));
        assertTrue(DyeRegistry.isDye(Material.LIGHT_BLUE_DYE));
        assertTrue(DyeRegistry.isDye(Material.YELLOW_DYE));
        assertTrue(DyeRegistry.isDye(Material.LIME_DYE));
        assertTrue(DyeRegistry.isDye(Material.PINK_DYE));
        assertTrue(DyeRegistry.isDye(Material.GRAY_DYE));
        assertTrue(DyeRegistry.isDye(Material.LIGHT_GRAY_DYE));
        assertTrue(DyeRegistry.isDye(Material.CYAN_DYE));
        assertTrue(DyeRegistry.isDye(Material.PURPLE_DYE));
        assertTrue(DyeRegistry.isDye(Material.BLUE_DYE));
        assertTrue(DyeRegistry.isDye(Material.BROWN_DYE));
        assertTrue(DyeRegistry.isDye(Material.GREEN_DYE));
        assertTrue(DyeRegistry.isDye(Material.RED_DYE));
        assertTrue(DyeRegistry.isDye(Material.BLACK_DYE));
    }

    @Test
    void isDye_falseForNonDyeMaterials() {
        assertFalse(DyeRegistry.isDye(Material.STONE));
        assertFalse(DyeRegistry.isDye(Material.OAK_PLANKS));
        assertFalse(DyeRegistry.isDye(Material.INK_SAC));
        assertFalse(DyeRegistry.isDye(Material.GLOW_INK_SAC));
        assertFalse(DyeRegistry.isDye(Material.WHITE_WOOL));
        assertFalse(DyeRegistry.isDye(Material.AIR));
    }

    // ── getColor ──────────────────────────────────────────────────────────────

    @Test
    void getColor_returnsCorrectColorForEachDye() {
        assertEquals(DyeColor.WHITE,      DyeRegistry.getColor(Material.WHITE_DYE));
        assertEquals(DyeColor.ORANGE,     DyeRegistry.getColor(Material.ORANGE_DYE));
        assertEquals(DyeColor.MAGENTA,    DyeRegistry.getColor(Material.MAGENTA_DYE));
        assertEquals(DyeColor.LIGHT_BLUE, DyeRegistry.getColor(Material.LIGHT_BLUE_DYE));
        assertEquals(DyeColor.YELLOW,     DyeRegistry.getColor(Material.YELLOW_DYE));
        assertEquals(DyeColor.LIME,       DyeRegistry.getColor(Material.LIME_DYE));
        assertEquals(DyeColor.PINK,       DyeRegistry.getColor(Material.PINK_DYE));
        assertEquals(DyeColor.GRAY,       DyeRegistry.getColor(Material.GRAY_DYE));
        assertEquals(DyeColor.LIGHT_GRAY, DyeRegistry.getColor(Material.LIGHT_GRAY_DYE));
        assertEquals(DyeColor.CYAN,       DyeRegistry.getColor(Material.CYAN_DYE));
        assertEquals(DyeColor.PURPLE,     DyeRegistry.getColor(Material.PURPLE_DYE));
        assertEquals(DyeColor.BLUE,       DyeRegistry.getColor(Material.BLUE_DYE));
        assertEquals(DyeColor.BROWN,      DyeRegistry.getColor(Material.BROWN_DYE));
        assertEquals(DyeColor.GREEN,      DyeRegistry.getColor(Material.GREEN_DYE));
        assertEquals(DyeColor.RED,        DyeRegistry.getColor(Material.RED_DYE));
        assertEquals(DyeColor.BLACK,      DyeRegistry.getColor(Material.BLACK_DYE));
    }

    @Test
    void getColor_returnsNullForNonDye() {
        assertNull(DyeRegistry.getColor(Material.STONE));
        assertNull(DyeRegistry.getColor(Material.INK_SAC));
        assertNull(DyeRegistry.getColor(Material.AIR));
    }

    // ── Map completeness ──────────────────────────────────────────────────────

    @Test
    void dyeColorByMaterial_containsAllSixteenColors() {
        // Every DyeColor value should have a corresponding dye material
        assertEquals(16, DyeRegistry.DYE_COLOR_BY_MATERIAL.size());
    }

    @Test
    void dyeColorByMaterial_eachValueIsDifferentColor() {
        // No two dye materials should map to the same DyeColor
        long distinctColors = DyeRegistry.DYE_COLOR_BY_MATERIAL.values()
            .stream().distinct().count();
        assertEquals(DyeRegistry.DYE_COLOR_BY_MATERIAL.size(), distinctColors);
    }

    @Test
    void roundTrip_isDyeMatchesGetColorNonNull() {
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            boolean isDye  = DyeRegistry.isDye(m);
            boolean hasColor = DyeRegistry.getColor(m) != null;
            assertEquals(isDye, hasColor,
                "isDye and getColor disagree for: " + m);
        }
    }

    @Test
    void buildDyeMapSkipsMissingMaterials() {
        Map<Material, DyeColor> map = DyeRegistry.buildDyeMap(name -> null);
        assertTrue(map.isEmpty());
    }
}
