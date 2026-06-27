package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import storagesign.compat.SignDisplayFormatter;

class SignDisplayFormatterTest {
    @AfterEach
    void clearVirtualIdentifiers() throws Exception {
        setVirtualIdentifiers(Map.of());
    }

    @Test
    void nullAndEmptyIdentifiersCollapseToBlank() {
        assertEquals("", SignDisplayFormatter.fit(null));
        assertEquals("", SignDisplayFormatter.fit(""));
    }

    @Test
    void everyRuntimeMaterialIdentifierFitsPhysicalSignWidth() {
        for (Material material : Material.values()) {
            StorageSign sign = StorageSign.fromSignLines(
                new String[] {"StorageSign", material.name(), "1"});
            assertNotNull(sign, material.name());
            String display = sign.getDisplayIdentifier();
            assertTrue(SignDisplayFormatter.width(display) <= 90,
                () -> material + " => " + display + " width=" + SignDisplayFormatter.width(display));
        }
    }

    @Test
    void shortIdentifiersRemainUnchangedAndLongConfiguredIdentifiersStayCanonical() throws Exception {
        assertEquals("STONE", SignDisplayFormatter.fit("STONE"));
        setVirtualIdentifiers(Map.of(
            "Very_Long_Configured_Compatibility_Identifier", "DIAMOND:7"));
        StorageSign sign = StorageSign.fromSignLines(new String[] {
            "StorageSign", "Very_Long_Configured_Compatibility_Identifier", "3"});
        assertNotNull(sign);
        assertEquals("Very_Long_Configured_Compatibility_Identifier", sign.getIdentifier());
        assertTrue(SignDisplayFormatter.width(sign.getDisplayIdentifier()) <= 90);
    }

    @Test
    void hangingSignsUseATighterWidthLimitThanStandingSigns() throws Exception {
        assertEquals("activator_", SignDisplayFormatter.fit("activator_", Material.OAK_HANGING_SIGN));
        assertEquals("a:rail", SignDisplayFormatter.fit("activator_rail", Material.OAK_HANGING_SIGN));
        assertEquals("activator_rail",
            SignDisplayFormatter.fit("activator_rail", Material.OAK_SIGN));
    }

    @Test
    void longUnderscoredIdentifiersCompressBeforeFallbackTruncation() {
        assertEquals(
            "VLN:IDENTIFIER:7",
            SignDisplayFormatter.fit("VERY_LONG_NAME_IDENTIFIER:7"));
    }

    @Test
    void overlongUnderscoredIdentifiersFallBackToEllipsisAfterCompressionFails() {
        String fitted = SignDisplayFormatter.fit(
            "VERY_LONG_NAME_WITH_MANY_PARTS_AND_EXTRA_DETAILS_IDENTIFIER:7");
        assertTrue(fitted.endsWith("..."), fitted);
        assertTrue(SignDisplayFormatter.width(fitted) <= 90, fitted);
    }

    @Test
    void emptyUnderscoreSegmentsAreSkippedDuringCompression() {
        assertEquals("VLN:IDENTIFIER:7", SignDisplayFormatter.fit("VERY__LONG__NAME_IDENTIFIER:7"));
    }

    @Test
    void underscoredIdentifiersWithoutSuffixCompressWithoutAddingExtraSuffixText() {
        assertEquals("VLNI:EXTRA", SignDisplayFormatter.fit("VERY_LONG_NAME_IDENTIFIER_EXTRA"));
    }

    @Test
    void leadingAndTrailingEmptyUnderscoreSegmentsAreIgnored() {
        String fitted = SignDisplayFormatter.fit("___VERY_LONG_NAME___");
        assertEquals("VL:NAME", fitted);
        assertTrue(SignDisplayFormatter.width(fitted) <= 90, fitted);
    }

    @Test
    void overlongUnderscoredIdentifiersWithoutSuffixStillFallBackToEllipsis() {
        String fitted = SignDisplayFormatter.fit(String.join("_", Collections.nCopies(20, "LONGWORD")));
        assertTrue(fitted.endsWith("..."), fitted);
        assertTrue(SignDisplayFormatter.width(fitted) <= 90, fitted);
    }

    @Test
    void overlongIdentifiersWithSuffixButNoUnderscoresStillFallBackToEllipsis() {
        String fitted = SignDisplayFormatter.fit("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:7");
        assertTrue(fitted.endsWith("..."), fitted);
        assertTrue(SignDisplayFormatter.width(fitted) <= 90, fitted);
    }

    @Test
    void compactedIdentifiersRespectWidthBoundaryBeforeTruncation() {
        String fitted = SignDisplayFormatter.fit("A_LONG_IDENTIFIER_WITH_SUFFIX:123");
        assertTrue(SignDisplayFormatter.width(fitted) <= 90, fitted);
    }

    @Test
    void widthAccountsForMixedPunctuationAndThinGlyphs() {
        int width = SignDisplayFormatter.width("I:l,.");
        assertEquals(14, width);
    }

    @Test
    void widthCountsLowercaseThinGlyphsIndividually() {
        assertEquals(12, SignDisplayFormatter.width("i1l"));
    }

    @Test
    void widthCountsExclamationMarkAsPunctuation() {
        assertEquals(2, SignDisplayFormatter.width("!"));
    }

    @Test
    void widthCountsPunctuationAndThinCharactersDifferently() {
        assertEquals(2, SignDisplayFormatter.width(":"));
        assertEquals(4, SignDisplayFormatter.width("I"));
        assertEquals(6, SignDisplayFormatter.width("A"));
        assertTrue(SignDisplayFormatter.width("I:") < SignDisplayFormatter.width("AA"));
    }

    @Test
    void longFlatIdentifiersFallBackToEllipsis() {
        String value = "ABCDEFGHIJKLMNOPQRSTUVWXYABCDEFGHIJKLMNOPQRSTUVWXY";
        String fitted = SignDisplayFormatter.fit(value);
        assertTrue(fitted.endsWith("..."), fitted);
        assertTrue(SignDisplayFormatter.width(fitted) <= 90, fitted);
    }

    @Test
    void truncateOnEmptyIdentifierProducesJustEllipsis() {
        assertEquals("...", SignDisplayFormatter.truncate(""));
    }

    @Test
    void maximumQuantityLinesFitPhysicalSignWidth() {
        StorageSign sign = StorageSign.fromSignLines(new String[] {
            "StorageSign", "STONE", Integer.toString(Integer.MAX_VALUE)});
        assertNotNull(sign);
        String[] lines = sign.getSignLines();
        assertTrue(SignDisplayFormatter.width(lines[2]) <= 90, lines[2]);
        assertTrue(SignDisplayFormatter.width(lines[3]) <= 90, lines[3]);
    }

    private static void setVirtualIdentifiers(Map<String, String> values) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        field.setAccessible(true);
        field.set(null, values);
    }
}
