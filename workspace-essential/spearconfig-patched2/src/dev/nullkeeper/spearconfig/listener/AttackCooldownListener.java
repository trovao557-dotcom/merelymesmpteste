/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.event.entity.EntityLungeEvent
 *  io.papermc.paper.event.player.PrePlayerAttackEntityEvent
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package dev.nullkeeper.spearconfig.listener;

import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.model.AttackType;
import dev.nullkeeper.spearconfig.model.SpearTier;
import dev.nullkeeper.spearconfig.service.CooldownManager;
import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AttackCooldownListener
implements Listener {
    private final PluginSettings settings;
    private final CooldownManager cooldowns;

    public AttackCooldownListener(PluginSettings settings, CooldownManager cooldowns) {
        this.settings = settings;
        this.cooldowns = cooldowns;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onJab(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack() || SpearTier.fromItem(event.getPlayer().getInventory().getItemInMainHand()).isEmpty()) {
            return;
        }
        this.check(event.getPlayer(), AttackType.JAB, arg_0 -> ((PrePlayerAttackEntityEvent)event).setCancelled(arg_0));
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onLunge(EntityLungeEvent event) {
        Player player;
        Entity entity = event.getEntity();
        if (!(entity instanceof Player) || SpearTier.fromItem((player = (Player)entity).getInventory().getItemInMainHand()).isEmpty()) {
            return;
        }
        this.check(player, AttackType.LUNGE, arg_0 -> ((EntityLungeEvent)event).setCancelled(arg_0));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void check(Player player, AttackType type, Cancellation cancellation) {
        if (!this.settings.cooldownEnabled(type)) {
            return;
        }
        CooldownManager.CooldownResult result = this.cooldowns.tryUse(player.getUniqueId(), type, this.settings.cooldown(type));
        if (result.allowed()) {
            return;
        }
        cancellation.cancel(true);
        double seconds = (double)result.remaining().toNanos() / 1.0E9;
        player.sendActionBar((Component)Component.text((String)(type.displayName() + " cooldown: " + String.format(Locale.ROOT, "%.1f", seconds) + "s"), (TextColor)NamedTextColor.RED));
    }

    @FunctionalInterface
    private static interface Cancellation {
        public void cancel(boolean var1);
    }
}

