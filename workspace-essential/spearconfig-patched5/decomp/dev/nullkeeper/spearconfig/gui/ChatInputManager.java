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

    public ChatInputManager(SpearConfigPlugin spearConfigPlugin) {
        this.plugin = spearConfigPlugin;
    }

    public void requestInteger(Player player, String string, int n, int n2, IntConsumer intConsumer, Runnable runnable) {
        PendingInput pendingInput = new PendingInput(n, n2, intConsumer, runnable);
        this.pendingInputs.put(player.getUniqueId(), pendingInput);
        player.closeInventory();
        player.sendMessage((Component)Component.text((String)("Enter " + string + " in chat (" + n + "\u2013" + n2 + "), or type cancel."), (TextColor)NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (!this.pendingInputs.remove(player.getUniqueId(), pendingInput)) {
                return;
            }
            if (player.isOnline()) {
                player.sendMessage((Component)Component.text((String)"Numeric input timed out.", (TextColor)NamedTextColor.RED));
                pendingInput.reopen().run();
            }
        }, 1200L);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncChatEvent asyncChatEvent) {
        Player player = asyncChatEvent.getPlayer();
        PendingInput pendingInput = this.pendingInputs.get(player.getUniqueId());
        if (pendingInput == null) {
            return;
        }
        asyncChatEvent.setCancelled(true);
        String string = PlainTextComponentSerializer.plainText().serialize(asyncChatEvent.message()).trim();
        if ("cancel".equalsIgnoreCase(string)) {
            if (this.pendingInputs.remove(player.getUniqueId(), pendingInput)) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    player.sendMessage((Component)Component.text((String)"Numeric input cancelled.", (TextColor)NamedTextColor.GRAY));
                    pendingInput.reopen().run();
                });
            }
            return;
        }
        Integer n = ChatInputManager.parseInteger(string);
        if (n == null || n < pendingInput.minimum() || n > pendingInput.maximum()) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.sendMessage((Component)Component.text((String)("Enter a whole number from " + pendingInput.minimum() + " to " + pendingInput.maximum() + ", or type cancel."), (TextColor)NamedTextColor.RED)));
            return;
        }
        if (this.pendingInputs.remove(player.getUniqueId(), pendingInput)) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> pendingInput.onValue().accept(n));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.pendingInputs.remove(playerQuitEvent.getPlayer().getUniqueId());
    }

    public void clear() {
        this.pendingInputs.clear();
    }

    private static Integer parseInteger(String string) {
        try {
            return Integer.valueOf(string);
        }
        catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private record PendingInput(int minimum, int maximum, IntConsumer onValue, Runnable reopen) {
    }
}

