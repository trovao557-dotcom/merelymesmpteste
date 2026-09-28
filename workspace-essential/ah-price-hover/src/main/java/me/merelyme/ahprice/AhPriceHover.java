package me.merelyme.ahprice;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AhPriceHover extends JavaPlugin {
    private AhMenuListener listener;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("SKAuction") == null) {
            getLogger().warning("SKAuction not found - AhPriceHover idle.");
            return;
        }
        this.listener = new AhMenuListener(this);
        Bukkit.getPluginManager().registerEvents(this.listener, this);
        getLogger().info("AH lore sync enabled.");
    }

    @Override
    public void onDisable() {
        if (this.listener != null) {
            this.listener.shutdown();
        }
    }
}
