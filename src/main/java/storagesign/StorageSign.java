package storagesign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.block.sign.Side;

import storagesign.item.EnchantHelper;
import storagesign.item.SpecialCaseItemSupport;
import storagesign.item.PotionHelper;
import storagesign.registry.LegacyNameRegistry;
import storagesign.registry.MaterialRegistry;
import storagesign.logging.PluginLogger;

/**
 * StorageSign のデータモデル（イミュータブル）。
 *
 * <p>StorageSign は以下の 2 形態で存在する:
 * <ol>
 *   <li>物理看板ブロック（4行テキスト）</li>
 *   <li>インベントリ内のアイテム（表示名 + Lore[0]）</li>
 * </ol>
 *
 * <h3>看板ブロックのテキスト形式</h3>
 * <pre>
 *   行 0: "StorageSign"
 *   行 1: アイテム識別子（下記参照）
 *   行 2: 保管数量（数値文字列）
 *   行 3: サマリー（"LC/スタック/個"）
 * </pre>
 *
 * <h3>アイテム Lore 形式</h3>
 * <pre>
 *   表示名:  "StorageSign"
 *   Lore[0]: "{識別子} {数量}"  または  "Empty"
 * </pre>
 *
 * <h3>アイテム識別子の形式</h3>
 * <ul>
 *   <li>通常アイテム:    {@code STONE}  または  {@code STONE:0}</li>
 *   <li>ポーション:      {@code POTION:HEAL:0}  /  {@code SPOTION:REGEN:1}  /  {@code LPOTION:HEAL:2}</li>
 *   <li>エンチャント本: {@code ENCHBOOK:sharp:5}</li>
 *   <li>不吉なビン:     {@code OMINOUS_BOTTLE:2}</li>
 *   <li>看板アイテム: {@code OakStorageSign}  (damage=1 で保管)</li>
 *   <li>旧ウマの卵:     {@code HorseEgg} (END_PORTAL, damage=1)</li>
 * </ul>
 */
public final class StorageSign {

    private static final PluginLogger LOG = PluginLogger.getLogger(StorageSign.class);

    public static final String HEADER_LINE  = "StorageSign";
    public static final String EMPTY_MARKER = "Empty";

    // ── 特殊レガシー値 ─────────────────────────────────────────────────────────────
    static final short DAMAGE_SS_ITEM   = 1;  // 看板/旧ウマの卵をアイテムとして保管する際の damage フラグ
    static final short DAMAGE_FIREWORK_ZERO = 0;  // firework power=1 を damage=0 として保管

    // ── 互換性デフォルト（config.yml で上書き可能）──────────────────────────────────
    private static final Map<String, String> DEFAULT_IDENTIFIER_ALIASES = Map.ofEntries(
        Map.entry("SIGN", "OAK_SIGN"),
        Map.entry("ROSE_RED", "RED_DYE"),
        Map.entry("DANDELION_YELLOW", "YELLOW_DYE"),
        Map.entry("CACTUS_GREEN", "GREEN_DYE"),
        Map.entry("OMINOUS_BOTTLE", "OMINOUS_BOTTLE"),
        Map.entry("ENCHBOOK", "ENCHANTED_BOOK"),
        Map.entry("SPOTION", "SPLASH_POTION"),
        Map.entry("LPOTION", "LINGERING_POTION"),
        Map.entry("STONE_SLAB", "SMOOTH_STONE_SLAB") // MC 1.13→1.14 migration
    );

    private static final Map<String, String> DEFAULT_VIRTUAL_IDENTIFIERS = Map.ofEntries(
        Map.entry("EmptySign", "OAK_SIGN:1"),
        Map.entry("HorseEgg", "END_PORTAL:1")
    );

    private static final Material LEGACY_MARKER_ITEM_MATERIAL = Material.GHAST_SPAWN_EGG;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Material material;
    private final short    damage;      // サブタイプ / アンプリファイア / レベル（マテリアルにより意味が異なる）
    private int            amount;      // 保管数量（アイテム預入・取出のたびに更新される）

