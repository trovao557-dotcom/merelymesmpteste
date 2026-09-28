/*
 * Decompiled with CFR 0.152.
 */
package net.skstudios.orders;

import java.util.Comparator;
import net.skstudios.orders.Order;

public enum SortMode {
    RECENT("recent", Comparator.comparingLong(Order::createdAt).reversed()),
    PAID("paid", Comparator.comparingDouble(Order::totalValue).reversed()),
    DELIVERED("delivered", Comparator.comparingInt(Order::delivered).reversed()),
    PRICE_PER_ITEM("ppi", Comparator.comparingDouble(Order::price).reversed());

    private final String id;
    private final Comparator<Order> comparator;

    private SortMode(String string2, Comparator<Order> comparator) {
        this.id = string2;
        this.comparator = comparator.thenComparingLong(Order::id);
    }

    public String id() {
        return this.id;
    }

    public Comparator<Order> comparator() {
        return this.comparator;
    }

    public SortMode next() {
        return SortMode.values()[(this.ordinal() + 1) % SortMode.values().length];
    }

    public static SortMode byId(String string) {
        for (SortMode sortMode : SortMode.values()) {
            if (!sortMode.id.equalsIgnoreCase(string)) continue;
            return sortMode;
        }
        return RECENT;
    }
}

