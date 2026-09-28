/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.event.player.AsyncChatEvent
 *  net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package net.skstudios.orders;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatPrompt
implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<UUID, Pending>();
    private List<String> cancelWords = List.of("cancel");
    private long timeoutMillis = 60000L;
    private final Consumer<UUID> onQuit;

    public ChatPrompt(JavaPlugin javaPlugin, Consumer<UUID> consumer) {
        this.plugin = javaPlugin;
        this.onQuit = consumer;
    }

    public void configure(List<String> list, int n) {
        this.cancelWords = list.stream().map(string -> string.toLowerCase(Locale.ROOT)).toList();
        this.timeoutMillis = (long)Math.max(5, n) * 1000L;
    }

    public void ask(Player player, Consumer<String> consumer) {
        this.waiting.put(player.getUniqueId(), new Pending(consumer, System.currentTimeMillis() + this.timeoutMillis));
    }

    public void forget(UUID uUID) {
        this.waiting.remove(uUID);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onChat(AsyncChatEvent asyncChatEvent) {
        UUID uUID = asyncChatEvent.getPlayer().getUniqueId();
        Pending pending = this.waiting.get(uUID);
        if (pending == null) {
            return;
        }
        this.waiting.remove(uUID);
        if (System.currentTimeMillis() > pending.expiresAt()) {
            return;
        }
        asyncChatEvent.setCancelled(true);
        String string = PlainTextComponentSerializer.plainText().serialize(asyncChatEvent.message()).trim();
        boolean bl = this.cancelWords.contains(string.toLowerCase(Locale.ROOT));
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> pending.handler().accept(bl ? null : string));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        UUID uUID = playerQuitEvent.getPlayer().getUniqueId();
        this.waiting.remove(uUID);
        this.onQuit.accept(uUID);
    }

    private record Pending(Consumer<String> handler, long expiresAt) {
    }
}

