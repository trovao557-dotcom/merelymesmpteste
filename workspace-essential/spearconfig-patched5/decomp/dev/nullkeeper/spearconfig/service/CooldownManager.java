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

    CooldownManager(LongSupplier longSupplier) {
        this.nanoTime = longSupplier;
        for (AttackType attackType : AttackType.values()) {
            this.deadlines.put(attackType, new HashMap());
        }
    }

    public CooldownResult tryUse(UUID uUID, AttackType attackType, int n) {
        Map<UUID, Long> map;
        long l;
        if (n <= 0) {
            return CooldownResult.permitted();
        }
        long l2 = this.nanoTime.getAsLong();
        if (l2 < (l = (map = this.deadlines.get((Object)attackType)).getOrDefault(uUID, 0L).longValue())) {
            return CooldownResult.blocked(Duration.ofNanos(l - l2));
        }
        map.put(uUID, l2 + Duration.ofSeconds(n).toNanos());
        return CooldownResult.permitted();
    }

    public void remove(UUID uUID) {
        this.deadlines.values().forEach(map -> map.remove(uUID));
    }

    public void clear() {
        this.deadlines.values().forEach(Map::clear);
    }

    public record CooldownResult(boolean allowed, Duration remaining) {
        private static CooldownResult permitted() {
            return new CooldownResult(true, Duration.ZERO);
        }

        private static CooldownResult blocked(Duration duration) {
            return new CooldownResult(false, duration);
        }
    }
}

