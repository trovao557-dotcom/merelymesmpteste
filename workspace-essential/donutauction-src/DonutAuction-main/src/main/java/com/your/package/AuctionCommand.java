package com.your.package;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class AuctionCommand implements CommandExecutor {
    private final JavaPlugin plugin;

    public AuctionCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("version")) {
            // Sends the requested message to the command sender
            sender.sendMessage(ChatColor.GREEN + "auction plugin forked by voxel");
            return true;
        }

        // Fallback usage message
        sender.sendMessage(ChatColor.RED + "Usage: /auction version");
        return true;
    }
}
