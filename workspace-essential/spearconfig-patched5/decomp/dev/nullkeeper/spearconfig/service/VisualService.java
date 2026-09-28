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

    public VisualService(SpearConfigPlugin spearConfigPlugin, PluginSettings pluginSettings, SpearTracker spearTracker) {
        this.plugin = spearConfigPlugin;
        this.settings = pluginSettings;
        this.tracker = spearTracker;
    }

    public void start() {
        if (this.task != null) {
            this.task.cancel();
        }
        this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::refreshNow, 1L, 20L);
        this.updateDroppedItems();
    }

    public void stop() {
        Player player2;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (Player player2 : Bukkit.getOnlinePlayers()) {
            this.restore(player2);
        }
        this.states.clear();
        Scoreboard scoreboard = VisualService.mainScoreboard();
        Team team = scoreboard == null ? null : scoreboard.getTeam(TEAM_NAME);
        player2 = team;
        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    public void refreshNow() {
        Team team = this.settings.glow() ? this.ensureGlowTeam() : this.existingGlowTeam();
        Team team2 = team;
        if (team != null) {
            team.color(this.settings.color().textColor());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.tracker.playerInventoryContainsSpear(player)) {
                this.apply(player, team);
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
    public void onItemSpawn(ItemSpawnEvent itemSpawnEvent) {
        this.styleDroppedItem(itemSpawnEvent.getEntity());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemMerge(ItemMergeEvent itemMergeEvent) {
        this.styleDroppedItem(itemMergeEvent.getTarget());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, this::refreshNow);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.restore(playerQuitEvent.getPlayer());
        this.states.remove(playerQuitEvent.getPlayer().getUniqueId());
    }

    private void apply(Player player, Team team) {
        PlayerVisualState playerVisualState = this.states.computeIfAbsent(player.getUniqueId(), uUID -> new PlayerVisualState());
        if (this.settings.glow()) {
            if (!playerVisualState.glowApplied) {
                playerVisualState.originalGlowing = player.isGlowing();
                Team team2 = VisualService.entryTeam(player);
                playerVisualState.originalTeamName = team2 == null ? null : team2.getName();
                playerVisualState.glowApplied = true;
            }
            if (team != null) {
                team.addEntry(player.getName());
            }
            player.setGlowing(true);
        } else {
            this.restoreGlow(player, playerVisualState);
        }
        if (this.settings.tab()) {
            if (!playerVisualState.tabApplied) {
                playerVisualState.originalPlayerListName = player.playerListName();
                playerVisualState.tabApplied = true;
            }
            player.playerListName((Component)Component.text((String)player.getName(), (TextColor)this.settings.color().textColor()));
        } else {
            VisualService.restoreTab(player, playerVisualState);
        }
        if (!playerVisualState.glowApplied && !playerVisualState.tabApplied) {
            this.states.remove(player.getUniqueId());
        }
    }

    private void restore(Player player) {
        PlayerVisualState playerVisualState = this.states.get(player.getUniqueId());
        if (playerVisualState == null) {
            return;
        }
        this.restoreGlow(player, playerVisualState);
        VisualService.restoreTab(player, playerVisualState);
        this.states.remove(player.getUniqueId());
    }

    private void restoreGlow(Player player, PlayerVisualState playerVisualState) {
        if (!playerVisualState.glowApplied) {
            return;
        }
        Team team = VisualService.entryTeam(player);
        if (team != null && TEAM_NAME.equals(team.getName())) {
            team.removeEntry(player.getName());
            if (playerVisualState.originalTeamName != null) {
                Scoreboard scoreboard = VisualService.mainScoreboard();
                Team team2 = scoreboard == null ? null : scoreboard.getTeam(playerVisualState.originalTeamName);
                Team team3 = team2;
                if (team2 != null) {
                    team2.addEntry(player.getName());
                }
            }
        }
        player.setGlowing(playerVisualState.originalGlowing);
        playerVisualState.glowApplied = false;
    }

    private static void restoreTab(Player player, PlayerVisualState playerVisualState) {
        if (!playerVisualState.tabApplied) {
            return;
        }
        player.playerListName(playerVisualState.originalPlayerListName);
        playerVisualState.tabApplied = false;
    }

    private void styleDroppedItem(Item item) {
        SpearTier.fromItem(item.getItemStack()).ifPresent(spearTier -> {
            item.customName((Component)Component.text((String)(spearTier.displayName() + " Spear"), (TextColor)this.settings.color().textColor()));
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

