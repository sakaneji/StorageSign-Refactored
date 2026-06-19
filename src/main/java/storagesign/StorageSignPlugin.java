package storagesign;

import java.util.logging.Level;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import storagesign.listener.BlockEventListener;
import storagesign.listener.CraftListener;
import storagesign.listener.EntityListener;
import storagesign.listener.InventoryListener;
import storagesign.listener.PlayerInteractListener;
import storagesign.listener.SignEditListenerFactory;
import storagesign.listener.SignPhysicsListener;
import storagesign.logging.PluginLogger;
import storagesign.registry.MaterialRegistry;
import storagesign.command.SsGiveCommand;

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
public final class StorageSignPlugin extends JavaPlugin {

    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSignPlugin.class);
    private static final long OMINOUS_BANNER_FIRST_RETRY_DELAY_TICKS = 1L;
    private static final long OMINOUS_BANNER_RETRY_PERIOD_TICKS = 100L;

    private BukkitTask ominousBannerRetryTask;
    private int ominousBannerRetryAttempts;
    private Function<Boolean, BannerMeta> ominousBannerMetaFactory = this::createOminousBannerMetaByApi;

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
        // /reload 等で同じクラスローダーが再利用されても、古いメタを持ち越さない。
        setOminousBannerMeta(null);

        // ── 1. Config ロード ──────────────────────────────────────────────────────────────
        ConfigLoader.load(this);
        PluginLogger.initialize(this, ConfigLoader.getLogLevel());
        LOG.debug("onEnable", () -> "ConfigLoader loaded: auto-import=" + ConfigLoader.getAutoImport()
                  + ", auto-export=" + ConfigLoader.getAutoExport()
                  + ", no-bud=" + ConfigLoader.getNoBud());

        // ── 2. レイドバナー ───────────────────────────────────────────────────────────
        loadOminousBanner();

        // ── 3. クラフトレシピ ─────────────────────────────────────────────────────────
        registerRecipes();

        // ── 4. イベントリスナー ────────────────────────────────────────────────────────
        registerListeners();

        getCommand("storagesigngive").setExecutor(new SsGiveCommand());

        LOG.info("onEnable", "StorageSign enabled. Sign types: " + MaterialRegistry.SIGN_MATERIALS.size()
                 + ", Shulker types: " + MaterialRegistry.SHULKER_BOX_MATERIALS.size());
    }

    @Override
    public void onDisable() {
        cancelOminousBannerRetry();
        LOG.info("onDisable", "StorageSign disabled.");
        PluginLogger.shutdown();
    }

    // ── レイドバナー ───────────────────────────────────────────────────────────────

    private void loadOminousBanner() {
        BannerMeta apiMeta = ominousBannerMetaFactory.apply(true);
        if (apiMeta != null) {
            setOminousBannerMeta(apiMeta);
            LOG.info("loadOminousBanner", "レイドバナーメタを API でロードしました ("
                             + apiMeta.numberOfPatterns() + " パターン)");
            return;
        }

        LOG.warning("loadOminousBanner",
            "API でレイドバナーを構築できませんでした。復旧するまで自動的に再試行します");
        scheduleOminousBannerRetry();
    }

    private void scheduleOminousBannerRetry() {
        if (ominousBannerRetryTask != null) return;

        ominousBannerRetryAttempts = 0;
        ominousBannerRetryTask = Bukkit.getScheduler().runTaskTimer(
            this,
            () -> {
                // 実物の不吉な旗を未登録 SS に登録して先に復旧した場合も終了する。
                if (getOminousBannerMeta() != null) {
                    cancelOminousBannerRetry();
                    return;
                }

                ominousBannerRetryAttempts++;
                BannerMeta recovered = ominousBannerMetaFactory.apply(false);
                if (recovered == null) return;

                setOminousBannerMeta(recovered);
                LOG.info("retryOminousBanner",
                    "レイドバナーメタを API で復旧しました (試行回数: "
                        + ominousBannerRetryAttempts + ")");
                cancelOminousBannerRetry();
            },
            OMINOUS_BANNER_FIRST_RETRY_DELAY_TICKS,
            OMINOUS_BANNER_RETRY_PERIOD_TICKS
        );
    }

    private void cancelOminousBannerRetry() {
        BukkitTask task = ominousBannerRetryTask;
        ominousBannerRetryTask = null;
        ominousBannerRetryAttempts = 0;
        if (task != null) task.cancel();
    }

    /**
     * API で不吉なバナー（白バナー 8 パターン）を構築する。
     * 成功時は BannerMeta、失敗時は null。
     */
    private BannerMeta createOminousBannerMetaByApi(boolean logFailureAsWarning) {
        try {
            ItemStack banner = new ItemStack(Material.WHITE_BANNER);
            ItemMeta itemMeta = banner.getItemMeta();
            if (!(itemMeta instanceof BannerMeta bm)) return null;

            bm.setPatterns(java.util.List.of(
                createBannerPattern(DyeColor.CYAN, "RHOMBUS", "RHOMBUS_MIDDLE"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "STRIPE_BOTTOM"),
                createBannerPattern(DyeColor.GRAY, "STRIPE_CENTER"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "BORDER"),
                createBannerPattern(DyeColor.BLACK, "STRIPE_MIDDLE"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "HALF_HORIZONTAL"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "CIRCLE", "CIRCLE_MIDDLE"),
                createBannerPattern(DyeColor.BLACK, "BORDER")
            ));
            return normalizeOminousBannerMeta(bm);
        } catch (Throwable e) {
            if (logFailureAsWarning) {
                LOG.log(Level.WARNING, "createOminousBannerMetaByApi",
                    "API 経由でレイドバナー構築に失敗しました", e);
            } else {
                LOG.debug("createOminousBannerMetaByApi",
                    () -> "レイドバナー復旧の再試行に失敗しました: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return null;
    }

    /** バニラ Java Edition の不吉な旗の 8 模様と完全に一致するか検証する。 */
    public static boolean isOminousBannerMeta(BannerMeta meta) {
        if (meta == null || meta.numberOfPatterns() != 8) return false;
        java.util.List<Pattern> patterns = meta.getPatterns();
        return isOminousBannerPatterns(
            patterns.stream().map(Pattern::getColor).toList(),
            patterns.stream().map(pattern -> pattern.getPattern().name()).toList()
        );
    }

    static boolean isOminousBannerPatterns(java.util.List<DyeColor> colors,
                                            java.util.List<String> typeNames) {
        if (colors.size() != 8 || typeNames.size() != 8) return false;
        return colors.equals(java.util.List.of(
                DyeColor.CYAN, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.LIGHT_GRAY,
                DyeColor.BLACK, DyeColor.LIGHT_GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK
            ))
            && matchesType(typeNames.get(0), "RHOMBUS", "RHOMBUS_MIDDLE")
            && matchesType(typeNames.get(1), "STRIPE_BOTTOM")
            && matchesType(typeNames.get(2), "STRIPE_CENTER")
            && matchesType(typeNames.get(3), "BORDER")
            && matchesType(typeNames.get(4), "STRIPE_MIDDLE")
            && matchesType(typeNames.get(5), "HALF_HORIZONTAL")
            && matchesType(typeNames.get(6), "CIRCLE", "CIRCLE_MIDDLE")
            && matchesType(typeNames.get(7), "BORDER");
    }

    private static boolean matchesType(String actual, String... candidates) {
        return java.util.Arrays.asList(candidates).contains(actual);
    }

    /** 模様だけで構築した場合でも、通常の White Banner 名で搬出されないようにする。 */
    private static BannerMeta normalizeOminousBannerMeta(BannerMeta source) {
        BannerMeta normalized = (BannerMeta) source.clone();
        if (!normalized.hasItemName() && !normalized.hasDisplayName()) {
            if (!setTranslatableOminousBannerName(normalized)) {
                // Spigot には Adventure Component 版 itemName API がないため、
                // Paper API が利用できない場合のみ固定名にフォールバックする。
                normalized.setItemName("§6Ominous Banner");
            }
        }
        normalized.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return normalized;
    }

    /** Paper 上ではバニラと同じ翻訳キーを使い、各クライアント言語で表示する。 */
    private static boolean setTranslatableOminousBannerName(BannerMeta meta) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> textColorClass = Class.forName("net.kyori.adventure.text.format.TextColor");
            Class<?> namedTextColorClass = Class.forName("net.kyori.adventure.text.format.NamedTextColor");

            Object component = componentClass.getMethod("translatable", String.class)
                .invoke(null, "block.minecraft.ominous_banner");
            Object gold = namedTextColorClass.getField("GOLD").get(null);
            component = componentClass.getMethod("color", textColorClass).invoke(component, gold);

            ItemMeta.class.getMethod("itemName", componentClass).invoke(meta, component);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private Pattern createBannerPattern(DyeColor color, String... candidateNames) {
        return new Pattern(color, resolvePatternType(candidateNames));
    }

    private PatternType resolvePatternType(String... candidateNames) {
        for (String candidateName : candidateNames) {
            try {
                return PatternType.valueOf(candidateName);
            } catch (IllegalArgumentException ignored) {
                // バージョン差分で enum 名が変わるため、候補を順に試す。
            }
        }
        throw new IllegalStateException(
            "Unsupported banner pattern type names: " + java.util.Arrays.toString(candidateNames)
        );
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
    private void registerRecipes() {
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

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new BlockEventListener(this), this);
        pm.registerEvents(new InventoryListener(this), this);
        pm.registerEvents(new EntityListener(), this);
        pm.registerEvents(new CraftListener(), this);
        SignEditListenerFactory.register(this);

        if (ConfigLoader.getNoBud()) {
            pm.registerEvents(new SignPhysicsListener(), this);
            LOG.info("registerListeners", "no-bud: BUD 防止を有効化しました。");
        }
    }
}
