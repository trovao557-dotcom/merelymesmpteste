/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package net.skstudios.tpa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.skstudios.tpa.Settings;
import net.skstudios.tpa.TpaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class TeleportManager {
    private final TpaPlugin plugin;
    private final Map<UUID, Countdown> active = new HashMap<UUID, Countdown>();

    public TeleportManager(TpaPlugin tpaPlugin) {
        this.plugin = tpaPlugin;
    }

    public boolean isCounting(UUID uUID) {
        return this.active.containsKey(uUID);
    }

    public void begin(Player player, Player player2) {
        Settings settings = this.plugin.settings();
        Location location = player2.getLocation().clone();
        String string = player2.getName();
        if (settings.countdownSeconds() <= 0 || Settings.allowed(player, settings.permSkipCountdown())) {
            this.teleport(player, location, string);
            return;
        }
        Countdown countdown = this.active.get(player.getUniqueId());
        if (countdown != null) {
            this.finish(countdown);
        }
        Countdown countdown2 = new Countdown(player.getUniqueId(), location, string, player.getLocation().clone(), settings.countdownSeconds());
        this.active.put(player.getUniqueId(), countdown2);
        countdown2.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> this.tick(countdown2), 0L, 20L);
    }

    private void tick(Countdown countdown) {
        Player player = Bukkit.getPlayer((UUID)countdown.mover);
        if (player == null) {
            this.finish(countdown);
            return;
        }
        if (this.movedTooFar(countdown, player.getLocation())) {
            this.cancel(countdown.mover, true);
            return;
        }
        if (countdown.remaining > 0) {
            this.plugin.send((CommandSender)player, "teleport.countdown", Map.of("seconds", String.valueOf(countdown.remaining)));
            this.plugin.settings().sounds().play(player, "countdown-tick");
            --countdown.remaining;
            return;
        }
        this.finish(countdown);
        this.teleport(player, countdown.destination, countdown.destinationName);
    }

    private void teleport(Player player, Location location, String string) {
        player.teleportAsync(location).thenAccept(bl -> {
            if (Boolean.TRUE.equals(bl)) {
                this.plugin.send((CommandSender)player, "teleport.done", Map.of("player", string));
                this.plugin.settings().sounds().play(player, "teleported");
            }
        });
    }

    public void handleMove(Player player, Location location) {
        Countdown countdown = this.active.get(player.getUniqueId());
        if (countdown != null && this.movedTooFar(countdown, location)) {
            this.cancel(player.getUniqueId(), true);
        }
    }

    private boolean movedTooFar(Countdown countdown, Location location) {
        Settings settings = this.plugin.settings();
        World world = countdown.start.getWorld();
        if (world == null || !world.equals((Object)location.getWorld())) {
            return settings.cancelOnWorldChange() || world == null;
        }
        if (!settings.cancelOnMove()) {
            return false;
        }
        return location.distanceSquared(countdown.start) > settings.moveDistanceSquared();
    }

    public void cancel(UUID uUID, boolean bl) {
        Countdown countdown = this.active.get(uUID);
        if (countdown == null) {
            return;
        }
        this.finish(countdown);
        if (!bl) {
            return;
        }
        Player player = Bukkit.getPlayer((UUID)uUID);
        if (player != null) {
            this.plugin.send((CommandSender)player, "teleport.cancelled", Map.of());
            this.plugin.settings().sounds().play(player, "countdown-cancelled");
        }
    }

    private void finish(Countdown countdown) {
        this.active.remove(countdown.mover, countdown);
        if (countdown.task != null) {
            countdown.task.cancel();
            countdown.task = null;
        }
    }

    public void stopAll() {
        for (Countdown countdown : new HashMap<UUID, Countdown>(this.active).values()) {
            this.finish(countdown);
        }
        this.active.clear();
    }

    private static final class Countdown {
        private final UUID mover;
        private final Location destination;
        private final String destinationName;
        private final Location start;
        private int remaining;
        private BukkitTask task;

        private Countdown(UUID uUID, Location location, String string, Location location2, int n) {
            this.mover = uUID;
            this.destination = location;
            this.destinationName = string;
            this.start = location2;
            this.remaining = n;
        }
    }
}
