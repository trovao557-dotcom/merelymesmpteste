/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.skstudios.core.gui.Menu
 *  net.skstudios.core.text.Text
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.ItemStack
 */
package net.skstudios.orders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.skstudios.core.gui.Menu;
import net.skstudios.core.text.Text;
import net.skstudios.orders.MenuFile;
import net.skstudios.orders.OrdersPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

abstract class ContentMenu<T>
extends Menu
implements OrdersPlugin.OrdersOwned {
    protected final OrdersPlugin module;
    protected final MenuFile file;
    private final Map<Integer, T> drawn = new HashMap<Integer, T>();
    private int lastSize;

    protected ContentMenu(Player player, OrdersPlugin ordersPlugin, MenuFile menuFile) {
        super(player);
        this.module = ordersPlugin;
        this.file = menuFile;
    }

    protected abstract List<T> entries();

    protected abstract int page();

    protected abstract void setPage(int var1);

    protected abstract ItemStack render(T var1);

    protected abstract void onEntryClick(InventoryClickEvent var1, T var2);

    protected abstract void onAction(String var1, InventoryClickEvent var2);

    protected abstract Map<String, String> placeholders();

    protected final String title() {
        return Text.apply((String)this.file.title(), this.allPlaceholders(this.entries().size()));
    }

    protected final int rows() {
        return this.file.rows();
    }

    private Map<String, String> allPlaceholders(int n) {
        Map<String, String> map = this.placeholders();
        map.put("page", String.valueOf(this.page() + 1));
        map.put("max_page", String.valueOf(Math.max(1, this.pageCount(n))));
        map.put("count", String.valueOf(n));
        return map;
    }

    private int pageCount(int n) {
        return (int)Math.ceil((double)n / (double)this.file.pageSize());
    }

    protected final void draw() {
        List<T> list = this.entries();
        this.lastSize = list.size();
        int n = Math.max(0, this.pageCount(this.lastSize) - 1);
        if (this.page() > n) {
            this.setPage(n);
        }
        Map<String, String> map = this.allPlaceholders(this.lastSize);
        this.file.config().render(this.getInventory(), map, new char[]{this.file.contentSymbol()});
        this.file.config().paging(this.getInventory(), Math.max(1, this.pageCount(this.lastSize)));
        this.drawn.clear();
        List<Integer> list2 = this.file.contentSlots();
        int n2 = this.page() * this.file.pageSize();
        for (int i = 0; i < list2.size(); ++i) {
            int n3 = list2.get(i);
            int n4 = n2 + i;
            if (n4 >= this.lastSize) {
                this.getInventory().setItem(n3, null);
                continue;
            }
            T t = list.get(n4);
            this.getInventory().setItem(n3, this.render(t));
            this.drawn.put(n3, t);
        }
    }

    protected void onClick(InventoryClickEvent inventoryClickEvent) {
        if (!this.clickedTopInventory(inventoryClickEvent)) {
            return;
        }
        T t = this.drawn.get(inventoryClickEvent.getSlot());
        if (t != null) {
            this.onEntryClick(inventoryClickEvent, t);
            return;
        }
        String string = this.file.config().actionAt(inventoryClickEvent.getSlot());
        if (string == null) {
            return;
        }
        switch (string) {
            case "previous": {
                this.turn(-1);
                break;
            }
            case "next": {
                this.turn(1);
                break;
            }
            case "refresh": {
                this.refresh();
                break;
            }
            case "close": {
                this.viewer.closeInventory();
                break;
            }
            default: {
                this.onAction(string, inventoryClickEvent);
            }
        }
    }

    private void turn(int n) {
        int n2 = this.page() + n;
        if (n2 < 0 || n2 > Math.max(0, this.pageCount(this.lastSize) - 1)) {
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        this.setPage(n2);
        this.module.sounds().play(this.viewer, "page");
        this.refresh();
    }
}

