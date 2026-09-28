/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.ItemStack
 */
package net.skstudios.orders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.skstudios.orders.Categories;
import net.skstudios.orders.ContentMenu;
import net.skstudios.orders.NewOrderMenu;
import net.skstudios.orders.OrdersPlugin;
import net.skstudios.orders.ViewState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class SelectItemMenu
extends ContentMenu<Material> {
    private final ViewState state;
    private List<Material> shown = List.of();
    private boolean builtAscending;
    private Categories.Category builtCategory;
    private String builtSearch;

    public SelectItemMenu(Player player, OrdersPlugin ordersPlugin) {
        super(player, ordersPlugin, ordersPlugin.selectItemMenuFile());
        this.state = ordersPlugin.state(player);
    }

    @Override
    protected List<Material> entries() {
        boolean bl;
        boolean bl2 = bl = this.builtSearch != null && this.builtAscending == this.state.itemsAscending() && this.builtCategory == this.state.itemCategory() && this.builtSearch.equals(this.state.itemSearch());
        if (bl) {
            return this.shown;
        }
        List<Material> list = this.module.catalogue().view(this.module.categories(), this.state.itemCategory(), this.state.itemsAscending());
        this.shown = this.module.catalogue().search(list, this.state.itemSearch());
        this.builtAscending = this.state.itemsAscending();
        this.builtCategory = this.state.itemCategory();
        this.builtSearch = this.state.itemSearch();
        return this.shown;
    }

    @Override
    protected int page() {
        return this.state.itemPage();
    }

    @Override
    protected void setPage(int n) {
        this.state.setItemPage(n);
    }

    @Override
    protected ItemStack render(Material material) {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        hashMap.put("item", this.module.itemName(material));
        hashMap.put("material", material.name());
        hashMap.put("category", this.module.categories().classify(material).display());
        return this.file.decorate(new ItemStack(material), hashMap);
    }

    @Override
    protected Map<String, String> placeholders() {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        String string = this.config("display.item-order-names.az", "A -> Z");
        String string2 = this.config("display.item-order-names.za", "Z -> A");
        hashMap.put("order_az", this.module.option(string, this.state.itemsAscending()));
        hashMap.put("order_za", this.module.option(string2, !this.state.itemsAscending()));
        hashMap.put("order", this.state.itemsAscending() ? string : string2);
        this.module.addCategoryPlaceholders(hashMap, this.state.itemCategory());
        hashMap.put("search", this.module.searchOrEmpty(this.state.itemSearch()));
        hashMap.put("selected", this.module.itemName(this.state.draftItem()));
        return hashMap;
    }

    private String config(String string, String string2) {
        return this.module.config().yaml().getString(string, string2);
    }

    @Override
    protected void onEntryClick(InventoryClickEvent inventoryClickEvent, Material material) {
        this.state.setDraftItem(material);
        this.module.sounds().play(this.viewer, "select");
        new NewOrderMenu(this.viewer, this.module).open();
    }

    @Override
    protected void onAction(String string2, InventoryClickEvent inventoryClickEvent) {
        switch (string2) {
            case "sort": {
                this.state.flipItemOrder();
                this.module.sounds().play(this.viewer, "toggle");
                this.refresh();
                break;
            }
            case "filter": {
                this.state.setItemCategory(inventoryClickEvent.isRightClick() ? this.module.categories().all() : this.module.categories().next(this.state.itemCategory()));
                this.module.sounds().play(this.viewer, "toggle");
                this.refresh();
                break;
            }
            case "search": {
                if (inventoryClickEvent.isRightClick()) {
                    this.state.setItemSearch("");
                    this.module.sounds().play(this.viewer, "toggle");
                    this.refresh();
                    return;
                }
                this.module.ask(this.viewer, "input.search-item", Map.of(), string -> {
                    this.state.setItemSearch(string == null ? "" : string);
                    new SelectItemMenu(this.viewer, this.module).open();
                });
                break;
            }
            case "back": {
                new NewOrderMenu(this.viewer, this.module).open();
                this.module.sounds().play(this.viewer, "click");
                break;
            }
        }
    }
}

