package storagesign.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.Server;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class CompatibilityDegradationTest {

    @Test
    void missingNameAndTooltipApisDoNotFailBannerCore() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemMetaDecorationAdapter adapter = new ItemMetaDecorationAdapter(
            false, new String[0], new String[0]
        );

        ItemMetaDecorationAdapter.DecorationResult result = adapter.decorateOminousBanner(meta);

        assertFalse(result.nameAvailable());
        assertFalse(result.tooltipAvailable());
    }

    @Test
    void missingSignEventsDisableOnlyEditGuard() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);

        boolean registered = new SignEditGuard("invalid.missing.SignOpenEvent").register(plugin);

        assertFalse(registered);
        verifyNoInteractions(manager);
    }
}
