/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.event.player.AsyncChatEvent
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 */
package dev.nullkeeper.spearconfig.gui;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class ChatInputManager
implements Listener {
    private static final long TIMEOUT_TICKS = 1200L;
    private final SpearConfigPlugin plugin;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<UUID, PendingInput>();

    public ChatInputManager(SpearConfigPlugin plugin) {
        this.plugin = plugin;
    }

    public void requestInteger(Player player, String label, int minimum, int maximum, IntConsumer onValue, Runnable reopen) {
        PendingInput pending = new PendingInput(minimum, maximum, onValue, reopen);
        this.pendingInputs.put(player.getUniqueId(), pending);
        player.closeInventory();
        player.sendMessage((Component)Component.text((String)("Enter " + label + " in chat (" + minimum + "\u2013" + maximum + "), or type cancel."), (TextColor)NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (!this.pendingInputs.remove(player.getUniqueId(), pending)) {
                return;
            }
            if (player.isOnline()) {
                player.sendMessage((Component)Component.text((String)"Numeric input timed out.", (TextColor)NamedTextColor.RED));
                pending.reopen().run();
            }
        }, 1200L);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = this.pendingInputs.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if ("cancel".equalsIgnoreCase(input)) {
            if (this.pendingInputs.remove(player.getUniqueId(), pending)) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    player.sendMessage((Component)Component.text((String)"Numeric input cancelled.", (TextColor)NamedTextColor.GRAY));
                    pending.reopen().run();
                });
            }
            return;
        }
        Integer value = ChatInputManager.parseInteger(input);
        if (value == null || value < pending.minimum() || value > pending.maximum()) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.sendMessage((Component)Component.text((String)("Enter a whole number from " + pending.minimum() + " to " + pending.maximum() + ", or type cancel."), (TextColor)NamedTextColor.RED)));
            return;
        }
        if (this.pendingInputs.remove(player.getUniqueId(), pending)) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> pending.onValue().accept(value));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        this.pendingInputs.clear();
    }

    private static Integer parseInteger(String input) {
        try {
            return Integer.valueOf(input);
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private record PendingInput(int minimum, int maximum, IntConsumer onValue, Runnable reopen) {
    }
}

