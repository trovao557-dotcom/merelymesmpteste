package me.merelyme.orderenchants;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class OrderCreateListener implements Listener {
    private final MerelyOrderEnchants plugin;

    OrderCreateListener(MerelyOrderEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConfirm(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder == null || !holder.getClass().getSimpleName().equals("NewOrderMenu")) {
            return;
        }
        if (!"confirm".equals(actionAt(holder, event.getRawSlot()))) {
            return;
        }
        ItemStack pending = this.plugin.peekPendingBook(player);
        if (pending == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> replaceLatestOrder(player, pending.clone()));
    }

    private void replaceLatestOrder(Player player, ItemStack book) {
        try {
            Object ordersPlugin = Bukkit.getPluginManager().getPlugin("SKOrders");
            Object store = ordersPlugin.getClass().getMethod("store").invoke(ordersPlugin);
            @SuppressWarnings("unchecked")
            List<Object> orders = (List<Object>) store.getClass().getMethod("ordersOf", java.util.UUID.class)
                    .invoke(store, player.getUniqueId());
            if (orders.isEmpty()) {
                return;
            }
            Object oldOrder = orders.stream()
                    .max(Comparator.comparingLong(o -> {
                        try {
                            return ((Number) o.getClass().getMethod("id").invoke(o)).longValue();
                        } catch (ReflectiveOperationException e) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
            if (oldOrder == null) {
                return;
            }

            ItemStack current = (ItemStack) oldOrder.getClass().getMethod("item").invoke(oldOrder);
            if (current.getType() != Material.ENCHANTED_BOOK) {
                this.plugin.clearPending(player);
                return;
            }

            long id = ((Number) oldOrder.getClass().getMethod("id").invoke(oldOrder)).longValue();
            java.util.UUID owner = (java.util.UUID) oldOrder.getClass().getMethod("owner").invoke(oldOrder);
            String ownerName = (String) oldOrder.getClass().getMethod("ownerName").invoke(oldOrder);
            int amount = (Integer) oldOrder.getClass().getMethod("amount").invoke(oldOrder);
            double price = ((Number) oldOrder.getClass().getMethod("price").invoke(oldOrder)).doubleValue();
            long createdAt = ((Number) oldOrder.getClass().getMethod("createdAt").invoke(oldOrder)).longValue();
            int delivered = (Integer) oldOrder.getClass().getMethod("delivered").invoke(oldOrder);
            int collectable = (Integer) oldOrder.getClass().getMethod("collectable").invoke(oldOrder);
            double escrow = ((Number) oldOrder.getClass().getMethod("escrow").invoke(oldOrder)).doubleValue();

            Class<?> orderClass = Class.forName("net.skstudios.orders.Order");
            Constructor<?> ctor = orderClass.getConstructor(
                    long.class, java.util.UUID.class, String.class, ItemStack.class,
                    int.class, double.class, long.class, int.class, int.class, double.class);
            Object fixed = ctor.newInstance(id, owner, ownerName, book, amount, price, createdAt, delivered, collectable, escrow);

            Field byId = store.getClass().getDeclaredField("byId");
            byId.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, Object> map = (Map<Long, Object>) byId.get(store);
            map.put(id, fixed);
            store.getClass().getMethod("touch").invoke(store);
            store.getClass().getMethod("save", boolean.class, boolean.class).invoke(store, false, false);
            this.plugin.takePendingBook(player);
        } catch (ReflectiveOperationException ex) {
            this.plugin.getLogger().warning("Failed to patch order item: " + ex.getMessage());
        }
    }

    private static String actionAt(Object menuHolder, int slot) {
        try {
            Field fileField = menuHolder.getClass().getDeclaredField("file");
            fileField.setAccessible(true);
            Object file = fileField.get(menuHolder);
            Object config = file.getClass().getMethod("config").invoke(file);
            return (String) config.getClass().getMethod("actionAt", int.class).invoke(config, slot);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
