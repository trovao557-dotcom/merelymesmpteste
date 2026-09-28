/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.ItemStack
 */
package net.skstudios.orders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.skstudios.orders.ContentMenu;
import net.skstudios.orders.NewOrderMenu;
import net.skstudios.orders.Order;
import net.skstudios.orders.OrdersPlugin;
import net.skstudios.orders.ViewState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class YourOrdersMenu
extends ContentMenu<Order> {
    private final ViewState state;
    private List<Order> mine = List.of();
    private long builtVersion = -1L;

    public YourOrdersMenu(Player player, OrdersPlugin ordersPlugin) {
        super(player, ordersPlugin, ordersPlugin.yourOrdersMenuFile());
        this.state = ordersPlugin.state(player);
    }

    @Override
    protected List<Order> entries() {
        if (this.builtVersion != this.module.store().version()) {
            this.mine = this.module.store().ordersOf(this.viewer.getUniqueId());
            this.builtVersion = this.module.store().version();
        }
        return this.mine;
    }

    @Override
    protected int page() {
        return this.state.ownPage();
    }

    @Override
    protected void setPage(int n) {
        this.state.setOwnPage(n);
    }

    @Override
    protected ItemStack render(Order order) {
        ItemStack itemStack = order.item().clone();
        itemStack.setAmount(Math.max(1, Math.min(Math.max(order.collectable(), 1), itemStack.getMaxStackSize())));
        return this.file.decorate(itemStack, this.module.placeholders(order));
    }

    @Override
    protected Map<String, String> placeholders() {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        int n = 0;
        double d = 0.0;
        for (Order order : this.entries()) {
            n += order.collectable();
            d += order.escrow();
        }
        hashMap.put("own", String.valueOf(this.entries().size()));
        hashMap.put("pending", String.valueOf(n));
        hashMap.put("escrow", this.module.money(d));
        return hashMap;
    }

    @Override
    protected void onEntryClick(InventoryClickEvent inventoryClickEvent, Order order) {
        Order order2 = this.module.store().byId(order.id());
        if (order2 == null) {
            this.module.message((CommandSender)this.viewer, "order.gone");
            this.module.sounds().play(this.viewer, "error");
            this.refresh();
            return;
        }
        if (inventoryClickEvent.isRightClick()) {
            if (this.module.cancelRequiresShift() && !inventoryClickEvent.isShiftClick()) {
                this.module.message((CommandSender)this.viewer, "order.cancel-hint");
                return;
            }
            this.module.cancelOrder(this.viewer, order2);
            this.refresh();
            return;
        }
        this.module.collect(this.viewer, order2);
        this.refresh();
    }

    @Override
    protected void onAction(String string, InventoryClickEvent inventoryClickEvent) {
        switch (string) {
            case "back": {
                this.module.openOverview(this.viewer);
                this.module.sounds().play(this.viewer, "click");
                break;
            }
            case "new_order": {
                if (!this.viewer.hasPermission("skorders.create")) {
                    this.module.message((CommandSender)this.viewer, "no-permission");
                    this.module.sounds().play(this.viewer, "error");
                    return;
                }
                new NewOrderMenu(this.viewer, this.module).open();
                this.module.sounds().play(this.viewer, "click");
                break;
            }
        }
    }
}

