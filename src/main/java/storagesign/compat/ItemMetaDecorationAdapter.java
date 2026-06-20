package storagesign.compat;

import java.lang.reflect.Method;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

/** Optional metadata decoration. Missing APIs never disable the banner itself. */
public final class ItemMetaDecorationAdapter {

    private final boolean adventureNameEnabled;
    private final String[] stringNameSetters;
    private final String[] tooltipFlags;

    public ItemMetaDecorationAdapter() {
        this(true, new String[] {"setItemName", "setDisplayName"},
            new String[] {"HIDE_ADDITIONAL_TOOLTIP", "HIDE_ITEM_SPECIFICS"});
    }

    ItemMetaDecorationAdapter(boolean adventureNameEnabled, String[] stringNameSetters,
                              String[] tooltipFlags) {
        this.adventureNameEnabled = adventureNameEnabled;
        this.stringNameSetters = stringNameSetters.clone();
        this.tooltipFlags = tooltipFlags.clone();
    }

    public DecorationResult decorateOminousBanner(ItemMeta meta) {
        boolean named = hasName(meta) || (adventureNameEnabled && setAdventureName(meta));
        for (String setter : stringNameSetters) {
            if (!named) named = invokeStringSetter(meta, setter);
        }
        boolean tooltipHidden = false;
        for (String flag : tooltipFlags) {
            if (!tooltipHidden) tooltipHidden = addFlag(meta, flag);
        }
        return new DecorationResult(named, tooltipHidden);
    }

    private boolean hasName(ItemMeta meta) {
        return invokeBoolean(meta, "hasItemName") || invokeBoolean(meta, "hasDisplayName");
    }

    private boolean setAdventureName(ItemMeta meta) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> textColorClass = Class.forName("net.kyori.adventure.text.format.TextColor");
            Class<?> namedTextColorClass = Class.forName("net.kyori.adventure.text.format.NamedTextColor");
            Object component = componentClass.getMethod("translatable", String.class)
                .invoke(null, "block.minecraft.ominous_banner");
            Object gold = namedTextColorClass.getField("GOLD").get(null);
            component = componentClass.getMethod("color", textColorClass).invoke(component, gold);
            meta.getClass().getMethod("itemName", componentClass).invoke(meta, component);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private boolean invokeStringSetter(ItemMeta meta, String methodName) {
        try {
            meta.getClass().getMethod(methodName, String.class).invoke(meta, "§6Ominous Banner");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private boolean invokeBoolean(ItemMeta meta, String methodName) {
        try {
            return Boolean.TRUE.equals(meta.getClass().getMethod(methodName).invoke(meta));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private boolean addFlag(ItemMeta meta, String flagName) {
        try {
            Method addItemFlags = meta.getClass().getMethod("addItemFlags", ItemFlag[].class);
            ItemFlag flag = ItemFlag.valueOf(flagName);
            addItemFlags.invoke(meta, (Object) new ItemFlag[] {flag});
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public record DecorationResult(boolean nameAvailable, boolean tooltipAvailable) {}
}
