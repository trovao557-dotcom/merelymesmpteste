/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.ItemMergeEvent
 *  org.bukkit.event.entity.ItemSpawnEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 *  org.bukkit.scoreboard.Scoreboard
 *  org.bukkit.scoreboard.Team
 */
package dev.nullkeeper.spearconfig.service;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.model.SpearTier;
import dev.nullkeeper.spearconfig.service.SpearTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class VisualService
implements Listener {
    private static final String TEAM_NAME = "sc_wielders";
    private final SpearConfigPlugin plugin;
    private final PluginSettings settings;
    private final SpearTracker tracker;
    private final Map<UUID, PlayerVisualState> states = new HashMap<UUID, PlayerVisualState>();
    private BukkitTask task;

    public VisualService(SpearConfigPlugin plugin, PluginSettings settings, SpearTracker tracker) {
        this.plugin = plugin;
        this.settings = settings;
        this.tracker = tracker;
    }

    public void start() {
        if (this.task != null) {
            this.task.cancel();
        }
        this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::refreshNow, 1L, 20L);
        this.updateDroppedItems();
    }

    public void stop() {
        Team team;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.restore(player);
        }
        this.states.clear();
        Scoreboard scoreboard = VisualService.mainScoreboard();
        Team team2 = team = scoreboard == null ? null : scoreboard.getTeam(TEAM_NAME);
        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    public void refreshNow() {
        Team glowTeam;
        Team team = glowTeam = this.settings.glow() ? this.ensureGlowTeam() : this.existingGlowTeam();
        if (glowTeam != null) {
            glowTeam.color(this.settings.color().textColor());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.tracker.playerInventoryContainsSpear(player)) {
                this.apply(player, glowTeam);
                continue;
            }
            this.restore(player);
        }
    }

    public void updateDroppedItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                this.styleDroppedItem(item);
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemSpawn(ItemSpawnEvent event) {
        this.styleDroppedItem(event.getEntity());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemMerge(ItemMergeEvent event) {
        this.styleDroppedItem(event.getTarget());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, this::refreshNow);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.restore(event.getPlayer());
        this.states.remove(event.getPlayer().getUniqueId());
    }

    private void apply(Player player, Team glowTeam) {
        PlayerVisualState state = this.states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerVisualState());
        if (this.settings.glow()) {
            if (!state.glowApplied) {
                state.originalGlowing = player.isGlowing();
                Team currentTeam = VisualService.entryTeam(player);
                state.originalTeamName = currentTeam == null ? null : currentTeam.getName();
                state.glowApplied = true;
            }
            if (glowTeam != null) {
                glowTeam.addEntry(player.getName());
            }
            player.setGlowing(true);
        } else {
            this.restoreGlow(player, state);
        }
        if (this.settings.tab()) {
            if (!state.tabApplied) {
                state.originalPlayerListName = player.playerListName();
                state.tabApplied = true;
            }
            player.playerListName((Component)Component.text((String)player.getName(), (TextColor)this.settings.color().textColor()));
        } else {
            VisualService.restoreTab(player, state);
        }
        if (!state.glowApplied && !state.tabApplied) {
            this.states.remove(player.getUniqueId());
        }
    }

    private void restore(Player player) {
        PlayerVisualState state = this.states.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        this.restoreGlow(player, state);
        VisualService.restoreTab(player, state);
        this.states.remove(player.getUniqueId());
    }

    private void restoreGlow(Player player, PlayerVisualState state) {
        if (!state.glowApplied) {
            return;
        }
        Team currentTeam = VisualService.entryTeam(player);
        if (currentTeam != null && TEAM_NAME.equals(currentTeam.getName())) {
            currentTeam.removeEntry(player.getName());
            if (state.originalTeamName != null) {
                Team original;
                Scoreboard scoreboard = VisualService.mainScoreboard();
                Team team = original = scoreboard == null ? null : scoreboard.getTeam(state.originalTeamName);
                if (original != null) {
                    original.addEntry(player.getName());
                }
            }
        }
        player.setGlowing(state.originalGlowing);
        state.glowApplied = false;
    }

    private static void restoreTab(Player player, PlayerVisualState state) {
        if (!state.tabApplied) {
            return;
        }
        player.playerListName(state.originalPlayerListName);
        state.tabApplied = false;
    }

    private void styleDroppedItem(Item item) {
        SpearTier.fromItem(item.getItemStack()).ifPresent(tier -> {
            item.customName((Component)Component.text((String)(tier.displayName() + " Spear"), (TextColor)this.settings.color().textColor()));
            item.setCustomNameVisible(true);
        });
    }

    private Team ensureGlowTeam() {
        Scoreboard scoreboard = VisualService.mainScoreboard();
        if (scoreboard == null) {
            return null;
        }
        Team team = scoreboard.getTeam(TEAM_NAME);
        return team == null ? scoreboard.registerNewTeam(TEAM_NAME) : team;
    }

    private Team existingGlowTeam() {
        Scoreboard scoreboard = VisualService.mainScoreboard();
        return scoreboard == null ? null : scoreboard.getTeam(TEAM_NAME);
    }

    private static Team entryTeam(Player player) {
        Scoreboard scoreboard = VisualService.mainScoreboard();
        return scoreboard == null ? null : scoreboard.getEntryTeam(player.getName());
    }

    private static Scoreboard mainScoreboard() {
        return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private static final class PlayerVisualState {
        private boolean originalGlowing;
        private String originalTeamName;
        private Component originalPlayerListName;
        private boolean glowApplied;
        private boolean tabApplied;

        private PlayerVisualState() {
        }
    }
}