    // リッチサブタイプフィールド（material によりいずれか 1 つのみ設定される）
    private final PotionType  potionType;
    private final Enchantment enchantment;
    private boolean           unregistered;  // non-final: 数量が 0 になると登録解除状態に切り替わる

    /**
     * {@code ItemMeta.setMaxStackSize(Integer)} の MethodHandle キャッシュ。
     * クラスロード時に一度だけ public ItemMeta インターフェース経由で解決する。
     * null = このサーバーでは API が利用できない（API 未対応の Spigot ビルド等）。
     * JIT コンパイル後は MethodHandle.invoke() は直接仮想呼び出しと同等の速度になる。
     */
    private static final MethodHandle SET_MAX_STACK_SIZE;
    static {
        MethodHandle h = null;
        try {
            // ボックス化 Integer（Paper API）を先に試み、失敗したらプリミティブ int にフォールバック。
            h = MethodHandles.publicLookup().findVirtual(
                    org.bukkit.inventory.meta.ItemMeta.class, "setMaxStackSize",
                    MethodType.methodType(void.class, Integer.class));
        } catch (NoSuchMethodException | IllegalAccessException e1) {
            try {
                h = MethodHandles.publicLookup().findVirtual(
                        org.bukkit.inventory.meta.ItemMeta.class, "setMaxStackSize",
                        MethodType.methodType(void.class, int.class));
            } catch (NoSuchMethodException | IllegalAccessException ignored) {}
        }
        SET_MAX_STACK_SIZE = h;
    }

    /** {@link #isSimilar} の通常アイテムパスで使う参照アイテム（遅延初期化）。 */
    private ItemStack cachedReference;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * 物理看板ブロックのテキスト行から StorageSign を生成する。
     *
     * @param lines 看板の 4 行（{@code Sign#getSide(FRONT).getLines()} の戻り値）
     * @return パース結果。有効な StorageSign でない場合は {@code null}。
     */
    public static StorageSign fromSignLines(String[] lines) {
        if (lines == null || lines.length < 3) return null;
        if (!HEADER_LINE.equals(lines[0]))     return null;

        String identifier = lines[1].trim();
        // 行 1 が空白（旧版では残量 0 時に "" を書き込む）または "Empty"（新形式）なら空 SS
        if (identifier.isBlank() || EMPTY_MARKER.equals(identifier)) {
            return empty();
        }

        int amount = 0;
        if (lines.length >= 3 && !lines[2].isBlank()) {
            try { amount = Integer.parseInt(lines[2].trim()); }
            catch (NumberFormatException ignored) {}
        }

        return parseIdentifier(identifier, amount);
    }

    /**
     * Sign ブロックから直接 StorageSign を生成する。
     *
     * @return パース結果。StorageSign でない場合は {@code null}。
     */
    public static StorageSign fromBlock(Block block) {
        if (block == null) return null;
        if (!MaterialRegistry.isAnySign(block.getType())) return null;
        if (!(block.getState() instanceof Sign sign)) return null;
        return fromSign(sign);
    }

    /**
     * 取得済みの {@link Sign} ブロック状態から StorageSign を生成する。
     * 呼び出し元が既に Sign を保持している場合に使用し、{@code getState()} の二重呼び出しを避ける。
     *
     * @return パース結果。有効な StorageSign でない場合は {@code null}。
     */
    public static StorageSign fromSign(Sign sign) {
        if (sign == null) return null;
        String[] lines = sign.getSide(Side.FRONT).getLines();
        return fromSignLines(lines);
    }

    /**
     * ItemStack から StorageSign を生成する。
     *
     * @return パース結果。StorageSign アイテムでない場合は {@code null}。
     */
    public static StorageSign fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        if (!HEADER_LINE.equals(meta.getDisplayName())) return null;

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return null;

        String loreLine = lore.get(0).trim();
        if (EMPTY_MARKER.equals(loreLine)) {
            return empty();
        }

        // Lore 形式: "{識別子} {数量}"
        int sep = loreLine.lastIndexOf(' ');
        if (sep < 0) return null;

        String identifier = loreLine.substring(0, sep).trim();
        int amount = 0;
        try { amount = Integer.parseInt(loreLine.substring(sep + 1).trim()); }
        catch (NumberFormatException ignored) {}

        return parseIdentifier(identifier, amount);
    }

