package storagesign.item;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class PotionHelperTest {

    // ── fromSignText (pre-existing) ───────────────────────────────────────────

    @Test
    void resolvesGenericNormalPotionWithoutMatchingLongOrStrongVariants() {
        assertEquals(PotionType.NIGHT_VISION, PotionHelper.fromSignText("NIGHT", "0"));
        assertEquals(PotionType.POISON, PotionHelper.fromSignText("POISO", "0"));
    }

    @Test
    void resolvesGenericExtendedAndStrongPotionVariantsByEnhanceCode() {
        assertEquals(PotionType.LONG_NIGHT_VISION, PotionHelper.fromSignText("NIGHT", "1"));
        assertEquals(PotionType.LONG_POISON, PotionHelper.fromSignText("POISO", "1"));
        assertEquals(PotionType.STRONG_POISON, PotionHelper.fromSignText("POISO", "2"));
    }

    @Test
    void preservesSpecialCasePotionMappings() {
        assertEquals(PotionType.HEALING, PotionHelper.fromSignText("HEAL", "0"));
        assertEquals(PotionType.STRONG_HEALING, PotionHelper.fromSignText("HEAL", "2"));
        assertEquals(PotionType.LONG_WATER_BREATHING, PotionHelper.fromSignText("BREAT", "1"));
    }

    // ── fromSignText: all special cases ──────────────────────────────────────

    @Test
    void fromSignText_allSpecialCaseMappings() {
        assertEquals(PotionType.HEALING,             PotionHelper.fromSignText("HEAL",  "0"));
        assertEquals(PotionType.STRONG_HEALING,      PotionHelper.fromSignText("HEAL",  "2"));
        assertEquals(PotionType.WATER_BREATHING,     PotionHelper.fromSignText("BREAT", "0"));
        assertEquals(PotionType.LONG_WATER_BREATHING,PotionHelper.fromSignText("BREAT", "1"));
        assertEquals(PotionType.HARMING,             PotionHelper.fromSignText("DAMAG", "0"));
        assertEquals(PotionType.STRONG_HARMING,      PotionHelper.fromSignText("DAMAG", "2"));
        assertEquals(PotionType.LEAPING,             PotionHelper.fromSignText("JUMP",  "0"));
        assertEquals(PotionType.LONG_LEAPING,        PotionHelper.fromSignText("JUMP",  "1"));
        assertEquals(PotionType.STRONG_LEAPING,      PotionHelper.fromSignText("JUMP",  "2"));
        assertEquals(PotionType.SWIFTNESS,           PotionHelper.fromSignText("SPEED", "0"));
        assertEquals(PotionType.LONG_SWIFTNESS,      PotionHelper.fromSignText("SPEED", "1"));
        assertEquals(PotionType.STRONG_SWIFTNESS,    PotionHelper.fromSignText("SPEED", "2"));
        assertEquals(PotionType.REGENERATION,        PotionHelper.fromSignText("REGEN", "0"));
        assertEquals(PotionType.LONG_REGENERATION,   PotionHelper.fromSignText("REGEN", "1"));
        assertEquals(PotionType.STRONG_REGENERATION, PotionHelper.fromSignText("REGEN", "2"));
    }

    @Test
    void fromSignText_unknownNameReturnsNull() {
        assertNull(PotionHelper.fromSignText("XXXXX", "0"));
    }

    // ── getShortName ──────────────────────────────────────────────────────────

    @Test
    void getShortName_specialCaseReturnsCustomKey() {
        assertEquals("HEAL",  PotionHelper.getShortName(PotionType.HEALING));
        assertEquals("HEAL",  PotionHelper.getShortName(PotionType.STRONG_HEALING));
        assertEquals("BREAT", PotionHelper.getShortName(PotionType.WATER_BREATHING));
        assertEquals("BREAT", PotionHelper.getShortName(PotionType.LONG_WATER_BREATHING));
        assertEquals("DAMAG", PotionHelper.getShortName(PotionType.HARMING));
        assertEquals("DAMAG", PotionHelper.getShortName(PotionType.STRONG_HARMING));
        assertEquals("JUMP",  PotionHelper.getShortName(PotionType.LEAPING));
        assertEquals("SPEED", PotionHelper.getShortName(PotionType.SWIFTNESS));
        assertEquals("REGEN", PotionHelper.getShortName(PotionType.REGENERATION));
        assertEquals("REGEN", PotionHelper.getShortName(PotionType.LONG_REGENERATION));
        assertEquals("REGEN", PotionHelper.getShortName(PotionType.STRONG_REGENERATION));
    }

    @Test
    void getShortName_genericUsesFirst5CharsOfBaseName() {
        assertEquals("NIGHT", PotionHelper.getShortName(PotionType.NIGHT_VISION));
        assertEquals("NIGHT", PotionHelper.getShortName(PotionType.LONG_NIGHT_VISION));
        assertEquals("POISO", PotionHelper.getShortName(PotionType.POISON));
        assertEquals("POISO", PotionHelper.getShortName(PotionType.LONG_POISON));
        assertEquals("POISO", PotionHelper.getShortName(PotionType.STRONG_POISON));
    }

    @Test
    void getShortName_shortNameReturnedAsIs() {
        // FIRE_RESISTANCE → base "FIRE_" (5 chars) → but actually name is FIRE_RESISTANCE (14 chars)
        // → first 5 chars = "FIRE_"
        // Test that getShortName produces a 5-char prefix for a long name
        String shortName = PotionHelper.getShortName(PotionType.FIRE_RESISTANCE);
        assertEquals(5, shortName.length()); // should be exactly 5 chars
    }

    // ── getEnhanceCode ────────────────────────────────────────────────────────

    @Test
    void getEnhanceCode_normalReturns0() {
        assertEquals("0", PotionHelper.getEnhanceCode(PotionType.HEALING));
        assertEquals("0", PotionHelper.getEnhanceCode(PotionType.NIGHT_VISION));
        assertEquals("0", PotionHelper.getEnhanceCode(PotionType.POISON));
    }

    @Test
    void getEnhanceCode_extendedReturns1() {
        assertEquals("1", PotionHelper.getEnhanceCode(PotionType.LONG_NIGHT_VISION));
        assertEquals("1", PotionHelper.getEnhanceCode(PotionType.LONG_POISON));
        assertEquals("1", PotionHelper.getEnhanceCode(PotionType.LONG_REGENERATION));
    }

    @Test
    void getEnhanceCode_strongReturns2() {
        assertEquals("2", PotionHelper.getEnhanceCode(PotionType.STRONG_HEALING));
        assertEquals("2", PotionHelper.getEnhanceCode(PotionType.STRONG_POISON));
        assertEquals("2", PotionHelper.getEnhanceCode(PotionType.STRONG_SWIFTNESS));
    }

    // ── getMaterialPrefix ─────────────────────────────────────────────────────

    @Test
    void getMaterialPrefix_normalPotionReturnsEmpty() {
        assertEquals("", PotionHelper.getMaterialPrefix(Material.POTION));
    }

    @Test
    void getMaterialPrefix_splashPotionReturnsS() {
        assertEquals("S", PotionHelper.getMaterialPrefix(Material.SPLASH_POTION));
    }

    @Test
    void getMaterialPrefix_lingeringPotionReturnsL() {
        assertEquals("L", PotionHelper.getMaterialPrefix(Material.LINGERING_POTION));
    }

    @Test
    void getMaterialPrefix_otherMaterialReturnsEmpty() {
        assertEquals("", PotionHelper.getMaterialPrefix(Material.STONE));
    }

    // ── materialFromPrefix ────────────────────────────────────────────────────

    @Test
    void materialFromPrefix_emptyStringReturnsPotion() {
        assertEquals(Material.POTION, PotionHelper.materialFromPrefix(""));
    }

    @Test
    void materialFromPrefix_sReturnsSplashPotion() {
        assertEquals(Material.SPLASH_POTION, PotionHelper.materialFromPrefix("S"));
    }

    @Test
    void materialFromPrefix_lReturnsLingeringPotion() {
        assertEquals(Material.LINGERING_POTION, PotionHelper.materialFromPrefix("L"));
    }

    // ── normalizeName ─────────────────────────────────────────────────────────

    @Test
    void normalizeName_convertsLegacyNBTNames() {
        assertEquals("HEAL",  PotionHelper.normalizeName("INSTANT_HEAL"));
        assertEquals("DAMAG", PotionHelper.normalizeName("INSTANT_DAMAGE"));
        assertEquals("JUMP",  PotionHelper.normalizeName("JUMP"));
        assertEquals("SPEED", PotionHelper.normalizeName("SPEED"));
        assertEquals("REGEN", PotionHelper.normalizeName("REGEN"));
        assertEquals("BREAT", PotionHelper.normalizeName("WATER_BREATHING"));
    }

    @Test
    void normalizeName_returnsUnchangedForUnknownName() {
        assertEquals("NIGHT", PotionHelper.normalizeName("NIGHT"));
        assertEquals("SOMETHING_UNKNOWN", PotionHelper.normalizeName("SOMETHING_UNKNOWN"));
    }

    // ── toSignText ────────────────────────────────────────────────────────────

    @Test
    void toSignText_formatsNormalPotionCorrectly() {
        assertEquals("POTION:HEAL:0",
            PotionHelper.toSignText(Material.POTION, PotionType.HEALING, (short) 0));
    }

    @Test
    void toSignText_formatsSplashPotionCorrectly() {
        assertEquals("SPOTION:REGEN:1",
            PotionHelper.toSignText(Material.SPLASH_POTION, PotionType.LONG_REGENERATION, (short) 1));
    }

    @Test
    void toSignText_formatsLingeringPotionCorrectly() {
        assertEquals("LPOTION:SPEED:2",
            PotionHelper.toSignText(Material.LINGERING_POTION, PotionType.STRONG_SWIFTNESS, (short) 2));
    }

    // ── Round-trip: getShortName + getEnhanceCode → fromSignText ─────────────

    @Test
    void roundTrip_specialCasePotions() {
        for (PotionType type : new PotionType[]{
                PotionType.HEALING, PotionType.STRONG_HEALING,
                PotionType.WATER_BREATHING, PotionType.LONG_WATER_BREATHING,
                PotionType.HARMING, PotionType.STRONG_HARMING,
                PotionType.LEAPING, PotionType.LONG_LEAPING, PotionType.STRONG_LEAPING,
                PotionType.SWIFTNESS, PotionType.LONG_SWIFTNESS, PotionType.STRONG_SWIFTNESS,
                PotionType.REGENERATION, PotionType.LONG_REGENERATION, PotionType.STRONG_REGENERATION}) {
            String shortName = PotionHelper.getShortName(type);
            String code      = PotionHelper.getEnhanceCode(type);
            assertEquals(type, PotionHelper.fromSignText(shortName, code),
                "Round-trip failed for: " + type);
        }
    }

    @Test
    void roundTrip_genericPotions() {
        for (PotionType type : new PotionType[]{
                PotionType.NIGHT_VISION, PotionType.LONG_NIGHT_VISION,
                PotionType.POISON, PotionType.LONG_POISON, PotionType.STRONG_POISON}) {
            String shortName = PotionHelper.getShortName(type);
            String code      = PotionHelper.getEnhanceCode(type);
            assertEquals(type, PotionHelper.fromSignText(shortName, code),
                "Round-trip failed for: " + type);
        }
    }

    @Test
    void canonicalRegistryIdentifierRoundTripsEveryRuntimePotionType() {
        for (PotionType type : PotionType.values()) {
            for (Material material : new Material[]{
                    Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION}) {
                String identifier = PotionHelper.toCanonicalIdentifier(material, type);
                PotionHelper.PotionData restored = PotionHelper.fromIdentifier(identifier);
                assertNotNull(restored, identifier);
                assertEquals(material, restored.material(), identifier);
                assertEquals(type, restored.type(), identifier);
            }
        }
    }

    @Test
    void displayIdentifierKeepsLegacySignWidthBound() {
        for (PotionType type : PotionType.values()) {
            for (Material material : new Material[]{
                    Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION}) {
                String display = PotionHelper.toDisplayIdentifier(material, type);
                assertTrue(display.length() <= 16, display);
                assertTrue(vanillaAsciiWidth(display) <= 90,
                    () -> display + " width=" + vanillaAsciiWidth(display));
            }
        }
    }

    private static int vanillaAsciiWidth(String value) {
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int glyph = switch (c) {
                case ':', '!', '.', ',' -> 2;
                case 'I', '1', 'i', 'l' -> 4;
                default -> 6;
            };
            width += glyph;
        }
        return width;
    }
}
