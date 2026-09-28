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

    public CraftLimitListener(SpearConfigPlugin spearConfigPlugin, PluginSettings pluginSettings, SpearTracker spearTracker) {
        this.plugin = spearConfigPlugin;
        this.settings = pluginSettings;
        this.tracker = spearTracker;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent prepareItemCraftEvent) {
        SpearTier spearTier = CraftLimitListener.recipeTier(prepareItemCraftEvent.getRecipe() == null ? null : prepareItemCraftEvent.getRecipe().getResult());
        if (spearTier == null || !this.isAtLimit(spearTier)) {
            return;
        }
        prepareItemCraftEvent.getInventory().setResult(null);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onCraft(CraftItemEvent craftItemEvent) {
        SpearTier spearTier = CraftLimitListener.recipeTier(craftItemEvent.getRecipe().getResult());
        if (spearTier == null) {
            return;
        }
        int n = CraftLimitListener.calculateCraftedAmount(craftItemEvent);
        int n2 = this.settings.limit(spearTier);
        int n3 = this.tracker.count(spearTier);
        int n4 = this.settings.limitEnabled(spearTier) ? Math.max(0, n2 - n3) : Integer.MAX_VALUE;
        int n5 = n4;
        if (n4 == 0 || n > n4) {
            craftItemEvent.setCancelled(true);
            HumanEntity humanEntity = craftItemEvent.getWhoClicked();
            if (humanEntity instanceof Player) {
                Player player = (Player)humanEntity;
                player.sendMessage((Component)Component.text((String)(n4 == 0 ? spearTier.displayName() + " spear crafting has reached its limit." : "That batch would exceed the " + spearTier.displayName().toLowerCase() + " spear limit. Craft at most " + n4 + "."), (TextColor)NamedTextColor.RED));
            }
            return;
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSuccessfulCraft(CraftItemEvent craftItemEvent) {
        SpearTier spearTier = CraftLimitListener.recipeTier(craftItemEvent.getRecipe().getResult());
        if (spearTier == null) {
            return;
        }
        int n = CraftLimitListener.calculateCraftedAmount(craftItemEvent);
        int n2 = this.settings.limit(spearTier);
        this.tracker.increment(spearTier, n);
        boolean bl = this.settings.limitEnabled(spearTier) && this.tracker.count(spearTier) >= n2;
        boolean bl2 = bl;
        if (this.settings.chatAnnouncements()) {
            Bukkit.broadcast((Component)Component.text((String)(craftItemEvent.getWhoClicked().getName() + " crafted " + n + " " + spearTier.displayName().toLowerCase() + " spear" + (n == 1 ? "" : "s") + "."), (TextColor)this.settings.color().textColor()));
            if (bl) {
                Bukkit.broadcast((Component)Component.text((String)(spearTier.displayName() + " spear crafting has reached its limit of " + n2 + "."), (TextColor)NamedTextColor.RED));
            }
        }
        if (this.settings.titleAnnouncements()) {
            CraftLimitListener.showTitle((Component)Component.text((String)"Spear Crafted", (TextColor)this.settings.color().textColor()), (Component)Component.text((String)(craftItemEvent.getWhoClicked().getName() + " crafted " + n + " " + spearTier.displayName().toLowerCase() + " spear" + (n == 1 ? "" : "s")), (TextColor)NamedTextColor.GRAY));
            if (bl) {
                this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> CraftLimitListener.showTitle((Component)Component.text((String)"Limit Reached", (TextColor)NamedTextColor.RED), (Component)Component.text((String)(spearTier.displayName() + " spear limit: " + n2), (TextColor)NamedTextColor.YELLOW)), 40L);
            }
        }
        this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, this.plugin.getVisualService()::refreshNow);
    }

    private boolean isAtLimit(SpearTier spearTier) {
        return this.settings.limitEnabled(spearTier) && this.tracker.count(spearTier) >= this.settings.limit(spearTier);
    }

    private static SpearTier recipeTier(ItemStack itemStack) {
        return SpearTier.fromItem(itemStack).orElse(null);
    }

    private static int calculateCraftedAmount(CraftItemEvent craftItemEvent) {
        int n = Math.max(1, craftItemEvent.getRecipe().getResult().getAmount());
        if (!craftItemEvent.isShiftClick()) {
            return n;
        }
        CraftingInventory craftingInventory = craftItemEvent.getInventory();
        int n2 = Integer.MAX_VALUE;
        for (ItemStack itemStack : craftingInventory.getMatrix()) {
            if (itemStack == null || itemStack.getType().isAir()) continue;
            n2 = Math.min(n2, itemStack.getAmount());
        }
        if (n2 == Integer.MAX_VALUE) {
            n2 = 1;
        }
        return Math.max(1, n2 * n);
    }

    private ItemStack blockedIndicator(SpearTier spearTier) {
        int n = this.settings.limit(spearTier);
        ItemStack itemStack = new ItemStack(Material.BARRIER);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(Component.text((String)(spearTier.displayName() + " Spear Limit Reached"), (TextColor)NamedTextColor.RED, (TextDecoration[])new TextDecoration[]{TextDecoration.BOLD}).decoration(TextDecoration.ITALIC, false));
        itemMeta.lore(List.of((TextComponent)Component.text((String)"This recipe is currently unavailable.", (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)("Tracked: " + this.tracker.count(spearTier) + " / " + n), (TextColor)NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Run /spearconfig refresh after removing spears.", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static void showTitle(Component component, Component component2) {
        Title title = Title.title((Component)component, (Component)component2, (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(2L), (Duration)Duration.ofMillis(500L)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }
}

