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

    public int count(SpearTier tier) {
        return this.tracked.getOrDefault((Object)tier, 0);
    }

    public void increment(SpearTier tier, int amount) {
        if (amount <= 0) {
            return;
        }
        this.tracked.merge(tier, amount, Integer::sum);
    }

    public boolean playerInventoryContainsSpear(Player player) {
        return SpearTracker.countAll(player.getInventory().getContents()) > 0;
    }

    public Map<SpearTier, Integer> snapshot() {
        return Map.copyOf(this.tracked);
    }

    public static Map<SpearTier, Integer> countContents(ItemStack[] contents) {
        EnumMap<SpearTier, Integer> counts = SpearTracker.emptyCounts();
        SpearTracker.countContents(contents, counts, 0);
        return Map.copyOf(counts);
    }

    public static int countAll(ItemStack[] contents) {
        return SpearTracker.countContents(contents).values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void countContents(ItemStack[] contents, EnumMap<SpearTier, Integer> counts, int depth) {
        if (contents == null || depth > 32) {
            return;
        }
        for (ItemStack item : contents) {
            BlockStateMeta blockStateMeta;
            if (item == null || item.getType().isAir()) continue;
            SpearTier.fromItem(item).ifPresent(tier -> counts.merge((SpearTier)((Object)tier), item.getAmount(), Integer::sum));
            ItemMeta itemMeta = item.getItemMeta();
            if (!(itemMeta instanceof BlockStateMeta) || !((itemMeta = (blockStateMeta = (BlockStateMeta)itemMeta).getBlockState()) instanceof ShulkerBox)) continue;
            ShulkerBox shulkerBox = (ShulkerBox)itemMeta;
            SpearTracker.countContents(shulkerBox.getInventory().getContents(), counts, depth + 1);
        }
    }

    private void merge(Map<SpearTier, Integer> counts) {
        counts.forEach((tier, amount) -> this.tracked.merge((SpearTier)((Object)tier), (Integer)amount, Integer::sum));
    }

    private void reset() {
        this.tracked.clear();
        this.tracked.putAll(SpearTracker.emptyCounts());
    }

    private static EnumMap<SpearTier, Integer> emptyCounts() {
        EnumMap<SpearTier, Integer> counts = new EnumMap<SpearTier, Integer>(SpearTier.class);
        for (SpearTier tier : SpearTier.values()) {
            counts.put(tier, 0);
        }
        return counts;
    }
}

