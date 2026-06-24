package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfiguredIdentifierTest {

    @AfterEach
    void clearMaps() throws Exception {
        set("identifierAliases", Map.of());
        set("virtualItemIdentifiers", Map.of());
        set("potionKeyAliases", Map.of());
    }

    @Test
    void configuredAliasMigratesRemovedIdentifierWithoutCodeChange() throws Exception {
        set("identifierAliases", Map.of("OLD_CUSTOM_STONE", "STONE"));
        StorageSign sign = StorageSign.fromSignLines(
            new String[] {"StorageSign", "OLD_CUSTOM_STONE", "12"});
        assertNotNull(sign);
        assertEquals(Material.STONE, sign.getMaterial());
        assertEquals(12, sign.getAmount());
    }

    @Test
    void defaultAliasStillMigratesLegacySignName()
            throws Exception {
        StorageSign legacy = StorageSign.fromSignLines(
            new String[] {"StorageSign", "SIGN", "12"});
        assertNotNull(legacy);
        assertEquals(Material.OAK_SIGN, legacy.getMaterial());
    }

    @Test
    void defaultVirtualIdentifierStillResolvesLegacyMarker() {
        StorageSign marker = StorageSign.fromSignLines(
            new String[] {"StorageSign", "HorseEgg", "1"});
        assertNotNull(marker);
        assertEquals(Material.END_PORTAL, marker.getMaterial());
        assertEquals(1, marker.getDamage());
    }

    @Test
    void configuredVirtualIdentifierUsesBackingMaterialAndDamage() throws Exception {
        set("virtualItemIdentifiers", Map.of("CustomMarker", "DIAMOND:7"));
        StorageSign sign = StorageSign.fromSignLines(
            new String[] {"StorageSign", "CustomMarker", "3"});
        assertNotNull(sign);
        assertEquals(Material.DIAMOND, sign.getMaterial());
        assertEquals(7, sign.getDamage());
        assertEquals("CustomMarker", sign.getIdentifier());
    }

    @Test
    void malformedVirtualIdentifierSpecFallsBackToNull() throws Exception {
        set("virtualItemIdentifiers", Map.of(
            "BrokenMarker", "NOT_A_MATERIAL:7",
            "BlankMarker", " "));
        assertNull(StorageSign.fromSignLines(
            new String[] {"StorageSign", "BrokenMarker", "3"}));
        assertNull(StorageSign.fromSignLines(
            new String[] {"StorageSign", "BlankMarker", "3"}));
    }

    @Test
    void configuredPotionKeyAliasMigratesRemovedRegistryKey() throws Exception {
        set("potionKeyAliases", Map.of("example:removed_healing", "minecraft:healing"));
        StorageSign sign = StorageSign.fromSignLines(
            new String[] {"StorageSign", "POTION:example:removed_healing", "3"});
        assertNotNull(sign);
        assertEquals(Material.POTION, sign.getMaterial());
        assertEquals(PotionType.HEALING, sign.getPotionType());
    }

    @Test
    void existingPotionKeyWinsAndAliasChainsAreRejected() throws Exception {
        set("potionKeyAliases", Map.of(
            "minecraft:healing", "minecraft:harming",
            "example:first", "example:second",
            "example:second", "minecraft:healing"));

        StorageSign existing = StorageSign.fromSignLines(
            new String[] {"StorageSign", "POTION:minecraft:healing", "1"});
        assertNotNull(existing);
        assertEquals(PotionType.HEALING, existing.getPotionType());
        assertNull(StorageSign.fromSignLines(
            new String[] {"StorageSign", "POTION:example:first", "1"}));
    }

    @Test
    void virtualSpecMatchingHandlesBlankAndMismatch() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "matchesVirtualSpec", Material.class, short.class, String.class);
        method.setAccessible(true);

        assertEquals(false, method.invoke(null, Material.END_PORTAL, (short) 1, " "));
        assertEquals(false, method.invoke(null, Material.END_PORTAL, (short) 1, "STONE:1"));
        assertEquals(true, method.invoke(null, Material.END_PORTAL, (short) 1, "END_PORTAL:1"));
    }

    @Test
    void resolveVirtualIdentifierFallsBackToDefaultHorseEggMarker() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveVirtualIdentifier", Material.class, short.class);
        method.setAccessible(true);

        assertEquals("HorseEgg", method.invoke(null, Material.END_PORTAL, (short) 1));
        assertNull(method.invoke(null, Material.STONE, (short) 0));
    }

    @Test
    void parseVirtualIdentifierHandlesBlankConfiguredAndDefaultSpecs() throws Exception {
        set("virtualItemIdentifiers", Map.of("CustomMarker", "DIAMOND:7"));
        Method method = StorageSign.class.getDeclaredMethod(
            "parseVirtualIdentifier", String.class, int.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, "", 1));
        assertNull(method.invoke(null, "BlankMarker", 1));
        assertNotNull(method.invoke(null, "CustomMarker", 1));
        assertNotNull(method.invoke(null, "HorseEgg", 1));
    }

    @Test
    void resolveMaterialFromIdentifierTokenUsesConfiguredAndLegacyAliases() throws Exception {
        set("identifierAliases", Map.of("CUSTOM_ALIAS", "DIAMOND"));
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        assertEquals(Material.DIAMOND, method.invoke(null, "CUSTOM_ALIAS"));
        assertEquals(Material.OAK_SIGN, method.invoke(null, "SIGN"));
        assertNull(method.invoke(null, "DOES_NOT_EXIST"));
    }

    @Test
    void resolveMaterialTokenHandlesBlankAndDirectMaterialLookups() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, ""));
        assertNull(method.invoke(null, "   "));
        assertEquals(Material.STONE, method.invoke(null, "stone"));
    }

    private static void set(String fieldName, Map<String, String> value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
