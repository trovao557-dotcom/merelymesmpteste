/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.inventory.ItemStack
 */
package net.skstudios.orders;

import java.util.UUID;
import net.skstudios.orders.Decoration;
import org.bukkit.inventory.ItemStack;

public final class Order {
    private final long id;
    private final UUID owner;
    private String ownerName;
    private final ItemStack item;
    private final int amount;
    private final double price;
    private final long createdAt;
    private int delivered;
    private int collectable;
    private double escrow;

    public Order(long l, UUID uUID, String string, ItemStack itemStack, int n, double d, long l2) {
        this(l, uUID, string, itemStack, n, d, l2, 0, 0, (double)n * d);
    }

    public Order(long l, UUID uUID, String string, ItemStack itemStack, int n, double d, long l2, int n2, int n3, double d2) {
        this.id = l;
        this.owner = uUID;
        this.ownerName = string;
        this.item = itemStack.asOne();
        this.amount = n;
        this.price = d;
        this.createdAt = l2;
        this.delivered = n2;
        this.collectable = n3;
        this.escrow = d2;
    }

    public long id() {
        return this.id;
    }

    public UUID owner() {
        return this.owner;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public void setOwnerName(String string) {
        if (string != null && !string.isEmpty()) {
            this.ownerName = string;
        }
    }

    public ItemStack item() {
        return this.item;
    }

    public int amount() {
        return this.amount;
    }

    public double price() {
        return this.price;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public int delivered() {
        return this.delivered;
    }

    public int collectable() {
        return this.collectable;
    }

    public double escrow() {
        return this.escrow;
    }

    public int remaining() {
        return Math.max(0, this.amount - this.delivered);
    }

    public double totalValue() {
        return (double)this.amount * this.price;
    }

    public boolean filled() {
        return this.delivered >= this.amount;
    }

    public boolean finished() {
        return this.filled() && this.collectable <= 0;
    }

    public boolean matches(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir() && this.item.isSimilar(Decoration.plain(itemStack));
    }

    public Delivery book(int n) {
        int n2 = Math.min(n, this.remaining());
        if (n2 <= 0) {
            return Delivery.NONE;
        }
        double d = Math.min(this.price * (double)n2, this.escrow);
        this.delivered += n2;
        this.collectable += n2;
        this.escrow -= d;
        return new Delivery(n2, d);
    }

    public void rollback(Delivery delivery) {
        if (delivery == null || delivery.isEmpty()) {
            return;
        }
        this.delivered -= delivery.count();
        this.collectable -= delivery.count();
        this.escrow += delivery.pay();
    }

    public int takeCollectable(int n) {
        int n2 = Math.min(Math.max(0, n), this.collectable);
        this.collectable -= n2;
        return n2;
    }

    public double refund() {
        double d = this.escrow;
        this.escrow = 0.0;
        return d;
    }

    public record Delivery(int count, double pay) {
        public static final Delivery NONE = new Delivery(0, 0.0);

        public boolean isEmpty() {
            return this.count <= 0;
        }
    }
}

