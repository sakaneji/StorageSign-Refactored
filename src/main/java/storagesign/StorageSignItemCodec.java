package storagesign;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Beehive;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import storagesign.item.SpecialCaseItemSupport;
import storagesign.registry.MaterialRegistry;

final class StorageSignItemCodec {
    private StorageSignItemCodec() {}

    static ItemStack createStorageSignItem(Material signMaterial, String loreText, int amount) {
        ItemStack ssItem = new ItemStack(signMaterial, Math.max(1, amount));
        ItemMeta meta = ssItem.getItemMeta();
        if (meta == null) return ssItem;

        meta.setDisplayName(StorageSign.HEADER_LINE);
        meta.setLore(List.of(loreText));
        applyConfiguredMaxStack(meta);
        ssItem.setItemMeta(meta);
        return ssItem;
    }

    static ItemStack createStorageSignItem(Material signMaterial, StorageSign contents, int amount) {
        ItemStack item = createStorageSignItem(signMaterial, contents.getLoreText(), amount);
        String canonical = contents.getCanonicalPotionIdentifier();
        if (canonical == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(
            StorageSign.potionIdentifierKey(), PersistentDataType.STRING, canonical);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack getContents(StorageSign sign, int requestedAmount) {
        if (sign.isUnregistered() || sign.getMaterial() == null || sign.getMaterial() == Material.AIR) return null;

        if (sign.getMaterial() == Material.END_PORTAL) {
            if (sign.getDamage() == StorageSign.DAMAGE_SS_ITEM) {
                String markerName = StorageSignIdentifierCodec.resolveVirtualIdentifier(sign.getMaterial(), sign.getDamage());
                if (markerName == null) markerName = "HorseEgg";
                return createLegacyMarkerItem(Math.min(requestedAmount, 1), markerName);
            }
            return createStorageSignItem(Material.OAK_SIGN, StorageSign.EMPTY_MARKER, Math.min(requestedAmount, 1));
        }

        if (MaterialRegistry.SIGN_MATERIALS.contains(sign.getMaterial())) {
            if (sign.getDamage() == StorageSign.DAMAGE_SS_ITEM) {
                return createStorageSignItem(sign.getMaterial(), StorageSign.EMPTY_MARKER,
                    Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
            }
            return new ItemStack(sign.getMaterial(), Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
        }

        ItemStack specialItem = SpecialCaseItemSupport.toContents(sign.getMaterial(), sign.getDamage(), requestedAmount);
        if (specialItem != null) return specialItem;

        if (sign.getMaterial() == Material.ENCHANTED_BOOK && sign.getEnchantment() != null) {
            ItemStack item = new ItemStack(sign.getMaterial(), Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(sign.getEnchantment(), sign.getDamage(), true);
                item.setItemMeta(meta);
            }
            return item;
        }

        if (MaterialRegistry.POTION_MATERIALS.contains(sign.getMaterial()) && sign.getPotionType() != null) {
            ItemStack item = new ItemStack(sign.getMaterial(), Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(sign.getPotionType());
                item.setItemMeta(meta);
            }
            return item;
        }

        if (sign.getMaterial() == Material.WHITE_BANNER && sign.getDamage() == 8) {
            BannerMeta bannerMeta = StorageSignPlugin.getOminousBannerMeta();
            if (bannerMeta != null) {
                ItemStack item = new ItemStack(sign.getMaterial(), Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
                if (!item.setItemMeta(bannerMeta.clone())) {
                    StorageSign.LOG.warning("getContents", "不吉なバナーのメタを ItemStack に適用できませんでした");
                    return null;
                }
                return item;
            }
            return null;
        }

        if (sign.getMaterial() == Material.FIREWORK_ROCKET) {
            ItemStack item = new ItemStack(sign.getMaterial(), Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
            if (sign.getDamage() > 1 && item.getItemMeta() instanceof FireworkMeta fireworkMeta) {
                fireworkMeta.setPower(sign.getDamage());
                item.setItemMeta(fireworkMeta);
            }
            return item;
        }

        ItemStack item = new ItemStack(sign.getMaterial(), Math.min(requestedAmount, sign.getMaterial().getMaxStackSize()));
        if (sign.getDamage() != 0 && item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(sign.getDamage());
            item.setItemMeta(damageable);
        }
        return item;
    }

    static boolean isSimilar(StorageSign sign, ItemStack item, ItemStack cachedReference) {
        if (item == null || item.getType() == Material.AIR) return false;

        if (sign.getMaterial() == Material.END_PORTAL && sign.getDamage() == StorageSign.DAMAGE_SS_ITEM) {
            var horseMeta = item.getItemMeta();
            String markerName = StorageSignIdentifierCodec.resolveVirtualIdentifier(sign.getMaterial(), sign.getDamage());
            if (markerName == null) markerName = "HorseEgg";
            return item.getType() == StorageSign.LEGACY_MARKER_ITEM_MATERIAL
                && horseMeta != null
                && markerName.equals(horseMeta.getDisplayName())
                && horseMeta.hasLore();
        }

        if (item.getType() != sign.getMaterial()) return false;

        var meta = item.getItemMeta();
        if (MaterialRegistry.BLOCK_ENTITY_DATA_MATERIALS.contains(sign.getMaterial())) return true;

        Boolean specialSimilarity = SpecialCaseItemSupport.isSimilar(sign.getMaterial(), meta, sign.getDamage());
        if (specialSimilarity != null) return specialSimilarity;

        if (sign.getMaterial() == Material.ENCHANTED_BOOK) {
            if (!(meta instanceof EnchantmentStorageMeta esm)) return false;
            if (sign.getEnchantment() == null) return false;
            return esm.hasStoredEnchant(sign.getEnchantment())
                && esm.getStoredEnchantLevel(sign.getEnchantment()) == sign.getDamage();
        }

        if (MaterialRegistry.POTION_MATERIALS.contains(sign.getMaterial())) {
            if (!(meta instanceof PotionMeta pm)) return false;
            return sign.getPotionType() != null && sign.getPotionType().equals(pm.getBasePotionType());
        }

        if (sign.getMaterial() == Material.WHITE_BANNER && sign.getDamage() == 8) {
            if (!(meta instanceof BannerMeta bm)) return false;
            BannerMeta ominous = StorageSignPlugin.getOminousBannerMeta();
            if (ominous != null && bm.equals(ominous)) return true;
            return StorageSignPlugin.isOminousBannerMeta(bm);
        }

        if (sign.getDamage() == StorageSign.DAMAGE_SS_ITEM && MaterialRegistry.SIGN_MATERIALS.contains(sign.getMaterial())) {
            StorageSign itemSS = StorageSign.fromItemStack(item);
            return itemSS != null && itemSS.isUnregistered();
        }

        if (MaterialRegistry.SHULKER_BOX_MATERIALS.contains(sign.getMaterial())
            && meta instanceof BlockStateMeta bsm
            && bsm.getBlockState() instanceof ShulkerBox shulker
            && shulker.getInventory().isEmpty()) {
            item = new ItemStack(item.getType());
        }

        if (meta instanceof Damageable damageable && damageable.getDamage() != sign.getDamage()) {
            return false;
        }

        if (cachedReference == null) cachedReference = getContents(sign, 1);
        return cachedReference != null && cachedReference.isSimilar(item);
    }

    static StorageSign fromStoredItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        Material mat = item.getType();
        ItemMeta meta = item.getItemMeta();

        if (StorageSign.isStorageSign(item) && MaterialRegistry.SIGN_MATERIALS.contains(mat)) {
            return new StorageSign(mat, StorageSign.DAMAGE_SS_ITEM, 0, null, null, false);
        }

        if (mat == StorageSign.LEGACY_MARKER_ITEM_MATERIAL && meta != null && meta.hasLore()) {
            String markerName = meta.getDisplayName();
            if (markerName == null || markerName.isBlank()) return null;
            StorageSign parsed = StorageSignIdentifierCodec.parseVirtualIdentifier(markerName, 0);
            if (parsed == null) return null;
            if (parsed.getMaterial() != Material.END_PORTAL || parsed.getDamage() != StorageSign.DAMAGE_SS_ITEM) return null;
            return new StorageSign(Material.END_PORTAL, StorageSign.DAMAGE_SS_ITEM, 0, null, null, false);
        }

        Short specialDamage = SpecialCaseItemSupport.fromStoredItem(mat, meta);
        if (specialDamage != null) {
            return ifExactlyRestorable(item, new StorageSign(mat, specialDamage, 0, null, null, false));
        }

        if (mat == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
            java.util.Map<Enchantment, Integer> stored = esm.getStoredEnchants();
            if (stored.size() != 1) return null;
            java.util.Map.Entry<Enchantment, Integer> entry = stored.entrySet().iterator().next();
            Enchantment ench = entry.getKey();
            short level = entry.getValue().shortValue();
            return ifExactlyRestorable(item, new StorageSign(mat, level, 0, null, ench, false));
        }

        if (MaterialRegistry.POTION_MATERIALS.contains(mat) && meta instanceof PotionMeta pm) {
            if (pm.hasCustomEffects()) return null;
            var pot = pm.getBasePotionType();
            short damage = (short) (storagesign.item.PotionHelper.getEnhanceCode(pot).charAt(0) - '0');
            return ifExactlyRestorable(item, new StorageSign(mat, damage, 0, pot, null, false));
        }

        if (mat == Material.WHITE_BANNER) {
            if (!(meta instanceof BannerMeta bm)) return null;
            if (StorageSignPlugin.isOminousBannerMeta(bm)) {
                StorageSignPlugin.setOminousBannerMeta((BannerMeta) bm.clone());
                return new StorageSign(mat, (short) 8, 0, null, null, false);
            }
        }

        if (mat == Material.FIREWORK_ROCKET && meta instanceof FireworkMeta fireworkMeta) {
            if (!fireworkMeta.getEffects().isEmpty()) return null;
            int power = fireworkMeta.getPower();
            short encoded = (short) (power > 1 ? power : StorageSign.DAMAGE_FIREWORK_ZERO);
            return ifExactlyRestorable(item, new StorageSign(mat, encoded, 0, null, null, false));
        }

        if (MaterialRegistry.SHULKER_BOX_MATERIALS.contains(mat)) {
            if (!(meta instanceof BlockStateMeta blockMeta)
                || !(blockMeta.getBlockState() instanceof ShulkerBox shulker)
                || !shulker.getInventory().isEmpty()) {
                return null;
            }
        }

        if (MaterialRegistry.BLOCK_ENTITY_DATA_MATERIALS.contains(mat)
            && meta instanceof BlockStateMeta blockMeta
            && blockMeta.getBlockState() instanceof Beehive beehive
            && beehive.getEntityCount() > 0) {
            return null;
        }

        if (meta instanceof Damageable damageable) {
            return ifExactlyRestorable(item, new StorageSign(mat, (short) damageable.getDamage(), 0, null, null, false));
        }
        return ifExactlyRestorable(item, new StorageSign(mat, (short) 0, 0, null, null, false));
    }

    private static StorageSign ifExactlyRestorable(ItemStack original, StorageSign candidate) {
        ItemMeta originalMeta = original.getItemMeta();
        if (originalMeta != null
            && (originalMeta.hasDisplayName() || originalMeta.hasLore()
                || originalMeta.hasEnchants() || !originalMeta.getItemFlags().isEmpty())) {
            return null;
        }
        ItemStack restored = candidate.getContents(1);
        if (restored == null) return null;
        ItemStack one = original.clone();
        one.setAmount(1);
        return restored.isSimilar(one) ? candidate : null;
    }

    private static ItemStack createLegacyMarkerItem(int amount, String markerName) {
        ItemStack markerItem = new ItemStack(StorageSign.LEGACY_MARKER_ITEM_MATERIAL, Math.max(1, amount));
        ItemMeta meta = markerItem.getItemMeta();
        if (meta == null) return markerItem;
        meta.setDisplayName(markerName);
        meta.setLore(List.of(StorageSign.EMPTY_MARKER));
        markerItem.setItemMeta(meta);
        return markerItem;
    }

    private static void applyConfiguredMaxStack(ItemMeta meta) {
        if (StorageSign.SET_MAX_STACK_SIZE == null) return;
        int configured = Math.max(1, ConfigLoader.getMaxStackSize());
        try {
            StorageSign.SET_MAX_STACK_SIZE.invoke(meta, (Integer) configured);
        } catch (Throwable ignored) {}
    }
}
