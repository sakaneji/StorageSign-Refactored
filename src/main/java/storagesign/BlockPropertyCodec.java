package storagesign;

import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** The explicitly stored block properties of a block item, never a serialized ItemStack. */
final class BlockPropertyCodec {
    private static final int MAX_IDENTIFIER_LENGTH = 512;
    private static final int MAX_PROPERTIES = 16;
    private static final int MAX_COMPONENT_LENGTH = 64;
    private static final String SERIALIZED_PROPERTIES_KEY = "BlockStateTag";

    private BlockPropertyCodec() {}

    static boolean hasBlockData(ItemMeta meta) {
        return meta instanceof BlockDataMeta blockMeta && blockMeta.hasBlockData();
    }

    /**
     * Paper's BlockDataMeta#getBlockData(material) fills in default properties and loses the
     * explicit-property mask. The public ItemMeta serialization retains just the item's
     * BlockStateTag string map. No other serialized metadata is copied into an identifier.
     */
    static String fromItemMeta(Material material, ItemMeta meta) {
        if (!(meta instanceof BlockDataMeta blockMeta) || !blockMeta.hasBlockData()
            || !isSupportedMaterial(material)) return null;
        Object raw;
        try {
            raw = meta.serialize().get(SERIALIZED_PROPERTIES_KEY);
        } catch (RuntimeException ex) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> properties) || properties.isEmpty()
            || properties.size() > MAX_PROPERTIES) return null;
        TreeMap<String, String> values = new TreeMap<>();
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!(entry.getKey() instanceof String name)
                || !(entry.getValue() instanceof String value)
                || !validComponent(name) || !validComponent(value)
                || values.putIfAbsent(name, value) != null) return null;
        }
        return parse(material.name() + "[" + join(values) + "]");
    }

    /** Return a canonical identifier, or null for malformed or unsupported state. */
    static String parse(String identifier) {
        if (identifier == null || identifier.length() > MAX_IDENTIFIER_LENGTH) return null;
        int open = identifier.indexOf('[');
        if (open <= 0 || identifier.charAt(identifier.length() - 1) != ']'
            || identifier.indexOf('[', open + 1) >= 0) return null;
        String materialName = identifier.substring(0, open);
        if (!materialName.matches("[A-Z0-9_]+")) return null;
        Material material = Material.getMaterial(materialName); // Deliberately bypass legacy aliases.
        if (!isSupportedMaterial(material)) return null;
        String body = identifier.substring(open + 1, identifier.length() - 1);
        if (body.isEmpty()) return null;
        String[] entries = body.split(",", -1);
        if (entries.length > MAX_PROPERTIES) return null;
        TreeMap<String, String> properties = new TreeMap<>();
        for (String entry : entries) {
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1
                || entry.indexOf('=', equals + 1) >= 0) return null;
            String name = entry.substring(0, equals);
            String value = entry.substring(equals + 1);
            if (!validComponent(name) || !validComponent(value)
                || properties.putIfAbsent(name, value) != null) return null;
        }
        String canonical = material.name() + "[" + join(properties) + "]";
        if (canonical.length() > MAX_IDENTIFIER_LENGTH) return null;
        return createBlockData(material, properties) == null ? null : canonical;
    }

    static ItemStack toItemStack(String identifier, int amount) {
        String canonical = parse(identifier);
        if (canonical == null) return null;
        int open = canonical.indexOf('[');
        Material material = Material.getMaterial(canonical.substring(0, open));
        TreeMap<String, String> properties = parseProperties(canonical.substring(open + 1, canonical.length() - 1));
        if (properties == null) return null;
        BlockData blockData = createBlockData(material, properties);
        if (blockData == null) return null;
        ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        if (!(item.getItemMeta() instanceof BlockDataMeta meta)) return null;
        meta.setBlockData(blockData);
        return item.setItemMeta(meta) ? item : null;
    }

    private static TreeMap<String, String> parseProperties(String body) {
        TreeMap<String, String> properties = new TreeMap<>();
        for (String entry : body.split(",", -1)) {
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1
                || entry.indexOf('=', equals + 1) >= 0) return null;
            String name = entry.substring(0, equals);
            String value = entry.substring(equals + 1);
            if (!validComponent(name) || !validComponent(value)
                || properties.putIfAbsent(name, value) != null) return null;
        }
        return properties;
    }

    private static BlockData createBlockData(Material material, TreeMap<String, String> properties) {
        if (Bukkit.getServer() == null) return null;
        try {
            BlockData data = Bukkit.createBlockData(material, "[" + join(properties) + "]");
            if (data.getMaterial() != material) return null;
            String explicit = data.getAsString(true);
            int open = explicit.indexOf('[');
            if (open < 0 || !explicit.endsWith("]")) return null;
            // A parser that drops a key or changes its value must fail closed.
            return properties.equals(parseProperties(explicit.substring(open + 1, explicit.length() - 1)))
                ? data : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isSupportedMaterial(Material material) {
        return material != null && material.isBlock() && material.isItem();
    }

    private static boolean validComponent(String component) {
        return component != null && !component.isEmpty()
            && component.length() <= MAX_COMPONENT_LENGTH
            && component.matches("[a-z0-9_]+");
    }

    private static String join(TreeMap<String, String> properties) {
        StringBuilder text = new StringBuilder();
        properties.forEach((name, value) -> {
            if (!text.isEmpty()) text.append(',');
            text.append(name).append('=').append(value);
        });
        return text.toString();
    }
}
