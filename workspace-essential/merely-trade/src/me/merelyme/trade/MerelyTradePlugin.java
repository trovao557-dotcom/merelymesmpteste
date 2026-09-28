package me.merelyme.trade;

import net.skstudios.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class MerelyTradePlugin extends JavaPlugin {
    private TradeManager trades;

    @Override
    public void onEnable() {
        this.trades = new TradeManager(this);
        Bukkit.getPluginManager().registerEvents(new TradeListener(this), this);
        getLogger().info("MerelyTrade ready - item trades only.");
    }

    @Override
    public void onDisable() {
        if (this.trades != null) {
            this.trades.shutdown();
        }
    }

    public TradeManager trades() {
        return this.trades;
    }

    public boolean blocksRequests(Player player) {
        return player.hasPermission("merelytrade.deny");
    }

    public void say(CommandSender sender, String message) {
        sender.sendMessage(Text.chat("&#00B5FF&lSMP &#9B9B9B» " + message));
    }

    public void say(CommandSender sender, String message, Map<String, String> vars) {
        this.say(sender, Text.apply(message, vars));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.say(sender, "&#FF2121Players only.");
            return true;
        }
        String name = command.getName().toLowerCase();
        if (name.equals("tradeaccept")) {
            return this.trades.accept(player, args.length > 0 ? args[0] : null);
        }
        if (name.equals("tradedeny")) {
            return this.trades.deny(player, args.length > 0 ? args[0] : null);
        }
        if (args.length == 0) {
            this.say(player, "&fUsage: &#00B5FF/trade <player>");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("accept") || sub.equals("aceitar")) {
            return this.trades.accept(player, args.length > 1 ? args[1] : null);
        }
        if (sub.equals("deny") || sub.equals("recusar")) {
            return this.trades.deny(player, args.length > 1 ? args[1] : null);
        }
        if (sub.equals("cancel") || sub.equals("cancelar")) {
            return this.trades.cancelByPlayer(player);
        }
        return this.trades.request(player, args[0]);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("trade") && args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> !n.equalsIgnoreCase(player.getName()))
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(20)
                    .toList();
        }
        return List.of();
    }
}
