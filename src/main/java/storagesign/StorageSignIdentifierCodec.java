package storagesign;

import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import storagesign.item.EnchantHelper;
import storagesign.item.PotionHelper;
import storagesign.item.SpecialCaseItemSupport;
import storagesign.registry.LegacyNameRegistry;

final class StorageSignIdentifierCodec {

    private StorageSignIdentifierCodec() {}

    static StorageSign fromSignLines(String[] lines) {
        if (lines == null || lines.length < 3) return null;
        if (!StorageSign.HEADER_LINE.equals(lines[0])) return null;

        String identifier = lines[1].trim();
        if (identifier.isBlank() || StorageSign.EMPTY_MARKER.equals(identifier)) {
            return StorageSign.empty();
        }

        Integer amount = parseStoredAmount(lines[2]);
        if (amount == null) return null;
        return parseIdentifier(identifier, amount);
    }

    static StorageSign fromSign(Sign sign) {
        if (sign == null) return null;
        String[] lines = sign.getSide(org.bukkit.block.sign.Side.FRONT).getLines();
        if (lines.length < 3 || !StorageSign.HEADER_LINE.equals(lines[0])) return null;
        Integer amount = parseStoredAmount(lines[2]);
        if (amount == null) return null;
        String canonical = sign.getPersistentDataContainer().get(
            StorageSign.canonicalPotionIdentifierKey(), PersistentDataType.STRING);
        if (canonical == null || canonical.isBlank()) {
            canonical = sign.getPersistentDataContainer().get(
                StorageSign.potionIdentifierKey(), PersistentDataType.STRING);
        }
        if (canonical != null) return parseIdentifier(canonical, amount);
        return fromSignLines(lines);
    }

    static StorageSign fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        var meta = item.getItemMeta();
        if (meta == null) return null;
        if (!StorageSign.HEADER_LINE.equals(meta.getDisplayName())) return null;

        java.util.List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return null;

        String loreLine = lore.get(0).trim();
        if (StorageSign.EMPTY_MARKER.equals(loreLine)) return StorageSign.empty();

        int sep = loreLine.lastIndexOf(' ');
        if (sep < 0) return null;

        String identifier = loreLine.substring(0, sep).trim();
        Integer amount = parseStoredAmount(loreLine.substring(sep + 1));
        if (amount == null) return null;

