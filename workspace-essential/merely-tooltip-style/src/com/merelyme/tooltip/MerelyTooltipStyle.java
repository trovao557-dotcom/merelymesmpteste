package com.merelyme.tooltip;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

public final class MerelyTooltipStyle extends JavaPlugin {
    private static final NamespacedKey BLUE_STYLE =
            new NamespacedKey("merelyme", "special_tools");

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskTimer(this, this::refreshOnlineInventories, 1L, 40L);
        getLogger().info("Blue special-tool tooltips enabled.");
    }

    private void refreshOnlineInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            styleInventory(player.getInventory());
            styleInventory(player.getEnderChest());
            Inventory top = player.getOpenInventory().getTopInventory();
            styleInventory(top);
        }
    }

    private void styleInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (isSpecialTool(item)) {
                ItemMeta meta = item.getItemMeta();
                if (!BLUE_STYLE.equals(meta.getTooltipStyle())) {
                    meta.setTooltipStyle(BLUE_STYLE);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    private boolean isSpecialTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return false;
        Component componentName = meta.displayName();
        String name = componentName == null
                ? ChatColor.stripColor(meta.getDisplayName())
                : PlainTextComponentSerializer.plainText().serialize(componentName);
        if (name == null) return false;
        String upper = name.toUpperCase(Locale.ROOT);
        boolean namedSpecial = upper.contains("SPECIAL") || upper.contains("SELL AXE");
        if (!namedSpecial) return false;

        Material material = item.getType();
        return material.name().endsWith("_PICKAXE")
                || material.name().endsWith("_AXE")
                || material.name().endsWith("_SHOVEL")
                || material.name().endsWith("_HOE")
                || material == Material.SPONGE
                || material == Material.WET_SPONGE
                || material == Material.WATER_BUCKET;
    }
}
