package storagesign.display;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class NearbyStorageSignDisplayTest {
    @Test
    void forwardConeUsesConfiguredFullAngle() {
        Vector forward = new Vector(0, 0, 1);

        assertTrue(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(0.9, 0, 1), 90.0));
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(1.1, 0, 1), 90.0));
        assertFalse(NearbyStorageSignDisplay.isInForwardCone(
            forward, new Vector(0, 0, -1), 90.0));
    }

    @Test
    void movementIncludesPositionAndViewChanges() {
        Location original = new Location(null, 1, 2, 3, 20, 10);

        assertFalse(NearbyStorageSignDisplay.moved(original, original.clone()));
        assertTrue(NearbyStorageSignDisplay.moved(
            original, new Location(null, 1.1, 2, 3, 20, 10)));
        assertTrue(NearbyStorageSignDisplay.moved(
            original, new Location(null, 1, 2, 3, 21, 10)));
    }

    @Test
    void wrappingNeverRemovesIdentifierText() {
        String identifier = "NETHERITE_UPGRADE_SMITHING_TEMPLATE_WITH_EXTRA_SUFFIX";
        String wrapped = NearbyStorageSignDisplay.wrap(identifier);

        assertTrue(wrapped.contains("\n"));
        assertTrue(wrapped.replace("\n", "").equals(identifier));
    }
}
