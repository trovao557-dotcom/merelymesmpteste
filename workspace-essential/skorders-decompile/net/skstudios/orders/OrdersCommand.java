/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.skstudios.core.data.PlayerRecord
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package net.skstudios.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.skstudios.core.data.PlayerRecord;
import net.skstudios.orders.Order;
import net.skstudios.orders.OrdersPlugin;
import net.skstudios.orders.SortMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class OrdersCommand
implements CommandExecutor,
TabCompleter {
    private final OrdersPlugin module;

    public OrdersCommand(OrdersPlugin ordersPlugin) {
        this.module = ordersPlugin;
    }

    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String string, @NotNull String[] stringArray) {
        if (stringArray.length == 0) {
            this.open(commandSender);
            return true;
        }
        switch (stringArray[0].toLowerCase()) {
            case "reload": {
                this.reload(commandSender);
                break;
            }
            case "list": {
                this.list(commandSender, stringArray);
                break;
            }
            case "info": {
                this.info(commandSender, stringArray);
                break;
            }
            case "remove": {
                this.remove(commandSender, stringArray);
                break;
            }
            default: {
                this.module.message(commandSender, "command.usage");
            }
        }
        return true;
    }

    private void open(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            this.module.message(commandSender, "command.players-only");
            return;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("skorders.use")) {
            this.module.message((CommandSender)player, "no-permission");
            return;
        }
        this.module.openOverview(player);
    }

    private void reload(CommandSender commandSender) {
        if (!commandSender.hasPermission("skorders.admin")) {
            this.module.message(commandSender, "no-permission");
            return;
        }
        this.module.reload();
        this.module.message(commandSender, "command.reloaded");
    }

    private void list(CommandSender commandSender, String[] stringArray) {
        List<Order> list;
        if (!commandSender.hasPermission("skorders.admin")) {
            this.module.message(commandSender, "no-permission");
            return;
        }
        if (stringArray.length >= 2) {
            PlayerRecord playerRecord = this.module.players().findByName(stringArray[1]);
            if (playerRecord == null) {
                this.module.message(commandSender, "command.unknown-player", Map.of("player", stringArray[1]));
                return;
            }
            list = this.module.store().ordersOf(playerRecord.uuid());
        } else {
            list = new ArrayList<Order>(this.module.store().all());
        }
        this.module.message(commandSender, "command.list-header", Map.of("count", String.valueOf(list.size())));
        for (Order order : list) {
            this.module.message(commandSender, "command.list-entry", this.module.placeholders(order));
        }
    }

    private void info(CommandSender commandSender, String[] stringArray) {
        if (!commandSender.hasPermission("skorders.admin")) {
            this.module.message(commandSender, "no-permission");
            return;
        }
        Order order = this.parse(commandSender, stringArray);
        if (order != null) {
            this.module.message(commandSender, "command.list-entry", this.module.placeholders(order));
        }
    }

    private void remove(CommandSender commandSender, String[] stringArray) {
        if (!commandSender.hasPermission("skorders.admin")) {
            this.module.message(commandSender, "no-permission");
            return;
        }
        Order order = this.parse(commandSender, stringArray);
        if (order != null) {
            this.module.forceRemove(commandSender, order);
        }
    }

    private Order parse(CommandSender commandSender, String[] stringArray) {
        long l;
        if (stringArray.length < 2) {
            this.module.message(commandSender, "command.usage");
            return null;
        }
        try {
            l = Long.parseLong(stringArray[1]);
        }
        catch (NumberFormatException numberFormatException) {
            this.module.message(commandSender, "command.unknown-order", Map.of("id", stringArray[1]));
            return null;
        }
        Order order = this.module.store().byId(l);
        if (order == null) {
            this.module.message(commandSender, "command.unknown-order", Map.of("id", stringArray[1]));
        }
        return order;
    }

    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String string, @NotNull String[] stringArray) {
        if (!commandSender.hasPermission("skorders.admin")) {
            return Collections.emptyList();
        }
        if (stringArray.length == 1) {
            return OrdersCommand.filter(List.of("reload", "list", "info", "remove"), stringArray[0]);
        }
        if (stringArray.length == 2) {
            if (stringArray[0].equalsIgnoreCase("list")) {
                ArrayList<String> arrayList = new ArrayList<String>();
                Bukkit.getOnlinePlayers().forEach(player -> arrayList.add(player.getName()));
                return OrdersCommand.filter(arrayList, stringArray[1]);
            }
            if (stringArray[0].equalsIgnoreCase("info") || stringArray[0].equalsIgnoreCase("remove")) {
                ArrayList<String> arrayList = new ArrayList<String>();
                for (Order order : this.module.store().view(SortMode.RECENT)) {
                    arrayList.add(String.valueOf(order.id()));
                    if (arrayList.size() < 50) continue;
                    break;
                }
                return OrdersCommand.filter(arrayList, stringArray[1]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> list, String string) {
        String string2 = string.toLowerCase();
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string3 : list) {
            if (!string3.toLowerCase().startsWith(string2)) continue;
            arrayList.add(string3);
        }
        return arrayList;
    }
}

