package me.merelyme.trade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class TradeSession {
    public final UUID leftId;
    public final UUID rightId;
    public boolean leftReady;
    public boolean rightReady;
    public boolean completing;
    public boolean cancelled;
    public boolean syncing;

    public TradeSession(Player left, Player right) {
        this.leftId = left.getUniqueId();
        this.rightId = right.getUniqueId();
    }

    public Player left() {
        return Bukkit.getPlayer(this.leftId);
    }

    public Player right() {
        return Bukkit.getPlayer(this.rightId);
    }

    public boolean isLeft(Player player) {
        return player.getUniqueId().equals(this.leftId);
    }

    public Player partner(Player player) {
        return this.isLeft(player) ? this.right() : this.left();
    }

    public boolean ready(Player player) {
        return this.isLeft(player) ? this.leftReady : this.rightReady;
    }

    public void setReady(Player player, boolean ready) {
        if (this.isLeft(player)) {
            this.leftReady = ready;
        } else {
            this.rightReady = ready;
        }
    }

    public void clearReady() {
        this.leftReady = false;
        this.rightReady = false;
    }

    public boolean bothReady() {
        return this.leftReady && this.rightReady;
    }

    public boolean bothOnline() {
        Player a = this.left();
        Player b = this.right();
        return a != null && b != null && a.isOnline() && b.isOnline();
    }
}
