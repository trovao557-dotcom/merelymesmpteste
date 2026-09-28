package me.merelyme.ahlore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class LoreSync {
    private LoreSync() {
    }

    public static ItemStack apply(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }

        ItemStack item = stack.clone();
        List<?> lore = readLore(item);
        if (lore.isEmpty()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                Method loreSetter = meta.getClass().getMethod("lore", List.class);
                loreSetter.invoke(meta, lore);
            } catch (ReflectiveOperationException ignored) {
                return item;
            }
            try {
                Method hide = meta.getClass().getMethod("setHideTooltip", boolean.class);
                hide.invoke(meta, false);
            } catch (ReflectiveOperationException ignored) {
            }
            meta.removeItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }

        try {
            Method stackLore = ItemStack.class.getMethod("lore", List.class);
            stackLore.invoke(item, lore);
        } catch (ReflectiveOperationException ignored) {
        }

        setPaperLoreComponent(item, lore);
        unsetPaperComponent(item, "TOOLTIP_DISPLAY");
        unsetPaperComponent(item, "TOOLTIP_STYLE");
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<?> readLore(ItemStack item) {
        try {
            Method stackLore = ItemStack.class.getMethod("lore");
            List<?> lore = (List<?>) stackLore.invoke(item);
            if (lore != null && !lore.isEmpty()) {
                return new ArrayList<>(lore);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        try {
            Method metaLore = meta.getClass().getMethod("lore");
            List<?> lore = (List<?>) metaLore.invoke(meta);
            return lore == null ? List.of() : new ArrayList<>(lore);
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    private static void setPaperLoreComponent(ItemStack item, List<?> lore) {
        try {
            Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object loreType = types.getField("LORE").get(null);
            Class<?> itemLore = Class.forName("io.papermc.paper.datacomponent.item.ItemLore");
            Method loreFactory = itemLore.getMethod("lore", List.class);
            Object loreComponent = loreFactory.invoke(null, lore);
            Method setData = ItemStack.class.getMethod("setData",
                    Class.forName("io.papermc.paper.datacomponent.DataComponentType"),
                    Object.class);
            setData.invoke(item, loreType, loreComponent);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void unsetPaperComponent(ItemStack item, String field) {
        try {
            Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object type = types.getField(field).get(null);
            Method unset = ItemStack.class.getMethod("unsetData",
                    Class.forName("io.papermc.paper.datacomponent.DataComponentType"));
            unset.invoke(item, type);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
