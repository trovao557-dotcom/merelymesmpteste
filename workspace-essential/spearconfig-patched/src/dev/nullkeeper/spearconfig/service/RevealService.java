/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package dev.nullkeeper.spearconfig.service;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.service.SpearTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class RevealService {
    private final SpearConfigPlugin plugin;
    private final PluginSettings settings;
    private final SpearTracker tracker;
    private BukkitTask task;

    public RevealService(SpearConfigPlugin plugin, PluginSettings settings, SpearTracker tracker) {
        this.plugin = plugin;
        this.settings = settings;
        this.tracker = tracker;
    }

    public void restart() {
        this.stop();
        if (!this.settings.revealEnabled()) {
            return;
        }
        long intervalTicks = (long)this.settings.revealInterval() * 20L;
        this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::broadcastWielders, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void broadcastWielders() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!this.tracker.playerInventoryContainsSpear(player)) continue;
            Location location = player.getLocation();
            Bukkit.broadcast((Component)((TextComponent)Component.text((String)"[Spear] ", (TextColor)this.settings.color().textColor()).append((Component)Component.text((String)player.getName(), (TextColor)this.settings.color().textColor()))).append((Component)Component.text((String)(" is in " + player.getWorld().getKey().asString() + " at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()), (TextColor)NamedTextColor.GRAY)));
        }
    }
}

