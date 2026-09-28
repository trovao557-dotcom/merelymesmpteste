package me.merelyme.ahlore;

import org.bukkit.plugin.java.JavaPlugin;

public final class MerelyAhLore extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new DonutAhListener(this), this);
        getLogger().info("AH lore sync enabled for Paper 1.21+ (all item types).");
    }
}
