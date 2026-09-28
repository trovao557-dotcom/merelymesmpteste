package me.merelyme.ahprice;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class AhLore {
    // Match reference AH: gold #FFD900 arrows + values, white labels
    private static final String GOLD = "§x§F§F§D§9§0§0";
    private static final String WHITE = "§f";
    private static final String GRAY = "§7";

    private AhLore() {
    }

    static ItemStack apply(ItemStack stack, String seller, String priceText, long remainingMs, boolean mine) {
        ItemStack item = stack.clone();
        List<?> lore = buildLore(seller, priceText, remainingMs, mine);
        if (lore.isEmpty()) {
            return item;
        }

        item.editMeta(meta -> {
            setMetaLore(meta, lore);
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
            stackLore.invoke(item, lore);
        } catch (ReflectiveOperationException ignored) {
        }

        setPaperLoreComponent(item, lore);
        unsetPaperComponent(item, "TOOLTIP_DISPLAY");
        unsetPaperComponent(item, "TOOLTIP_STYLE");
        return item;
    }

    private static List<?> buildLore(String seller, String priceText, long remainingMs, boolean mine) {
        List<Object> lore = new ArrayList<>();
        lore.add(deserialize(""));
        if (!mine && seller != null && !seller.isBlank()) {
            lore.add(deserialize(GOLD + "▶ " + WHITE + "SELLER: " + GOLD + seller));
        }
        lore.add(deserialize(GOLD + "▶ " + WHITE + "PRICE: " + GOLD + formatPrice(priceText)));
        lore.add(deserialize(GOLD + "▶ " + WHITE + "EXPIRES: " + GOLD + formatRemaining(remainingMs)));
        lore.add(deserialize(""));
        lore.add(deserialize(GRAY + "▶▶ " + WHITE + "CLICK TO " + (mine ? "CANCEL" : "BUY")));
        return lore;
    }

    private static Object deserialize(String line) {
        try {
            Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object legacy = serializer.getField("legacySection").get(null);
            Method deserialize = serializer.getMethod("deserialize", String.class);
            return deserialize.invoke(legacy, line);
        } catch (ReflectiveOperationException ex) {
            return line;
        }
    }

    private static void setMetaLore(ItemMeta meta, List<?> lore) {
        try {
            Method loreSetter = meta.getClass().getMethod("lore", List.class);
            loreSetter.invoke(meta, lore);
        } catch (ReflectiveOperationException ignored) {
            List<String> legacy = new ArrayList<>();
            for (Object line : lore) {
                legacy.add(String.valueOf(line));
            }
            meta.setLore(legacy);
        }
    }

    private static void setPaperLoreComponent(ItemStack item, List<?> lore) {
        try {
            Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object loreType = types.getField("LORE").get(null);
            Class<?> itemLore = Class.forName("io.papermc.paper.datacomponent.item.ItemLore");
            Method loreFactory = itemLore.getMethod("lore", List.class);
            Object loreComponent = loreFactory.invoke(null, lore);
            boolean ok = false;
            for (Method m : ItemStack.class.getMethods()) {
                if (!m.getName().equals("setData") || m.getParameterCount() != 2) {
                    continue;
                }
                try {
                    m.invoke(item, loreType, loreComponent);
                    ok = true;
                    break;
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
            if (!ok) {
                // fallback older signature
                Method setData = ItemStack.class.getMethod("setData",
                        Class.forName("io.papermc.paper.datacomponent.DataComponentType"),
                        Object.class);
                setData.invoke(item, loreType, loreComponent);
            }
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

    private static String formatPrice(String priceText) {
        if (priceText == null || priceText.isBlank()) {
            return "$0";
        }
        String trimmed = priceText.trim().replace(",", "");
        if (trimmed.startsWith("$")) {
            return trimmed;
        }
        return "$" + trimmed;
    }

    static String formatRemaining(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes >= 1440L) {
            return (minutes / 1440L) + "D " + (minutes % 1440L / 60L) + "H";
        }
        if (minutes >= 60L) {
            return (minutes / 60L) + "H " + (minutes % 60L) + "M";
        }
        return Math.max(1L, minutes) + "M";
    }
}
