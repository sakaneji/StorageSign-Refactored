package storagesign;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import storagesign.registry.MaterialRegistry;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class StorageSignPluginIntegrationTest {

    private ServerMock server;
    private StorageSignPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginLoadsAndRegistersPublicCommand() {
        assertTrue(plugin.isEnabled());
        assertNotNull(server.getPluginCommand("storagesigngive"));
        assertNotNull(plugin.getConfig().getDefaults());
    }

    @Test
    void pluginBuildsOminousBannerMetadataOnEnable() {
        assertNotNull(StorageSignPlugin.getOminousBannerMeta());
        assertTrue(StorageSignPlugin.isOminousBannerMeta(
            StorageSignPlugin.getOminousBannerMeta()
        ));
    }

    @Test
    void potionSignStoresCanonicalPdcWhileKeepingReadableLegacyLine() {
        var world = server.addSimpleWorld("potion-pdc");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        StorageSign stored = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "SPOTION:REGEN:1", "19"});

        stored.applyToSign(sign);

        Sign persisted = (Sign) block.getState();
        assertEquals("SPOTION:REGEN:1", persisted.getSide(org.bukkit.block.sign.Side.FRONT).getLine(1));
        assertEquals("SPOTION:minecraft:long_regeneration",
            persisted.getPersistentDataContainer().get(
                new NamespacedKey("storagesign", "potion_identifier"), PersistentDataType.STRING));
        StorageSign restored = StorageSign.fromSign(persisted);
        assertEquals(Material.SPLASH_POTION, restored.getMaterial());
        assertEquals(PotionType.LONG_REGENERATION, restored.getPotionType());
        assertEquals(19, restored.getAmount());
    }

    @Test
    void registersRecipeForEverySignMaterial() {
        for (Material sign : MaterialRegistry.SIGN_MATERIALS) {
            var recipe = server.getRecipe(new NamespacedKey(plugin,
                "storagesign_" + sign.name().toLowerCase()));
            assertTrue(recipe instanceof ShapedRecipe, "Missing recipe for " + sign);
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            assertEquals(sign, shaped.getIngredientMap().get('S').getType());
            assertEquals(Material.CHEST, shaped.getIngredientMap().get('H').getType());
        }
    }

    @Test
    void hardRecipeUsesEnderChest() throws Exception {
        Field hard = ConfigLoader.class.getDeclaredField("hardrecipe");
        hard.setAccessible(true);
        hard.setBoolean(null, true);
        Method register = StorageSignPlugin.class.getDeclaredMethod("registerRecipes");
        register.setAccessible(true);
        register.invoke(plugin);

        ShapedRecipe recipe = (ShapedRecipe) server.getRecipe(
            new NamespacedKey(plugin, "storagesign_oak_sign"));
        assertEquals(Material.ENDER_CHEST, recipe.getIngredientMap().get('H').getType());
    }

    @Test
    void reenableReloadsConfigurationAndOminousBannerState() throws Exception {
        Field manualImport = ConfigLoader.class.getDeclaredField("manualImport");
        manualImport.setAccessible(true);
        manualImport.setBoolean(null, false);
        plugin.onDisable();
        StorageSignPlugin.setOminousBannerMeta(null);
        plugin.onEnable();

        assertTrue(ConfigLoader.getManualImport());
        assertNotNull(StorageSignPlugin.getOminousBannerMeta());
    }
}
