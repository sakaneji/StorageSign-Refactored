package storagesign;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code config.yml} の全値をロード・キャッシュするクラス。
 *
 * <p>インストール済みの {@code config.yml} が引き続き動作するよう、
 * 元プラグインのキー名と完全に一致させてある。
 *
 * <p>キー {@code no-permisson} （「i」欠き）は元プラグインのタイポをそのまま維持している。
 */
public final class ConfigLoader {

    // ── 設定キー定数 ────────────────────────────────────────────────────────────
    private static final String KEY_NO_PERMISSION     = "no-permisson";  // 元プラグインのタイポ — 互換のためそのまま維持
    private static final String KEY_LOG_LEVEL         = "log-level";
    private static final String KEY_MANUAL_IMPORT     = "manual-import";
    private static final String KEY_MANUAL_EXPORT     = "manual-export";
    private static final String KEY_AUTO_IMPORT       = "auto-import";
    private static final String KEY_AUTO_EXPORT       = "auto-export";
    private static final String KEY_AUTOCOLLECT       = "autocollect";
    private static final String KEY_HARDRECIPE        = "hardrecipe";
    private static final String KEY_DIVIDE_LIMIT      = "divide-limit";
    private static final String KEY_SNEAK_DIVIDE_LIMIT = "sneak-divide-limit";
    private static final String KEY_MAX_STACK_SIZE    = "max-stack-size";
    private static final String KEY_UNREGISTER_ON_EMPTY = "unregister-on-empty";
    private static final String KEY_NO_BUD            = "no-bud";
    private static final String KEY_FALLING_BLOCK     = "falling-block-itemSS";
    private static final String KEY_BANNER_DEBUG      = "banner-debug";
    private static final String KEY_IDENTIFIER_ALIASES = "item-identifier-aliases";
    private static final String KEY_POTION_KEY_ALIASES = "potion-key-aliases";
    private static final String KEY_BREWING_INGREDIENTS = "brewing-ingredient-identifiers";
    private static final String KEY_VIRTUAL_IDENTIFIERS = "virtual-item-identifiers";
    private static final String KEY_STORAGE_INDEX_ENABLED = "storage-index.enabled";
    private static final String KEY_INDEX_CHUNKS_PER_TICK = "storage-index.rebuild-chunks-per-tick";
    private static final String KEY_NEARBY_DISPLAY_ENABLED = "nearby-display.enabled";
    private static final String KEY_DISPLAY_DISTANCE = "nearby-display.distance";
    private static final String KEY_DISPLAY_FOV = "nearby-display.field-of-view-degrees";
    private static final String KEY_DISPLAY_IDLE_TICKS = "nearby-display.idle-delay-ticks";
    private static final String KEY_DISPLAY_INTERVAL_TICKS = "nearby-display.monitor-interval-ticks";
    private static final String KEY_DISPLAY_MAX_PER_PLAYER = "nearby-display.max-per-player";
    private static final String KEY_DISPLAY_SEARCHES_PER_TICK = "nearby-display.max-searches-per-tick";
    private static final String KEY_DISPLAY_GLOBAL_LIMIT = "nearby-display.global-label-limit";
    private static final String KEY_SEARCH_PAGE_SIZE = "admin-search.page-size";
    private static final String KEY_SEARCH_MAX_CONCURRENT = "admin-search.max-concurrent";

