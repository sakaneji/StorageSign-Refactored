package storagesign.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

@Tag("integration")
class OminousBottleHelperTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everySupportedAmplifierRoundTripsThroughItemMeta() {
        for (short amplifier = 0; amplifier <= 4; amplifier++) {
            ItemStack item = OminousBottleHelper.toItemStack(amplifier, 1);
            assertEquals(Material.OMINOUS_BOTTLE, item.getType());
            assertEquals(amplifier, OminousBottleHelper.getAmplifier(item.getItemMeta()));
            assertTrue(OminousBottleHelper.isSimilar(item.getItemMeta(), amplifier));
            assertEquals("OMINOUS_BOTTLE:" + amplifier,
                OminousBottleHelper.toSignText(amplifier));
            assertEquals("OMINOUS_BOTTLE:" + amplifier + " 17",
                OminousBottleHelper.toLoreText(amplifier, 17));
        }
    }

    @Test
    void absentAmplifierMeansVanillaLevelZero() {
        OminousBottleMeta meta = (OminousBottleMeta)
            new ItemStack(Material.OMINOUS_BOTTLE).getItemMeta();

        assertEquals(0, OminousBottleHelper.getAmplifier(meta));
        assertTrue(OminousBottleHelper.isSimilar(meta, (short) 0));
        assertFalse(OminousBottleHelper.isSimilar(meta, (short) 1));
        assertEquals(0, OminousBottleHelper.getAmplifier((ItemMeta) null));
        assertFalse(OminousBottleHelper.isSimilar(null, (short) 0));
    }

    @Test
    void requestedAmountIsClampedWithoutCreatingInvalidStacks() {
        ItemStack zero = OminousBottleHelper.toItemStack((short) 2, 0);
        ItemStack maximum = OminousBottleHelper.toItemStack((short) 2, Integer.MAX_VALUE);

        assertEquals(1, zero.getAmount());
        assertEquals(maximum.getMaxStackSize(), maximum.getAmount());
        assertEquals(2, OminousBottleHelper.getAmplifier(maximum.getItemMeta()));
    }
}
