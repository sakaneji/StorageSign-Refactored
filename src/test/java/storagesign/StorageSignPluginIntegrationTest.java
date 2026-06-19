package storagesign;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
