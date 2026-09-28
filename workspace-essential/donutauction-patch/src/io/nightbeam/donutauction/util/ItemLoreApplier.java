package io.nightbeam.donutauction.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemLoreApplier {

    public enum LoreMode {
        APPEND,
        REPLACE,
        DISABLED
    }

    private ItemLoreApplier() {
    }

    public static void applyLore(ItemStack item, LoreMode mode, boolean showSeparator, List<Component> auctionLore) {
        applyLore(item, mode, showSeparator, auctionLore, null);
    }

    public static void applyLore(ItemStack item, LoreMode mode, boolean showSeparator, List<Component> auctionLore,
                                 MessageUtil messages) {
        if (mode == LoreMode.DISABLED) {
            return;
        }

        List<Component> combined = new ArrayList<>();

        if (mode == LoreMode.REPLACE) {
            combined.addAll(auctionLore);
        } else {
            List<Component> existingLore = readExistingLore(item);
            if (!existingLore.isEmpty()) {
                combined.addAll(existingLore);
                if (showSeparator) {
                    combined.add(messages != null
                            ? messages.component(messages.raw("gui.listing-lore.separator", "&8 "))
                            : Component.text(" ", NamedTextColor.DARK_GRAY));
                }
            }
            combined.addAll(auctionLore);
        }

        item.editMeta(meta -> {
            meta.lore(combined);
            try {
                Method hide = meta.getClass().getMethod("setHideTooltip", boolean.class);
                hide.invoke(meta, false);
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                meta.removeItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            } catch (Throwable ignored) {
            }
        });

        try {
            Method stackLore = ItemStack.class.getMethod("lore", List.class);
            stackLore.invoke(item, combined);
        } catch (ReflectiveOperationException ignored) {
        }

        setPaperLoreComponent(item, combined);
        unsetPaperComponent(item, "TOOLTIP_DISPLAY");
        unsetPaperComponent(item, "TOOLTIP_STYLE");
    }

    @SuppressWarnings("unchecked")
    private static List<Component> readExistingLore(ItemStack item) {
        try {
            Method stackLore = ItemStack.class.getMethod("lore");
            List<Component> lore = (List<Component>) stackLore.invoke(item);
            if (lore != null && !lore.isEmpty()) {
                return new ArrayList<>(lore);
            }
        } catch (ReflectiveOperationException ignored) {
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        List<Component> lore = meta.lore();
        return lore == null ? List.of() : new ArrayList<>(lore);
    }

    private static void setPaperLoreComponent(ItemStack item, List<Component> lore) {
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

    public static LoreMode parseMode(String value) {
        if (value == null) {
            return LoreMode.APPEND;
        }
        try {
            return LoreMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return LoreMode.APPEND;
        }
    }
}
