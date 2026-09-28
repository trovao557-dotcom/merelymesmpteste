package me.merelyme.ahprice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AhMenuListener implements Listener {
    private final AhPriceHover plugin;

    AhMenuListener(AhPriceHover plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        schedulePatch(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        schedulePatch(event.getView().getTopInventory());
    }

    private void schedulePatch(Inventory top) {
        Object holder = top.getHolder();
        if (holder == null) {
            return;
        }
        String kind = holder.getClass().getSimpleName();
        if (!kind.equals("BrowseMenu") && !kind.equals("MineMenu")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> patchMenu(top, kind), 1L);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> patchMenu(top, kind), 3L);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> patchMenu(top, kind), 5L);
    }

    private void patchMenu(Inventory top, String kind) {
        Object holder = top.getHolder();
        if (holder == null || !holder.getClass().getSimpleName().equals(kind)) {
            return;
        }
        try {
            Field drawnField = holder.getClass().getDeclaredField("drawn");
            drawnField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, Long> drawn = (Map<Integer, Long>) drawnField.get(holder);

            Field pluginField = holder.getClass().getDeclaredField("plugin");
            pluginField.setAccessible(true);
            Object auctionPlugin = pluginField.get(holder);

            Object store = auctionPlugin.getClass().getMethod("store").invoke(auctionPlugin);
            Method byId = store.getClass().getMethod("byId", long.class);
            Method price = null;
            Method sellerName = null;
            Method remaining = null;
            Method money = auctionPlugin.getClass().getMethod("money", double.class);

            boolean mine = kind.equals("MineMenu");
            for (Map.Entry<Integer, Long> entry : drawn.entrySet()) {
                Object listing = byId.invoke(store, entry.getValue());
                if (listing == null) {
                    continue;
                }
                if (price == null) {
                    price = listing.getClass().getMethod("price");
                    remaining = listing.getClass().getMethod("remaining", long.class);
                    if (!mine) {
                        sellerName = listing.getClass().getMethod("sellerName");
                    }
                }
                double listingPrice = ((Number) price.invoke(listing)).doubleValue();
                long msLeft = ((Number) remaining.invoke(listing, System.currentTimeMillis())).longValue();
                String seller = mine ? null : (String) sellerName.invoke(listing);
                String priceText = (String) money.invoke(auctionPlugin, listingPrice);

                ItemStack current = top.getItem(entry.getKey());
                if (current == null || current.getType().isAir()) {
                    continue;
                }
                top.setItem(entry.getKey(), AhLore.apply(current, seller, priceText, msLeft, mine));
            }
        } catch (ReflectiveOperationException ex) {
            this.plugin.getLogger().warning("Failed to patch AH menu: " + ex.getMessage());
        }
    }
}
