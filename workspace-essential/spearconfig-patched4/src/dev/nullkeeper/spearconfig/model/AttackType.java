/*
 * Decompiled with CFR 0.152.
 */
package dev.nullkeeper.spearconfig.model;

import java.util.Locale;
import java.util.Optional;

public enum AttackType {
    JAB("jab", "Jab"),
    LUNGE("lunge", "Lunge");

    private final String key;
    private final String displayName;

    private AttackType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public static Optional<AttackType> fromKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (AttackType type : AttackType.values()) {
            if (!type.key.equals(normalized)) continue;
            return Optional.of(type);
        }
        return Optional.empty();
    }
}