    // ── Static factory helpers ─────────────────────────────────────────────────

    /** アイテム未登録の空 StorageSign を返す。 */
    public static StorageSign empty() {
        return new StorageSign(Material.AIR, (short) 0, 0, null, null, true);
    }

    // ── パース処理 ─────────────────────────────────────────────────────────────────

    /**
     * アイテム識別子文字列を StorageSign インスタンスに変換する。
     * これがデシリアライズの中核メソッド。
     */
    private static StorageSign parseIdentifier(String identifier, int amount) {
        if (identifier == null || identifier.isBlank()) return null;

        // ── 看板アイテム: "OakStorageSign", "SpruceStorageSign" など ───────────
        Material signMat = LegacyNameRegistry.NAME_TO_MATERIAL.get(identifier);
        if (signMat != null) {
            return new StorageSign(signMat, DAMAGE_SS_ITEM, amount, null, null, false);
        }

        // ── 仮想識別子（config / デフォルト互換テーブル）────────────────────────────
        StorageSign virtualSign = parseVirtualIdentifier(identifier, amount);
        if (virtualSign != null) {
            return virtualSign;
        }

        // ── 特殊アイテム（現時点では不吉なビン）──────────────────────────────────────
        // OMINOUS_BOTTLE はバニラアイテムだが、識別子/アンプリファイア形式に
        // 専用の互換ロジックが必要なためここで処理する。
        Material specialMaterial = SpecialCaseItemSupport.materialFromIdentifier(identifier);
        if (specialMaterial != null) {
            short specialDamage = SpecialCaseItemSupport.parseDamageFromIdentifier(identifier);
            return new StorageSign(specialMaterial, specialDamage, amount, null, null, false);
        }

        // ── エンチャント本（新形式）: "ENCHBOOK:sharp:5" ──────────────────────────
        if (identifier.startsWith("ENCHBOOK:")) {
            String[] parts = identifier.split(":");
            if (parts.length < 3) return null;
            Enchantment ench = EnchantHelper.fromPrefix(parts[1]);
            short level = 0;
            try { level = Short.parseShort(parts[2]); }
            catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "parseIdentifier", "エンチャントレベルが不正: {0}", identifier);
            }
            return new StorageSign(Material.ENCHANTED_BOOK, level, amount, null, ench, false);
        }

        // ── ポーション: "POTION:HEAL:0", "SPOTION:REGEN:1", "LPOTION:HEAL:2" ───
        if (identifier.contains("POTION:")) {
            int potionIdx = identifier.indexOf("POTION:");
            String prefix = identifier.substring(0, potionIdx); // "", "S", "L"
            Material potMat = PotionHelper.materialFromPrefix(prefix);
            String rest = identifier.substring(potionIdx + "POTION:".length());
            String[] parts = rest.split(":");
            if (parts.length < 2) return null;
            String shortName = PotionHelper.normalizeName(parts[0]);
            String code = parts[1];
            PotionType pot = PotionHelper.fromSignText(shortName, code);
            short damage = 0;
            try { damage = Short.parseShort(code); }
            catch (NumberFormatException ignored) {}
            return new StorageSign(potMat, damage, amount, pot, null, false);
        }

        // ── 通常アイテム: "STONE", "STONE:0"（レガシー名前解決あり）──────────────
        String[] parts = identifier.split(":");
        String matName = parts[0].toUpperCase();
        short damage = 0;
        if (parts.length >= 2) {
            try { damage = Short.parseShort(parts[1]); }
            catch (NumberFormatException e) {
                // parts[1] が数値でない → 旧形式 "ENCHANTED_BOOK:fire_protection:3" の可能性
                if (matName.equals("ENCHANTED_BOOK") && parts.length >= 3) {
                    Enchantment ench = EnchantHelper.fromPrefix(parts[1]);
                    short level = 0;
                    try { level = Short.parseShort(parts[2]); }
                    catch (NumberFormatException ignored2) {}
                    return new StorageSign(Material.ENCHANTED_BOOK, level, amount, null, ench, false);
                }
                // 未知の非数値サブタイプ → damage を 0 として扱う
            }
        }

        // config / デフォルトテーブルのエイリアスを解決してからマテリアル直接検索。
        Material mat = resolveMaterialFromIdentifierToken(matName);
        if (mat == null) {
            LOG.log(Level.WARNING, "parseIdentifier", "StorageSign 識別子に未知のマテリアル: {0}", identifier);
            return null;
        }

        return new StorageSign(mat, damage, amount, null, null, false);
    }

    // ── プライベートコンストラクタ ──────────────────────────────────────────────────

    private StorageSign(Material material, short damage, int amount,
                        PotionType potionType, Enchantment enchantment, boolean isUnregistered) {
        this.material    = material;
        this.damage      = damage;
        this.amount      = amount;
        this.potionType  = potionType;
        this.enchantment = enchantment;
        this.unregistered = isUnregistered;
    }

    // ── アクセサ ────────────────────────────────────────────────────────────────

    public Material getMaterial() { return material; }
    public short    getDamage()   { return damage;   }
    public int      getAmount()   { return amount;   }
    public boolean  isUnregistered() { return unregistered; }

    public PotionType  getPotionType()  { return potionType;  }
    public Enchantment getEnchantment() { return enchantment; }
    public boolean isSignAsItem()       { return LegacyNameRegistry.MATERIAL_TO_NAME.containsKey(material)
                                                 && damage == DAMAGE_SS_ITEM; }

    public void setAmount(int amount) {
        this.amount = amount;
        if (amount <= 0) {
            this.amount = 0;
            // 設定で unregister-on-empty が有効な場合、数量 0 で登録解除状態に移行する
            if (ConfigLoader.getUnregisterOnEmpty()) {
                this.unregistered = true;
            }
        }
    }

    // ── 派生ヘルパー ──────────────────────────────────────────────────────────────

    /**
     * アイテム識別子文字列を返す。看板の行 1 またはアイテム Lore に保存される値。
     */
    public String getIdentifier() {
        if (unregistered) return EMPTY_MARKER;

        // 看板アイテム（sign-in-sign）
        String signName = LegacyNameRegistry.MATERIAL_TO_NAME.get(material);
        if (signName != null && damage == DAMAGE_SS_ITEM) return signName;

        // 仮想識別子（レガシー / 管理者定義の互換マーカー）
        String virtualIdentifier = resolveVirtualIdentifier(material, damage);
        if (virtualIdentifier != null) return virtualIdentifier;

        // 特殊アイテム
        String specialIdentifier = SpecialCaseItemSupport.toIdentifier(material, damage);
        if (specialIdentifier != null) return specialIdentifier;

        // エンチャント本
        if (material == Material.ENCHANTED_BOOK && enchantment != null) {
            return "ENCHBOOK:" + EnchantHelper.toShortKey(enchantment) + ":" + damage;
        }

        // ポーション
        if (MaterialRegistry.POTION_MATERIALS.contains(material) && potionType != null) {
            return PotionHelper.getMaterialPrefix(material)
                   + "POTION:" + PotionHelper.getShortName(potionType)
                   + ":" + PotionHelper.getEnhanceCode(potionType);
        }

        // 通常アイテム
        if (damage != 0) return material + ":" + damage;
        return material.toString();
    }

    /**
     * 物理看板ブロック用の 4 行テキストを生成する。
     */
    public String[] getSignLines() {
        // 空（未登録）のとき行 1 は空文字列（旧版の getShortName() が "" を返すのと同じ）
        String identifier = unregistered ? "" : getIdentifier();
        int lc = amount / 3456;
        int rem = amount % 3456;
        int stacks = rem / 64;
        int singles = rem % 64;
        String summary = lc + "LC " + stacks + "s " + singles;
        return new String[]{ HEADER_LINE, identifier, String.valueOf(amount), summary };
    }

    /**
     * StorageSign アイテムの Lore（行 0）に保存する文字列を生成する。
     */
    public String getLoreText() {
        if (unregistered) return EMPTY_MARKER;
        return getIdentifier() + " " + amount;
    }

    /**
     * インベントリドロップ・出力用の StorageSign アイテム（表示名 + Lore）を生成する。
     */
    public static ItemStack createStorageSignItem(Material signMaterial, String loreText, int amount) {
        ItemStack ssItem = new ItemStack(signMaterial, Math.max(1, amount));
        ItemMeta meta = ssItem.getItemMeta();
        if (meta == null) return ssItem;

        meta.setDisplayName(HEADER_LINE);
        meta.setLore(List.of(loreText));
        applyConfiguredMaxStack(meta);
        ssItem.setItemMeta(meta);
        return ssItem;
    }

    private static void applyConfiguredMaxStack(ItemMeta meta) {
        if (SET_MAX_STACK_SIZE == null) return;
        // JIT 後は MethodHandle.invoke() は実質的な直接呼び出しと同等 — リフレクションのオーバーヘッドなし。
        int configured = Math.max(1, ConfigLoader.getMaxStackSize());
        try {
            SET_MAX_STACK_SIZE.invoke(meta, (Integer) configured);
        } catch (Throwable ignored) {}
    }

    /**
     * この StorageSign のデータを既存の看板ブロックに書き込む。
     */
    public void applyToSign(Sign sign) {
        // getSignLines() をインライン展開し、看板書き込みのたびに String[] を確保しない。
        String identifier = unregistered ? "" : getIdentifier();
        int lc = amount / 3456;
        int rem = amount % 3456;
        int stacks = rem / 64;
        int singles = rem % 64;
        var front = sign.getSide(Side.FRONT);
        front.setLine(0, HEADER_LINE);
        front.setLine(1, identifier);
        front.setLine(2, String.valueOf(amount));
        front.setLine(3, lc + "LC " + stacks + "s " + singles);
        sign.update();
    }

    /**
     * 保管中のアイテム種別・サブタイプに合致した ItemStack を生成する。
     *
     * @param requestedAmount 生成個数（最大スタックサイズにクランプ）
     * @return 対応する ItemStack。マテリアルが不明な場合は {@code null}。
     */
    public ItemStack getContents(int requestedAmount) {
        if (unregistered || material == null || material == Material.AIR) return null;

        // ── レガシー END_PORTAL マーカーアイテム ──────────────────────────────────
        if (material == Material.END_PORTAL) {
            if (damage == DAMAGE_SS_ITEM) {
                String markerName = resolveVirtualIdentifier(material, damage);
                if (markerName == null) markerName = "HorseEgg";
                return createLegacyMarkerItem(Math.min(requestedAmount, 1), markerName);
            }
            return createStorageSignItem(Material.OAK_SIGN, EMPTY_MARKER, Math.min(requestedAmount, 1));
        }

        // ── 看板マテリアル ────────────────────────────────────────────────────────
        if (MaterialRegistry.SIGN_MATERIALS.contains(material)) {
            if (damage == DAMAGE_SS_ITEM) {
                return createStorageSignItem(material, EMPTY_MARKER, Math.min(requestedAmount, material.getMaxStackSize()));
            }
            return new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
        }

        // ── 特殊アイテム ──────────────────────────────────────────────────────────
        ItemStack specialItem = SpecialCaseItemSupport.toContents(material, damage, requestedAmount);
        if (specialItem != null) {
            return specialItem;
        }

        // ── エンチャント本 ─────────────────────────────────────────────────────────
        if (material == Material.ENCHANTED_BOOK && enchantment != null) {
            ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(enchantment, damage, true);
                item.setItemMeta(meta);
            }
            return item;
        }

        // ── ポーション ─────────────────────────────────────────────────────────────
        if (MaterialRegistry.POTION_MATERIALS.contains(material) && potionType != null) {
            ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(potionType);
                item.setItemMeta(meta);
            }
            return item;
        }

        // ── 白バナー（レイドバナー — 特殊 NBT が必要）──────────────────────────────
        if (material == Material.WHITE_BANNER && damage == 8) {
            BannerMeta bannerMeta = StorageSignPlugin.getOminousBannerMeta();
            if (bannerMeta != null) {
                ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
                if (!item.setItemMeta(bannerMeta.clone())) {
                    LOG.warning("getContents", "不吉なバナーのメタを ItemStack に適用できませんでした");
                    return null;
                }
                return item;
            }
            LOG.warning("getContents", "不吉なバナーのメタが null — レイドバナーを再構築できません");
            return null;
        }

        // ── 打ち上げ花火（power を damage に保管; power=1 → damage=0）────────────
        if (material == Material.FIREWORK_ROCKET) {
            ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
            // damage フィールドに花火 power を保管; 21.4 互換のため power=1 は damage=0 のまま保持。
            if (damage > 1 && item.getItemMeta() instanceof FireworkMeta fireworkMeta) {
                fireworkMeta.setPower(damage);
                item.setItemMeta(fireworkMeta);
            }
            return item;
        }

        // ── 通常アイテム ──────────────────────────────────────────────────────────
        ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
        if (damage != 0 && item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(damage);
            item.setItemMeta(damageable);
        }
        return item;
    }

    // ── 静的ヘルパー ──────────────────────────────────────────────────────────────

    /** 指定ブロックが有効な StorageSign なら {@code true} を返す。 */
    public static boolean isStorageSign(Block block) {
        return fromBlock(block) != null;
    }

    /** 指定アイテムが StorageSign アイテムなら {@code true} を返す。 */
    public static boolean isStorageSign(ItemStack item) {
        return fromItemStack(item) != null;
    }

    /**
     * {@code item} がこの StorageSign に保管されているアイテム種別と一致すれば {@code true} を返す。
     *
     * <p>ほとんどのアイテムは {@link ItemStack#isSimilar} に委譲する。特殊ケース:
     * <ul>
     *   <li>ブロックエンティティデータアイテム (BEE_NEST, BEEHIVE): マテリアルのみで比較。</li>
     *   <li>エンチャント本: エンチャント種別とレベルで比較。</li>
     *   <li>ポーション: PotionType とマテリアル（通常/スプラッシュ/残留）で比較。</li>
     *   <li>不吉なビン: アンプリファイアで比較。</li>
     *   <li>パターン 8 枚の白バナー: ロード済みの不吉なバナーメタと比較。</li>
     * </ul>
     */
    public boolean isSimilar(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // HorseEgg レガシーマーカー
        if (material == Material.END_PORTAL && damage == DAMAGE_SS_ITEM) {
            ItemMeta horseMeta = item.getItemMeta();
            String markerName = resolveVirtualIdentifier(material, damage);
            if (markerName == null) markerName = "HorseEgg";
            return item.getType() == LEGACY_MARKER_ITEM_MATERIAL
                && horseMeta != null
                && markerName.equals(horseMeta.getDisplayName())
                && horseMeta.hasLore();
        }

        if (item.getType() != material) return false;

        ItemMeta meta = item.getItemMeta();

        // BEE_NEST / BEEHIVE — マテリアルのみで比較
        if (MaterialRegistry.BLOCK_ENTITY_DATA_MATERIALS.contains(material)) return true;

        // 特殊アイテム
        Boolean specialSimilarity = SpecialCaseItemSupport.isSimilar(material, meta, damage);
        if (specialSimilarity != null) {
            return specialSimilarity;
        }

        // エンチャント本
        if (material == Material.ENCHANTED_BOOK) {
            if (!(meta instanceof EnchantmentStorageMeta esm)) return false;
            if (enchantment == null) return false;
            return esm.hasStoredEnchant(enchantment) && esm.getStoredEnchantLevel(enchantment) == damage;
        }

        // ポーション
        if (MaterialRegistry.POTION_MATERIALS.contains(material)) {
            if (!(meta instanceof PotionMeta pm)) return false;
            return potionType != null && potionType.equals(pm.getBasePotionType());
        }

        // 白バナー（レイドバナー）
        if (material == Material.WHITE_BANNER && damage == 8) {
            if (!(meta instanceof BannerMeta bm)) return false;
            BannerMeta ominous = StorageSignPlugin.getOminousBannerMeta();
            if (ominous == null) return false;
            if (bm.equals(ominous)) return true;
            // バージョン差分でコンポーネント表現が変わっても、実パターン一致なら互換として許容する。
            return bm.numberOfPatterns() == ominous.numberOfPatterns()
                && bm.getPatterns().equals(ominous.getPatterns());
        }

        // 看板アイテム: damage=1 のときは看板マテリアルのみ対象とし、ダメージ値が偶然
        // 1 になるツール・防具・STONE_SLAB 等との誤検出を防ぐ。
        if (damage == DAMAGE_SS_ITEM && MaterialRegistry.SIGN_MATERIALS.contains(material)) {
            // 旧版の動作: 同種看板の空 StorageSign アイテムのみ受け入れる。
            StorageSign itemSS = fromItemStack(item);
            return itemSS != null && itemSS.isUnregistered();
        }

        // 空のシュルカーボックスはマテリアル等価の空状態で比較する。
        if (MaterialRegistry.SHULKER_BOX_MATERIALS.contains(material)
            && meta instanceof BlockStateMeta bsm
            && bsm.getBlockState() instanceof ShulkerBox shulker
            && shulker.getInventory().isEmpty()) {
            item = new ItemStack(item.getType());
        }

        // 通常アイテム — Bukkit の isSimilar に委譲。
        // 遅延キャッシュを使い、呼び出しのたびに ItemStack を確保するのを避ける。
        if (cachedReference == null) cachedReference = getContents(1);
        return cachedReference != null && cachedReference.isSimilar(item);
    }

    /**
     * 指定 ItemStack のメタデータから StorageSign を生成する。
     * アイテムを「保管対象」として扱い（StorageSign アイテムとしてではなく）、
     * 新規登録時に使用する。
     *
     * @return amount=0 の新規 StorageSign。保管できないアイテム種別の場合は {@code null}。
     */
    public static StorageSign fromStoredItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        Material mat = item.getType();
        ItemMeta meta = item.getItemMeta();

        // StorageSign アイテム（sign-in-sign）
        if (isStorageSign(item) && MaterialRegistry.SIGN_MATERIALS.contains(mat)) {
            return new StorageSign(mat, DAMAGE_SS_ITEM, 0, null, null, false);
        }

        // レガシーマーカーアイテム（デフォルト: HorseEgg）→ 仮想識別子のバッキングマテリアル
        if (mat == LEGACY_MARKER_ITEM_MATERIAL && meta != null && meta.hasLore()) {
            String markerName = meta.getDisplayName();
            if (markerName == null || markerName.isBlank()) return null;
            StorageSign parsed = parseVirtualIdentifier(markerName, 0);
            if (parsed == null) return null;
            if (parsed.material != Material.END_PORTAL || parsed.damage != DAMAGE_SS_ITEM) return null;
            return new StorageSign(Material.END_PORTAL, DAMAGE_SS_ITEM, 0, null, null, false);
        }

        // 特殊アイテム
        Short specialDamage = SpecialCaseItemSupport.fromStoredItem(mat, meta);
        if (specialDamage != null) {
            return new StorageSign(mat, specialDamage, 0, null, null, false);
        }

        // エンチャント本
        if (mat == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
            Map<Enchantment, Integer> stored = esm.getStoredEnchants();
            if (stored.size() != 1) return null;  // エンチャントが複数付いている本は保管不可
            Map.Entry<Enchantment, Integer> entry = stored.entrySet().iterator().next();
            Enchantment ench = entry.getKey();
            short level = entry.getValue().shortValue();
            return new StorageSign(mat, level, 0, null, ench, false);
        }

        // ポーション
        if (MaterialRegistry.POTION_MATERIALS.contains(mat) && meta instanceof PotionMeta pm) {
            PotionType pot = pm.getBasePotionType();
            short damage = (short) (PotionHelper.getEnhanceCode(pot).charAt(0) - '0');
            return new StorageSign(mat, damage, 0, pot, null, false);
        }

        // 白バナー（レイドバナー）
        if (mat == Material.WHITE_BANNER) {
            // WHITE_BANNER でメタ自体を取得できない場合は、無地の白旗として
            // 誤登録せず fail closed にする。
            if (!(meta instanceof BannerMeta bm)) return null;
            if (StorageSignPlugin.isOminousBannerMeta(bm)) {
                StorageSignPlugin.setOminousBannerMeta((BannerMeta) bm.clone());
                return new StorageSign(mat, (short) 8, 0, null, null, false);
            }
        }

        // 打ち上げ花火（power を damage に保管、power=1 は互換のため 0）
        if (mat == Material.FIREWORK_ROCKET && meta instanceof FireworkMeta fireworkMeta) {
            int power = fireworkMeta.getPower();
            short encoded = (short) (power > 1 ? power : DAMAGE_FIREWORK_ZERO);
            return new StorageSign(mat, encoded, 0, null, null, false);
        }

        // 耐久値を持つ通常アイテム（耐久値を保持する）
        if (meta instanceof Damageable damageable) {
            return new StorageSign(mat, (short) damageable.getDamage(), 0, null, null, false);
        }

        // その他の通常アイテム
        return new StorageSign(mat, (short) 0, 0, null, null, false);
    }

    private static ItemStack createLegacyMarkerItem(int amount, String markerName) {
        ItemStack markerItem = new ItemStack(LEGACY_MARKER_ITEM_MATERIAL, Math.max(1, amount));
        ItemMeta meta = markerItem.getItemMeta();
        if (meta == null) return markerItem;
        meta.setDisplayName(markerName);
        meta.setLore(List.of(EMPTY_MARKER));
        markerItem.setItemMeta(meta);
        return markerItem;
    }

    private static StorageSign parseVirtualIdentifier(String identifier, int amount) {
        String spec = ConfigLoader.getVirtualItemIdentifiers().get(identifier);
        if (spec == null) {
            spec = DEFAULT_VIRTUAL_IDENTIFIERS.get(identifier);
        }
        if (spec == null || spec.isBlank()) return null;

        String[] specParts = spec.split(":", 2);
        String materialToken = specParts[0].trim();
        Material specMaterial = Material.matchMaterial(materialToken);
        if (specMaterial == null) return null;

        short specDamage = 0;
        if (specParts.length >= 2) {
            try {
                specDamage = Short.parseShort(specParts[1].trim());
            } catch (NumberFormatException ignored) {
                specDamage = 0;
            }
        }

        return new StorageSign(specMaterial, specDamage, amount, null, null, false);
    }

    private static Material resolveMaterialFromIdentifierToken(String token) {
        if (token == null || token.isBlank()) return null;

        String normalized = token.trim().toUpperCase();

        String configuredAlias = ConfigLoader.getIdentifierAliases().get(normalized);
        if (configuredAlias == null) {
            configuredAlias = ConfigLoader.getIdentifierAliases().get(token.trim());
        }
        if (configuredAlias != null && !configuredAlias.isBlank()) {
            Material configured = Material.matchMaterial(configuredAlias.trim());
            if (configured != null) return configured;
        }

        String defaultAlias = DEFAULT_IDENTIFIER_ALIASES.get(normalized);
        if (defaultAlias != null) {
            Material legacy = Material.matchMaterial(defaultAlias);
            if (legacy != null) return legacy;
        }

        return Material.matchMaterial(normalized);
    }

    private static String resolveVirtualIdentifier(Material material, short damage) {
        for (Entry<String, String> entry : ConfigLoader.getVirtualItemIdentifiers().entrySet()) {
            if (matchesVirtualSpec(material, damage, entry.getValue())) {
                return entry.getKey();
            }
        }

        for (Entry<String, String> entry : DEFAULT_VIRTUAL_IDENTIFIERS.entrySet()) {
            if (matchesVirtualSpec(material, damage, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean matchesVirtualSpec(Material material, short damage, String spec) {
        if (spec == null || spec.isBlank()) return false;
        String[] specParts = spec.split(":", 2);
        Material specMaterial = Material.matchMaterial(specParts[0].trim());
        if (specMaterial == null || specMaterial != material) return false;

        short specDamage = 0;
        if (specParts.length >= 2) {
            try {
                specDamage = Short.parseShort(specParts[1].trim());
            } catch (NumberFormatException ignored) {
                specDamage = 0;
            }
        }
        return specDamage == damage;
    }

    @Override
    public String toString() {
        return "StorageSign{material=" + material + ", damage=" + damage
               + ", amount=" + amount + ", identifier=" + getIdentifier() + "}";
    }
}
