package storagesign;

import java.util.logging.Level;
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

import storagesign.config.StorageSignNBTConfig;
import storagesign.listener.BlockEventListener;
import storagesign.listener.CraftListener;
import storagesign.listener.EntityListener;
import storagesign.listener.InventoryListener;
import storagesign.listener.PlayerInteractListener;
import storagesign.listener.SignEditListenerFactory;
import storagesign.listener.SignPhysicsListener;
import storagesign.registry.MaterialRegistry;
import storagesign.command.SsGiveCommand;

/**
 * StorageSign プラグインのメインクラス。
 *
 * <p>要約:
 * <ol>
 *   <li>起動時に config および NBT データをロードする</li>
 *   <li>レイドバナーのメタをロードする</li>
 *   <li>全看板種別に対するクラフトレシピを登録する</li>
 *   <li>全イベントリスナーを登録する</li>
 * </ol>
 *
 * <p>イベント処理ロジックはすべて {@code listener.*} パッケージに割り当ててあり、このクラスはシンプルに保つ。
 */
public final class StorageSignPlugin extends JavaPlugin {

    /**
     * レイドバナー（白バナー パターン 8 枚）の BannerMeta。
     * 起動時に {@code storageSignNBT.yml} からロードする。
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

        // ── 2. レイドバナー ───────────────────────────────────────────────────────────
        if (!ConfigLoader.getBannerDebug()) {
            loadOminousBanner();
        } else {
            getLogger().info("banner-debug=true: レイドバナーのロードをスキップしました");
        }

        // ── 3. クラフトレシピ ─────────────────────────────────────────────────────────
        registerRecipes();

        // ── 4. イベントリスナー ────────────────────────────────────────────────────────
        registerListeners();

        getCommand("storagesigngive").setExecutor(new SsGiveCommand());

        getLogger().info("StorageSign enabled. Sign types: " + MaterialRegistry.SIGN_MATERIALS.size()
                         + ", Shulker types: " + MaterialRegistry.SHULKER_BOX_MATERIALS.size());
    }

    @Override
    public void onDisable() {
        getLogger().info("StorageSign disabled.");
    }

    // ── レイドバナー ───────────────────────────────────────────────────────────────

    private void loadOminousBanner() {
        BannerMeta apiMeta = createOminousBannerMetaByApi();
        if (apiMeta != null) {
            setOminousBannerMeta(apiMeta);
            getLogger().info("レイドバナーメタを API でロードしました ("
                             + apiMeta.numberOfPatterns() + " パターン)");
            return;
        }

        getLogger().warning("API でレイドバナー構築に失敗したため、SNBT フォールバックを試行します");

        StorageSignNBTConfig nbtConfig = new StorageSignNBTConfig(this);
        if (!nbtConfig.isLoaded()) return;

        // バージョンキーは storageSignNBT.yml の記込形式に合わせる: "1.21.1"(スナップショット文字列ではなく)
        String version = Bukkit.getBukkitVersion().split("-")[0];
        String nbt = nbtConfig.getNbtString(version);
        if (nbt == null) {
            nbt = nbtConfig.getFirstNbtString();
        }
        if (nbt == null) {
            getLogger().warning("No NBT string found in storageSignNBT.yml for version: " + version);
            return;
        }

        try {
            // NBT 文字列から WHITE_BANNER アイテムをデシリアライズし、BannerMeta を取得する
            ItemStack banner = deserializeBannerFromNbt(nbt);
            if (banner != null
                && banner.getType() == Material.WHITE_BANNER
                && banner.getItemMeta() instanceof BannerMeta bm
                && isOminousBannerMeta(bm)) {
                setOminousBannerMeta(bm);
                getLogger().info("レイドバナーメタをロードしました ("
                                 + bm.numberOfPatterns() + " パターン)");
            } else {
                getLogger().warning("バージョン " + version + " のレイドバナー NBT のパースに失敗しました");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "レイドバナーのロード中に例外が発生しました", e);
        }
    }

    /**
     * API で不吉なバナー（白バナー 8 パターン）を構築する。
     * 成功時は BannerMeta、失敗時は null。
     */
    private BannerMeta createOminousBannerMetaByApi() {
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
            getLogger().log(Level.WARNING, "API 経由でレイドバナー構築に失敗しました", e);
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

    /**
     * SNBT 文字列からバナーをデシリアライズする。
     *
     * <p>{@code ItemFactory.createItemStack(String)} — 元プラグインと同じ API を使用。
     * {@code storageSignNBT.yml} に保存されている SNBT テキスト形式を受け入れる。
     */
    private ItemStack deserializeBannerFromNbt(String nbt) {
        try {
            return Bukkit.getItemFactory().createItemStack(nbt);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "ItemFactory 経由でバナー NBT のデシリアライズに失敗しました", e);
        }
        return null;
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
        getLogger().info(MaterialRegistry.SIGN_MATERIALS.size() + " 種類の StorageSign レシピを登録しました。");
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
            getLogger().info("no-bud: BUD 防止を有効化しました。");
        }
    }
}
