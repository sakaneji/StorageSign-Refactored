package storagesign.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SpecialCaseItemSupportTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ominousBottleIdentifiersRoundTripAndOtherMaterialsStayOutOfTheSpecialPath() {
        assertTrue(SpecialCaseItemSupport.isSpecialIdentifier("OMINOUS_BOTTLE:3"));
        assertFalse(SpecialCaseItemSupport.isSpecialIdentifier("STONE:3"));
        assertFalse(SpecialCaseItemSupport.isSpecialIdentifier(null));

        assertEquals(Material.OMINOUS_BOTTLE,
            SpecialCaseItemSupport.materialFromIdentifier("OMINOUS_BOTTLE:3"));
        assertNull(SpecialCaseItemSupport.materialFromIdentifier("STONE:3"));
        assertNull(SpecialCaseItemSupport.materialFromIdentifier(null));

        assertEquals((short) 3, SpecialCaseItemSupport.parseDamageFromIdentifier("OMINOUS_BOTTLE:3"));
        assertEquals(0, SpecialCaseItemSupport.parseDamageFromIdentifier("OMINOUS_BOTTLE"));
        assertEquals(0, SpecialCaseItemSupport.parseDamageFromIdentifier("OMINOUS_BOTTLE:"));
        assertEquals(0, SpecialCaseItemSupport.parseDamageFromIdentifier("OMINOUS_BOTTLE:not-a-number"));
        assertEquals(0, SpecialCaseItemSupport.parseDamageFromIdentifier(null));

        assertEquals("OMINOUS_BOTTLE:3",
            SpecialCaseItemSupport.toIdentifier(Material.OMINOUS_BOTTLE, (short) 3));
        assertNull(SpecialCaseItemSupport.toIdentifier(Material.STONE, (short) 3));

        assertEquals(Material.OMINOUS_BOTTLE,
            SpecialCaseItemSupport.materialFromIdentifier("OMINOUS_BOTTLE:3"));
        assertNull(SpecialCaseItemSupport.toContents(Material.STONE, (short) 3, 1));
        assertTrue(SpecialCaseItemSupport.toContents(Material.OMINOUS_BOTTLE, (short) 3, 2) != null);
        assertTrue(Boolean.TRUE.equals(SpecialCaseItemSupport.isSimilar(
            Material.OMINOUS_BOTTLE,
            SpecialCaseItemSupport.toContents(Material.OMINOUS_BOTTLE, (short) 3, 1).getItemMeta(),
            (short) 3)));
        assertNull(SpecialCaseItemSupport.isSimilar(Material.STONE, null, (short) 3));
        assertFalse(Boolean.TRUE.equals(SpecialCaseItemSupport.isSimilar(Material.STONE, null, (short) 3)));
        assertEquals(Short.valueOf((short) 3),
            SpecialCaseItemSupport.fromStoredItem(Material.OMINOUS_BOTTLE,
                SpecialCaseItemSupport.toContents(Material.OMINOUS_BOTTLE, (short) 3, 1).getItemMeta()));
        assertNull(SpecialCaseItemSupport.fromStoredItem(Material.STONE, null));
    }
}
