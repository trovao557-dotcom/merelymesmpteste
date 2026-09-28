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

    private AttackType(String string2, String string3) {
        this.key = string2;
        this.displayName = string3;
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public static Optional<AttackType> fromKey(String string) {
        String string2 = string.toLowerCase(Locale.ROOT);
        for (AttackType attackType : AttackType.values()) {
            if (!attackType.key.equals(string2)) continue;
            return Optional.of(attackType);
        }
        return Optional.empty();
    }
}

