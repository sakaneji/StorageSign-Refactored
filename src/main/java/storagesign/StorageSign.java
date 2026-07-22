package storagesign;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.block.sign.Side;

import storagesign.compat.SignDisplayFormatter;
import storagesign.item.PotionHelper;
import storagesign.registry.LegacyNameRegistry;
import storagesign.registry.MaterialRegistry;
import storagesign.logging.PluginLogger;

/**
 * StorageSign のデータモデル。
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

    static final PluginLogger LOG = PluginLogger.getLogger(StorageSign.class);

    public static final String HEADER_LINE  = "StorageSign";
    public static final String EMPTY_MARKER = "Empty";
    private static final NamespacedKey POTION_IDENTIFIER_KEY =
        new NamespacedKey("storagesign", "potion_identifier");
    private static final NamespacedKey CANONICAL_IDENTIFIER_KEY =
        new NamespacedKey("storagesign", "storage_identifier");

    // ── 特殊レガシー値 ─────────────────────────────────────────────────────────────
    static final short DAMAGE_SS_ITEM   = 1;  // 看板/旧ウマの卵をアイテムとして保管する際の damage フラグ
    static final short DAMAGE_FIREWORK_ZERO = 0;  // firework power=1 を damage=0 として保管

    // ── 互換性デフォルト（config.yml で上書き可能）──────────────────────────────────
    static final Map<String, String> DEFAULT_IDENTIFIER_ALIASES = Map.ofEntries(
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

    static final Map<String, String> DEFAULT_VIRTUAL_IDENTIFIERS = Map.ofEntries(
        Map.entry("EmptySign", "OAK_SIGN:1"),
        Map.entry("HorseEgg", "END_PORTAL:1")
    );

    static final Material LEGACY_MARKER_ITEM_MATERIAL = Material.GHAST_SPAWN_EGG;

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
    static final java.lang.invoke.MethodHandle SET_MAX_STACK_SIZE;
    static {
        java.lang.invoke.MethodHandle h = null;
        try {
            // ボックス化 Integer（Paper API）を先に試み、失敗したらプリミティブ int にフォールバック。
            h = java.lang.invoke.MethodHandles.publicLookup().findVirtual(
                    org.bukkit.inventory.meta.ItemMeta.class, "setMaxStackSize",
                    java.lang.invoke.MethodType.methodType(void.class, Integer.class));
        } catch (NoSuchMethodException | IllegalAccessException e1) {
            try {
                h = java.lang.invoke.MethodHandles.publicLookup().findVirtual(
                        org.bukkit.inventory.meta.ItemMeta.class, "setMaxStackSize",
                        java.lang.invoke.MethodType.methodType(void.class, int.class));
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
        return StorageSignIdentifierCodec.fromSignLines(lines);
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
        return StorageSignIdentifierCodec.fromSign(sign);
    }

    /**
     * ItemStack から StorageSign を生成する。
     *
     * @return パース結果。StorageSign アイテムでない場合は {@code null}。
     */
    public static StorageSign fromItemStack(ItemStack item) {
        return StorageSignIdentifierCodec.fromItemStack(item);
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
    // ── プライベートコンストラクタ ──────────────────────────────────────────────────

    StorageSign(Material material, short damage, int amount,
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
        return StorageSignIdentifierCodec.getIdentifier(this);
    }

    /** Physical-sign label. The complete identifier is persisted in the sign PDC. */
    String getDisplayIdentifier() {
        return getDisplayIdentifier(null);
    }

    String getDisplayIdentifier(Material signMaterial) {
        return unregistered ? "" : SignDisplayFormatter.fit(getIdentifier(), signMaterial);
    }

    /**
     * 物理看板ブロック用の 4 行テキストを生成する。
     */
    public String[] getSignLines() {
        return getSignLines(null);
    }

    /**
     * 物理看板ブロック用の 4 行テキストを生成する。
     *
     * @param signMaterial 表示先の看板素材。つり看板は通常看板より短い幅で省略する。
     */
    public String[] getSignLines(Material signMaterial) {
        // 空（未登録）のとき行 1 は空文字列（旧版の getShortName() が "" を返すのと同じ）
        String identifier = getDisplayIdentifier(signMaterial);
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

    /** PDCへ保存する完全なPotion識別子。Potion以外はnull。 */
    public String getCanonicalPotionIdentifier() {
        if (unregistered || potionType == null || !MaterialRegistry.POTION_MATERIALS.contains(material)) {
            return null;
        }
        return PotionHelper.toCanonicalIdentifier(material, potionType);
    }

    /**
     * インベントリドロップ・出力用の StorageSign アイテム（表示名 + Lore）を生成する。
     */
    public static ItemStack createStorageSignItem(Material signMaterial, String loreText, int amount) {
        return StorageSignItemCodec.createStorageSignItem(signMaterial, loreText, amount);
    }

    /** StorageSignモデルからLoreと正規Potion PDCを同時に生成する。 */
    public static ItemStack createStorageSignItem(Material signMaterial, StorageSign contents, int amount) {
        return StorageSignItemCodec.createStorageSignItem(signMaterial, contents, amount);
    }

    /**
     * この StorageSign のデータを既存の看板ブロックに書き込む。
     */
    public void applyToSign(Sign sign) {
        StorageSignBlockWriter.applyToSign(this, sign);
    }

    /**
     * 保管中のアイテム種別・サブタイプに合致した ItemStack を生成する。
     *
     * @param requestedAmount 生成個数（最大スタックサイズにクランプ）
     * @return 対応する ItemStack。マテリアルが不明な場合は {@code null}。
     */
    public ItemStack getContents(int requestedAmount) {
        return StorageSignItemCodec.getContents(this, requestedAmount);
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
        if (cachedReference == null) cachedReference = getContents(1);
        return StorageSignItemCodec.isSimilar(this, item, cachedReference);
    }

    /**
     * 指定 ItemStack のメタデータから StorageSign を生成する。
     * アイテムを「保管対象」として扱い（StorageSign アイテムとしてではなく）、
     * 新規登録時に使用する。
     *
     * @return amount=0 の新規 StorageSign。保管できないアイテム種別の場合は {@code null}。
     */
    public static StorageSign fromStoredItem(ItemStack item) {
        return StorageSignItemCodec.fromStoredItem(item);
    }

    static StorageSign parseIdentifier(String identifier, int amount) {
        return StorageSignIdentifierCodec.parseIdentifier(identifier, amount);
    }

    static StorageSign parseVirtualIdentifier(String identifier, int amount) {
        return StorageSignIdentifierCodec.parseVirtualIdentifier(identifier, amount);
    }

    static Material resolveMaterialFromIdentifierToken(String token) {
        return StorageSignIdentifierCodec.resolveMaterialFromIdentifierToken(token);
    }

    static String resolveVirtualIdentifier(Material material, short damage) {
        return StorageSignIdentifierCodec.resolveVirtualIdentifier(material, damage);
    }

    static Integer parseStoredAmount(String value) {
        return StorageSignIdentifierCodec.parseStoredAmount(value);
    }

    private static boolean matchesVirtualSpec(Material material, short damage, String spec) {
        return StorageSignIdentifierCodec.matchesVirtualSpec(material, damage, spec);
    }

    private static StorageSign ifExactlyRestorable(ItemStack original, StorageSign candidate) {
        return StorageSignItemCodec.ifExactlyRestorable(original, candidate);
    }

    private static ItemStack createLegacyMarkerItem(int amount, String markerName) {
        return StorageSignItemCodec.createLegacyMarkerItem(amount, markerName);
    }

    private static void applyConfiguredMaxStack(ItemMeta meta) {
        StorageSignItemCodec.applyConfiguredMaxStack(meta);
    }

    @Override
    public String toString() {
        return "StorageSign{material=" + material + ", damage=" + damage
               + ", amount=" + amount + ", identifier=" + getIdentifier() + "}";
    }

    static NamespacedKey potionIdentifierKey() {
        return POTION_IDENTIFIER_KEY;
    }

    static NamespacedKey canonicalPotionIdentifierKey() {
        return CANONICAL_IDENTIFIER_KEY;
    }
}
