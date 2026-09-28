/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.inventory.ItemStack
 */
package dev.nullkeeper.spearconfig.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum SpearTier {
    WOOD("wood", "Wood", Material.WOODEN_SPEAR),
    STONE("stone", "Stone", Material.STONE_SPEAR),
    COPPER("copper", "Copper", Material.COPPER_SPEAR),
    IRON("iron", "Iron", Material.IRON_SPEAR),
    GOLD("gold", "Gold", Material.GOLDEN_SPEAR),
    DIAMOND("diamond", "Diamond", Material.DIAMOND_SPEAR),
    NETHERITE("netherite", "Netherite", Material.NETHERITE_SPEAR);

    private final String key;
    private final String displayName;
    private final Material material;

    private SpearTier(String key, String displayName, Material material) {
        this.key = key;
        this.displayName = displayName;
        this.material = material;
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public Material material() {
        return this.material;
    }

    public static Optional<SpearTier> fromKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(SpearTier.values()).filter(tier -> tier.key.equals(normalized)).findFirst();
    }

    public static Optional<SpearTier> fromMaterial(Material material) {
        return Arrays.stream(SpearTier.values()).filter(tier -> tier.material == material).findFirst();
    }

    public static Optional<SpearTier> fromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        return SpearTier.fromMaterial(item.getType());
    }
}

