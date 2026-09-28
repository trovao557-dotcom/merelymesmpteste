/*
 * Decompiled with CFR 0.152.
 */
package dev.nullkeeper.spearconfig.service;

import dev.nullkeeper.spearconfig.model.AttackType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class CooldownManager {
    private final EnumMap<AttackType, Map<UUID, Long>> deadlines = new EnumMap(AttackType.class);
    private final LongSupplier nanoTime;

    public CooldownManager() {
        this(System::nanoTime);
    }

    CooldownManager(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        for (AttackType type : AttackType.values()) {
            this.deadlines.put(type, new HashMap());
        }
    }

    public CooldownResult tryUse(UUID playerId, AttackType type, int seconds) {
        Map<UUID, Long> typeDeadlines;
        long deadline;
        if (seconds <= 0) {
            return CooldownResult.permitted();
        }
        long now = this.nanoTime.getAsLong();
        if (now < (deadline = (typeDeadlines = this.deadlines.get((Object)type)).getOrDefault(playerId, 0L).longValue())) {
            return CooldownResult.blocked(Duration.ofNanos(deadline - now));
        }
        typeDeadlines.put(playerId, now + Duration.ofSeconds(seconds).toNanos());
        return CooldownResult.permitted();
    }

    public void remove(UUID playerId) {
        this.deadlines.values().forEach(map -> map.remove(playerId));
    }

    public void clear() {
        this.deadlines.values().forEach(Map::clear);
    }

    public record CooldownResult(boolean allowed, Duration remaining) {
        private static CooldownResult permitted() {
            return new CooldownResult(true, Duration.ZERO);
        }

        private static CooldownResult blocked(Duration remaining) {
            return new CooldownResult(false, remaining);
        }
    }
}

