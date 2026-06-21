package storagesign.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * 関連素材の動的に計算されたセットを提供する。
 *
 * <p>ハードコード化された EnumSet と異なり、クラスロード時に命名規則に従って
 * {@link Material#values()} から導出する。
 * Minecraft が新しい看板バリエントやシュルカーボックスの色を追加しても
 * コード変更なしに自動対応する。
 */
public final class MaterialRegistry {

    // ── 立て看板素材（OAK_SIGN, SPRUCE_SIGN, …） ─────────────────────
    public static final Set<Material> SIGN_MATERIALS;

    // ── 壁付き看板素材（OAK_WALL_SIGN, OAK_WALL_HANGING_SIGN, …） ─────────
    public static final Set<Material> WALL_SIGN_MATERIALS;

    // ── 壁付き看板 → 立て看板のルックアップ（OAK_WALL_SIGN → OAK_SIGN, …） ─────
    public static final Map<Material, Material> WALL_TO_SIGN;

    // ── シュルカーボックス素材（SHULKER_BOX, WHITE_SHULKER_BOX, …） ────────────
    public static final Set<Material> SHULKER_BOX_MATERIALS;

    // ── ポーション素材（MC バージョン間で安定; 変更なしに揳われる） ──────────
    public static final Set<Material> POTION_MATERIALS = Collections.unmodifiableSet(
        EnumSet.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION)
    );

    // ── Materials whose block-entity data makes isSimilar() unreliable ────────
    public static final Set<Material> BLOCK_ENTITY_DATA_MATERIALS = Collections.unmodifiableSet(
        EnumSet.of(Material.BEE_NEST, Material.BEEHIVE)
    );

    static {
        // HashSet のハッシュ計算より EnumSet.contains() のビットマスク演算（~1 ns）の方が高速。
        // ホッパーイベントごとに各面でチェックされるため、負荷が高い場合に有利。
        EnumSet<Material> signMats = EnumSet.noneOf(Material.class);
        EnumSet<Material> wallSignMats = EnumSet.noneOf(Material.class);
        EnumSet<Material> shulkerMats = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            String name = m.name();
            if (name.endsWith("_SIGN") && !name.contains("_WALL_")) signMats.add(m);
            else if (name.endsWith("_WALL_SIGN") || name.endsWith("_WALL_HANGING_SIGN")) {
                wallSignMats.add(m);
            }
            if (name.endsWith("SHULKER_BOX"))                        shulkerMats.add(m);
        }
        SIGN_MATERIALS      = Collections.unmodifiableSet(signMats);
        WALL_SIGN_MATERIALS = Collections.unmodifiableSet(wallSignMats);
        SHULKER_BOX_MATERIALS = Collections.unmodifiableSet(shulkerMats);

        Map<Material, Material> wallToSign = new EnumMap<>(Material.class);
        for (Material wall : WALL_SIGN_MATERIALS) {
            String standingName = toStandingSignName(wall.name());
            Material standing = Material.matchMaterial(standingName);
            if (standing != null) {
                wallToSign.put(wall, standing);
            }
        }
        WALL_TO_SIGN = Collections.unmodifiableMap(wallToSign);
    }

    /** Returns true if the material is any kind of sign block (standing or wall-mounted). */
    public static boolean isAnySign(Material material) {
        return SIGN_MATERIALS.contains(material) || WALL_SIGN_MATERIALS.contains(material);
    }

    /** 看板ブロック素材に対応するインベントリアイテム素材を返す。 */
    public static Material toItemSignMaterial(Material material) {
        if (material == null) return null;
        return WALL_TO_SIGN.getOrDefault(material, material);
    }

    private static String toStandingSignName(String wallName) {
        if (wallName.endsWith("_WALL_HANGING_SIGN")) {
            return wallName.replace("_WALL_HANGING_SIGN", "_HANGING_SIGN");
        }
        return wallName.replace("_WALL_SIGN", "_SIGN");
    }

    private MaterialRegistry() {}
}
