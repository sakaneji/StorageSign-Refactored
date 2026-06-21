package storagesign.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.StorageSign;

@Tag("integration")
class EnchantHelperTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void specialLegacyShortKeysRemainStable() {
        assertEquals("fire_p", EnchantHelper.toShortKey(Enchantment.FIRE_PROTECTION));
        assertEquals("fire_a", EnchantHelper.toShortKey(Enchantment.FIRE_ASPECT));
        assertEquals(Enchantment.FIRE_PROTECTION, EnchantHelper.fromPrefix("fire_p"));
        assertEquals(Enchantment.FIRE_ASPECT, EnchantHelper.fromPrefix("fire_a"));
    }

    @Test
    void everyRuntimeEnchantmentRoundTripsWithoutShortKeyCollision() {
        Map<String, Enchantment> seen = new HashMap<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            String shortKey = EnchantHelper.toShortKey(enchantment);
            Enchantment previous = seen.putIfAbsent(shortKey, enchantment);
            assertTrue(previous == null || previous.equals(enchantment),
                () -> shortKey + " collides: " + previous + " / " + enchantment);
            assertEquals(enchantment, EnchantHelper.fromPrefix(shortKey), shortKey);
            assertEquals(enchantment,
                EnchantHelper.fromPrefix(enchantment.getKey().getKey()));
        }
    }

    @Test
    void itemMetaIdentifierAndLorePreserveTypeAndLevel() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.SHARPNESS, 5, true);
        book.setItemMeta(meta);

        StorageSign stored = StorageSign.fromStoredItem(book);
        assertNotNull(stored);
        assertEquals("ENCHBOOK:sharp:5", stored.getIdentifier());
        assertEquals("ENCHBOOK:sharp:5 0", stored.getLoreText());

        ItemStack restored = stored.getContents(1);
        EnchantmentStorageMeta restoredMeta =
            (EnchantmentStorageMeta) restored.getItemMeta();
        assertEquals(Map.of(Enchantment.SHARPNESS, 5), restoredMeta.getStoredEnchants());
        assertTrue(stored.isSimilar(book));

        StorageSign parsed = StorageSign.fromSignLines(
            new String[] {"StorageSign", "ENCHBOOK:sharp:5", "12"});
        assertNotNull(parsed);
        assertEquals(Map.of(Enchantment.SHARPNESS, 5),
            ((EnchantmentStorageMeta) parsed.getContents(1).getItemMeta()).getStoredEnchants());
    }

    @Test
    void blankUnknownAndMalformedEnchantmentsAreRejected() {
        assertNull(EnchantHelper.fromPrefix(null));
        assertNull(EnchantHelper.fromPrefix(" "));
        assertNull(EnchantHelper.fromPrefix("definitely_missing"));
        assertNull(StorageSign.fromSignLines(
            new String[] {"StorageSign", "ENCHBOOK:definitely_missing:3", "1"}));
        assertNull(StorageSign.fromSignLines(
            new String[] {"StorageSign", "ENCHBOOK:sharp:not-a-level", "1"}));
    }
}
