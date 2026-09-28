/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.title.Title
 *  net.kyori.adventure.title.Title$Times
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.CraftItemEvent
 *  org.bukkit.event.inventory.PrepareItemCraftEvent
 *  org.bukkit.inventory.CraftingInventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package dev.nullkeeper.spearconfig.listener;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.model.SpearTier;
import dev.nullkeeper.spearconfig.service.SpearTracker;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class CraftLimitListener
implements Listener {
    private final SpearConfigPlugin plugin;
    private final PluginSettings settings;
    private final SpearTracker tracker;

    public CraftLimitListener(SpearConfigPlugin plugin, PluginSettings settings, SpearTracker tracker) {
        this.plugin = plugin;
        this.settings = settings;
        this.tracker = tracker;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        SpearTier tier = CraftLimitListener.recipeTier(event.getRecipe() == null ? null : event.getRecipe().getResult());
        if (tier == null || !this.isAtLimit(tier)) {
            return;
        }
        // Keep the recipe output empty at the cap. A barrier is only a GUI
        // placeholder and must never be exposed as a real item to players.
        event.getInventory().setResult(null);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onCraft(CraftItemEvent event) {
        int remaining;
        SpearTier tier = CraftLimitListener.recipeTier(event.getRecipe().getResult());
        if (tier == null) {
            return;
        }
        int craftedAmount = CraftLimitListener.calculateCraftedAmount(event);
        int limit = this.settings.limit(tier);
        int tracked = this.tracker.count(tier);
        int n = remaining = this.settings.limitEnabled(tier) ? Math.max(0, limit - tracked) : Integer.MAX_VALUE;
        if (remaining == 0 || craftedAmount > remaining) {
            event.setCancelled(true);
            HumanEntity humanEntity = event.getWhoClicked();
            if (humanEntity instanceof Player) {
                Player player = (Player)humanEntity;
                player.sendMessage((Component)Component.text((String)(remaining == 0 ? tier.displayName() + " spear crafting has reached its limit." : "That batch would exceed the " + tier.displayName().toLowerCase() + " spear limit. Craft at most " + remaining + "."), (TextColor)NamedTextColor.RED));
            }
            return;
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSuccessfulCraft(CraftItemEvent event) {
        boolean reachedLimit;
        SpearTier tier = CraftLimitListener.recipeTier(event.getRecipe().getResult());
        if (tier == null) {
            return;
        }
        int craftedAmount = CraftLimitListener.calculateCraftedAmount(event);
        int limit = this.settings.limit(tier);
        this.tracker.increment(tier, craftedAmount);
        boolean bl = reachedLimit = this.settings.limitEnabled(tier) && this.tracker.count(tier) >= limit;
        if (this.settings.chatAnnouncements()) {
            Bukkit.broadcast((Component)Component.text((String)(event.getWhoClicked().getName() + " crafted " + craftedAmount + " " + tier.displayName().toLowerCase() + " spear" + (craftedAmount == 1 ? "" : "s") + "."), (TextColor)this.settings.color().textColor()));
            if (reachedLimit) {
                Bukkit.broadcast((Component)Component.text((String)(tier.displayName() + " spear crafting has reached its limit of " + limit + "."), (TextColor)NamedTextColor.RED));
            }
        }
        if (this.settings.titleAnnouncements()) {
            CraftLimitListener.showTitle((Component)Component.text((String)"Spear Crafted", (TextColor)this.settings.color().textColor()), (Component)Component.text((String)(event.getWhoClicked().getName() + " crafted " + craftedAmount + " " + tier.displayName().toLowerCase() + " spear" + (craftedAmount == 1 ? "" : "s")), (TextColor)NamedTextColor.GRAY));
            if (reachedLimit) {
                this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> CraftLimitListener.showTitle((Component)Component.text((String)"Limit Reached", (TextColor)NamedTextColor.RED), (Component)Component.text((String)(tier.displayName() + " spear limit: " + limit), (TextColor)NamedTextColor.YELLOW)), 40L);
            }
        }
        this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, this.plugin.getVisualService()::refreshNow);
    }

    private boolean isAtLimit(SpearTier tier) {
        return this.settings.limitEnabled(tier) && this.tracker.count(tier) >= this.settings.limit(tier);
    }

    private static SpearTier recipeTier(ItemStack result) {
        return SpearTier.fromItem(result).orElse(null);
    }

    private static int calculateCraftedAmount(CraftItemEvent event) {
        int resultAmount = Math.max(1, event.getRecipe().getResult().getAmount());
        if (!event.isShiftClick()) {
            return resultAmount;
        }
        CraftingInventory inventory = event.getInventory();
        int possibleCrafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : inventory.getMatrix()) {
            if (ingredient == null || ingredient.getType().isAir()) continue;
            possibleCrafts = Math.min(possibleCrafts, ingredient.getAmount());
        }
        if (possibleCrafts == Integer.MAX_VALUE) {
            possibleCrafts = 1;
        }
        return Math.max(1, possibleCrafts * resultAmount);
    }

    private ItemStack blockedIndicator(SpearTier tier) {
        int limit = this.settings.limit(tier);
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.displayName(Component.text((String)(tier.displayName() + " Spear Limit Reached"), (TextColor)NamedTextColor.RED, (TextDecoration[])new TextDecoration[]{TextDecoration.BOLD}).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((TextComponent)Component.text((String)"This recipe is currently unavailable.", (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)("Tracked: " + this.tracker.count(tier) + " / " + limit), (TextColor)NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Run /spearconfig refresh after removing spears.", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        barrier.setItemMeta(meta);
        return barrier;
    }

    private static void showTitle(Component title, Component subtitle) {
        Title display = Title.title((Component)title, (Component)subtitle, (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(2L), (Duration)Duration.ofMillis(500L)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(display);
        }
    }
}