    // ── Cached values ─────────────────────────────────────────────────────────
    private static String  noPermission;
    private static String  logLevel;
    private static boolean manualImport;
    private static boolean manualExport;
    private static boolean autoImport;
    private static boolean autoExport;
    private static boolean autocollect;
    private static boolean hardrecipe;
    private static int     divideLimit;
    private static int     sneakDivideLimit;
    private static int     maxStackSize;
    private static boolean unregisterOnEmpty = false;
    private static boolean noBud;
    private static boolean fallingBlockItemSS;
    private static boolean bannerDebug;
    private static Map<String, String> identifierAliases = Map.of();
    private static Map<String, String> potionKeyAliases = Map.of();
    private static java.util.Set<String> brewingIngredientIdentifiers = java.util.Set.of();
    private static Map<String, String> virtualItemIdentifiers = Map.of();
    private static boolean storageIndexEnabled;
    private static int indexChunksPerTick;
    private static boolean nearbyDisplayEnabled;
    private static double nearbyDisplayDistance;
    private static double nearbyDisplayFov;
    private static int nearbyDisplayIdleTicks;
    private static int nearbyDisplayIntervalTicks;
    private static int nearbyDisplayMaxPerPlayer;
    private static int nearbyDisplaySearchesPerTick;
    private static int nearbyDisplayGlobalLimit;
    private static int adminSearchPageSize;
    private static int adminSearchMaxConcurrent;

    private ConfigLoader() {}

