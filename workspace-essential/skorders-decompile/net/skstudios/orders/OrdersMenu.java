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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.skstudios.orders.Categories;
import net.skstudios.orders.ContentMenu;
import net.skstudios.orders.DeliverMenu;
import net.skstudios.orders.Order;
import net.skstudios.orders.OrderStore;
import net.skstudios.orders.OrdersPlugin;
import net.skstudios.orders.SortMode;
import net.skstudios.orders.ViewState;
import net.skstudios.orders.YourOrdersMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class OrdersMenu
extends ContentMenu<Order> {
    private final ViewState state;
    private List<Order> filtered = List.of();
    private long builtVersion = -1L;
    private SortMode builtSort;
    private Categories.Category builtCategory;
    private String builtSearch = "";
    private boolean builtHideFull;

    public OrdersMenu(Player player, OrdersPlugin ordersPlugin) {
        super(player, ordersPlugin, ordersPlugin.overviewMenuFile());
        this.state = ordersPlugin.state(player);
    }

    @Override
    protected List<Order> entries() {
        boolean bl;
        OrderStore orderStore = this.module.store();
        boolean bl2 = bl = this.builtVersion == orderStore.version() && this.builtSort == this.state.sort() && this.builtCategory == this.state.category() && this.builtHideFull == this.module.hideFullOrders() && this.builtSearch.equals(this.state.search());
        if (bl) {
            return this.filtered;
        }
        String string = this.state.search().toLowerCase(Locale.ROOT);
        Categories.Category category = this.state.category();
        boolean bl3 = category == this.module.categories().all();
        ArrayList<Order> arrayList = new ArrayList<Order>();
        for (Order order : orderStore.view(this.state.sort())) {
            if (this.module.hideFullOrders() && order.filled() || !bl3 && this.module.categories().classify(order.item().getType()) != category || !string.isEmpty() && !this.matchesSearch(order, string)) continue;
            arrayList.add(order);
        }
        this.filtered = arrayList;
        this.builtVersion = orderStore.version();
        this.builtSort = this.state.sort();
        this.builtCategory = category;
        this.builtSearch = this.state.search();
        this.builtHideFull = this.module.hideFullOrders();
        return this.filtered;
    }

    private boolean matchesSearch(Order order, String string) {
        if (this.module.itemName(order.item()).toLowerCase(Locale.ROOT).contains(string)) {
            return true;
        }
        return this.module.searchIncludesOwner() && order.ownerName().toLowerCase(Locale.ROOT).contains(string);
    }

    @Override
    protected int page() {
        return this.state.page();
    }

    @Override
    protected void setPage(int n) {
        this.state.setPage(n);
    }

    @Override
    protected ItemStack render(Order order) {
        ItemStack itemStack = order.item().clone();
        itemStack.setAmount(this.module.showRemainingAsStackSize() ? Math.max(1, Math.min(order.remaining(), itemStack.getMaxStackSize())) : 1);
        return this.file.decorate(itemStack, this.module.placeholders(order));
    }

    @Override
    protected Map<String, String> placeholders() {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        this.module.addSortPlaceholders(hashMap, this.state.sort());
        this.module.addCategoryPlaceholders(hashMap, this.state.category());
        hashMap.put("search", this.module.searchOrEmpty(this.state.search()));
        hashMap.put("own", String.valueOf(this.module.store().countOf(this.viewer.getUniqueId())));
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
        if (order2.owner().equals(this.viewer.getUniqueId()) && !this.module.allowOwnOrders()) {
            this.module.message((CommandSender)this.viewer, "delivery.own-order");
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        if (!this.viewer.hasPermission("skorders.deliver")) {
            this.module.message((CommandSender)this.viewer, "no-permission");
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        if (order2.filled()) {
            this.module.message((CommandSender)this.viewer, "delivery.order-full");
            this.module.sounds().play(this.viewer, "error");
            this.refresh();
            return;
        }
        new DeliverMenu(this.viewer, this.module, order2.id()).open();
        this.module.sounds().play(this.viewer, "click");
    }

    @Override
    protected void onAction(String string2, InventoryClickEvent inventoryClickEvent) {
        switch (string2) {
            case "your_orders": {
                new YourOrdersMenu(this.viewer, this.module).open();
                this.module.sounds().play(this.viewer, "click");
                break;
            }
            case "sort": {
                this.state.setSort(inventoryClickEvent.isRightClick() ? SortMode.RECENT : this.state.sort().next());
                this.module.sounds().play(this.viewer, "toggle");
                this.refresh();
                break;
            }
            case "filter": {
                this.state.setCategory(inventoryClickEvent.isRightClick() ? this.module.categories().all() : this.module.categories().next(this.state.category()));
                this.module.sounds().play(this.viewer, "toggle");
                this.refresh();
                break;
            }
            case "search": {
                if (inventoryClickEvent.isRightClick()) {
                    this.state.setSearch("");
                    this.module.sounds().play(this.viewer, "toggle");
                    this.refresh();
                    return;
                }
                this.module.ask(this.viewer, "input.search-order", Map.of(), string -> {
                    this.state.setSearch(string == null ? "" : string);
                    new OrdersMenu(this.viewer, this.module).open();
                });
                break;
            }
        }
    }
}

