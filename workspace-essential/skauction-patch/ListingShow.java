package net.skstudios.auction;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Paper 1.21+ fix: keep listing-lore (SELLER/PRICE/EXPIRES) and sync LORE data component.
 * Do NOT strip lines with $ / price: and do NOT rewrite the item name.
 */
final class ListingShow {
    private ListingShow() {
    }

    static ItemStack priced(ItemStack stack, double price, String fallbackName) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            lore = stack.lore();
        }
        if (lore == null || lore.isEmpty()) {
            return stack;
        }

        List<Component> copy = new ArrayList<>(lore);
        meta.lore(copy);
        try {
            meta.setHideTooltip(false);
        } catch (Throwable ignored) {
        }
        try {
            meta.removeItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        } catch (Throwable ignored) {
        }
        stack.setItemMeta(meta);

        stack.setData(DataComponentTypes.LORE, ItemLore.lore(copy));
        try {
            stack.unsetData(DataComponentTypes.TOOLTIP_DISPLAY);
        } catch (Throwable ignored) {
        }
        try {
            stack.unsetData(DataComponentTypes.TOOLTIP_STYLE);
        } catch (Throwable ignored) {
        }
        return stack;
    }

    static String compact(double amount) {
        double value = amount;
        String suffix = "";
        if (amount >= 1_000_000_000d) {
            value = amount / 1_000_000_000d;
            suffix = "b";
        } else if (amount >= 1_000_000d) {
            value = amount / 1_000_000d;
            suffix = "m";
        } else if (amount >= 1_000d) {
            value = amount / 1_000d;
            suffix = "k";
        } else if (Math.abs(amount - Math.rint(amount)) < 0.0001d) {
            return String.valueOf((long) Math.rint(amount));
        } else {
            return stripZeros(amount);
        }
        return stripZeros(value) + suffix;
    }

    private static String stripZeros(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05d) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format(Locale.US, "%.1f", value);
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }
}
