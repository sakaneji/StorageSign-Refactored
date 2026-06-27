package storagesign;

import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import storagesign.compat.ItemMetaDecorationAdapter;
import storagesign.compat.OminousBannerCodec;
import storagesign.compat.SignEditGuard;
import storagesign.listener.BlockEventListener;
import storagesign.listener.CraftListener;
import storagesign.listener.EntityListener;
import storagesign.listener.InventoryListener;
import storagesign.listener.PlayerInteractListener;
import storagesign.listener.SignPhysicsListener;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;
import storagesign.command.SsGiveCommand;
import storagesign.command.StorageSignIndexCommand;
import storagesign.command.StorageSignSearchCommand;
import storagesign.display.NearbyStorageSignDisplay;
import storagesign.index.StorageSignIndex;
import storagesign.search.StorageSignQueryService;

/**
 * StorageSign プラグインのメインクラス。
 *
 * <p>要約:
 * <ol>
 *   <li>起動時に config をロードする</li>
 *   <li>レイドバナーのメタを API で構築する</li>
 *   <li>全看板種別に対するクラフトレシピを登録する</li>
 *   <li>全イベントリスナーを登録する</li>
 * </ol>
 *
 * <p>イベント処理ロジックはすべて {@code listener.*} パッケージに割り当ててあり、このクラスはシンプルに保つ。
 */
