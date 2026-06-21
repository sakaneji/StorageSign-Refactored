package storagesign.registry;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MaterialRegistry}.
 *
 * <p>All operations rely solely on the {@link Material} enum, which is available in the
 * Spigot API JAR without requiring a running server.
 */
class MaterialRegistryTest {

    // ── SIGN_MATERIALS ────────────────────────────────────────────────────────

    @Test
    void signMaterials_containsOakSign() {
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.OAK_SIGN));
    }

    @Test
    void signMaterials_containsSpruceSign() {
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.SPRUCE_SIGN));
    }

    @Test
    void signMaterials_containsDarkOakSign() {
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.DARK_OAK_SIGN));
    }

    @Test
    void signMaterials_containsBirchSign() {
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.BIRCH_SIGN));
    }

    @Test
    void signMaterials_doesNotContainWallSign() {
        assertFalse(MaterialRegistry.SIGN_MATERIALS.contains(Material.OAK_WALL_SIGN));
        assertFalse(MaterialRegistry.SIGN_MATERIALS.contains(Material.SPRUCE_WALL_SIGN));
    }

    @Test
    void signMaterials_doesNotContainNonSign() {
        assertFalse(MaterialRegistry.SIGN_MATERIALS.contains(Material.STONE));
        assertFalse(MaterialRegistry.SIGN_MATERIALS.contains(Material.OAK_PLANKS));
    }

    @Test
    void signMaterials_isNotEmpty() {
        assertFalse(MaterialRegistry.SIGN_MATERIALS.isEmpty());
    }

    // ── WALL_SIGN_MATERIALS ───────────────────────────────────────────────────

    @Test
    void wallSignMaterials_containsOakWallSign() {
        assertTrue(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.OAK_WALL_SIGN));
    }

    @Test
    void wallSignMaterials_containsSpruceWallSign() {
        assertTrue(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.SPRUCE_WALL_SIGN));
    }

    @Test
    void wallSignMaterials_containsWallHangingSign() {
        assertTrue(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.OAK_WALL_HANGING_SIGN));
    }

    @Test
    void wallSignMaterials_doesNotContainStandingSign() {
        assertFalse(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.OAK_SIGN));
        assertFalse(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.SPRUCE_SIGN));
    }

    @Test
    void wallSignMaterials_doesNotContainNonSign() {
        assertFalse(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.STONE));
    }

    // ── isAnySign ─────────────────────────────────────────────────────────────

    @Test
    void isAnySign_trueForStandingSign() {
        assertTrue(MaterialRegistry.isAnySign(Material.OAK_SIGN));
        assertTrue(MaterialRegistry.isAnySign(Material.DARK_OAK_SIGN));
    }

    @Test
    void isAnySign_trueForWallSign() {
        assertTrue(MaterialRegistry.isAnySign(Material.OAK_WALL_SIGN));
        assertTrue(MaterialRegistry.isAnySign(Material.SPRUCE_WALL_SIGN));
    }

    @Test
    void isAnySign_falseForNonSign() {
        assertFalse(MaterialRegistry.isAnySign(Material.STONE));
        assertFalse(MaterialRegistry.isAnySign(Material.CHEST));
        assertFalse(MaterialRegistry.isAnySign(Material.AIR));
    }

    // ── WALL_TO_SIGN ──────────────────────────────────────────────────────────

    @Test
    void wallToSign_mapsOakWallSignToOakSign() {
        assertEquals(Material.OAK_SIGN, MaterialRegistry.WALL_TO_SIGN.get(Material.OAK_WALL_SIGN));
    }

    @Test
    void wallToSign_mapsSpruceWallSignToSpruceSign() {
        assertEquals(Material.SPRUCE_SIGN, MaterialRegistry.WALL_TO_SIGN.get(Material.SPRUCE_WALL_SIGN));
    }

    @Test
    void wallToSign_mapsDarkOakWallSignToDarkOakSign() {
        assertEquals(Material.DARK_OAK_SIGN, MaterialRegistry.WALL_TO_SIGN.get(Material.DARK_OAK_WALL_SIGN));
    }

    @Test
    void wallToSign_mapsWallHangingSignToHangingSign() {
        assertEquals(Material.OAK_HANGING_SIGN, MaterialRegistry.WALL_TO_SIGN.get(Material.OAK_WALL_HANGING_SIGN));
    }

    @Test
    void wallToSign_doesNotContainStandingSign() {
        assertNull(MaterialRegistry.WALL_TO_SIGN.get(Material.OAK_SIGN));
    }

    // ── POTION_MATERIALS ──────────────────────────────────────────────────────

    @Test
    void potionMaterials_containsAllThreePotionTypes() {
        assertTrue(MaterialRegistry.POTION_MATERIALS.contains(Material.POTION));
        assertTrue(MaterialRegistry.POTION_MATERIALS.contains(Material.SPLASH_POTION));
        assertTrue(MaterialRegistry.POTION_MATERIALS.contains(Material.LINGERING_POTION));
    }

    @Test
    void potionMaterials_hasExactlyThreeEntries() {
        assertEquals(3, MaterialRegistry.POTION_MATERIALS.size());
    }

    @Test
    void potionMaterials_doesNotContainNonPotion() {
        assertFalse(MaterialRegistry.POTION_MATERIALS.contains(Material.GLASS_BOTTLE));
    }

    // ── SHULKER_BOX_MATERIALS ─────────────────────────────────────────────────

    @Test
    void shulkerBoxMaterials_containsUncoloredShulkerBox() {
        assertTrue(MaterialRegistry.SHULKER_BOX_MATERIALS.contains(Material.SHULKER_BOX));
    }

    @Test
    void shulkerBoxMaterials_containsWhiteShulkerBox() {
        assertTrue(MaterialRegistry.SHULKER_BOX_MATERIALS.contains(Material.WHITE_SHULKER_BOX));
    }

    @Test
    void shulkerBoxMaterials_containsRedShulkerBox() {
        assertTrue(MaterialRegistry.SHULKER_BOX_MATERIALS.contains(Material.RED_SHULKER_BOX));
    }

    @Test
    void shulkerBoxMaterials_doesNotContainNonShulkerBox() {
        assertFalse(MaterialRegistry.SHULKER_BOX_MATERIALS.contains(Material.CHEST));
        assertFalse(MaterialRegistry.SHULKER_BOX_MATERIALS.contains(Material.STONE));
    }

    // ── Set sizes are consistent ───────────────────────────────────────────────

    @Test
    void hangingSignsAreSplitBetweenStandingAndWallSets() {
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.OAK_HANGING_SIGN));
        assertTrue(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.OAK_WALL_HANGING_SIGN));
    }

    @Test
    void hangingSignsAreInSignMaterials() {
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.OAK_HANGING_SIGN));
        assertTrue(MaterialRegistry.SIGN_MATERIALS.contains(Material.SPRUCE_HANGING_SIGN));
    }

    @Test
    void hangingSignsAreNotInWallSignMaterials() {
        assertFalse(MaterialRegistry.WALL_SIGN_MATERIALS.contains(Material.OAK_HANGING_SIGN));
    }

    @Test
    void wallHangingSignsAreAnySign() {
        assertTrue(MaterialRegistry.isAnySign(Material.OAK_WALL_HANGING_SIGN));
    }

    @Test
    void wallToSignContainsEntryForEveryWallSign() {
        for (Material wall : MaterialRegistry.WALL_SIGN_MATERIALS) {
            assertTrue(MaterialRegistry.WALL_TO_SIGN.containsKey(wall),
                "Missing WALL_TO_SIGN mapping for: " + wall);
        }
    }

    @Test
    void resolvesBlockSignMaterialToMatchingInventoryItem() {
        assertEquals(Material.OAK_SIGN,
            MaterialRegistry.toItemSignMaterial(Material.OAK_SIGN));
        assertEquals(Material.OAK_SIGN,
            MaterialRegistry.toItemSignMaterial(Material.OAK_WALL_SIGN));
        assertEquals(Material.OAK_HANGING_SIGN,
            MaterialRegistry.toItemSignMaterial(Material.OAK_WALL_HANGING_SIGN));
        assertNull(MaterialRegistry.toItemSignMaterial(null));
    }

    @Test
    void signAndWallSignMaterialSetsAreDisjoint() {
        for (Material m : MaterialRegistry.SIGN_MATERIALS) {
            assertFalse(MaterialRegistry.WALL_SIGN_MATERIALS.contains(m),
                "Material is in both SIGN and WALL_SIGN sets: " + m);
        }
    }
}
