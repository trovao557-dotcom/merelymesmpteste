/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.skstudios.core.gui.ItemSpec
 *  net.skstudios.core.gui.Menu
 *  net.skstudios.core.text.Text
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package net.skstudios.orders;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import net.skstudios.core.gui.ItemSpec;
import net.skstudios.core.gui.Menu;
import net.skstudios.core.text.Text;
import net.skstudios.orders.MenuFile;
import net.skstudios.orders.Order;
import net.skstudios.orders.OrdersPlugin;
import net.skstudios.orders.SelectItemMenu;
import net.skstudios.orders.ViewState;
import net.skstudios.orders.YourOrdersMenu;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class NewOrderMenu
extends Menu
implements OrdersPlugin.OrdersOwned {
    private final OrdersPlugin module;
    private final MenuFile file;
    private final ViewState state;

    public NewOrderMenu(Player player, OrdersPlugin ordersPlugin) {
        super(player);
        this.module = ordersPlugin;
        this.file = ordersPlugin.newOrderMenuFile();
        this.state = ordersPlugin.state(player);
    }

    protected String title() {
        return Text.apply((String)this.file.title(), this.placeholders());
    }

    protected int rows() {
        return this.file.rows();
    }

    private Map<String, String> placeholders() {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        double d = (double)this.state.draftAmount() * this.state.draftPrice();
        hashMap.put("item", this.module.itemName(this.state.draftItem()));
        hashMap.put("material", this.state.draftItem().name());
        hashMap.put("amount", String.valueOf(this.state.draftAmount()));
        hashMap.put("price", this.module.money(this.state.draftPrice()));
        hashMap.put("total", this.module.money(d));
        hashMap.put("balance", this.module.money(this.module.economy().balance((OfflinePlayer)this.viewer)));
        hashMap.put("min_amount", String.valueOf(this.module.minAmount()));
        hashMap.put("max_amount", String.valueOf(this.module.maxAmount()));
        hashMap.put("min_price", this.module.money(this.module.minPrice()));
        hashMap.put("max_price", this.module.money(this.module.maxPrice()));
        return hashMap;
    }

    protected void draw() {
        Map<String, String> map = this.placeholders();
        this.file.config().render(this.getInventory(), map, new char[0]);
        ItemSpec itemSpec = this.file.config().item("select_item");
        if (itemSpec == null) {
            return;
        }
        ItemStack itemStack = NewOrderMenu.retype(itemSpec.build(map), this.state.draftItem());
        Iterator iterator = this.file.config().slotsOfAction("select_item").iterator();
        while (iterator.hasNext()) {
            int n = (Integer)iterator.next();
            this.getInventory().setItem(n, itemStack.clone());
        }
    }

    private static ItemStack retype(ItemStack itemStack, Material material) {
        ItemStack itemStack2 = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        ItemMeta itemMeta2 = itemStack2.getItemMeta();
        if (itemMeta == null || itemMeta2 == null) {
            return itemStack2;
        }
        itemMeta2.displayName(itemMeta.displayName());
        itemMeta2.lore(itemMeta.lore());
        itemMeta2.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        itemStack2.setItemMeta(itemMeta2);
        return itemStack2;
    }

    protected void onClick(InventoryClickEvent inventoryClickEvent) {
        if (!this.clickedTopInventory(inventoryClickEvent)) {
            return;
        }
        String string = this.file.config().actionAt(inventoryClickEvent.getSlot());
        if (string == null) {
            return;
        }
        switch (string) {
            case "select_item": {
                new SelectItemMenu(this.viewer, this.module).open();
                this.module.sounds().play(this.viewer, "click");
                break;
            }
            case "amount": {
                this.askAmount();
                break;
            }
            case "price": {
                this.askPrice();
                break;
            }
            case "confirm": {
                this.confirm();
                break;
            }
            case "cancel": {
                this.module.resetDraft(this.state);
                new YourOrdersMenu(this.viewer, this.module).open();
                this.module.sounds().play(this.viewer, "click");
                break;
            }
            case "close": {
                this.viewer.closeInventory();
                break;
            }
        }
    }

    private void askAmount() {
        Map<String, String> map = Map.of("min", String.valueOf(this.module.minAmount()), "max", String.valueOf(this.module.maxAmount()));
        this.module.ask(this.viewer, "input.amount", map, string -> {
            if (string != null) {
                Integer n = NewOrderMenu.parseInt(string);
                if (n == null) {
                    this.module.message((CommandSender)this.viewer, "input.not-a-number", Map.of("input", string));
                    this.module.sounds().play(this.viewer, "error");
                } else {
                    this.state.setDraftAmount(Math.max(this.module.minAmount(), Math.min(this.module.maxAmount(), n)));
                }
            }
            new NewOrderMenu(this.viewer, this.module).open();
        });
    }

    private void askPrice() {
        Map<String, String> map = Map.of("min", this.module.money(this.module.minPrice()), "max", this.module.money(this.module.maxPrice()));
        this.module.ask(this.viewer, "input.price", map, string -> {
            if (string != null) {
                Double d = NewOrderMenu.parseDouble(string);
                if (d == null) {
                    this.module.message((CommandSender)this.viewer, "input.not-a-number", Map.of("input", string));
                    this.module.sounds().play(this.viewer, "error");
                } else {
                    double d2 = this.module.allowDecimalPrice() ? d : Math.floor(d);
                    this.state.setDraftPrice(Math.max(this.module.minPrice(), Math.min(this.module.maxPrice(), d2)));
                }
            }
            new NewOrderMenu(this.viewer, this.module).open();
        });
    }

    private void confirm() {
        Order order = this.module.createOrder(this.viewer, this.state.draftItem(), this.state.draftAmount(), this.state.draftPrice());
        if (order == null) {
            return;
        }
        this.module.resetDraft(this.state);
        new YourOrdersMenu(this.viewer, this.module).open();
    }

    private static Integer parseInt(String string) {
        try {
            return (int)Math.min(Integer.MAX_VALUE, Math.round(NewOrderMenu.parseNumber(string)));
        }
        catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private static Double parseDouble(String string) {
        try {
            return NewOrderMenu.parseNumber(string);
        }
        catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private static double parseNumber(String string) {
        double d;
        char c;
        int n;
        String string2 = string.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").replace("_", "");
        double d2 = 1.0;
        if (string2.length() > 1 && (n = "kmb".indexOf(c = string2.charAt(string2.length() - 1))) >= 0) {
            d2 = Math.pow(1000.0, n + 1);
            string2 = string2.substring(0, string2.length() - 1);
        }
        if (Double.isNaN(d = Double.parseDouble(string2) * d2) || Double.isInfinite(d)) {
            throw new NumberFormatException(string);
        }
        return d;
    }
}

