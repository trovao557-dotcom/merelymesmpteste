package me.merelyme.orderenchants;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class OrderSelectListener implements Listener {
    private static final int CONTENT_ROWS = 45;
    private final MerelyOrderEnchants plugin;

    OrderSelectListener(MerelyOrderEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder == null || !holder.getClass().getSimpleName().equals("SelectItemMenu")) {
            return;
        }

        try {
            Object ordersPlugin = holder.getClass().getDeclaredField("module").get(holder);
            String action = actionAt(holder, event.getRawSlot());

            if ("filter".equals(action) && !event.isRightClick()) {
                Object state = ordersPlugin.getClass().getMethod("state", Player.class).invoke(ordersPlugin, player);
                Object categories = ordersPlugin.getClass().getMethod("categories").invoke(ordersPlugin);
                Object current = state.getClass().getMethod("itemCategory").invoke(state);
                Object next = categories.getClass().getMethod("next", current.getClass()).invoke(categories, current);
                String nextId = (String) next.getClass().getMethod("id").invoke(next);
                if ("books".equals(nextId)) {
                    event.setCancelled(true);
                    state.getClass().getMethod("setItemCategory", current.getClass()).invoke(state, next);
                    Bukkit.getScheduler().runTask(this.plugin, () -> new OrderEnchantMenu(this.plugin, player).open());
                    return;
                }
            }

            if (event.getRawSlot() < CONTENT_ROWS) {
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType() == Material.ENCHANTED_BOOK) {
                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(this.plugin, () -> new OrderEnchantMenu(this.plugin, player).open());
                }
            }
        } catch (ReflectiveOperationException ex) {
            this.plugin.getLogger().warning("SelectItemMenu hook failed: " + ex.getMessage());
        }
    }

    static void openNewOrderForm(MerelyOrderEnchants plugin, Player player, ItemStack book) {
        try {
            Object ordersPlugin = Bukkit.getPluginManager().getPlugin("SKOrders");
            Object state = ordersPlugin.getClass().getMethod("state", Player.class).invoke(ordersPlugin, player);
            state.getClass().getMethod("setDraftItem", Material.class).invoke(state, Material.ENCHANTED_BOOK);
            plugin.setPendingBook(player, book);
            Class<?> menuClass = Class.forName("net.skstudios.orders.NewOrderMenu");
            Object menu = menuClass.getConstructor(Player.class, ordersPlugin.getClass()).newInstance(player, ordersPlugin);
            menuClass.getMethod("open").invoke(menu);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Failed to open order form: " + ex.getMessage());
        }
    }

    private static String actionAt(Object menuHolder, int slot) {
        try {
            java.lang.reflect.Field fileField = menuHolder.getClass().getDeclaredField("file");
            fileField.setAccessible(true);
            Object file = fileField.get(menuHolder);
            Object config = file.getClass().getMethod("config").invoke(file);
            return (String) config.getClass().getMethod("actionAt", int.class).invoke(config, slot);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
