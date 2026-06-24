package storagesign;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Unit tests for {@link StorageSign} parsing, serialisation, and mutation logic.
 *
 * <p>Tests use only Bukkit enum constants (Material, PotionType) which are available
 * without a running Bukkit server.  Tests that require ItemStack or Block APIs
 * (getContents, isSimilar, fromItemStack) are not covered here.
 */
class StorageSignTest {

    // ── fromSignLines: null / malformed input ────────────────────────────────

    @Test
    void fromSignLines_nullInputReturnsNull() {
        assertNull(StorageSign.fromSignLines(null));
    }

    @Test
    void fromSignLines_emptyArrayReturnsNull() {
        assertNull(StorageSign.fromSignLines(new String[]{}));
    }

    @Test
    void fromSignLines_oneLineReturnsNull() {
        assertNull(StorageSign.fromSignLines(new String[]{"StorageSign"}));
    }

    @Test
    void fromSignLines_twoLinesReturnsNull() {
        assertNull(StorageSign.fromSignLines(new String[]{"StorageSign", "STONE"}));
    }

    @Test
    void fromSignLines_wrongHeaderReturnsNull() {
        assertNull(StorageSign.fromSignLines(
            new String[]{"NotStorageSign", "STONE", "100", "0LC 1s 36"}));
    }

    @Test
    void fromSignLines_emptyHeaderReturnsNull() {
        assertNull(StorageSign.fromSignLines(
            new String[]{"", "STONE", "100", "0LC 1s 36"}));
    }

    @Test
    void fromSignLines_headerCaseSensitive() {
        assertNull(StorageSign.fromSignLines(
            new String[]{"storagesign", "STONE", "100", "0LC 1s 36"}));
    }

    // ── fromSignLines: empty sign markers ────────────────────────────────────

