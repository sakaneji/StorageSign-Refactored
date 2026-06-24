package storagesign.item;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.Map;
import sun.misc.Unsafe;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import storagesign.ConfigLoader;

class PotionHelperTest {

    @AfterEach
    void clearPotionAliases() throws Exception {
        setPotionAliases(Map.of());
    }

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

    @Test
    void fromSignText_knownNameWithInvalidCodeReturnsNull() {
        assertNull(PotionHelper.fromSignText("NIGHT", "2"));
        assertNull(PotionHelper.fromSignText("REGEN", "3"));
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

    @Test
    void toLoreText_formatsMaterialPotionCodeAndAmount() {
        assertEquals("POTION:HEAL:0 32",
            PotionHelper.toLoreText(Material.POTION, PotionType.HEALING, (short) 0, 32));
        assertEquals("SPLASH_POTION:REGEN:1 4",
            PotionHelper.toLoreText(Material.SPLASH_POTION, PotionType.LONG_REGENERATION, (short) 1, 4));
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

    @Test
    void fromIdentifierRejectsNullMalformedAndWrongPrefix() {
        assertNull(PotionHelper.fromIdentifier(null));
        assertNull(PotionHelper.fromIdentifier("POTION:bad"));
        assertNull(PotionHelper.fromIdentifier("XPOTION:minecraft:healing"));
    }

    @Test
    void fromIdentifierRejectsStringsWithoutPotionMarker() {
        assertNull(PotionHelper.fromIdentifier("STONE"));
    }

    @Test
    void fromIdentifierUsesConfiguredPotionKeyAlias() throws Exception {
        setPotionAliases(Map.of("example:removed_healing", "minecraft:healing"));

        PotionHelper.PotionData restored = PotionHelper.fromIdentifier(
            "POTION:example:removed_healing");

        assertNotNull(restored);
        assertEquals(Material.POTION, restored.material());
        assertEquals(PotionType.HEALING, restored.type());
    }

    @Test
    void fromIdentifierRejectsAliasChains() throws Exception {
        setPotionAliases(Map.of(
            "example:first", "example:second",
            "example:second", "minecraft:healing"));

        assertNull(PotionHelper.fromIdentifier("POTION:example:first"));
    }

    @Test
    void fromIdentifierRejectsAliasTargetsThatCannotBeResolved() throws Exception {
        setPotionAliases(Map.of("example:missing", "minecraft:not_a_real_potion"));

        assertNull(PotionHelper.fromIdentifier("POTION:example:missing"));
    }

    @Test
    void fromIdentifierRejectsUnknownCanonicalPotionKeys() {
        assertNull(PotionHelper.fromIdentifier("POTION:minecraft:not_a_real_potion"));
    }

    @Test
    void fromIdentifierRejectsUnknownNamespacedKeysWithoutAliases() {
        assertNull(PotionHelper.fromIdentifier("POTION:example:not_a_real_potion"));
    }

    @Test
    void resolveRegistryKeyRejectsInvalidNamespacedTargetAliases() throws Exception {
        setPotionAliases(Map.of("example:bad", "not a key"));
        Method method = PotionHelper.class.getDeclaredMethod("resolveRegistryKey", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, "example:bad"));
    }

    @Test
    void fromIdentifierRejectsLegacyEntriesWithInvalidEnhanceCode() {
        assertNull(PotionHelper.fromIdentifier("POTION:HEAL:x"));
    }

    @Test
    void fromIdentifierRejectsLegacyEntriesWithUnknownShortNames() {
        assertNull(PotionHelper.fromIdentifier("POTION:XXXXX:0"));
    }

    @Test
    void resolveRegistryKeyRejectsInvalidAliasedTargets() throws Exception {
        setPotionAliases(Map.of("example:bad", "not_a_key"));
        Method method = PotionHelper.class.getDeclaredMethod("resolveRegistryKey", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, "example:bad"));
    }

