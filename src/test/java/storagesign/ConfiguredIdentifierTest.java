package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
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

    private static void set(String fieldName, Map<String, String> value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
