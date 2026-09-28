/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.block.ShulkerBox
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.BlockStateMeta
 *  org.bukkit.inventory.meta.ItemMeta
 */
package dev.nullkeeper.spearconfig.service;

import dev.nullkeeper.spearconfig.model.SpearTier;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class SpearTracker {
    private static final int MAXIMUM_CONTAINER_DEPTH = 32;
    private final EnumMap<SpearTier, Integer> tracked = new EnumMap(SpearTier.class);

    public SpearTracker() {
        this.reset();
    }

    public Map<SpearTier, Integer> refreshOnlinePlayers() {
        this.reset();
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.merge(SpearTracker.countContents(player.getInventory().getContents()));
            this.merge(SpearTracker.countContents(player.getEnderChest().getContents()));
        }
        return this.snapshot();
    }

    public int count(SpearTier spearTier) {
        return this.tracked.getOrDefault((Object)spearTier, 0);
    }

    public void increment(SpearTier spearTier, int n) {
        if (n <= 0) {
            return;
        }
        this.tracked.merge(spearTier, n, Integer::sum);
    }

    public boolean playerInventoryContainsSpear(Player player) {
        return SpearTracker.countAll(player.getInventory().getContents()) > 0;
    }

    public Map<SpearTier, Integer> snapshot() {
        return Map.copyOf(this.tracked);
    }

    public static Map<SpearTier, Integer> countContents(ItemStack[] itemStackArray) {
        EnumMap<SpearTier, Integer> enumMap = SpearTracker.emptyCounts();
        SpearTracker.countContents(itemStackArray, enumMap, 0);
        return Map.copyOf(enumMap);
    }

    public static int countAll(ItemStack[] itemStackArray) {
        return SpearTracker.countContents(itemStackArray).values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void countContents(ItemStack[] itemStackArray, EnumMap<SpearTier, Integer> enumMap, int n) {
        if (itemStackArray == null || n > 32) {
            return;
        }
        for (ItemStack itemStack : itemStackArray) {
            BlockStateMeta blockStateMeta;
            if (itemStack == null || itemStack.getType().isAir()) continue;
            SpearTier.fromItem(itemStack).ifPresent(spearTier -> enumMap.merge((SpearTier)((Object)spearTier), itemStack.getAmount(), Integer::sum));
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (!(itemMeta instanceof BlockStateMeta) || !((blockStateMeta = (BlockStateMeta)itemMeta).getBlockState() instanceof ShulkerBox)) continue;
            ShulkerBox shulkerBox = (ShulkerBox)blockStateMeta.getBlockState();
            SpearTracker.countContents(shulkerBox.getInventory().getContents(), enumMap, n + 1);
        }
    }

    private void merge(Map<SpearTier, Integer> map) {
        map.forEach((spearTier, n) -> this.tracked.merge((SpearTier)((Object)spearTier), (Integer)n, Integer::sum));
    }

    private void reset() {
        this.tracked.clear();
        this.tracked.putAll(SpearTracker.emptyCounts());
    }

    private static EnumMap<SpearTier, Integer> emptyCounts() {
        EnumMap<SpearTier, Integer> enumMap = new EnumMap<SpearTier, Integer>(SpearTier.class);
        for (SpearTier spearTier : SpearTier.values()) {
            enumMap.put(spearTier, 0);
        }
        return enumMap;
    }
}

