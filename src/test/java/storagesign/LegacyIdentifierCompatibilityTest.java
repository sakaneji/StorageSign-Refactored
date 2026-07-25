package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.compat.LegacyStorageSignCompatibility;
import storagesign.item.PotionHelper;

@Tag("integration")
class LegacyIdentifierCompatibilityTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareSignKeepsLegacyEmptyStorageSignItemMeaning() {
        StorageSign parsed = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "SIGN", "4"
        });

        assertNotNull(parsed);
        assertEquals(Material.OAK_SIGN, parsed.getMaterial());
        assertEquals(1, parsed.getDamage());
        ItemStack contents = parsed.getContents(1);
        assertTrue(StorageSign.isStorageSign(contents));
        assertTrue(StorageSign.fromItemStack(contents).isUnregistered());
    }

    @Test
    void explicitSignDamageStillOverridesLegacyDefault() {
        StorageSign parsed = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "SIGN:0", "4"
        });

        assertNotNull(parsed);
        assertEquals(Material.OAK_SIGN, parsed.getMaterial());
        assertEquals(0, parsed.getDamage());
        assertFalse(StorageSign.isStorageSign(parsed.getContents(1)));
    }

    @Test
    void stoneSlabKeepsLegacyBareAndExplicitMeaningsSeparate() {
        StorageSign bare = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "STONE_SLAB", "2"
        });
        StorageSign explicit = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "STONE_SLAB:1", "2"
        });

        assertNotNull(bare);
        assertNotNull(explicit);
        assertEquals(Material.SMOOTH_STONE_SLAB, bare.getMaterial());
        assertEquals(Material.STONE_SLAB, explicit.getMaterial());
        assertEquals(1, explicit.getDamage());
        assertEquals(Material.STONE_SLAB, explicit.getContents(1).getType());
    }

    @Test
    void parsesLegacyPotionItemLoreMaterialPrefixes() {
        PotionHelper.PotionData splash =
            PotionHelper.fromIdentifier("SPLASH_POTION:REGEN:1");
        PotionHelper.PotionData lingering =
            PotionHelper.fromIdentifier("LINGERING_POTION:INSTANT_HEAL:2");

        assertNotNull(splash);
        assertNotNull(lingering);
        assertEquals(Material.SPLASH_POTION, splash.material());
        assertEquals(PotionType.LONG_REGENERATION, splash.type());
        assertEquals(Material.LINGERING_POTION, lingering.material());
        assertEquals(PotionType.STRONG_HEALING, lingering.type());
    }

    @Test
    void parsesLegacyFullPotionTypeNamesFromItemLore() {
        PotionHelper.PotionData normal =
            PotionHelper.fromIdentifier("POTION:NIGHT_VISION:0");
        PotionHelper.PotionData extended =
            PotionHelper.fromIdentifier("POTION:POISON:1");

        assertNotNull(normal);
        assertNotNull(extended);
        assertEquals(PotionType.NIGHT_VISION, normal.type());
        assertEquals(PotionType.LONG_POISON, extended.type());
    }

    @Test
    void parsesPdcLessLegacyPotionStorageSignItemsThroughPublicItemPath() {
        ItemStack splashItem = StorageSign.createStorageSignItem(
            Material.OAK_SIGN, "SPLASH_POTION:REGEN:1 4", 1);
        ItemStack normalItem = StorageSign.createStorageSignItem(
            Material.OAK_SIGN, "POTION:NIGHT_VISION:0 6", 1);

        StorageSign splash = StorageSign.fromItemStack(splashItem);
        StorageSign normal = StorageSign.fromItemStack(normalItem);
        assertNotNull(splash);
        assertNotNull(normal);
        assertEquals(Material.SPLASH_POTION, splash.getMaterial());
        assertEquals(PotionType.LONG_REGENERATION, splash.getPotionType());
        assertEquals(4, splash.getAmount());
        assertEquals(Material.POTION, normal.getMaterial());
        assertEquals(PotionType.NIGHT_VISION, normal.getPotionType());
        assertEquals(6, normal.getAmount());
    }

    @Test
    void legacyRecipeKeysOnlyCoverOriginalStandingSigns() {
        assertEquals("storagesign:ssroak_sign",
            LegacyStorageSignCompatibility.legacyRecipeKey(Material.OAK_SIGN).toString());
        assertNull(LegacyStorageSignCompatibility.legacyRecipeKey(Material.OAK_HANGING_SIGN));
    }
}
