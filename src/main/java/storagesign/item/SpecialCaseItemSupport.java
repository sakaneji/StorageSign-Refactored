package storagesign.item;

import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import storagesign.logging.PluginLogger;

/**
 * 特殊ケース処理が必要なアイテムタイプのサポートメソッド群。
 *
 * <p>このクラスにまとめることで、{@code StorageSign} の条件分岐を増やさずに
 * 例外アイテムの追加・削除がしやすい。
 */
public final class SpecialCaseItemSupport {

    private static final PluginLogger LOG = PluginLogger.getLogger(SpecialCaseItemSupport.class);

    private static final String OMINOUS_BOTTLE_PREFIX = "OMINOUS_BOTTLE:";

    private SpecialCaseItemSupport() {}

    /** 存在する場合、指定イデンティファイアが特殊ケースアイテムかどうかを返す。 */
    public static boolean isSpecialIdentifier(String identifier) {
        return identifier != null && identifier.startsWith(OMINOUS_BOTTLE_PREFIX);
    }

    /** 特殊ケースイデンティファイアが表す素材を返す。非特殊ケースの場合は {@code null}。 */
    public static Material materialFromIdentifier(String identifier) {
        if (isSpecialIdentifier(identifier)) {
            return Material.OMINOUS_BOTTLE;
        }
        return null;
    }

    /** イデンティファイアからサブタイプデータ（現在は刀豊の瓶のアンプリファイア）をパースする。 */
    public static short parseDamageFromIdentifier(String identifier) {
        if (!isSpecialIdentifier(identifier)) return 0;

        String[] parts = identifier.split(":");
        if (parts.length < 2) return 0;

        try {
            return Short.parseShort(parts[1]);
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "parseDamageFromIdentifier",
                    "Invalid special-case item identifier: {0}", identifier);
            return 0;
        }
    }

    /** 特殊ケース素材のイデンティファイアテキストを返す。非特殊ケースの場合は {@code null}。 */
    public static String toIdentifier(Material material, short damage) {
        if (material == Material.OMINOUS_BOTTLE) {
            return material + ":" + damage;
        }
        return null;
    }

    /** 特殊ケース素材の ItemStack を返す。非特殊ケースの場合は {@code null}。 */
    public static ItemStack toContents(Material material, short damage, int requestedAmount) {
        if (material == Material.OMINOUS_BOTTLE) {
            return OminousBottleHelper.toItemStack(damage, requestedAmount);
        }
        return null;
    }

    /** Returns similarity result for special-case materials; otherwise {@code null}. */
    public static Boolean isSimilar(Material material, ItemMeta meta, short damage) {
        if (material == Material.OMINOUS_BOTTLE) {
            return OminousBottleHelper.isSimilar(meta, damage);
        }
        return null;
    }

    /** Returns encoded sub-type data from stored special-case items, or {@code null}. */
    public static Short fromStoredItem(Material material, ItemMeta meta) {
        if (material == Material.OMINOUS_BOTTLE) {
            return OminousBottleHelper.getAmplifier(meta);
        }
        return null;
    }
}
