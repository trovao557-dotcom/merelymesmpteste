package com.your.package;

import org.bukkit.plugin.java.JavaPlugin;

public class DonutAuction extends JavaPlugin {
    @Override
    public void onEnable() {
        // register the auction command executor
        if (this.getCommand("auction") != null) {
            this.getCommand("auction").setExecutor(new AuctionCommand(this));
        } else {
            getLogger().warning("Command 'auction' not defined in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        // any cleanup
    }
}