    /**
     * デフォルト config がなければ生成し、全値をロードする。
     */
    public static void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        noPermission      = cfg.getString(KEY_NO_PERMISSION, "You don't have permission");
        logLevel          = cfg.getString(KEY_LOG_LEVEL, "INFO");
        manualImport      = cfg.getBoolean(KEY_MANUAL_IMPORT, true);
        manualExport      = cfg.getBoolean(KEY_MANUAL_EXPORT, true);
        autoImport        = cfg.getBoolean(KEY_AUTO_IMPORT, true);
        autoExport        = cfg.getBoolean(KEY_AUTO_EXPORT, true);
        autocollect       = cfg.getBoolean(KEY_AUTOCOLLECT, true);
        hardrecipe        = cfg.getBoolean(KEY_HARDRECIPE, false);
        divideLimit       = cfg.getInt(KEY_DIVIDE_LIMIT, 345600);
        sneakDivideLimit  = cfg.getInt(KEY_SNEAK_DIVIDE_LIMIT, 34560);
        maxStackSize      = cfg.getInt(KEY_MAX_STACK_SIZE, 16);
        unregisterOnEmpty = cfg.getBoolean(KEY_UNREGISTER_ON_EMPTY, false);
        noBud             = cfg.getBoolean(KEY_NO_BUD, false);
        fallingBlockItemSS = cfg.getBoolean(KEY_FALLING_BLOCK, false);
        bannerDebug       = cfg.getBoolean(KEY_BANNER_DEBUG, false);
        identifierAliases = readStringMap(cfg.getConfigurationSection(KEY_IDENTIFIER_ALIASES));
        potionKeyAliases = readStringMap(cfg.getConfigurationSection(KEY_POTION_KEY_ALIASES));
        java.util.Set<String> brewingIngredients = new java.util.HashSet<>();
        for (String value : cfg.getStringList(KEY_BREWING_INGREDIENTS)) {
            if (value != null && !value.isBlank()) brewingIngredients.add(value.trim().toUpperCase());
        }
        brewingIngredientIdentifiers = Collections.unmodifiableSet(brewingIngredients);
        virtualItemIdentifiers = readStringMap(cfg.getConfigurationSection(KEY_VIRTUAL_IDENTIFIERS));
        storageIndexEnabled = cfg.getBoolean(KEY_STORAGE_INDEX_ENABLED, true);
        indexChunksPerTick = positive(cfg.getInt(KEY_INDEX_CHUNKS_PER_TICK, 8), 8);
        nearbyDisplayEnabled = cfg.getBoolean(KEY_NEARBY_DISPLAY_ENABLED, true);
        nearbyDisplayDistance = positive(cfg.getDouble(KEY_DISPLAY_DISTANCE, 6.0), 6.0);
        nearbyDisplayFov = clamp(cfg.getDouble(KEY_DISPLAY_FOV, 90.0), 1.0, 360.0);
        nearbyDisplayIdleTicks = positive(cfg.getInt(KEY_DISPLAY_IDLE_TICKS, 10), 10);
        nearbyDisplayIntervalTicks = positive(cfg.getInt(KEY_DISPLAY_INTERVAL_TICKS, 5), 5);
        nearbyDisplayMaxPerPlayer = positive(cfg.getInt(KEY_DISPLAY_MAX_PER_PLAYER, 3), 3);
        nearbyDisplaySearchesPerTick = positive(cfg.getInt(KEY_DISPLAY_SEARCHES_PER_TICK, 25), 25);
        nearbyDisplayGlobalLimit = positive(cfg.getInt(KEY_DISPLAY_GLOBAL_LIMIT, 512), 512);
        adminSearchPageSize = positive(cfg.getInt(KEY_SEARCH_PAGE_SIZE, 10), 10);
        adminSearchMaxConcurrent = positive(cfg.getInt(KEY_SEARCH_MAX_CONCURRENT, 2), 2);
    }

    // ── ゲッター ───────────────────────────────────────────────────────────────

    public static String  getNoPermission()      { return noPermission;      }
    public static String  getLogLevel()          { return logLevel;          }
    public static boolean getManualImport()      { return manualImport;      }
    public static boolean getManualExport()      { return manualExport;      }
    public static boolean getAutoImport()        { return autoImport;        }
    public static boolean getAutoExport()        { return autoExport;        }
    public static boolean getAutocollect()       { return autocollect;       }
    public static boolean getHardrecipe()        { return hardrecipe;        }
    public static int     getDivideLimit()       { return divideLimit;       }
    public static int     getSneakDivideLimit()  { return sneakDivideLimit;  }
    public static int     getMaxStackSize()      { return maxStackSize;      }
    public static boolean getUnregisterOnEmpty() { return unregisterOnEmpty; }
    public static boolean getNoBud()             { return noBud;             }
    public static boolean getFallingBlockItemSS(){ return fallingBlockItemSS;}
    public static boolean getBannerDebug()       { return bannerDebug;       }
    public static Map<String, String> getIdentifierAliases() { return identifierAliases; }
    public static Map<String, String> getPotionKeyAliases() { return potionKeyAliases; }
    public static java.util.Set<String> getBrewingIngredientIdentifiers() { return brewingIngredientIdentifiers; }
    public static Map<String, String> getVirtualItemIdentifiers() { return virtualItemIdentifiers; }
    public static boolean getStorageIndexEnabled() { return storageIndexEnabled; }
    public static int getIndexChunksPerTick() { return indexChunksPerTick; }
    public static boolean getNearbyDisplayEnabled() { return nearbyDisplayEnabled; }
    public static boolean getEffectiveNearbyDisplayEnabled() {
        return storageIndexEnabled && nearbyDisplayEnabled;
    }
    public static double getNearbyDisplayDistance() { return nearbyDisplayDistance; }
    public static double getNearbyDisplayFov() { return nearbyDisplayFov; }
    public static int getNearbyDisplayIdleTicks() { return nearbyDisplayIdleTicks; }
    public static int getNearbyDisplayIntervalTicks() { return nearbyDisplayIntervalTicks; }
    public static int getNearbyDisplayMaxPerPlayer() { return nearbyDisplayMaxPerPlayer; }
    public static int getNearbyDisplaySearchesPerTick() { return nearbyDisplaySearchesPerTick; }
    public static int getNearbyDisplayGlobalLimit() { return nearbyDisplayGlobalLimit; }
    public static int getAdminSearchPageSize() { return adminSearchPageSize; }
    public static int getAdminSearchMaxConcurrent() { return adminSearchMaxConcurrent; }

    private static Map<String, String> readStringMap(ConfigurationSection section) {
        if (section == null) return Map.of();

        Map<String, String> values = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            values.put(key.trim(), value.trim());
        }
        return Collections.unmodifiableMap(values);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
