package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
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
