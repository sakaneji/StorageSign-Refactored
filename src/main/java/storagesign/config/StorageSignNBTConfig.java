package storagesign.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import storagesign.logging.PluginLogger;

/**
 * プラグインデータフォルダから {@code storageSignNBT.yml} を読み込む。
 *
 * <p>Minecraft バージョン文字列から剄刴たのバナー・レイドバナーの再構築に必要な
 * NBT データ文字列へのマッピングを保持する。
 * プラグインのアップデートなしにバージョン固有の値を上書きできるようにメイン設定と分離する。
 *
 * <p>このファイルは {@code resources/} からコピーされない—サーバー管理者または
 * インストーラーがプラグインデータフォルダに手動で配置する必要がある。
 */
public final class StorageSignNBTConfig {

    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSignNBTConfig.class);

    private final YamlConfiguration config;
    private final boolean loaded;

    public StorageSignNBTConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "storageSignNBT.yml");
        YamlConfiguration cfg = null;
        boolean ok = false;
        if (file.exists()) {
            try {
                cfg = YamlConfiguration.loadConfiguration(file);
                ok = true;
                LOG.debug("load", () -> "loaded=" + file.getAbsolutePath());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "load", "Failed to load storageSignNBT.yml", e);
            }
        } else {
            LOG.info("load", "storageSignNBT.yml not found — ominous banner will not be loaded");
        }
        this.config = cfg;
        this.loaded = ok;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 指定した Minecraft バージョンキーに対応する NBT 文字列を返す。
     * 見つからない場合は {@code null}。
     */
    public String getNbtString(String version) {
        if (config == null) return null;
        return config.getString(version);
    }

    /**
     * バージョンキーに関わらず、ファイル内の最初に見つかった null 以外の NBT 文字列を返す。
     * 具体的な MC バージョンキーが存在しない場合のフォールバックとして使用する。
     */
    public String getFirstNbtString() {
        if (config == null) return null;
        for (String key : config.getKeys(false)) {
            String val = config.getString(key);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }
}