    @Test
    void fromSignLines_blankIdentifierReturnsEmptySign() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "", "0", "0LC 0s 0"});
        assertNotNull(ss);
        assertTrue(ss.isUnregistered());
        assertEquals(0, ss.getAmount());
    }

    @Test
    void fromSignLines_whitespaceIdentifierReturnsEmptySign() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "   ", "0", "0LC 0s 0"});
        assertNotNull(ss);
        assertTrue(ss.isUnregistered());
    }

    @Test
    void fromSignLines_emptyMarkerReturnsEmptySign() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "Empty", "0", "0LC 0s 0"});
        assertNotNull(ss);
        assertTrue(ss.isUnregistered());
    }

    // ── fromSignLines: normal items ───────────────────────────────────────────

    @Test
    void fromSignLines_parsesNormalItem() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        assertFalse(ss.isUnregistered());
        assertEquals(Material.STONE, ss.getMaterial());
        assertEquals(0, ss.getDamage());
        assertEquals(100, ss.getAmount());
    }

    @Test
    void fromSignLines_parsesItemWithDamageSubtype() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE:5", "50", "0LC 0s 50"});
        assertNotNull(ss);
        assertEquals(Material.STONE, ss.getMaterial());
        assertEquals(5, ss.getDamage());
        assertEquals(50, ss.getAmount());
    }


    @Test
    void fromSignLines_nonNumericAmountIsRejected() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "not_a_number", "..."});
        assertNull(ss);
    }

    @Test
    void fromSignLines_blankAmountLineIsRejected() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "", "..."});
        assertNull(ss);
    }

    @Test
    void fromSignLines_negativeAndOverflowAmountsAreRejected() {
        assertNull(StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "-1"}));
        assertNull(StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "2147483648"}));
    }

    @Test
    void fromSignLines_unknownMaterialReturnsNull() {
        assertNull(StorageSign.fromSignLines(
            new String[]{"StorageSign", "TOTALLY_FAKE_ITEM_XYZABC", "100", "..."}));
    }

    @Test
    void fromSignLines_threeLinesAreEnough() {
        // Lines[3] (summary) is display-only; only 3 lines are required for parsing
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "64"});
        assertNotNull(ss);
        assertEquals(64, ss.getAmount());
    }

    // ── fromSignLines: legacy material names ─────────────────────────────────

    @Test
    void fromSignLines_legacySignNameMapsToOakSign() {
        // "SIGN" was the pre-1.13 material name
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "SIGN", "10", "..."});
        assertNotNull(ss);
        assertEquals(Material.OAK_SIGN, ss.getMaterial());
    }

    @Test
    void fromSignLines_legacyRoseRedMapsToRedDye() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "ROSE_RED", "32", "..."});
        assertNotNull(ss);
        assertEquals(Material.RED_DYE, ss.getMaterial());
    }

    @Test
    void fromSignLines_legacyStoneSlab() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE_SLAB", "64", "..."});
        assertNotNull(ss);
        assertEquals(Material.SMOOTH_STONE_SLAB, ss.getMaterial());
    }

    // ── fromSignLines: potion items ───────────────────────────────────────────

    @Test
    void fromSignLines_parsesNormalPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "POTION:HEAL:0", "5", "0LC 0s 5"});
        assertNotNull(ss);
        assertEquals(Material.POTION, ss.getMaterial());
        assertEquals(PotionType.HEALING, ss.getPotionType());
        assertEquals(5, ss.getAmount());
    }

    @Test
    void fromSignLines_parsesSplashPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "SPOTION:REGEN:1", "3", "0LC 0s 3"});
        assertNotNull(ss);
        assertEquals(Material.SPLASH_POTION, ss.getMaterial());
        assertEquals(PotionType.LONG_REGENERATION, ss.getPotionType());
    }

    @Test
    void fromSignLines_parsesLingeringPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "LPOTION:SPEED:2", "2", "0LC 0s 2"});
        assertNotNull(ss);
        assertEquals(Material.LINGERING_POTION, ss.getMaterial());
        assertEquals(PotionType.STRONG_SWIFTNESS, ss.getPotionType());
    }

    @Test
    void fromSignLines_parsesGenericLongPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "POTION:NIGHT:1", "1", "0LC 0s 1"});
        assertNotNull(ss);
        assertEquals(PotionType.LONG_NIGHT_VISION, ss.getPotionType());
    }

    // ── fromSignLines: ominous bottle ─────────────────────────────────────────

    @Test
    void fromSignLines_parsesOminousBottle() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OMINOUS_BOTTLE:3", "10", "0LC 0s 10"});
        assertNotNull(ss);
        assertEquals(Material.OMINOUS_BOTTLE, ss.getMaterial());
        assertEquals(3, ss.getDamage());
        assertEquals(10, ss.getAmount());
    }

    @Test
    void fromSignLines_parsesOminousBottleAmplifierZero() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OMINOUS_BOTTLE:0", "1", "0LC 0s 1"});
        assertNotNull(ss);
        assertEquals(Material.OMINOUS_BOTTLE, ss.getMaterial());
        assertEquals(0, ss.getDamage());
    }

    @Test
    void fromSignLines_parsesOminousBottleMaxAmplifier() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OMINOUS_BOTTLE:4", "1", "0LC 0s 1"});
        assertNotNull(ss);
        assertEquals(4, ss.getDamage());
    }

    // ── fromSignLines: legacy identifiers ────────────────────────────────────

    @Test
    void fromSignLines_parsesHorseEggLegacy() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "HorseEgg", "5", "0LC 0s 5"});
        assertNotNull(ss);
        assertEquals(Material.END_PORTAL, ss.getMaterial());
        assertEquals(1, ss.getDamage()); // DAMAGE_SS_ITEM
    }

    @Test
    void fromSignLines_parsesEmptySignLegacy() {
        // Pre-1.13 "EmptySign" identifier → OAK_SIGN stored-as-item
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "EmptySign", "3", "0LC 0s 3"});
        assertNotNull(ss);
        assertEquals(Material.OAK_SIGN, ss.getMaterial());
        assertEquals(1, ss.getDamage()); // DAMAGE_SS_ITEM
    }

    @Test
    void fromSignLines_parsesOakSignAsItem() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OakStorageSign", "16", "0LC 0s 16"});
        assertNotNull(ss);
        assertEquals(Material.OAK_SIGN, ss.getMaterial());
        assertEquals(1, ss.getDamage()); // DAMAGE_SS_ITEM
        assertEquals(16, ss.getAmount());
        assertTrue(ss.isSignAsItem());
    }

    @Test
    void fromSignLines_parsesDarkOakSignAsItem() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "DarkOakStorageSign", "8", "0LC 0s 8"});
        assertNotNull(ss);
        assertEquals(Material.DARK_OAK_SIGN, ss.getMaterial());
        assertEquals(1, ss.getDamage());
        assertTrue(ss.isSignAsItem());
    }

    @Test
    void fromSignLines_parsesSpruceSignAsItem() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "SpruceStorageSign", "4", "0LC 0s 4"});
        assertNotNull(ss);
        assertEquals(Material.SPRUCE_SIGN, ss.getMaterial());
        assertTrue(ss.isSignAsItem());
    }

    // ── getIdentifier round-trips ─────────────────────────────────────────────

    @Test
    void getIdentifier_emptySignReturnsEmptyMarker() {
        assertEquals(StorageSign.EMPTY_MARKER, StorageSign.empty().getIdentifier());
    }

    @Test
    void getIdentifier_roundTripNormalItem() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        assertEquals("STONE", ss.getIdentifier());
    }

    @Test
    void getIdentifier_roundTripItemWithDamage() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE:5", "50", "0LC 0s 50"});
        assertNotNull(ss);
        assertEquals("STONE:5", ss.getIdentifier());
    }


    @Test
    void getIdentifier_roundTripPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "POTION:HEAL:0", "5", "0LC 0s 5"});
        assertNotNull(ss);
        assertEquals("POTION:HEAL:0", ss.getIdentifier());
    }

    @Test
    void getIdentifier_roundTripSplashPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "SPOTION:REGEN:1", "3", "0LC 0s 3"});
        assertNotNull(ss);
        assertEquals("SPOTION:REGEN:1", ss.getIdentifier());
    }

    @Test
    void getIdentifier_roundTripLingeringPotion() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "LPOTION:SPEED:2", "2", "0LC 0s 2"});
        assertNotNull(ss);
        assertEquals("LPOTION:SPEED:2", ss.getIdentifier());
    }

    @Test
    void getIdentifier_roundTripSignAsItem() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OakStorageSign", "16", "0LC 0s 16"});
        assertNotNull(ss);
        assertEquals("OakStorageSign", ss.getIdentifier());
    }

    @Test
    void getIdentifier_roundTripOminousBottle() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OMINOUS_BOTTLE:3", "10", "0LC 0s 10"});
        assertNotNull(ss);
        assertEquals("OMINOUS_BOTTLE:3", ss.getIdentifier());
    }

    @Test
    void parseEnchantedBookIdentifierExposesStoredEnchantment() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "ENCHBOOK:sharp:5", "2", "0LC 0s 2"});
        assertNotNull(ss);
        assertEquals(org.bukkit.Material.ENCHANTED_BOOK, ss.getMaterial());
        assertNotNull(ss.getEnchantment());
        assertTrue(ss.toString().contains("ENCHANTED_BOOK"));
    }

    @Test
    void toStringIncludesIdentifierAndAmount() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "9", "0LC 0s 9"});
        assertNotNull(ss);
        String text = ss.toString();
        assertTrue(text.contains("material=STONE"));
        assertTrue(text.contains("amount=9"));
        assertTrue(text.contains("identifier=STONE"));
    }

    @Test
    void fromBlockRejectsNullAndNonSignBlocks() {
        assertNull(StorageSign.fromBlock(null));
        org.bukkit.block.Block block = org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        assertNull(StorageSign.fromBlock(block));
    }

    @Test
    void isStorageSignRecognizesStoredSignItemsOnly() {
        MockBukkit.mock();
        try {
            ItemStack sign = StorageSign.createStorageSignItem(Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1);
            assertTrue(StorageSign.isStorageSign(sign));
            assertFalse(StorageSign.isStorageSign((ItemStack) new ItemStack(Material.OAK_SIGN)));
            assertFalse(StorageSign.isStorageSign((ItemStack) null));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void isSignAsItemRejectsNonSignItems() {
        StorageSign stone = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "1", "0LC 0s 1"});
        assertNotNull(stone);
        assertFalse(stone.isSignAsItem());
    }

    // ── static empty() ────────────────────────────────────────────────────────

    @Test
    void empty_producesEmptySign() {
        StorageSign ss = StorageSign.empty();
        assertTrue(ss.isUnregistered());
        assertEquals(0, ss.getAmount());
        assertEquals(StorageSign.EMPTY_MARKER, ss.getIdentifier());
    }

    // ── setAmount behavior ────────────────────────────────────────────────────

    @Test
    void setAmount_updatesPositiveValue() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        ss.setAmount(200);
        assertEquals(200, ss.getAmount());
        assertFalse(ss.isUnregistered());
    }

    @Test
    void setAmount_zeroKeepsRegistrationWhenConfigured() throws Exception {
        setUnregisterOnEmpty(false);
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        ss.setAmount(0);
        assertEquals(0, ss.getAmount());
        assertFalse(ss.isUnregistered());
    }

    @Test
    void setAmount_negativeClampsToZeroAndKeepsRegistrationWhenConfigured() throws Exception {
        setUnregisterOnEmpty(false);
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        ss.setAmount(-10);
        assertEquals(0, ss.getAmount());
        assertFalse(ss.isUnregistered());
    }

    @Test
    void setAmount_oneRemainsNonEmpty() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        ss.setAmount(1);
        assertEquals(1, ss.getAmount());
        assertFalse(ss.isUnregistered());
    }

    @Test
    void setAmount_zeroUnregistersWhenConfigured() throws Exception {
        setUnregisterOnEmpty(true);
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);

        ss.setAmount(0);

        assertEquals(0, ss.getAmount());
        assertTrue(ss.isUnregistered());
        setUnregisterOnEmpty(false);
    }

    private static void setUnregisterOnEmpty(boolean value) throws Exception {
        var field = ConfigLoader.class.getDeclaredField("unregisterOnEmpty");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    // ── getSignLines format ───────────────────────────────────────────────────

    @Test
    void getSignLines_headerIsAlwaysFirstLine() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "0LC 1s 36"});
        assertNotNull(ss);
        assertEquals(StorageSign.HEADER_LINE, ss.getSignLines()[0]);
    }

    @Test
    void getSignLines_emptySignHasBlankIdentifierLine() {
        String[] lines = StorageSign.empty().getSignLines();
        assertEquals(4, lines.length);
        assertEquals(StorageSign.HEADER_LINE, lines[0]);
        assertEquals("", lines[1]); // blank, NOT "Empty"
        assertEquals("0", lines[2]);
        assertEquals("0LC 0s 0", lines[3]);
    }

    @Test
    void getSignLines_summaryFor100Items() {
        // 100 = 0*3456 + 1*64 + 36
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "..."});
        assertNotNull(ss);
        String[] lines = ss.getSignLines();
        assertEquals("STONE",    lines[1]);
        assertEquals("100",      lines[2]);
        assertEquals("0LC 1s 36", lines[3]);
    }

    @Test
    void getSignLines_summaryForExactlyOneLC() {
        // 3456 = 1*3456 + 0*64 + 0
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "3456", "..."});
        assertNotNull(ss);
        assertEquals("1LC 0s 0", ss.getSignLines()[3]);
    }

    @Test
    void getSignLines_summaryForMixedLCStacksSingles() {
        // 3521 = 1*3456 + 65 = 1*3456 + 1*64 + 1
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "3521", "..."});
        assertNotNull(ss);
        assertEquals("1LC 1s 1", ss.getSignLines()[3]);
    }

    @Test
    void getSignLines_summaryForMultipleLC() {
        // 7050 = 2*3456 + 138 = 2*3456 + 2*64 + 10
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "7050", "..."});
        assertNotNull(ss);
        assertEquals("2LC 2s 10", ss.getSignLines()[3]);
    }

    @Test
    void getSignLines_summaryForSingleItem() {
        // 1 = 0*3456 + 0*64 + 1
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "1", "..."});
        assertNotNull(ss);
        assertEquals("0LC 0s 1", ss.getSignLines()[3]);
    }

    // ── getLoreText ───────────────────────────────────────────────────────────

    @Test
    void getLoreText_emptySignReturnsEmptyMarker() {
        assertEquals(StorageSign.EMPTY_MARKER, StorageSign.empty().getLoreText());
    }

    @Test
    void getLoreText_normalItemReturnsIdentifierAndAmount() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "STONE", "100", "..."});
        assertNotNull(ss);
        assertEquals("STONE 100", ss.getLoreText());
    }

    @Test
    void getLoreText_potionReturnsIdentifierAndAmount() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "POTION:HEAL:0", "5", "..."});
        assertNotNull(ss);
        assertEquals("POTION:HEAL:0 5", ss.getLoreText());
    }

    @Test
    void getLoreText_ominousBottleReturnsIdentifierAndAmount() {
        StorageSign ss = StorageSign.fromSignLines(
            new String[]{"StorageSign", "OMINOUS_BOTTLE:2", "10", "..."});
        assertNotNull(ss);
        assertEquals("OMINOUS_BOTTLE:2 10", ss.getLoreText());
    }
}
