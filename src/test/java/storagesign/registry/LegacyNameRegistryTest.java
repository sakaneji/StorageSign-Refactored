package storagesign.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LegacyNameRegistry}.
 *
 * <p>Verifies that the bidirectional material ↔ name map is populated correctly
 * and that names are generated from material names following the expected pattern.
 */
class LegacyNameRegistryTest {

    // ── NAME_TO_MATERIAL ──────────────────────────────────────────────────────

    @Test
    void nameToMaterial_containsOakStorageSign() {
        assertEquals(Material.OAK_SIGN,
            LegacyNameRegistry.NAME_TO_MATERIAL.get("OakStorageSign"));
    }

    @Test
    void nameToMaterial_containsDarkOakStorageSign() {
        assertEquals(Material.DARK_OAK_SIGN,
            LegacyNameRegistry.NAME_TO_MATERIAL.get("DarkOakStorageSign"));
    }

    @Test
    void nameToMaterial_containsSpruceStorageSign() {
        assertEquals(Material.SPRUCE_SIGN,
            LegacyNameRegistry.NAME_TO_MATERIAL.get("SpruceStorageSign"));
    }

    @Test
    void nameToMaterial_containsBirchStorageSign() {
        assertEquals(Material.BIRCH_SIGN,
            LegacyNameRegistry.NAME_TO_MATERIAL.get("BirchStorageSign"));
    }

    @Test
    void nameToMaterial_containsJungleStorageSign() {
        assertEquals(Material.JUNGLE_SIGN,
            LegacyNameRegistry.NAME_TO_MATERIAL.get("JungleStorageSign"));
    }

    @Test
    void nameToMaterial_containsAcaciaStorageSign() {
        assertEquals(Material.ACACIA_SIGN,
            LegacyNameRegistry.NAME_TO_MATERIAL.get("AcaciaStorageSign"));
    }

    @Test
    void nameToMaterial_returnsNullForUnknownName() {
        assertNull(LegacyNameRegistry.NAME_TO_MATERIAL.get("NotARealStorageSign"));
        assertNull(LegacyNameRegistry.NAME_TO_MATERIAL.get("STONE"));
        assertNull(LegacyNameRegistry.NAME_TO_MATERIAL.get(""));
    }

    // ── MATERIAL_TO_NAME ──────────────────────────────────────────────────────

    @Test
    void materialToName_mapsOakSignToOakStorageSign() {
        assertEquals("OakStorageSign",
            LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.OAK_SIGN));
    }

    @Test
    void materialToName_mapsDarkOakSignToDarkOakStorageSign() {
        assertEquals("DarkOakStorageSign",
            LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.DARK_OAK_SIGN));
    }

    @Test
    void materialToName_mapsSpruceSignToSpruceStorageSign() {
        assertEquals("SpruceStorageSign",
            LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.SPRUCE_SIGN));
    }

    @Test
    void materialToName_returnsNullForNonSignMaterial() {
        assertNull(LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.STONE));
        assertNull(LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.OAK_PLANKS));
    }

    @Test
    void materialToName_doesNotContainWallSigns() {
        // Only standing signs are stored as SS items, not wall signs
        assertNull(LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.OAK_WALL_SIGN));
    }

    // ── Map consistency ───────────────────────────────────────────────────────

    @Test
    void mapsHaveSameSize() {
        assertEquals(LegacyNameRegistry.NAME_TO_MATERIAL.size(),
                     LegacyNameRegistry.MATERIAL_TO_NAME.size());
    }

    @Test
    void allSignMaterialsHaveNameEntry() {
        for (Material mat : MaterialRegistry.SIGN_MATERIALS) {
            assertTrue(LegacyNameRegistry.MATERIAL_TO_NAME.containsKey(mat),
                "Missing name entry for sign material: " + mat);
        }
    }

    @Test
    void roundTrip_nameToMaterialToName() {
        for (var entry : LegacyNameRegistry.MATERIAL_TO_NAME.entrySet()) {
            Material mat  = entry.getKey();
            String   name = entry.getValue();
            // Forward: mat → name, then name → mat
            assertEquals(mat, LegacyNameRegistry.NAME_TO_MATERIAL.get(name),
                "Round-trip failed for: " + mat + " → " + name);
        }
    }

    @Test
    void roundTrip_materialToNameToMaterial() {
        for (var entry : LegacyNameRegistry.NAME_TO_MATERIAL.entrySet()) {
            String   name = entry.getKey();
            Material mat  = entry.getValue();
            assertEquals(name, LegacyNameRegistry.MATERIAL_TO_NAME.get(mat),
                "Round-trip failed for: " + name + " → " + mat);
        }
    }

    // ── Generated name format ─────────────────────────────────────────────────

    @Test
    void generatedNamesEndWithStorageSign() {
        for (String name : LegacyNameRegistry.NAME_TO_MATERIAL.keySet()) {
            assertTrue(name.endsWith("StorageSign"),
                "Generated name does not end with 'StorageSign': " + name);
        }
    }

    @Test
    void generatedNamesUseTitleCase() {
        // First char of each word part should be uppercase
        String name = LegacyNameRegistry.MATERIAL_TO_NAME.get(Material.DARK_OAK_SIGN);
        assertNotNull(name);
        assertEquals("DarkOakStorageSign", name);
        assertTrue(Character.isUpperCase(name.charAt(0)));
    }

    @Test
    void generatedNamesContainNoUnderscores() {
        for (String name : LegacyNameRegistry.NAME_TO_MATERIAL.keySet()) {
            assertFalse(name.contains("_"),
                "Generated name contains underscore: " + name);
        }
    }

    @Test
    void buildNameSkipsEmptyMaterialNameSegments() throws Exception {
        Method method = LegacyNameRegistry.class.getDeclaredMethod("buildName", String.class);
        method.setAccessible(true);

        assertEquals("DarkOakStorageSign", method.invoke(null, "DARK__OAK__SIGN"));
        assertEquals("StorageSign", method.invoke(null, "__SIGN__"));
    }
}
