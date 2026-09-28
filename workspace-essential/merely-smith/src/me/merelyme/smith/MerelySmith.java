package me.merelyme.smith;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MerelySmith extends JavaPlugin implements CommandExecutor {
    private static final String PREFIX = "§x§0§0§B§5§F§F§lSMP §x§9§B§9§B§9§B» ";

    @Override
    public void onEnable() {
        getCommand("smith").setExecutor(this);
        getLogger().info("MerelySmith ready - /smith [player]");
    }

    private boolean canUse(CommandSender sender) {
        return sender.isOp()
            || sender.hasPermission("merelysmith.use")
            || sender.hasPermission("group.prime")
            || sender.hasPermission("group.macer")
            || sender.hasPermission("group.helper")
            || sender.hasPermission("group.mod")
            || sender.hasPermission("group.admin")
            || sender.hasPermission("group.owner");
    }

    private boolean canOpenForOthers(CommandSender sender) {
        return sender.isOp()
            || sender.hasPermission("merelysmith.others")
            || sender.hasPermission("group.helper")
            || sender.hasPermission("group.mod")
            || sender.hasPermission("group.admin")
            || sender.hasPermission("group.owner");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(PREFIX + "§x§F§F§2§1§2§1You cannot use this!");
            return true;
        }

        Player target;
        if (args.length >= 1) {
            if (!canOpenForOthers(sender)) {
                sender.sendMessage(PREFIX + "§x§F§F§2§1§2§1You cannot open this for other players!");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(PREFIX + "§x§F§F§2§1§2§1Player is not online!");
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + "§7Usage: /smith <player>");
                return true;
            }
            target = player;
        }

        target.openSmithingTable(null, true);
        if (sender != target) {
            sender.sendMessage(PREFIX + "§x§7§1§F§5§2§1Opened smithing table for §f" + target.getName() + "§x§7§1§F§5§2§1.");
        }
        return true;
    }
}
