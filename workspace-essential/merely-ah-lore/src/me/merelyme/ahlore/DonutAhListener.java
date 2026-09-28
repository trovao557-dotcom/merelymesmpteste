package me.merelyme.ahlore;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class DonutAhListener implements Listener {
    private final MerelyAhLore plugin;

    DonutAhListener(MerelyAhLore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        schedulePatch(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        schedulePatch(event.getView().getTopInventory());
    }

    private void schedulePatch(Inventory inventory) {
        if (!isDonutAuction(inventory)) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> patch(inventory));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> patch(inventory), 2L);
    }

    private boolean isDonutAuction(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return false;
        }
        String name = holder.getClass().getName();
        return name.startsWith("io.nightbeam.donutauction.gui.");
    }

    private void patch(Inventory inventory) {
        if (!isDonutAuction(inventory)) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                continue;
            }
            ItemStack fixed = LoreSync.apply(current);
            if (fixed != current) {
                inventory.setItem(slot, fixed);
            }
        }
    }
}
