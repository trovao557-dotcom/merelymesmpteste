package me.merelyme.trade;

import java.util.UUID;

public final class TradeRequest {
    public final UUID from;
    public final UUID to;
    public final long expiresAt;

    public TradeRequest(UUID from, UUID to, long expiresAt) {
        this.from = from;
        this.to = to;
        this.expiresAt = expiresAt;
    }

    public boolean expired() {
        return System.currentTimeMillis() > this.expiresAt;
    }
}
