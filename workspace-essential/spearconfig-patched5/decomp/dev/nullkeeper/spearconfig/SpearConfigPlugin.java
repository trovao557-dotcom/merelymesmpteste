/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.PluginManager
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.nullkeeper.spearconfig;

import dev.nullkeeper.spearconfig.command.SpearConfigCommand;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.gui.ChatInputManager;
import dev.nullkeeper.spearconfig.gui.ConfigGui;
import dev.nullkeeper.spearconfig.listener.AttackCooldownListener;
import dev.nullkeeper.spearconfig.listener.CraftLimitListener;
import dev.nullkeeper.spearconfig.listener.EnchantmentListener;
import dev.nullkeeper.spearconfig.listener.MobDropListener;
import dev.nullkeeper.spearconfig.model.SpearTier;
import dev.nullkeeper.spearconfig.service.CooldownManager;
import dev.nullkeeper.spearconfig.service.RevealService;
import dev.nullkeeper.spearconfig.service.SpearTracker;
import dev.nullkeeper.spearconfig.service.VisualService;
import java.util.Map;
import java.util.Objects;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpearConfigPlugin
extends JavaPlugin {
    private PluginSettings settings;
    private SpearTracker tracker;
    private CooldownManager cooldowns;
    private VisualService visualService;
    private RevealService revealService;
    private ChatInputManager chatInputManager;
    private ConfigGui configGui;

    public void onEnable() {
        this.saveDefaultConfig();
        this.settings = new PluginSettings(this);
        this.tracker = new SpearTracker();
        this.cooldowns = new CooldownManager();
        this.visualService = new VisualService(this, this.settings, this.tracker);
        this.revealService = new RevealService(this, this.settings, this.tracker);
        this.chatInputManager = new ChatInputManager(this);
        this.configGui = new ConfigGui(this, this.settings, this.tracker, this.chatInputManager);
        this.settings.reload();
        this.tracker.refreshOnlinePlayers();
        PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents((Listener)new CraftLimitListener(this, this.settings, this.tracker), (Plugin)this);
        pluginManager.registerEvents((Listener)new MobDropListener(this.settings), (Plugin)this);
        pluginManager.registerEvents((Listener)new AttackCooldownListener(this.settings, this.cooldowns), (Plugin)this);
        pluginManager.registerEvents((Listener)new EnchantmentListener(this.settings), (Plugin)this);
        pluginManager.registerEvents((Listener)this.visualService, (Plugin)this);
        pluginManager.registerEvents((Listener)this.chatInputManager, (Plugin)this);
        pluginManager.registerEvents((Listener)this.configGui, (Plugin)this);
        SpearConfigCommand spearConfigCommand = new SpearConfigCommand(this, this.settings, this.tracker, this.configGui);
        PluginCommand pluginCommand = Objects.requireNonNull(this.getCommand("spearconfig"), "spearconfig command is missing from plugin.yml");
        pluginCommand.setExecutor((CommandExecutor)spearConfigCommand);
        pluginCommand.setTabCompleter((TabCompleter)spearConfigCommand);
        this.visualService.start();
        this.revealService.restart();
        this.getLogger().info("Spear Config is enabled for Paper 26.1-26.2.");
    }

    public void onDisable() {
        if (this.revealService != null) {
            this.revealService.stop();
        }
        if (this.visualService != null) {
            this.visualService.stop();
        }
        if (this.chatInputManager != null) {
            this.chatInputManager.clear();
        }
    }

    public void applyConfigurationChanges() {
        this.saveConfig();
        this.settings.reload();
        this.cooldowns.clear();
        this.revealService.restart();
        this.visualService.refreshNow();
        this.visualService.updateDroppedItems();
    }

    public void reloadPluginConfiguration() {
        this.reloadConfig();
        this.settings.reload();
        this.cooldowns.clear();
        this.revealService.restart();
        this.visualService.refreshNow();
        this.visualService.updateDroppedItems();
    }

    public Map<SpearTier, Integer> refreshTrackedSpears() {
        Map<SpearTier, Integer> map = this.tracker.refreshOnlinePlayers();
        this.visualService.refreshNow();
        return map;
    }

    public VisualService getVisualService() {
        return this.visualService;
    }
}