        String canonical = meta.getPersistentDataContainer().get(
            StorageSign.potionIdentifierKey(), PersistentDataType.STRING);
        if (canonical != null) return parseIdentifier(canonical, amount);
        return parseIdentifier(identifier, amount);
    }

    static StorageSign parseIdentifier(String identifier, int amount) {
        if (identifier == null || identifier.isBlank()) return null;

        Material signMat = LegacyNameRegistry.NAME_TO_MATERIAL.get(identifier);
        if (signMat != null) {
            return new StorageSign(signMat, StorageSign.DAMAGE_SS_ITEM, amount, null, null, false);
        }

        StorageSign virtualSign = parseVirtualIdentifier(identifier, amount);
        if (virtualSign != null) return virtualSign;

        Material specialMaterial = SpecialCaseItemSupport.materialFromIdentifier(identifier);
        if (specialMaterial != null) {
            short specialDamage = SpecialCaseItemSupport.parseDamageFromIdentifier(identifier);
            return new StorageSign(specialMaterial, specialDamage, amount, null, null, false);
        }

        if (identifier.startsWith("ENCHBOOK:")) {
            String[] parts = identifier.split(":");
            if (parts.length < 3) return null;
            Enchantment ench = EnchantHelper.fromPrefix(parts[1]);
            if (ench == null) return null;
            short level;
            try {
                level = Short.parseShort(parts[2]);
            } catch (NumberFormatException e) {
                StorageSign.LOG.log(Level.WARNING, "parseIdentifier", "エンチャントレベルが不正: {0}", identifier);
                return null;
            }
            return new StorageSign(Material.ENCHANTED_BOOK, level, amount, null, ench, false);
        }

        if (identifier.contains("POTION:")) {
            PotionHelper.PotionData potion = PotionHelper.fromIdentifier(identifier);
            if (potion == null) return null;
            short damage = (short) (PotionHelper.getEnhanceCode(potion.type()).charAt(0) - '0');
            return new StorageSign(potion.material(), damage, amount, potion.type(), null, false);
        }

        String[] parts = identifier.split(":");
        String matName = parts[0].toUpperCase();
        short damage = 0;
        if (parts.length >= 2) {
            try {
                damage = Short.parseShort(parts[1]);
            } catch (NumberFormatException e) {
                if (matName.equals("ENCHANTED_BOOK") && parts.length >= 3) {
                    Enchantment ench = EnchantHelper.fromPrefix(parts[1]);
                    if (ench == null) return null;
                    short level;
                    try {
                        level = Short.parseShort(parts[2]);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                    return new StorageSign(Material.ENCHANTED_BOOK, level, amount, null, ench, false);
                }
                return null;
            }
        }

        Material mat = resolveMaterialFromIdentifierToken(matName);
        if (mat == null) {
            StorageSign.LOG.log(Level.WARNING, "parseIdentifier", "StorageSign 識別子に未知のマテリアル: {0}", identifier);
            return null;
        }
        return new StorageSign(mat, damage, amount, null, null, false);
    }

    static String getIdentifier(StorageSign sign) {
        if (sign.isUnregistered()) return StorageSign.EMPTY_MARKER;

        String signName = LegacyNameRegistry.MATERIAL_TO_NAME.get(sign.getMaterial());
        if (signName != null && sign.getDamage() == StorageSign.DAMAGE_SS_ITEM) return signName;

        String virtualIdentifier = resolveVirtualIdentifier(sign.getMaterial(), sign.getDamage());
        if (virtualIdentifier != null) return virtualIdentifier;

        String specialIdentifier = SpecialCaseItemSupport.toIdentifier(sign.getMaterial(), sign.getDamage());
        if (specialIdentifier != null) return specialIdentifier;

        if (sign.getMaterial() == Material.ENCHANTED_BOOK && sign.getEnchantment() != null) {
            return "ENCHBOOK:" + EnchantHelper.toShortKey(sign.getEnchantment()) + ":" + sign.getDamage();
        }

        if (storagesign.registry.MaterialRegistry.POTION_MATERIALS.contains(sign.getMaterial())
            && sign.getPotionType() != null) {
            return PotionHelper.toDisplayIdentifier(sign.getMaterial(), sign.getPotionType());
        }

        if (sign.getDamage() != 0) return sign.getMaterial() + ":" + sign.getDamage();
        return sign.getMaterial().toString();
    }

    static StorageSign parseVirtualIdentifier(String identifier, int amount) {
        String spec = ConfigLoader.getVirtualItemIdentifiers().get(identifier);
        if (spec == null) spec = StorageSign.DEFAULT_VIRTUAL_IDENTIFIERS.get(identifier);
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

    static Material resolveMaterialFromIdentifierToken(String token) {
        if (token == null || token.isBlank()) return null;

        String normalized = token.trim().toUpperCase();
        String configuredAlias = ConfigLoader.getIdentifierAliases().get(normalized);
        if (configuredAlias == null) configuredAlias = ConfigLoader.getIdentifierAliases().get(token.trim());
        if (configuredAlias != null && !configuredAlias.isBlank()) {
            Material configured = Material.matchMaterial(configuredAlias.trim());
            if (configured != null) return configured;
        }

        String defaultAlias = StorageSign.DEFAULT_IDENTIFIER_ALIASES.get(normalized);
        if (defaultAlias != null) {
            Material legacy = Material.matchMaterial(defaultAlias);
            if (legacy != null) return legacy;
        }
        return Material.matchMaterial(normalized);
    }

    static String resolveVirtualIdentifier(Material material, short damage) {
        for (Entry<String, String> entry : ConfigLoader.getVirtualItemIdentifiers().entrySet()) {
            if (matchesVirtualSpec(material, damage, entry.getValue())) return entry.getKey();
        }
        for (Entry<String, String> entry : StorageSign.DEFAULT_VIRTUAL_IDENTIFIERS.entrySet()) {
            if (matchesVirtualSpec(material, damage, entry.getValue())) return entry.getKey();
        }
        return null;
    }

    static Integer parseStoredAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean matchesVirtualSpec(Material material, short damage, String spec) {
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
}