public class StorageSignPlugin extends JavaPlugin {

    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSignPlugin.class);
    private static final long OMINOUS_BANNER_FIRST_RETRY_DELAY_TICKS = 1L;
    private static final long OMINOUS_BANNER_RETRY_PERIOD_TICKS = 20L * 5L;
    private static final OminousBannerCodec OMINOUS_BANNER_CODEC = new OminousBannerCodec();
    private static final ItemMetaDecorationAdapter ITEM_META_DECORATOR = new ItemMetaDecorationAdapter();

    private BukkitTask ominousBannerRetryTask;
    private boolean ominousBannerRetryWarningLogged = false;
    private boolean ominousBannerNameAvailable = true;
    private boolean ominousBannerTooltipAvailable = true;
    private Function<Boolean, BannerMeta> ominousBannerMetaFactory = this::createOminousBannerMetaByApi;
    private StorageSignIndex storageSignIndex;
    private NearbyStorageSignDisplay nearbyStorageSignDisplay;
    private StorageSignQueryService storageSignQueries;

    /**
     * レイドバナー（白バナー パターン 8 枚）の BannerMeta。
     * 起動時に Bukkit API から構築する。
     * {@link StorageSign#getContents} および {@link StorageSign#isSimilar} から静的参照される。
     */
    private static BannerMeta ominousBannerMeta = null;

    public static BannerMeta getOminousBannerMeta() {
        return ominousBannerMeta;
    }

    public static void setOminousBannerMeta(BannerMeta meta) {
        ominousBannerMeta = meta == null ? null : normalizeOminousBannerMeta(meta);
    }

    @Override
    public void onEnable() {
        new StorageSignPluginBootstrap(this).enable();
    }

    @Override
    public void onDisable() {
        new StorageSignPluginBootstrap(this).disable();
    }

    /** Public loaded-chunk position index for other StorageSign features. */
    public StorageSignIndex getStorageSignIndex() {
        return storageSignIndex;
    }

    // ── レイドバナー ───────────────────────────────────────────────────────────────

    void resetOminousBannerMeta() {
        // /reload 等で同じクラスローダーが再利用されても、古いメタを持ち越さない。
        setOminousBannerMeta(null);
    }

    void loadOminousBanner() {
        BannerMeta apiMeta = ominousBannerMetaFactory.apply(true);
        if (apiMeta != null) {
            setOminousBannerMeta(apiMeta);
            LOG.info("loadOminousBanner", "レイドバナーメタを API でロードしました ("
                             + apiMeta.numberOfPatterns() + " パターン)");
            logDegradedBannerDecorations();
            return;
        }

        scheduleOminousBannerRetry();
    }

    void scheduleOminousBannerRetry() {
        if (ominousBannerRetryTask != null) return;

        ominousBannerRetryWarningLogged = false;
        ominousBannerRetryTask = Bukkit.getScheduler().runTaskTimer(
            this,
            this::retryOminousBanner,
            OMINOUS_BANNER_FIRST_RETRY_DELAY_TICKS,
            OMINOUS_BANNER_RETRY_PERIOD_TICKS
        );
    }

    void cancelOminousBannerRetry() {
        BukkitTask task = ominousBannerRetryTask;
        ominousBannerRetryTask = null;
        if (task != null) task.cancel();
    }

    void retryOminousBanner() {
        // 実物の不吉な旗を未登録 SS に登録して先に復旧した場合も終了する。
        if (getOminousBannerMeta() != null) {
            cancelOminousBannerRetry();
            return;
        }

        BannerMeta recovered = ominousBannerMetaFactory.apply(false);
        if (recovered == null) {
            if (!ominousBannerRetryWarningLogged) {
                LOG.warning("retryOminousBanner",
                    "不吉な旗の生成APIが利用できないため、旗の搬出だけを無効化し、5秒間隔で復旧を再試行します");
                ominousBannerRetryWarningLogged = true;
            } else {
                LOG.debug("retryOminousBanner",
                    () -> "不吉な旗の生成APIがまだ利用できないため、復旧を継続します");
            }
            return;
        }

        setOminousBannerMeta(recovered);
        LOG.info("retryOminousBanner",
            "レイドバナーメタを API で復旧しました");
        logDegradedBannerDecorations();
        cancelOminousBannerRetry();
    }

    /**
     * API で不吉なバナー（白バナー 8 パターン）を構築する。
     * 成功時は BannerMeta、失敗時は null。
     */
    BannerMeta createOminousBannerMetaByApi(boolean logFailureAsWarning) {
        try {
            BannerMeta meta = OMINOUS_BANNER_CODEC.create();
            if (meta == null) return null;
            BannerMeta normalized = (BannerMeta) meta.clone();
            ItemMetaDecorationAdapter.DecorationResult result =
                ITEM_META_DECORATOR.decorateOminousBanner(normalized);
            ominousBannerNameAvailable = result.nameAvailable();
            ominousBannerTooltipAvailable = result.tooltipAvailable();
            return normalized;
        } catch (Throwable e) {
            LOG.debug("createOminousBannerMetaByApi",
                () -> "レイドバナー構築に失敗しました: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }

    void logDegradedBannerDecorations() {
        if (!ominousBannerNameAvailable) {
            LOG.warning("loadOminousBanner",
                "不吉な旗の名前APIが利用できないため、名前装飾だけを無効化しました");
        }
        if (!ominousBannerTooltipAvailable) {
            LOG.warning("loadOminousBanner",
                "不吉な旗のツールチップAPIが利用できないため、非表示装飾だけを無効化しました");
        }
    }

    /** バニラ Java Edition の不吉な旗の 8 模様と完全に一致するか検証する。 */
    public static boolean isOminousBannerMeta(BannerMeta meta) {
        try {
            return OMINOUS_BANNER_CODEC.matches(meta);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** 模様だけで構築した場合でも、通常の White Banner 名で搬出されないようにする。 */
    private static BannerMeta normalizeOminousBannerMeta(BannerMeta source) {
        BannerMeta normalized = (BannerMeta) source.clone();
        ITEM_META_DECORATOR.decorateOminousBanner(normalized);
        return normalized;
    }

    // ── クラフトレシピ ──────────────────────────────────────────────────────────────

    /**
     * 各看板マテリアルに対するクラフトレシピを登録する。
     *
     * <p>元プラグイン互換のレシピ形状:
     * <pre>
     *   C C C
     *   C S C
     *   C H C
     * </pre>
     * C=CHEST、S=対象看板、H=CHEST（hardrecipe=true 時は ENDER_CHEST）
     */
    void registerRecipes() {
        for (Material signMat : MaterialRegistry.SIGN_MATERIALS) {
            NamespacedKey key = new NamespacedKey(this, "storagesign_" + signMat.name().toLowerCase());
            // リロード時の重複登録を防ぐため、同じキーのレシピは一度削除する
            Bukkit.removeRecipe(key);

            ItemStack result = StorageSign.createStorageSignItem(signMat, StorageSign.EMPTY_MARKER, 1);

            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("CCC", "CSC", "CHC");
            recipe.setIngredient('C', Material.CHEST);
            recipe.setIngredient('S', signMat);
            recipe.setIngredient('H', ConfigLoader.getHardrecipe() ? Material.ENDER_CHEST : Material.CHEST);
            recipe.setCategory(CraftingBookCategory.MISC);
            recipe.setGroup("StorageSign");
            Bukkit.addRecipe(recipe);
        }
        LOG.info("registerRecipes", MaterialRegistry.SIGN_MATERIALS.size() + " 種類の StorageSign レシピを登録しました。");
    }

    // ── イベントリスナー ────────────────────────────────────────────────────────────

    void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new BlockEventListener(this), this);
        pm.registerEvents(new InventoryListener(this), this);
        pm.registerEvents(new EntityListener(storageSignIndex), this);
        pm.registerEvents(new CraftListener(), this);
        new SignEditGuard().register(this);

        if (storageSignIndex.isEnabled()) {
            pm.registerEvents(storageSignIndex, this);
        }

        if (ConfigLoader.getNoBud()) {
            pm.registerEvents(new SignPhysicsListener(), this);
            LOG.info("registerListeners", "no-bud: BUD 防止を有効化しました。");
        }
    }

    void setStorageSignIndex(StorageSignIndex storageSignIndex) {
        this.storageSignIndex = storageSignIndex;
    }

    NearbyStorageSignDisplay getNearbyStorageSignDisplay() {
        return nearbyStorageSignDisplay;
    }

    void setNearbyStorageSignDisplay(NearbyStorageSignDisplay nearbyStorageSignDisplay) {
        this.nearbyStorageSignDisplay = nearbyStorageSignDisplay;
    }

    StorageSignQueryService getStorageSignQueries() {
        return storageSignQueries;
    }

    void setStorageSignQueries(StorageSignQueryService storageSignQueries) {
        this.storageSignQueries = storageSignQueries;
    }
}
