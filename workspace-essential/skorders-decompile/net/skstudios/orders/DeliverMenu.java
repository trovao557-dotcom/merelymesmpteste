/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.skstudios.core.gui.Menu
 *  net.skstudios.core.text.Text
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryAction
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 */
package net.skstudios.orders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.skstudios.core.gui.Menu;
import net.skstudios.core.text.Text;
import net.skstudios.orders.MenuFile;
import net.skstudios.orders.Order;
import net.skstudios.orders.OrdersPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class DeliverMenu
extends Menu
implements OrdersPlugin.OrdersOwned {
    private final OrdersPlugin module;
    private final MenuFile file;
    private final long orderId;
    private boolean closed;

    public DeliverMenu(Player player, OrdersPlugin ordersPlugin, long l) {
        super(player);
        this.module = ordersPlugin;
        this.file = ordersPlugin.deliverMenuFile();
        this.orderId = l;
    }

    private Order order() {
        return this.module.store().byId(this.orderId);
    }

    protected String title() {
        return Text.apply((String)this.file.title(), this.placeholders());
    }

    protected int rows() {
        return this.file.rows();
    }

    protected boolean cancelByDefault() {
        return false;
    }

    private Map<String, String> placeholders() {
        Order order = this.order();
        Map<Object, Object> map = order != null ? this.module.placeholders(order) : new HashMap();
        int n = order == null ? 0 : this.depositedMatching(order);
        map.put("deposited", String.valueOf(n));
        map.put("payout", this.module.money(order == null ? 0.0 : (double)n * order.price()));
        map.put("holding", String.valueOf(order == null ? 0 : this.holding(order)));
        return map;
    }

    private int holding(Order order) {
        int n = 0;
        for (ItemStack itemStack : this.viewer.getInventory().getStorageContents()) {
            if (itemStack == null || !order.matches(itemStack)) continue;
            n += itemStack.getAmount();
        }
        return n;
    }

    private int depositedMatching(Order order) {
        Inventory inventory = this.inventoryOrNull();
        if (inventory == null) {
            return 0;
        }
        int n = 0;
        for (int n2 : this.file.contentSlots()) {
            ItemStack itemStack = inventory.getItem(n2);
            if (itemStack == null || !order.matches(itemStack)) continue;
            n += itemStack.getAmount();
        }
        return n;
    }

    protected void draw() {
        this.file.config().render(this.getInventory(), this.placeholders(), new char[]{this.file.contentSymbol()});
    }

    private boolean isDepositSlot(int n) {
        return this.file.contentSlots().contains(n);
    }

    protected void onClick(InventoryClickEvent inventoryClickEvent) {
        if (inventoryClickEvent.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            inventoryClickEvent.setCancelled(true);
            return;
        }
        if (!this.clickedTopInventory(inventoryClickEvent)) {
            if (inventoryClickEvent.isShiftClick()) {
                inventoryClickEvent.setCancelled(true);
                this.quickDeposit(inventoryClickEvent.getCurrentItem());
            }
            return;
        }
        if (this.isDepositSlot(inventoryClickEvent.getSlot())) {
            return;
        }
        inventoryClickEvent.setCancelled(true);
        String string = this.file.config().actionAt(inventoryClickEvent.getSlot());
        if (string == null) {
            return;
        }
        switch (string) {
            case "confirm": {
                this.submit(false);
                break;
            }
            case "fill": {
                this.fillFromInventory();
                break;
            }
            case "back": {
                this.module.openOverview(this.viewer);
                break;
            }
            case "close": {
                this.viewer.closeInventory();
                break;
            }
        }
    }

    protected void onClose(InventoryCloseEvent inventoryCloseEvent) {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.module.submitOnClose()) {
            this.submit(true);
            return;
        }
        for (ItemStack itemStack : this.drain()) {
            this.module.giveOrDrop(this.viewer, itemStack);
        }
    }

    private List<ItemStack> drain() {
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
        for (int n : this.file.contentSlots()) {
            ItemStack itemStack = this.getInventory().getItem(n);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            this.getInventory().setItem(n, null);
            arrayList.add(itemStack);
        }
        return arrayList;
    }

    private void submit(boolean bl) {
        List<ItemStack> list = this.drain();
        if (list.isEmpty()) {
            if (!bl) {
                this.module.message((CommandSender)this.viewer, "delivery.nothing-deposited");
                this.module.sounds().play(this.viewer, "error");
            }
            return;
        }
        OrdersPlugin.Settlement settlement = this.module.deliver(this.viewer, this.order(), list);
        for (ItemStack itemStack : settlement.leftovers()) {
            this.module.giveOrDrop(this.viewer, itemStack);
        }
        if (settlement.accepted() <= 0 && !bl) {
            this.module.message((CommandSender)this.viewer, "delivery.nothing-accepted");
            this.module.sounds().play(this.viewer, "error");
        }
        if (!bl) {
            Order order = this.order();
            if (order == null || order.filled()) {
                this.module.openOverview(this.viewer);
                return;
            }
            this.refresh();
        }
    }

    private void fillFromInventory() {
        Order order = this.order();
        if (order == null) {
            this.module.message((CommandSender)this.viewer, "order.gone");
            this.module.sounds().play(this.viewer, "error");
            this.viewer.closeInventory();
            return;
        }
        int n = order.remaining() - this.depositedMatching(order);
        if (n <= 0) {
            this.module.message((CommandSender)this.viewer, "delivery.enough-deposited");
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        int n2 = 0;
        ItemStack[] itemStackArray = this.viewer.getInventory().getStorageContents();
        for (int i = 0; i < itemStackArray.length && n > 0; ++i) {
            ItemStack itemStack = itemStackArray[i];
            if (itemStack == null || !order.matches(itemStack)) continue;
            int n3 = this.placeIntoDeposit(itemStack, Math.min(n, itemStack.getAmount()));
            if (n3 <= 0) break;
            itemStack.setAmount(itemStack.getAmount() - n3);
            if (itemStack.getAmount() <= 0) {
                itemStackArray[i] = null;
            }
            n -= n3;
            n2 += n3;
        }
        this.viewer.getInventory().setStorageContents(itemStackArray);
        if (n2 <= 0) {
            this.module.message((CommandSender)this.viewer, "delivery.nothing-to-fill", Map.of("item", this.module.itemName(order.item())));
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        this.module.sounds().play(this.viewer, "click");
        this.refresh();
    }

    private void quickDeposit(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        Order order = this.order();
        if (order == null) {
            this.module.message((CommandSender)this.viewer, "order.gone");
            this.module.sounds().play(this.viewer, "error");
            this.viewer.closeInventory();
            return;
        }
        if (!order.matches(itemStack)) {
            this.module.message((CommandSender)this.viewer, "delivery.wrong-item", Map.of("item", this.module.itemName(order.item())));
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        int n = order.remaining() - this.depositedMatching(order);
        if (n <= 0) {
            this.module.message((CommandSender)this.viewer, "delivery.enough-deposited");
            this.module.sounds().play(this.viewer, "error");
            return;
        }
        int n2 = this.placeIntoDeposit(itemStack, Math.min(n, itemStack.getAmount()));
        if (n2 <= 0) {
            return;
        }
        itemStack.setAmount(itemStack.getAmount() - n2);
        this.module.sounds().play(this.viewer, "click");
        this.refresh();
    }

    private int placeIntoDeposit(ItemStack itemStack, int n) {
        int n2 = 0;
        int n3 = Math.max(1, itemStack.getMaxStackSize());
        for (int n4 : this.file.contentSlots()) {
            if (n2 >= n) break;
            ItemStack itemStack2 = this.getInventory().getItem(n4);
            if (itemStack2 == null || itemStack2.getType().isAir()) {
                ItemStack itemStack3 = itemStack.clone();
                itemStack3.setAmount(Math.min(n - n2, n3));
                this.getInventory().setItem(n4, itemStack3);
                n2 += itemStack3.getAmount();
                continue;
            }
            if (!itemStack2.isSimilar(itemStack) || itemStack2.getAmount() >= n3) continue;
            int n5 = Math.min(n3 - itemStack2.getAmount(), n - n2);
            itemStack2.setAmount(itemStack2.getAmount() + n5);
            this.getInventory().setItem(n4, itemStack2);
            n2 += n5;
        }
        return n2;
    }
}

