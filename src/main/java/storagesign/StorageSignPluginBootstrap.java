package storagesign;

import org.bukkit.Bukkit;
import storagesign.command.SsGiveCommand;
import storagesign.command.StorageSignIndexCommand;
import storagesign.command.StorageSignSearchCommand;
import storagesign.command.StorageSignWarpCommand;
import storagesign.display.NearbyStorageSignDisplay;
import storagesign.index.StorageSignIndex;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;
import storagesign.search.StorageSignQueryService;

final class StorageSignPluginBootstrap {
    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSignPluginBootstrap.class);

    private final StorageSignPlugin plugin;

    StorageSignPluginBootstrap(StorageSignPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        plugin.resetOminousBannerMeta();

        ConfigLoader.load(plugin);
        PluginLogger.initialize(plugin, ConfigLoader.getLogLevel());
        LOG.debug("onEnable", () -> "ConfigLoader loaded: auto-import=" + ConfigLoader.getAutoImport()
                  + ", auto-export=" + ConfigLoader.getAutoExport()
                  + ", no-bud=" + ConfigLoader.getNoBud());

        StorageSignIndex storageSignIndex = new StorageSignIndex(plugin, ConfigLoader.getStorageIndexEnabled());
        plugin.setStorageSignIndex(storageSignIndex);
        storageSignIndex.load();

        plugin.loadOminousBanner();
        plugin.registerRecipes();
        plugin.registerListeners();

        StorageSignCommandTabCompleter tabCompleter = new StorageSignCommandTabCompleter();
        plugin.getCommand("storagesigngive").setExecutor(new SsGiveCommand());
        plugin.getCommand("storagesigngive").setTabCompleter(tabCompleter);
        plugin.getCommand("storagesignindex").setExecutor(new StorageSignIndexCommand(storageSignIndex));
        plugin.getCommand("storagesignindex").setTabCompleter(tabCompleter);
        StorageSignQueryService storageSignQueries = new StorageSignQueryService(plugin, storageSignIndex);
        plugin.setStorageSignQueries(storageSignQueries);
        plugin.getCommand("storagesignsearch").setExecutor(
            new StorageSignSearchCommand(storageSignIndex, storageSignQueries));
        plugin.getCommand("storagesignsearch").setTabCompleter(tabCompleter);
        plugin.getCommand("storagesignwarp").setExecutor(new StorageSignWarpCommand(storageSignIndex));
        plugin.getCommand("storagesignwarp").setTabCompleter(tabCompleter);

        NearbyStorageSignDisplay nearbyStorageSignDisplay = new NearbyStorageSignDisplay(plugin, storageSignIndex);
        plugin.setNearbyStorageSignDisplay(nearbyStorageSignDisplay);
        if (storageSignIndex.isEnabled()) {
            storageSignIndex.rebuild(Bukkit.getWorlds(), result -> {
                LOG.info("storageSignIndex", "StorageSign index ready: chunks=" + result.chunksScanned()
                    + ", signs=" + result.countAfter());
                nearbyStorageSignDisplay.start();
            });
        }
        if (ConfigLoader.getNearbyDisplayEnabled() && !storageSignIndex.isEnabled()) {
            LOG.warning("nearbyDisplay",
                "nearby-display is disabled because storage-index.enabled is false");
        }
        if (!storageSignIndex.isEnabled()) nearbyStorageSignDisplay.start();

        LOG.info("onEnable", "StorageSign enabled. Sign types: " + MaterialRegistry.SIGN_MATERIALS.size()
                 + ", Shulker types: " + MaterialRegistry.SHULKER_BOX_MATERIALS.size());
    }

    void disable() {
        NearbyStorageSignDisplay nearbyStorageSignDisplay = plugin.getNearbyStorageSignDisplay();
        if (nearbyStorageSignDisplay != null) nearbyStorageSignDisplay.shutdown();
        plugin.setNearbyStorageSignDisplay(null);
        StorageSignIndex storageSignIndex = plugin.getStorageSignIndex();
        if (storageSignIndex != null) {
            storageSignIndex.saveSync();
            storageSignIndex.shutdown();
        }
        plugin.setStorageSignIndex(null);
        plugin.setStorageSignQueries(null);
        plugin.cancelOminousBannerRetry();
        LOG.info("onDisable", "StorageSign disabled.");
        PluginLogger.shutdown();
    }
}
