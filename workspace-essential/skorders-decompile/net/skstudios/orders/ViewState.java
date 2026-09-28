/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package net.skstudios.orders;

import net.skstudios.orders.Categories;
import net.skstudios.orders.SortMode;
import org.bukkit.Material;

public final class ViewState {
    private SortMode sort = SortMode.RECENT;
    private Categories.Category category;
    private String search = "";
    private int page;
    private int ownPage;
    private boolean itemsAscending = true;
    private Categories.Category itemCategory;
    private String itemSearch = "";
    private int itemPage;
    private Material draftItem;
    private int draftAmount;
    private double draftPrice;
    private long lastCreated;

    public ViewState(Categories.Category category, Material material, int n, double d) {
        this.category = category;
        this.itemCategory = category;
        this.draftItem = material;
        this.draftAmount = n;
        this.draftPrice = d;
    }

    public SortMode sort() {
        return this.sort;
    }

    public void setSort(SortMode sortMode) {
        this.sort = sortMode;
        this.page = 0;
    }

    public Categories.Category category() {
        return this.category;
    }

    public void setCategory(Categories.Category category) {
        this.category = category;
        this.page = 0;
    }

    public String search() {
        return this.search;
    }

    public void setSearch(String string) {
        this.search = string == null ? "" : string;
        this.page = 0;
    }

    public int page() {
        return this.page;
    }

    public void setPage(int n) {
        this.page = Math.max(0, n);
    }

    public int ownPage() {
        return this.ownPage;
    }

    public void setOwnPage(int n) {
        this.ownPage = Math.max(0, n);
    }

    public boolean itemsAscending() {
        return this.itemsAscending;
    }

    public void flipItemOrder() {
        this.itemsAscending = !this.itemsAscending;
        this.itemPage = 0;
    }

    public Categories.Category itemCategory() {
        return this.itemCategory;
    }

    public void setItemCategory(Categories.Category category) {
        this.itemCategory = category;
        this.itemPage = 0;
    }

    public String itemSearch() {
        return this.itemSearch;
    }

    public void setItemSearch(String string) {
        this.itemSearch = string == null ? "" : string;
        this.itemPage = 0;
    }

    public int itemPage() {
        return this.itemPage;
    }

    public void setItemPage(int n) {
        this.itemPage = Math.max(0, n);
    }

    public Material draftItem() {
        return this.draftItem;
    }

    public void setDraftItem(Material material) {
        this.draftItem = material;
    }

    public int draftAmount() {
        return this.draftAmount;
    }

    public void setDraftAmount(int n) {
        this.draftAmount = n;
    }

    public double draftPrice() {
        return this.draftPrice;
    }

    public void setDraftPrice(double d) {
        this.draftPrice = d;
    }

    public long lastCreated() {
        return this.lastCreated;
    }

    public void markCreated() {
        this.lastCreated = System.currentTimeMillis();
    }

    public void resetDraft(Material material, int n, double d) {
        this.draftItem = material;
        this.draftAmount = n;
        this.draftPrice = d;
    }
}