    @Test
    void fromIdentifierRejectsBlankAndSelfReferentialAliases() throws Exception {
        setPotionAliases(Map.of(
            "example:blank", " ",
            "example:self", "example:self"));

        assertNull(PotionHelper.fromIdentifier("POTION:example:blank"));
        assertNull(PotionHelper.fromIdentifier("POTION:example:self"));
    }

    @Test
    void fromIdentifierUsesCanonicalRegistryKeyDirectly() {
        PotionHelper.PotionData restored = PotionHelper.fromIdentifier(
            "POTION:minecraft:healing");

        assertNotNull(restored);
        assertEquals(Material.POTION, restored.material());
        assertEquals(PotionType.HEALING, restored.type());
    }

    @Test
    void fromIdentifierAcceptsLegacySplashAndLingeringIdentifiers() {
        PotionHelper.PotionData splash = PotionHelper.fromIdentifier("SPOTION:HEAL:0");
        PotionHelper.PotionData lingering = PotionHelper.fromIdentifier("LPOTION:SPEED:2");

        assertNotNull(splash);
        assertEquals(Material.SPLASH_POTION, splash.material());
        assertEquals(PotionType.HEALING, splash.type());

        assertNotNull(lingering);
        assertEquals(Material.LINGERING_POTION, lingering.material());
        assertEquals(PotionType.STRONG_SWIFTNESS, lingering.type());
    }

    @Test
    void fromIdentifierParsesLegacyPotionNamesThroughNormalisation() {
        PotionHelper.PotionData healed = PotionHelper.fromIdentifier("POTION:INSTANT_HEAL:0");
        PotionHelper.PotionData damaged = PotionHelper.fromIdentifier("SPOTION:INSTANT_DAMAGE:2");

        assertNotNull(healed);
        assertEquals(Material.POTION, healed.material());
        assertEquals(PotionType.HEALING, healed.type());

        assertNotNull(damaged);
        assertEquals(Material.SPLASH_POTION, damaged.material());
        assertEquals(PotionType.STRONG_HARMING, damaged.type());
    }

    @Test
    void fromSignTextRejectsAmbiguousAndUnknownLegacyKeys() {
        assertNull(PotionHelper.fromSignText("HEAL", "1"));
        assertNull(PotionHelper.fromSignText("UNKNOWN", "0"));
    }

    @Test
    void fromSignTextRejectsEveryAmbiguousLegacyKey() throws Exception {
        Field field = PotionHelper.class.getDeclaredField("AMBIGUOUS_LEGACY_KEYS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> ambiguous = (Set<String>) field.get(null);

        for (String key : ambiguous) {
            String[] parts = key.split(":");
            assertNull(PotionHelper.fromSignText(parts[0], parts[1]));
        }
    }

    @Test
    void buildCompleteLookupMarksSyntheticDuplicateKeysAsAmbiguous() {
        PotionHelper.LookupTables<String> tables = PotionHelper.buildCompleteLookup(
            Map.of(), java.util.List.of(new String("first"), new String("second")),
            ignored -> "SYNTHETIC",
            ignored -> "0");

        assertTrue(tables.lookup().get("SYNTHETIC").containsKey("0"));
        assertEquals(1, tables.ambiguousKeys().size());
        assertTrue(tables.ambiguousKeys().contains("SYNTHETIC:0"));
    }

    @Test
    void fromSignTextRejectsAnInjectedAmbiguousLegacyKey() throws Exception {
        Field field = PotionHelper.class.getDeclaredField("AMBIGUOUS_LEGACY_KEYS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> original = (Set<String>) field.get(null);
        Set<String> injected = Set.of("XXXXX:0");

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);

        try {
            unsafe.putObject(base, offset, injected);
            assertNull(PotionHelper.fromSignText("XXXXX", "0"));
        } finally {
            unsafe.putObject(base, offset, original);
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

    private static void setPotionAliases(Map<String, String> values) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("potionKeyAliases");
        field.setAccessible(true);
        field.set(null, values);
    }
}
